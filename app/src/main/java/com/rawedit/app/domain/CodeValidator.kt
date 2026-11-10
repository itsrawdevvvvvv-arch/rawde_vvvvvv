package com.rawedit.app.domain

import android.content.Context
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.rawedit.app.data.CacheManager
import com.rawedit.app.render.LocalAssetServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class CodeValidationResult(
    val isValid: Boolean,
    val cleanedHtml: String,
    val durationSec: Double,
    val warning: String? = null,
    val summaryMessage: String
)

@Serializable
private data class DryRunPayload(
    val ok: Boolean = false,
    val duration: Double = 0.0,
    val nonDeterministic: Boolean = false,
    val error: String = ""
)

object CodeValidator {

    private const val DRY_RUN_TIMEOUT_MS = 20_000L

    private val FORBIDDEN_PATTERNS = listOf(
        Regex("<script\\s+[^>]*src\\s*=", RegexOption.IGNORE_CASE) to "External <script src> found — ask AI to inline all JS into one single HTML file.",
        Regex("<link\\b", RegexOption.IGNORE_CASE) to "External <link> tag found — ask AI to inline all CSS inside <style> in one single HTML file.",
        Regex("@import\\b", RegexOption.IGNORE_CASE) to "CSS @import rules are not allowed — inline all CSS directly.",
        Regex("\\bfetch\\s*\\(", RegexOption.IGNORE_CASE) to "Network fetch() calls are not allowed offline.",
        Regex("\\bXMLHttpRequest\\b", RegexOption.IGNORE_CASE) to "XMLHttpRequest is not allowed offline.",
        Regex("\\bWebSocket\\b", RegexOption.IGNORE_CASE) to "WebSocket is not allowed offline.",
        Regex("\\bimport\\s*\\(", RegexOption.IGNORE_CASE) to "Dynamic import() is not allowed offline.",
        Regex("\\bsendBeacon\\b", RegexOption.IGNORE_CASE) to "navigator.sendBeacon is not allowed offline.",
        Regex("\\bEventSource\\b", RegexOption.IGNORE_CASE) to "EventSource is not allowed offline.",
        Regex("window\\.open\\s*\\(", RegexOption.IGNORE_CASE) to "window.open() is not allowed offline."
    )

    private val URL_REGEX = Regex("""https?://[^\s"'`<>()\\]+""", RegexOption.IGNORE_CASE)
    private val HTML_COMMENT_REGEX = Regex("""<!--[\s\S]*?-->""")

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun unwrapMarkdownFences(raw: String): String {
        var text = raw.trim()

        val fenceRegex = Regex("```[a-zA-Z0-9_-]*\\s*\\n([\\s\\S]*?)```")
        val allMatches = fenceRegex.findAll(text).map { it.groupValues[1].trim() }.toList()
        if (allMatches.isNotEmpty()) {
            val bestHtmlMatch = allMatches
                .filter {
                    val lower = it.lowercase()
                    lower.contains("<html") || lower.contains("<!doctype") || lower.contains("window.raw") || lower.contains("seek")
                }
                .maxByOrNull { it.length }
            text = bestHtmlMatch ?: allMatches.maxByOrNull { it.length } ?: text
        } else if (text.startsWith("```")) {
            // Handle unclosed opening fence if AI output ended right after </html>
            text = text.removePrefix("```html").removePrefix("```HTML").removePrefix("```").trim()
            if (text.endsWith("```")) {
                text = text.removeSuffix("```").trim()
            }
        }

        val docIdx = text.indexOf("<!DOCTYPE", ignoreCase = true)
        val htmlIdx = text.indexOf("<html", ignoreCase = true)
        val startIdx = when {
            docIdx >= 0 -> docIdx
            htmlIdx >= 0 -> htmlIdx
            else -> 0
        }
        val endHtmlIdx = text.lastIndexOf("</html>", ignoreCase = true)
        if (endHtmlIdx > startIdx) {
            text = text.substring(startIdx, endHtmlIdx + 7).trim()
        }
        return text
    }

    fun staticValidate(rawInput: String): Pair<Boolean, String> {
        val code = unwrapMarkdownFences(rawInput)
        if (code.isBlank()) {
            return false to "Paste the HTML code from Cloud AI first."
        }

        val hasContract = (code.contains("RAW") || code.contains("PROJECT") || code.contains("PROJECT_DURATION")) &&
            code.contains("seek")
        if (!hasContract) {
            return false to "Missing window.RAW (or window.PROJECT + window.seek). Ask Cloud AI to follow the self-contained HTML rules."
        }

        val withoutComments = HTML_COMMENT_REGEX.replace(code, " ")

        for ((pattern, message) in FORBIDDEN_PATTERNS) {
            if (pattern.containsMatchIn(withoutComments)) {
                return false to message
            }
        }

        for (match in URL_REGEX.findAll(withoutComments)) {
            val url = match.value
            val allowed = url.startsWith("https://assets.local/", ignoreCase = true) ||
                url.startsWith("http://www.w3.org/", ignoreCase = true) ||
                url.startsWith("https://www.w3.org/", ignoreCase = true) ||
                url.startsWith("https://raw.local/", ignoreCase = true)
            if (!allowed) {
                return false to "External URL found ($url). Everything must run offline without CDN links."
            }
        }

        return true to code
    }

