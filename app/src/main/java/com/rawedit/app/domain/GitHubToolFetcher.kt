package com.rawedit.app.domain

import android.util.Base64
import com.rawedit.app.data.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GitHubSkillItem(
    val name: String,
    val repoFullName: String,
    val description: String,
    val localFileName: String,
    val contentPreview: String
)

object GitHubToolFetcher {

    suspend fun verifyGithubToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = token.trim().removePrefix("Bearer ").removePrefix("token ").trim()
            if (clean.isEmpty()) throw IllegalArgumentException("GitHub token is empty.")
            val conn = openGithubConnection("https://api.github.com/user", clean)
            try {
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(body)
                    val login = obj.optString("login", "GitHub User")
                    "Verified @$login"
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("GitHub HTTP $code: ${err.take(120)}")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * Fetches a skill, README, or tool file from GitHub by `owner/repo[/path]`, full `https://github.com/...` URL,
     * or search query, and saves it locally on the phone in `cacheManager.githubSkillsDir`.
     */
    suspend fun fetchAndSaveFromGithub(
        queryOrUrl: String,
        githubToken: String,
        cacheManager: CacheManager
    ): Result<List<GitHubSkillItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = queryOrUrl.trim()
            if (trimmed.isEmpty()) throw IllegalArgumentException("Provide a GitHub repo, URL, or search query.")

            val cleanToken = githubToken.trim().removePrefix("Bearer ").removePrefix("token ").trim()
            val savedItems = mutableListOf<GitHubSkillItem>()

            // Case 1: Direct GitHub URL or owner/repo reference
            val repoSpec = parseOwnerRepoAndPath(trimmed)
            if (repoSpec != null) {
                val (owner, repo, subPath) = repoSpec
                val item = downloadFromRepo(owner, repo, subPath, cleanToken, cacheManager)
                savedItems.add(item)
                return@runCatching savedItems
            }

            // Case 2: Search GitHub repositories matching query
            val encodedQ = URLEncoder.encode(trimmed, "UTF-8")
            val searchUrl = "https://api.github.com/search/repositories?q=$encodedQ&sort=stars&order=desc&per_page=3"
            val conn = openGithubConnection(searchUrl, cleanToken)
            val reposToFetch = mutableListOf<Triple<String, String, String>>()
            try {
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                    for (i in 0 until minOf(2, items.length())) {
                        val r = items.optJSONObject(i) ?: continue
                        val fullName = r.optString("full_name", "")
                        val desc = r.optString("description", "")
                        if (fullName.contains('/')) {
                            val parts = fullName.split('/', limit = 2)
                            reposToFetch.add(Triple(parts[0], parts[1], desc))
                        }
                    }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("GitHub search failed (HTTP ${conn.responseCode}): ${err.take(120)}")
                }
            } finally {
                conn.disconnect()
            }

            if (reposToFetch.isEmpty()) {
                throw IllegalStateException("No GitHub repositories matched \"$trimmed\".")
            }

            for ((owner, repo, desc) in reposToFetch) {
                runCatching {
                    val item = downloadFromRepo(owner, repo, "", cleanToken, cacheManager, fallbackDesc = desc)
                    savedItems.add(item)
                }
            }

