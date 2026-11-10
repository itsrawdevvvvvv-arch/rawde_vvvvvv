package com.rawedit.app.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rawedit.app.data.CacheManager
import java.io.ByteArrayInputStream

class LocalAssetServer(
    private val context: Context,
    private val cacheManager: CacheManager,
    private val htmlProvider: () -> String
) {

    fun buildInjectedHtml(rawHtml: String): String {
        val fontStyle = buildFontFaceCss()
        val runtimeHelper = buildRuntimeHelperScript()
        val injection = "$fontStyle\n$runtimeHelper"

        val lower = rawHtml.lowercase()
        val headEndIdx = lower.indexOf("</head>")
        return when {
            headEndIdx >= 0 -> {
                rawHtml.substring(0, headEndIdx) + injection + rawHtml.substring(headEndIdx)
            }
            lower.indexOf("<body") >= 0 -> {
                val bodyIdx = lower.indexOf("<body")
                rawHtml.substring(0, bodyIdx) + "<head>$injection</head>" + rawHtml.substring(bodyIdx)
            }
            else -> "$injection\n$rawHtml"
        }
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        val scheme = url.scheme?.lowercase()
        val host = url.host?.lowercase()
        val path = url.path.orEmpty()

        if (scheme == "https" && host == RAW_HOST) {
            if (path.startsWith("/fonts/")) {
                return serveBundledFont(path.removePrefix("/fonts/"))
            }
            if (path == "/index.html" || path == "/" || path.isEmpty()) {
                val html = htmlProvider()
                return WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    200,
                    "OK",
                    CORS_HEADERS,
                    ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
                )
            }
        }

        if (scheme == "https" && host == ASSETS_HOST) {
            if (path.startsWith("/fonts/")) {
                return serveBundledFont(path.removePrefix("/fonts/"))
            } else {
                val assetName = path.removePrefix("/")
                val assetFile = cacheManager.getAssetFile(assetName)
                if (assetFile != null) {
                    val mime = when {
                        assetName.endsWith(".png", ignoreCase = true) -> "image/png"
                        assetName.endsWith(".webp", ignoreCase = true) -> "image/webp"
                        else -> "image/jpeg"
                    }
                    return runCatching {
                        WebResourceResponse(
                            mime,
                            null,
                            200,
                            "OK",
                            CORS_HEADERS,
                            assetFile.inputStream()
                        )
                    }.getOrElse { forbidden() }
                }
            }
        }

        return forbidden()
    }

    private fun serveBundledFont(requestedFile: String): WebResourceResponse {
        val cleanName = requestedFile.substringAfterLast('/')
        val resolvedName = FONT_ALIASES[cleanName] ?: cleanName
        if (!FONT_FILENAME_REGEX.matches(resolvedName)) return forbidden()
        return runCatching {
            val stream = context.assets.open("fonts/$resolvedName")
            WebResourceResponse(
                "font/ttf",
                null,
                200,
                "OK",
                CORS_HEADERS,
                stream
            )
        }.getOrElse { forbidden() }
    }

    private fun forbidden(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            403,
            "Forbidden",
            CORS_HEADERS,
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun buildFontFaceCss(): String {
        val rules = listOf(
            Triple("Montserrat", 600, "Montserrat-Bold.ttf"),
            Triple("Montserrat", 700, "Montserrat-Bold.ttf"),
            Triple("Montserrat", 800, "Montserrat-ExtraBold.ttf"),
            Triple("Montserrat", 900, "Montserrat-Black.ttf"),
            Triple("Open Sans", 400, "OpenSans-Regular.ttf"),
            Triple("Open Sans", 600, "OpenSans-SemiBold.ttf"),
            Triple("Poppins", 400, "Poppins-SemiBold.ttf"),
            Triple("Poppins", 600, "Poppins-SemiBold.ttf"),
            Triple("Poppins", 700, "Poppins-ExtraBold.ttf"),
            Triple("Poppins", 800, "Poppins-ExtraBold.ttf"),
            Triple("Poppins", 900, "Poppins-ExtraBold.ttf"),
            Triple("Inter", 400, "Inter-Regular.ttf"),
            Triple("Inter", 500, "Inter-Medium.ttf"),
            Triple("Inter", 600, "Inter-SemiBold.ttf"),
            Triple("Inter", 700, "Inter-Bold.ttf"),
            Triple("Inter", 800, "Inter-ExtraBold.ttf"),
            Triple("Inter", 900, "Inter-ExtraBold.ttf"),
            Triple("Bebas Neue", 400, "BebasNeue-Regular.ttf"),
            Triple("Anton", 400, "Anton-Regular.ttf"),
            Triple("Oswald", 600, "Oswald-SemiBold.ttf"),
            Triple("Playfair Display", 700, "PlayfairDisplay-Bold.ttf"),
            Triple("Playfair Display", 900, "PlayfairDisplay-Black.ttf"),
            Triple("Space Grotesk", 700, "SpaceGrotesk-Bold.ttf"),
            Triple("JetBrains Mono", 400, "JetBrainsMono-Regular.ttf"),
            Triple("JetBrains Mono", 500, "JetBrainsMono-Medium.ttf"),
            Triple("JetBrains Mono", 700, "JetBrainsMono-Bold.ttf")
        ).joinToString("\n") { (family, weight, file) ->
            """
            @font-face {
              font-family: '$family';
              font-style: normal;
              font-weight: $weight;
              font-display: block;
              src: url('https://$ASSETS_HOST/fonts/$file') format('truetype');
            }
            """.trimIndent()
        }

        return "<style id=\"raw-edit-fonts\">\n$rules\n</style>"
    }

    private fun buildRuntimeHelperScript(): String {
        return """
        <script id="raw-edit-runtime">
        window.__rawLastError = '';
        window.addEventListener('error', function(e) {
          if (!window.__rawLastError) {
            window.__rawLastError = (e && e.message) ? String(e.message) : 'Script error';
          }
        });
        window.__ensureRawContract = function() {
          if (window.RAW && typeof window.RAW.seek === 'function' && typeof window.RAW.duration === 'number' && window.RAW.duration > 0) {
            return window.RAW;
          }
          if (typeof window.seek === 'function') {
            var dur = (window.PROJECT && typeof window.PROJECT.duration === 'number' && window.PROJECT.duration > 0)
              ? window.PROJECT.duration
              : ((typeof window.PROJECT_DURATION === 'number' && window.PROJECT_DURATION > 0) ? window.PROJECT_DURATION : 0);
            if (dur > 0) {
              window.RAW = {
                duration: dur,
                width: 1920,
                height: 1080,
                fps: 30,
                seek: function(t) { return window.seek(t); }
              };
              return window.RAW;
            }
          }
          return null;
        };
        window.__rawSeekAndWait = function(t, reqId) {
          try {
            var raw = window.__ensureRawContract();
            if (!raw || typeof raw.seek !== 'function') {
              window.__rawDoneId = reqId;
              return;
            }
            var dur = (typeof raw.duration === 'number' && raw.duration > 0) ? raw.duration : t;
            var safeT = Math.max(0, Math.min(t, dur));
            raw.seek(safeT);
            var imgs = Array.prototype.slice.call(document.querySelectorAll('img')).filter(function(img) {
              return img && img.getAttribute('src') && img.style.display !== 'none' && (!img.complete || img.naturalWidth === 0);
            });
            var decodeProms = imgs.map(function(img) {
              if (typeof img.decode === 'function') {
                return img.decode().catch(function(){});
              }
              return Promise.resolve();
            });
            Promise.all(decodeProms).then(function() {
              requestAnimationFrame(function() {
                requestAnimationFrame(function() {
                  window.__rawDoneId = reqId;
                });
              });
            });
          } catch (e) {
            window.__rawDoneId = reqId;
          }
        };
        </script>
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configureSandboxedWebView(
        webView: WebView,
        onPageReady: (() -> Unit)? = null
    ) {
        webView.setBackgroundColor(Color.parseColor("#0F0F0F"))
        webView.settings.apply {
            javaScriptEnabled = true
            blockNetworkLoads = true
            blockNetworkImage = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            domStorageEnabled = false
            databaseEnabled = false
            useWideViewPort = false
            loadWithOverviewMode = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = true
        }
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse {
                return intercept(request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageReady?.invoke()
            }
        }
    }

    companion object {
        const val RAW_HOST = "raw.local"
        const val ASSETS_HOST = "assets.local"
        const val ENTRY_URL = "https://raw.local/index.html"
        private val FONT_FILENAME_REGEX = Regex("^[A-Za-z0-9_-]+\\.ttf$")
        private val CORS_HEADERS = mapOf("Access-Control-Allow-Origin" to "*")
        private val FONT_ALIASES = mapOf(
            "Poppins-Black.ttf" to "Poppins-ExtraBold.ttf",
            "Poppins-Bold.ttf" to "Poppins-ExtraBold.ttf",
            "Poppins-Regular.ttf" to "Poppins-SemiBold.ttf",
            "Montserrat-SemiBold.ttf" to "Montserrat-Bold.ttf"
        )
    }
}