    suspend fun validateFull(
        context: Context,
        cacheManager: CacheManager,
        rawInput: String
    ): CodeValidationResult {
        val (staticOk, staticPayload) = staticValidate(rawInput)
        if (!staticOk) {
            return CodeValidationResult(
                isValid = false,
                cleanedHtml = "",
                durationSec = 0.0,
                summaryMessage = staticPayload
            )
        }
        val cleanHtml = staticPayload

        val dryRun = withTimeoutOrNull(DRY_RUN_TIMEOUT_MS) {
            runSandboxedDryRun(context, cacheManager, cleanHtml)
        } ?: return CodeValidationResult(
            isValid = false,
            cleanedHtml = cleanHtml,
            durationSec = 0.0,
            summaryMessage = "Validation timed out while testing the HTML stage. Try splitting long scripts or ask AI to simplify init."
        )

        if (!dryRun.ok) {
            val msg = dryRun.error.ifBlank { "window.RAW could not be initialized." }
            return CodeValidationResult(
                isValid = false,
                cleanedHtml = cleanHtml,
                durationSec = 0.0,
                summaryMessage = msg
            )
        }

        val warning = if (dryRun.nonDeterministic) {
            "Note: repeated seek() calls slightly altered DOM markup; visual frame output may still work fine."
        } else {
            null
        }

        val durStr = String.format(java.util.Locale.US, "%.1f", dryRun.duration)
        return CodeValidationResult(
            isValid = true,
            cleanedHtml = cleanHtml,
            durationSec = dryRun.duration,
            warning = warning,
            summaryMessage = "Code verified  •  $durStr s ready to render."
        )
    }

    private suspend fun runSandboxedDryRun(
        context: Context,
        cacheManager: CacheManager,
        cleanHtml: String
    ): DryRunPayload = withContext(Dispatchers.Main) {
        var htmlToServe = ""
        val server = LocalAssetServer(context, cacheManager) { htmlToServe }
        htmlToServe = server.buildInjectedHtml(cleanHtml)

        val webView = WebView(context)
        val pageFinished = CompletableDeferred<Unit>()
        var consoleError: String? = null
        try {
            server.configureSandboxedWebView(webView) {
                if (!pageFinished.isCompleted) pageFinished.complete(Unit)
            }
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                    if (cm != null && cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR && consoleError == null) {
                        consoleError = "JS Error (line ${cm.lineNumber()}): ${cm.message()}"
                    }
                    return true
                }
            }
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, 1920, 1080)
            webView.loadUrl(LocalAssetServer.ENTRY_URL)

            pageFinished.await()

            val probeJs = """
                (function() {
                  var raw = (window.__ensureRawContract && window.__ensureRawContract()) || window.RAW;
                  if (!raw) {
                    if (window.__rawLastError) {
                      return JSON.stringify({ ok:false, error:'JS Error: ' + window.__rawLastError });
                    }
                    return null;
                  }
                  if (typeof raw.duration !== 'number' || !(raw.duration > 0)) {
                    return JSON.stringify({ ok:false, error:'duration must be a number greater than 0.' });
                  }
                  if (typeof raw.seek !== 'function') {
                    return JSON.stringify({ ok:false, error:'RAW.seek / window.seek must be a function.' });
                  }
                  var stage = document.getElementById('stage');
                  if (!stage) {
                    return JSON.stringify({ ok:false, error:'Missing <div id="stage"> element.' });
                  }
                  try {
                    raw.seek(0);
                    raw.seek(raw.duration / 2);
                    raw.seek(Math.max(0, raw.duration - 0.04));
                    var sampleT = Math.min(1, raw.duration * 0.25);
                    raw.seek(sampleT);
                    var h1 = stage.innerHTML;
                    raw.seek(sampleT);
                    var h2 = stage.innerHTML;
                    var hashDiff = (h1 !== h2);
                    return JSON.stringify({
                      ok: true,
                      duration: raw.duration,
                      nonDeterministic: hashDiff,
                      error: ''
                    });
                  } catch (err) {
                    return JSON.stringify({ ok:false, error: 'seek(t) threw: ' + String(err) });
                  }
                })();
            """.trimIndent()

            repeat(160) { idx ->
                val evalResult = CompletableDeferred<String>()
                webView.evaluateJavascript(probeJs) { result ->
                    evalResult.complete(result ?: "null")
                }
                val rawStr = evalResult.await()
                if (rawStr != "null" && rawStr.isNotBlank()) {
                    val unquoted = runCatching {
                        jsonParser.decodeFromString<String>(rawStr)
                    }.getOrNull()
                    if (unquoted != null) {
                        return@withContext runCatching {
                            jsonParser.decodeFromString<DryRunPayload>(unquoted)
                        }.getOrElse {
                            DryRunPayload(ok = false, error = "Invalid validation response.")
                        }
                    }
                }
                val err = consoleError
                if (err != null && idx >= 4) {
                    return@withContext DryRunPayload(ok = false, error = err)
                }
                delay(100)
            }

            DryRunPayload(
                ok = false,
                error = consoleError ?: "window.RAW (or window.seek) was not defined within 16 seconds."
            )
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }
}
