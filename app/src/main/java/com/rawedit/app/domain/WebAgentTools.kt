package com.rawedit.app.domain

import com.rawedit.app.data.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

data class WebSearchHit(
    val title: String,
    val url: String,
    val snippet: String
)

object WebAgentTools {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Redmi Note 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    suspend fun searchWeb(query: String, maxResults: Int = 5): Result<List<WebSearchHit>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanQ = query.trim()
            if (cleanQ.isEmpty()) throw IllegalArgumentException("Empty search query.")
            val encoded = URLEncoder.encode(cleanQ, "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encoded"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            val html = try {
                if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    throw IllegalStateException("Search HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }

            val hits = mutableListOf<WebSearchHit>()
            val linkRegex = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
            val snipRegex = Regex("""<a[^>]*class="result__snippet"[^>]*>([\s\S]*?)</a>|<div[^>]*class="result__snippet"[^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)

            val linkMatches = linkRegex.findAll(html).toList()
            val snipMatches = snipRegex.findAll(html).toList()

            for (i in 0 until minOf(maxResults, linkMatches.size)) {
                val lm = linkMatches[i]
                var href = lm.groupValues[1]
                if (href.contains("uddg=")) {
                    val uddg = href.substringAfter("uddg=").substringBefore("&")
                    href = runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrDefault(href)
                }
                val title = stripHtml(lm.groupValues[2])
                val snippet = snipMatches.getOrNull(i)?.let { sm ->
                    stripHtml(sm.groupValues[1].ifBlank { sm.groupValues[2] })
                }.orEmpty()
                if (title.isNotBlank()) {
                    hits.add(WebSearchHit(title = title, url = href, snippet = snippet))
                }
            }
            hits
        }
    }

    suspend fun fetchWebPageText(url: String, maxChars: Int = 9000): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val html = try {
                if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    throw IllegalStateException("HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
            val noScripts = html
                .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""<noscript[\s\S]*?</noscript>""", RegexOption.IGNORE_CASE), " ")
            stripHtml(noScripts).take(maxChars)
        }
    }

    suspend fun downloadWebDependency(
        url: String,
        fileName: String,
        cacheManager: CacheManager
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            val safeName = fileName.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "web_dep.bin" }
            val outFile = File(cacheManager.sharedPacksDir, safeName)
            outFile.parentFile?.mkdirs()

            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (conn.responseCode !in 200..299) {
                    throw IllegalStateException("Download failed: HTTP ${conn.responseCode}")
                }
                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output, bufferSize = 16 * 1024)
                    }
                }
            } finally {
                conn.disconnect()
            }
            outFile
        }
    }

    private fun stripHtml(raw: String): String {
        return raw
            .replace(Regex("""<[^>]+>"""), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
