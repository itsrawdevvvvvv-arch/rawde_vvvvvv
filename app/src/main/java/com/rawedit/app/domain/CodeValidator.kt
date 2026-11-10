package com.rawedit.app.domain

import android.content.Context
import android.view.View
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

    private const val DRY_RUN_TIMEOUT_MS = 15_000L

    private val FORBIDDEN_PATTERNS = listOf(
        Regex("<script\\s+[^>]*src\\s*=", RegexOption.IGNORE_CASE) to "External <script src> tags are not allowed.",
        Regex("<link\\b", RegexOption.IGNORE_CASE) to "External <link> tags are not allowed.",
        Regex("@import\\b", RegexOption.IGNORE_CASE) to "CSS @import rules are not allowed.",
        Regex("\\bfetch\\s*\\(", RegexOption.IGNORE_CASE) to "Network fetch() calls are not allowed.",
        Regex("\\bXMLHttpRequest\\b", RegexOption.IGNORE_CASE) to "XMLHttpRequest is not allowed.",
        Regex("\\bWebSocket\\b", RegexOption.IGNORE_CASE) to "WebSocket is not allowed.",
        Regex("\\bimport\\s*\\(", RegexOption.IGNORE_CASE) to "Dynamic import() is not allowed.",
        Regex("\\bsendBeacon\\b", RegexOption.IGNORE_CASE) to "navigator.sendBeacon is not allowed.",
        Regex("\\bEventSource\\b", RegexOption.IGNORE_CASE) to "EventSource is not allowed.",
        Regex("window\\.open\\s*\\(", RegexOption.IGNORE_CASE) to "window.open() is not allowed."
    )

    private val URL_REGEX = Regex("""https?://[^\s"'`<>()\\]+""", RegexOption.IGNORE_CASE)

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun unwrapMarkdownFences(raw: String): String {
        var text = raw.trim()
        val fenceRegex = Regex("```(?:html|HTML)?\\s*([\\s\\S]*?)```")
        val fenceMatch = fenceRegex.find(text)
        if (fenceMatch != null) {
            text = fenceMatch.groupValues[1].trim()
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
            return false to "Paste the HTML code from Claude first."
        }
        if (!code.contains("RAW") || !code.contains("seek")) {
            return false to "Missing window.RAW or RAW.seek(t). Ask Claude to try again and follow the rules."
        }

        for ((pattern, message) in FORBIDDEN_PATTERNS) {
            if (pattern.containsMatchIn(code)) {
                return false to "$message Ask Claude to try again and follow the rules."
            }
        }

        for (match in URL_REGEX.findAll(code)) {
            val url = match.value
            val allowed = url.startsWith("https://assets.local/", ignoreCase = true) ||
                url.startsWith("http://www.w3.org/", ignoreCase = true) ||
                url.startsWith("https://raw.local/", ignoreCase = true)
            if (!allowed) {
                return false to "External URL found ($url). Everything must run offline. Ask Claude to try again and follow the rules."
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
            summaryMessage = "Validation timed out while checking window.RAW. Ask Claude to try again and follow the rules."
        )

        if (!dryRun.ok) {
            val msg = dryRun.error.ifBlank { "window.RAW could not be initialized." }
            return CodeValidationResult(
                isValid = false,
                cleanedHtml = cleanHtml,
                durationSec = 0.0,
                summaryMessage = "$msg Ask Claude to try again and follow the rules."
            )
        }

        val warning = if (dryRun.nonDeterministic) {
            "Warning: RAW.seek(1) produced slightly different output on repeat calls; frame output may vary."
        } else {
            null
        }

        val durStr = String.format(java.util.Locale.US, "%.1f", dryRun.duration)
        return CodeValidationResult(
            isValid = true,
            cleanedHtml = cleanHtml,
            durationSec = dryRun.duration,
            warning = warning,
            summaryMessage = "Code looks good  •  $durStr s ready to render."
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
        try {
            server.configureSandboxedWebView(webView) {
                if (!pageFinished.isCompleted) pageFinished.complete(Unit)
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
                  if (!window.RAW) return null;
                  if (typeof window.RAW.duration !== 'number' || !(window.RAW.duration > 0)) {
                    return JSON.stringify({ ok:false, error:'RAW.duration must be a number greater than 0.' });
                  }
                  if (typeof window.RAW.seek !== 'function') {
                    return JSON.stringify({ ok:false, error:'RAW.seek must be a function.' });
                  }
                  var stage = document.getElementById('stage');
                  if (!stage) {
                    return JSON.stringify({ ok:false, error:'Missing <div id="stage"> element.' });
                  }
                  try {
                    window.RAW.seek(0);
                    window.RAW.seek(window.RAW.duration / 2);
                    window.RAW.seek(window.RAW.duration);
                    window.RAW.seek(1);
                    var h1 = stage.innerHTML;
                    window.RAW.seek(1);
                    var h2 = stage.innerHTML;
                    var hashDiff = (h1 !== h2);
                    return JSON.stringify({
                      ok: true,
                      duration: window.RAW.duration,
                      nonDeterministic: hashDiff,
                      error: ''
                    });
                  } catch (err) {
                    return JSON.stringify({ ok:false, error: 'RAW.seek threw an error: ' + String(err) });
                  }
                })();
            """.trimIndent()

            repeat(80) {
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
                delay(100)
            }

            DryRunPayload(ok = false, error = "window.RAW was not defined within 15 seconds.")
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }
}