            if (savedItems.isEmpty()) {
                throw IllegalStateException("Could not download skill/README content from matching GitHub repositories.")
            }
            savedItems
        }
    }

    private fun downloadFromRepo(
        owner: String,
        repo: String,
        subPath: String,
        token: String,
        cacheManager: CacheManager,
        fallbackDesc: String = ""
    ): GitHubSkillItem {
        val candidatePaths = if (subPath.isNotBlank()) {
            listOf(subPath, "$subPath/SKILL.md", "$subPath/README.md")
        } else {
            listOf("SKILL.md", "README.md", "docs/SKILL.md", "index.js", "engine.js")
        }

        for (path in candidatePaths) {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/${path.trimStart('/')}"
            val conn = openGithubConnection(apiUrl, token)
            try {
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val trimmedBody = body.trim()
                    if (trimmedBody.startsWith("{")) {
                        val obj = JSONObject(trimmedBody)
                        val name = obj.optString("name", path.substringAfterLast('/'))
                        val contentB64 = obj.optString("content", "")
                        val downloadUrl = obj.optString("download_url", "")
                        val decodedText = if (contentB64.isNotBlank()) {
                            String(Base64.decode(contentB64.replace("\n", "").replace("\r", ""), Base64.DEFAULT), Charsets.UTF_8)
                        } else if (downloadUrl.isNotBlank()) {
                            fetchRawUrlText(downloadUrl, token)
                        } else {
                            ""
                        }
                        if (decodedText.isNotBlank()) {
                            val localName = "${owner}_${repo}_${name}".replace(Regex("[^A-Za-z0-9._-]"), "_")
                            val savedFile: File = cacheManager.saveGithubSkillFile(localName, decodedText)
                            return GitHubSkillItem(
                                name = "$owner/$repo ($name)",
                                repoFullName = "$owner/$repo",
                                description = fallbackDesc.ifBlank { "Fetched $path from $owner/$repo" },
                                localFileName = savedFile.name,
                                contentPreview = decodedText.take(600)
                            )
                        }
                    } else if (trimmedBody.startsWith("[")) {
                        // Directory listing: pick first .md, .js, or .html file
                        val arr = JSONArray(trimmedBody)
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val itemType = item.optString("type")
                            val itemName = item.optString("name")
                            val dlUrl = item.optString("download_url")
                            if (itemType == "file" && dlUrl.isNotBlank() &&
                                (itemName.endsWith(".md", true) || itemName.endsWith(".js", true) || itemName.endsWith(".html", true))
                            ) {
                                val text = fetchRawUrlText(dlUrl, token)
                                if (text.isNotBlank()) {
                                    val localName = "${owner}_${repo}_${itemName}".replace(Regex("[^A-Za-z0-9._-]"), "_")
                                    val savedFile = cacheManager.saveGithubSkillFile(localName, text)
                                    return GitHubSkillItem(
                                        name = "$owner/$repo ($itemName)",
                                        repoFullName = "$owner/$repo",
                                        description = fallbackDesc.ifBlank { "Fetched $itemName from $owner/$repo" },
                                        localFileName = savedFile.name,
                                        contentPreview = text.take(600)
                                    )
                                }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("Could not find SKILL.md, README.md, or tool files in $owner/$repo.")
    }

    private fun fetchRawUrlText(url: String, token: String): String {
        val conn = openGithubConnection(url, token)
        return try {
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else ""
        } finally {
            conn.disconnect()
        }
    }

    private fun openGithubConnection(url: String, token: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "RawEdit-Android-App")
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
    }

    fun parseOwnerRepoAndPath(input: String): Triple<String, String, String>? {
        val clean = input.trim()
        val urlRegex = Regex("""https?://(?:www\.)?github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:/(?:blob|tree)/[^/]+/(.*)|/(.*))?""", RegexOption.IGNORE_CASE)
        val urlMatch = urlRegex.find(clean)
        if (urlMatch != null) {
            val owner = urlMatch.groupValues[1]
            val repo = urlMatch.groupValues[2].removeSuffix(".git")
            val subPath = urlMatch.groupValues[3].ifBlank { urlMatch.groupValues[4] }
            return Triple(owner, repo, subPath)
        }
        val slugRegex = Regex("""^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:/(.*))?$""")
        val slugMatch = slugRegex.find(clean)
        if (slugMatch != null && !clean.contains(' ')) {
            return Triple(slugMatch.groupValues[1], slugMatch.groupValues[2], slugMatch.groupValues[3])
        }
        return null
    }

    suspend fun listUserRepos(githubToken: String, limit: Int = 15): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = githubToken.trim().removePrefix("Bearer ").removePrefix("token ").trim()
            if (clean.isEmpty()) throw IllegalArgumentException("GitHub token required.")
            val conn = openGithubConnection("https://api.github.com/user/repos?sort=updated&per_page=$limit", clean)
            try {
                if (conn.responseCode != 200) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("GitHub HTTP ${conn.responseCode}: ${err.take(120)}")
                }
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val full = o.optString("full_name")
                    val priv = if (o.optBoolean("private")) "private" else "public"
                    "$full ($priv)"
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun createRepo(
        githubToken: String,
        name: String,
        description: String = "",
        isPrivate: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = githubToken.trim().removePrefix("Bearer ").removePrefix("token ").trim()
            if (clean.isEmpty()) throw IllegalArgumentException("GitHub token required to create a repo.")
            val conn = (URL("https://api.github.com/user/repos").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "RawEdit-Android-App")
                setRequestProperty("Authorization", "Bearer $clean")
            }
            try {
                val payload = JSONObject().apply {
                    put("name", name.trim())
                    if (description.isNotBlank()) put("description", description.trim())
                    put("private", isPrivate)
                    put("auto_init", true)
                }.toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code in 200..299) {
                    val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    obj.optString("full_name", name)
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Create repo failed (HTTP $code): ${err.take(140)}")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun readRepoFile(
        githubToken: String,
        owner: String,
        repo: String,
        path: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = githubToken.trim().removePrefix("Bearer ").removePrefix("token ").trim()
            val cleanPath = path.trimStart('/')
            val conn = openGithubConnection("https://api.github.com/repos/$owner/$repo/contents/$cleanPath", clean)
            try {
                if (conn.responseCode != 200) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Read file failed (HTTP ${conn.responseCode}): ${err.take(120)}")
                }
                val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val sha = obj.optString("sha", "")
                val b64 = obj.optString("content", "")
                val text = if (b64.isNotBlank()) {
                    String(Base64.decode(b64.replace("\n", "").replace("\r", ""), Base64.DEFAULT), Charsets.UTF_8)
                } else ""
                text to sha
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun createOrUpdateRepoFile(
        githubToken: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        commitMessage: String = "Update via RAW EDIT AI"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = githubToken.trim().removePrefix("Bearer ").removePrefix("token ").trim()
            if (clean.isEmpty()) throw IllegalArgumentException("GitHub token required to edit files.")
            val cleanPath = path.trimStart('/')
            val existingSha = readRepoFile(clean, owner, repo, cleanPath).getOrNull()?.second.orEmpty()
            val conn = (URL("https://api.github.com/repos/$owner/$repo/contents/$cleanPath").openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "RawEdit-Android-App")
                setRequestProperty("Authorization", "Bearer $clean")
            }
            try {
                val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val payload = JSONObject().apply {
                    put("message", commitMessage.ifBlank { "Update $cleanPath" })
                    put("content", b64)
                    if (existingSha.isNotBlank()) put("sha", existingSha)
                }.toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code in 200..299) {
                    "$owner/$repo/$cleanPath"
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("GitHub write failed (HTTP $code): ${err.take(140)}")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun deleteRepoFile(
        githubToken: String,
        owner: String,
        repo: String,
        path: String,
        commitMessage: String = "Delete via RAW EDIT AI"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = githubToken.trim().removePrefix("Bearer ").removePrefix("token ").trim()
            if (clean.isEmpty()) throw IllegalArgumentException("GitHub token required.")
            val cleanPath = path.trimStart('/')
            val existingSha = readRepoFile(clean, owner, repo, cleanPath).getOrThrow().second
            if (existingSha.isBlank()) throw IllegalStateException("File SHA not found for $cleanPath")
            val conn = (URL("https://api.github.com/repos/$owner/$repo/contents/$cleanPath").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "RawEdit-Android-App")
                setRequestProperty("Authorization", "Bearer $clean")
            }
            try {
                val payload = JSONObject().apply {
                    put("message", commitMessage.ifBlank { "Delete $cleanPath" })
                    put("sha", existingSha)
                }.toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code in 200..299) {
                    "Deleted $owner/$repo/$cleanPath"
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("GitHub delete failed (HTTP $code): ${err.take(140)}")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

}
