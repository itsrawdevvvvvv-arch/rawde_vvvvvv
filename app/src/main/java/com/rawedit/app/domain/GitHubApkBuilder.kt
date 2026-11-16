package com.rawedit.app.domain

import android.util.Base64
import com.rawedit.app.data.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class ApkBuildOutcome(
    val apkFile: File,
    val repoFullName: String,
    val runUrl: String,
    val notes: String
)

object GitHubApkBuilder {

    private const val WORKFLOW_YAML = """name: Build Debug APK
on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew || true
      - name: Build Debug APK
        run: ./gradlew assembleDebug --no-daemon --stacktrace
      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: raw-edit-debug-apk
          path: app/build/outputs/apk/debug/*.apk
"""

    suspend fun buildApkFromZip(
        zipFile: File,
        githubToken: String,
        geminiRouter: GeminiWaterfallRouter,
        geminiApiKey: String,
        cacheManager: CacheManager
    ): Result<ApkBuildOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val token = githubToken.trim().removePrefix("Bearer ").removePrefix("token ").trim()
            if (token.isEmpty()) {
                throw IllegalStateException("GitHub token is missing. Add your GitHub Personal Access Token (repo + workflow scopes) in Profile -> Settings first.")
            }
            if (!zipFile.exists()) {
                throw IllegalStateException("Uploaded ZIP file not found: ${zipFile.name}")
            }

            // 1. Get GitHub user login
            val userObj = ghJson("GET", "https://api.github.com/user", token)
            val owner = userObj.optString("login").ifBlank {
                throw IllegalStateException("Could not verify GitHub user.")
            }

            // 2. Ensure builder repo exists
            val repoName = "raw-edit-cloud-builder"
            val repoCheck = ghRaw("GET", "https://api.github.com/repos/$owner/$repoName", token)
            if (repoCheck.first == 404) {
                val body = JSONObject().apply {
                    put("name", repoName)
                    put("description", "Automated Android APK builder for RAW EDIT")
                    put("private", true)
                    put("auto_init", true)
                }
                ghJson("POST", "https://api.github.com/user/repos", token, body.toString())
                delay(1500)
            } else if (repoCheck.first !in 200..299) {
                throw IllegalStateException("GitHub repo check failed (HTTP ${repoCheck.first}): ${repoCheck.second.take(150)}")
            }

