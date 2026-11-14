package com.rawedit.app.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GitHubRepoItem(
    val name: String,
    val fullName: String,
    val description: String,
    val stars: Int,
    val htmlUrl: String,
    val defaultBranch: String
)

data class GitHubFileItem(
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val downloadUrl: String?
)

object GitHubService {

    private const val GITHUB_API_BASE = "https://api.github.com"

    suspend fun searchRepositories(
        token: String?,
        query: String,
        limit: Int = 8
    ): Result<List<GitHubRepoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = URLEncoder.encode(query.trim().ifBlank { "video editing tools" }, "UTF-8")
            val endpoint = "$GITHUB_API_BASE/search/repositories?q=$encodedQuery&per_page=$limit&sort=stars"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "RawEdit-Android-App")
                token?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
            }

            val status = conn.responseCode
            if (status != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("GitHub search failed ($status): ${parseGitHubErrorMessage(err)}")
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(response)
            val items = root.optJSONArray("items") ?: JSONArray()
            val result = mutableListOf<GitHubRepoItem>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                result.add(
                    GitHubRepoItem(
                        name = item.optString("name", "repo"),
                        fullName = item.optString("full_name", ""),
                        description = item.optString("description", "").trim(),
                        stars = item.optInt("stargazers_count", 0),
                        htmlUrl = item.optString("html_url", ""),
                        defaultBranch = item.optString("default_branch", "main")
                    )
                )
            }
            result
        }
    }

    suspend fun listRepoContents(
        token: String?,
        owner: String,
        repo: String,
        path: String = ""
    ): Result<List<GitHubFileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanPath = path.trim().removePrefix("/").removeSuffix("/")
            val endpoint = "$GITHUB_API_BASE/repos/$owner/$repo/contents/$cleanPath"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "RawEdit-Android-App")
                token?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
            }

            val status = conn.responseCode
            if (status != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("GitHub contents failed ($status): ${parseGitHubErrorMessage(err)}")
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(response)
            val result = mutableListOf<GitHubFileItem>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                result.add(
                    GitHubFileItem(
                        name = item.optString("name", ""),
                        path = item.optString("path", ""),
                        type = item.optString("type", "file"),
                        size = item.optLong("size", 0L),
                        downloadUrl = item.optString("download_url").takeIf { it.isNotBlank() }
                    )
                )
            }
            result
        }
    }

    suspend fun downloadRawFile(
        token: String?,
        downloadUrl: String,
        destFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            destFile.parentFile?.mkdirs()
            val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "RawEdit-Android-App")
                token?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
            }
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("File download failed with HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        }
    }

    suspend fun downloadRepoZip(
        token: String?,
        owner: String,
        repo: String,
        ref: String = "main",
        destZip: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            destZip.parentFile?.mkdirs()
            val endpoint = "$GITHUB_API_BASE/repos/$owner/$repo/zipball/$ref"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "RawEdit-Android-App")
                token?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
            }

            var redirectedConn = conn
            var code = redirectedConn.responseCode
            var redirects = 0
            while ((code == 301 || code == 302 || code == 307 || code == 308) && redirects < 5) {
                val loc = redirectedConn.getHeaderField("Location") ?: break
                redirectedConn.disconnect()
                redirectedConn = (URL(loc).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "RawEdit-Android-App")
                }
                code = redirectedConn.responseCode
                redirects++
            }

            if (code !in 200..299) {
                val err = redirectedConn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("GitHub zip download failed ($code): ${parseGitHubErrorMessage(err)}")
            }

            redirectedConn.inputStream.use { input ->
                FileOutputStream(destZip).use { output ->
                    input.copyTo(output)
                }
            }
            destZip
        }
    }

    private fun parseGitHubErrorMessage(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            obj.optString("message", raw.take(200))
        } catch (_: Exception) {
            raw.take(200)
        }
    }
}
