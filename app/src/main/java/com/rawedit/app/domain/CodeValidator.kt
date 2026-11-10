package com.rawedit.app.domain

import android.content.Context
import android.view.View
import android.webkit.WebView
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.model.LayerJson
import com.rawedit.app.data.model.RawLayer
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
    val layers: List<RawLayer>,
    val hasEditableLayers: Boolean,
    val warning: String? = null,
    val summaryMessage: String
)

@Serializable
private data class DryRunPayload(
    val ok: Boolean = false,
    val duration: Double = 0.0,
    val hasLayers: Boolean = false,
    val layersJson: String = "[]",
    val nonDeterministic: Boolean = false,
    val error: String = ""
)

object CodeValidator {

    private const val MAX_CODE_BYTES = 5 * 1024 * 1024
    private const val DRY_RUN_TIMEOUT_MS = 10_000L

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
        val trimmed = raw.trim()
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            val lines = trimmed.lines()
            if (lines.size >= 3) {
                return lines.drop(1).dropLast(1).joinToString("\n").trim()
            }
        }
        return trimmed
    }

    fun staticValidate(rawInput: String): Pair<Boolean, String> {
        val code = unwrapMarkdownFences(rawInput)
        if (code.isBlank()) {
            return false to "Paste the HTML code from Claude first."
        }
        if (code.toByteArray(Charsets.UTF_8).size >= MAX_CODE_BYTES) {
            return false to "This code is 5 MB or larger. Ask Claude to keep it under 150 KB."
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
                layers = emptyList(),
                hasEditableLayers = false,
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
            layers = emptyList(),
            hasEditableLayers = false,
            summaryMessage = "Validation timed out after 10 seconds while checking window.RAW. Ask Claude to try again and follow the rules."
        )

        if (!dryRun.ok) {
            val msg = dryRun.error.ifBlank { "window.RAW could not be initialized." }
            return CodeValidationResult(
                isValid = false,
                cleanedHtml = cleanHtml,
                durationSec = 0.0,
                layers = emptyList(),
                hasEditableLayers = false,
                summaryMessage = "$msg Ask Claude to try again and follow the rules."
            )
        }

        val parsedLayers = if (dryRun.hasLayers) {
            LayerJson.decode(dryRun.layersJson)
        } else {
            emptyList()
        }

        val warning = if (dryRun.nonDeterministic) {
            "Warning: RAW.seek(1) produced slightly different output on repeat calls; frame output may vary."
        } else {
            null
        }

        val durStr = String.format(java.util.Locale.US, "%.1f", dryRun.duration)
        val summary = if (dryRun.hasLayers) {
            "Code looks good. ${parsedLayers.size} layers found, $durStr s."
        } else {
            "Code looks good, $durStr s. No editable layers found, but ready to render."
        }

        return CodeValidationResult(
            isValid = true,
            cleanedHtml = cleanHtml,
            durationSec = dryRun.duration,
            layers = parsedLayers,
            hasEditableLayers = dryRun.hasLayers,
            warning = warning,
            summaryMessage = summary
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
                    var hasLayers = Array.isArray(window.RAW.layers);
                    var layersJson = hasLayers ? JSON.stringify(window.RAW.layers) : '[]';
                    return JSON.stringify({
                      ok: true,
                      duration: window.RAW.duration,
                      hasLayers: hasLayers,
                      layersJson: layersJson,
                      nonDeterministic: hashDiff,
                      error: ''
                    });
                  } catch (err) {
                    return JSON.stringify({ ok:false, error: 'RAW.seek threw an error: ' + String(err) });
                  }
                })();
            """.trimIndent()

            // Poll up to 8 seconds for window.RAW to appear after scripts execute
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

            DryRunPayload(ok = false, error = "window.RAW was not defined within 10 seconds.")
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }
}