            // 3. Extract ZIP entries and strip common top-level wrapper folder (e.g., "raw_edit/")
            val rawEntries = LinkedHashMap<String, ByteArray>()
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.replace("\\", "/").trimStart('/')
                        if (name.isNotBlank() &&
                            !name.contains("/build/") &&
                            !name.startsWith("build/") &&
                            !name.contains("/.gradle/") &&
                            !name.contains("/.kotlin/") &&
                            !name.contains("/.idea/") &&
                            !name.endsWith("local.properties")
                        ) {
                            rawEntries[name] = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (rawEntries.isEmpty()) {
                throw IllegalStateException("ZIP archive is empty or contains no buildable files.")
            }

            val firstSegments = rawEntries.keys.map { it.substringBefore('/') }.distinct()
            val stripPrefix = if (firstSegments.size == 1 && rawEntries.keys.any { it.contains('/') }) {
                "${firstSegments.first()}/"
            } else ""

            val repoFiles = LinkedHashMap<String, ByteArray>()
            rawEntries.forEach { (k, v) ->
                val cleanPath = if (stripPrefix.isNotEmpty()) k.removePrefix(stripPrefix) else k
                if (cleanPath.isNotBlank()) repoFiles[cleanPath] = v
            }
            repoFiles[".github/workflows/build_apk.yml"] = WORKFLOW_YAML.toByteArray(Charsets.UTF_8)

            // 4. Push all files in ONE atomic Git commit via Git Data API
            pushTreeCommit(owner, repoName, token, repoFiles, "Upload ${zipFile.name} for APK build")

            // 5. Poll GitHub Actions run + self-heal up to 1 retry if compile error occurs
            var attempt = 0
            var lastRunUrl = "https://github.com/$owner/$repoName/actions"
            while (attempt < 2) {
                attempt++
                delay(5000)
                val run = waitForLatestWorkflowRun(owner, repoName, token)
                    ?: throw IllegalStateException("GitHub Actions workflow did not start. Ensure your GitHub token has 'workflow' scope.")
                val runId = run.optLong("id")
                val conclusion = run.optString("conclusion")
                lastRunUrl = run.optString("html_url", lastRunUrl)

                if (conclusion == "success") {
                    val apkFile = downloadApkArtifact(owner, repoName, runId, token, cacheManager)
                    return@runCatching ApkBuildOutcome(
                        apkFile = apkFile,
                        repoFullName = "$owner/$repoName",
                        runUrl = lastRunUrl,
                        notes = if (attempt > 1) "Auto-fixed build error and compiled APK cleanly." else "Compiled APK cleanly on GitHub Actions."
                    )
                }

                // Self-healing: fetch failure logs, ask Gemini for fixes, commit & retry
                if (attempt < 2 && geminiApiKey.isNotBlank()) {
                    val errLogs = fetchFailedRunLogs(owner, repoName, runId, token)
                    if (errLogs.isNotBlank()) {
                        val fixPrompt = """
                            Fix these Android Gradle build errors in the repository `$owner/$repoName`.
                            Return ONLY file replacement blocks using exact paths:
                            <<<FIX_FILE path="app/src/main/java/...">>>
                            [complete corrected file]
                            <<<END_FIX_FILE>>>

                            BUILD ERRORS:
                            ${errLogs.take(6000)}
                        """.trimIndent()
                        val fixRes = geminiRouter.generate(
                            apiKey = geminiApiKey,
                            prompt = fixPrompt,
                            systemInstruction = "You fix Android Kotlin/Gradle compiler errors precisely.",
                            routeMode = GeminiRouteMode.FAST_CHAT,
                            maxOutputTokens = 8192
                        ).getOrNull()
                        val fixRegex = Regex("""<<<FIX_FILE\s+path="([^"]+)"\s*>>>\s*\n([\s\S]*?)<<<END_FIX_FILE>>>""", RegexOption.IGNORE_CASE)
                        val fixes = fixRes?.text?.let { fixRegex.findAll(it).toList() }.orEmpty()
                        if (fixes.isNotEmpty()) {
                            val patchMap = LinkedHashMap<String, ByteArray>()
                            fixes.forEach { m ->
                                val fPath = m.groupValues[1].trim()
                                val fContent = m.groupValues[2]
                                if (fPath.isNotBlank() && fContent.isNotBlank()) {
                                    patchMap[fPath] = fContent.toByteArray(Charsets.UTF_8)
                                }
                            }
                            if (patchMap.isNotEmpty()) {
                                pushTreeCommit(owner, repoName, token, patchMap, "Auto-fix build errors (attempt $attempt)")
                                continue
                            }
                        }
                    }
                }
                throw IllegalStateException("GitHub APK build failed ($conclusion). Check logs at $lastRunUrl")
            }
            throw IllegalStateException("GitHub APK build did not succeed. See $lastRunUrl")
        }
    }

    private fun pushTreeCommit(
        owner: String,
        repo: String,
        token: String,
        files: Map<String, ByteArray>,
        message: String
    ) {
        val refObj = runCatching { ghJson("GET", "https://api.github.com/repos/$owner/$repo/git/ref/heads/main", token) }.getOrNull()
        val parentSha = refObj?.optJSONObject("object")?.optString("sha").orEmpty()
        val baseTreeSha = if (parentSha.isNotBlank()) {
            ghJson("GET", "https://api.github.com/repos/$owner/$repo/git/commits/$parentSha", token)
                .optJSONObject("tree")?.optString("sha").orEmpty()
        } else ""

        val treeItems = JSONArray()
        files.forEach { (path, bytes) ->
            val blobReq = JSONObject().apply {
                put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                put("encoding", "base64")
            }
            val blobRes = ghJson("POST", "https://api.github.com/repos/$owner/$repo/git/blobs", token, blobReq.toString())
            val blobSha = blobRes.optString("sha")
            val mode = if (path == "gradlew") "100755" else "100644"
            treeItems.put(JSONObject().apply {
                put("path", path)
                put("mode", mode)
                put("type", "blob")
                put("sha", blobSha)
            })
        }

        val treeReq = JSONObject().apply {
            if (baseTreeSha.isNotBlank()) put("base_tree", baseTreeSha)
            put("tree", treeItems)
        }
        val treeRes = ghJson("POST", "https://api.github.com/repos/$owner/$repo/git/trees", token, treeReq.toString())
        val newTreeSha = treeRes.optString("sha")

        val commitReq = JSONObject().apply {
            put("message", message)
            put("tree", newTreeSha)
            if (parentSha.isNotBlank()) put("parents", JSONArray().put(parentSha))
        }
        val commitRes = ghJson("POST", "https://api.github.com/repos/$owner/$repo/git/commits", token, commitReq.toString())
        val newCommitSha = commitRes.optString("sha")

        if (parentSha.isNotBlank()) {
            val patchReq = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", true)
            }
            ghJson("PATCH", "https://api.github.com/repos/$owner/$repo/git/refs/heads/main", token, patchReq.toString())
        } else {
            val createRef = JSONObject().apply {
                put("ref", "refs/heads/main")
                put("sha", newCommitSha)
            }
            ghJson("POST", "https://api.github.com/repos/$owner/$repo/git/refs", token, createRef.toString())
        }
    }

    private suspend fun waitForLatestWorkflowRun(owner: String, repo: String, token: String): JSONObject? {
        repeat(60) { // up to ~6 minutes
            val res = ghJson("GET", "https://api.github.com/repos/$owner/$repo/actions/runs?per_page=3", token)
            val runs = res.optJSONArray("workflow_runs")
            if (runs != null && runs.length() > 0) {
                val latest = runs.optJSONObject(0)
                if (latest != null && latest.optString("status") == "completed") {
                    return latest
                }
            }
            delay(6000)
        }
        return null
    }

    private fun fetchFailedRunLogs(owner: String, repo: String, runId: Long, token: String): String {
        return runCatching {
            val jobs = ghJson("GET", "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs", token)
                .optJSONArray("jobs") ?: return ""
            for (i in 0 until jobs.length()) {
                val j = jobs.optJSONObject(i) ?: continue
                if (j.optString("conclusion") != "success") {
                    val jobId = j.optLong("id")
                    val (_, logTxt) = ghRaw("GET", "https://api.github.com/repos/$owner/$repo/actions/jobs/$jobId/logs", token)
                    val errLines = logTxt.lines().filter { it.contains("e:") || it.contains("FAILURE") || it.contains("error:") }
                    return if (errLines.isNotEmpty()) errLines.takeLast(60).joinToString("\n") else logTxt.takeLast(4000)
                }
            }
            ""
        }.getOrDefault("")
    }

    private fun downloadApkArtifact(owner: String, repo: String, runId: Long, token: String, cacheManager: CacheManager): File {
        val arts = ghJson("GET", "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts", token)
            .optJSONArray("artifacts") ?: throw IllegalStateException("No APK artifacts found.")
        var dlUrl = ""
        for (i in 0 until arts.length()) {
            val a = arts.optJSONObject(i) ?: continue
            if (a.optString("name").contains("apk", ignoreCase = true)) {
                dlUrl = a.optString("archive_download_url")
                break
            }
        }
        if (dlUrl.isBlank()) throw IllegalStateException("APK artifact URL missing.")

        val zipBytes = downloadBytesFollowRedirect(dlUrl, token)
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.endsWith(".apk", ignoreCase = true)) {
                    val out = File(cacheManager.sharedPacksDir, "raw_edit_debug_${System.currentTimeMillis()}.apk")
                    out.parentFile?.mkdirs()
                    out.writeBytes(zis.readBytes())
                    return out
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        throw IllegalStateException("Downloaded artifact did not contain an .apk file.")
    }

    private fun downloadBytesFollowRedirect(url: String, token: String): ByteArray {
        var current = url
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "RawEdit-Android-App")
                if (current.contains("api.github.com")) {
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location").orEmpty()
                    if (loc.isNotBlank()) {
                        current = loc
                        return@repeat
                    }
                }
                if (code !in 200..299) {
                    throw IllegalStateException("Artifact download HTTP $code")
                }
                val bos = ByteArrayOutputStream()
                conn.inputStream.copyTo(bos)
                return bos.toByteArray()
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("Too many redirects downloading APK.")
    }

    private fun ghJson(method: String, url: String, token: String, body: String? = null): JSONObject {
        val (code, txt) = ghRaw(method, url, token, body)
        if (code !in 200..299) {
            throw IllegalStateException("GitHub $method $url -> HTTP $code: ${txt.take(180)}")
        }
        return if (txt.trim().startsWith("{")) JSONObject(txt) else JSONObject()
    }

    private fun ghRaw(method: String, url: String, token: String, body: String? = null): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 35_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "RawEdit-Android-App")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val txt = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to txt
        } finally {
            conn.disconnect()
        }
    }
}
