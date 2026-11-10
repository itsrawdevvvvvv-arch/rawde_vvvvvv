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

    fun buildInjectedHtml(
        rawHtml: String,
        editedLayersJson: String? = null,
        previewFitViewport: Boolean = false
    ): String {
        val viewportMeta = if (previewFitViewport) {
            """<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">"""
        } else {
            ""
        }
        val previewStyle = if (previewFitViewport) {
            """
            <style id="raw-editor-preview-fit">
            html {
              margin: 0 !important;
              padding: 0 !important;
              width: 100% !important;
              height: 100% !important;
              overflow: hidden !important;
              background: #0F0F0F !important;
            }
            body {
              margin: 0 !important;
              padding: 0 !important;
              width: 1920px !important;
              height: 1080px !important;
              position: fixed !important;
              left: 0 !important;
              top: 0 !important;
              overflow: hidden !important;
              display: block !important;
              transform-origin: 0 0 !important;
            }
            #stage {
              position: fixed !important;
              left: 0 !important;
              top: 0 !important;
              right: auto !important;
              bottom: auto !important;
              margin: 0 !important;
              width: 1920px !important;
              height: 1080px !important;
              overflow: hidden !important;
            }
            </style>
            """.trimIndent()
        } else {
            ""
        }
        val fontStyle = buildFontFaceCss()
        val runtimeHelper = buildRuntimeHelperScript(editedLayersJson, previewFitViewport)
        val injection = "$viewportMeta\n$fontStyle\n$previewStyle\n$runtimeHelper"

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

        if (scheme == "https" && host == RAW_HOST && (path == "/index.html" || path == "/" || path.isEmpty())) {
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

        if (scheme == "https" && host == ASSETS_HOST) {
            if (path.startsWith("/fonts/")) {
                val fontFile = path.removePrefix("/fonts/")
                if (FONT_FILENAME_REGEX.matches(fontFile)) {
                    return runCatching {
                        val stream = context.assets.open("fonts/$fontFile")
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
            Triple("Montserrat", 700, "Montserrat-Bold.ttf"),
            Triple("Montserrat", 800, "Montserrat-ExtraBold.ttf"),
            Triple("Montserrat", 900, "Montserrat-Black.ttf"),
            Triple("Open Sans", 400, "OpenSans-Regular.ttf"),
            Triple("Open Sans", 600, "OpenSans-SemiBold.ttf"),
            Triple("Poppins", 600, "Poppins-SemiBold.ttf"),
            Triple("Poppins", 800, "Poppins-ExtraBold.ttf"),
            Triple("Inter", 400, "Inter-Regular.ttf"),
            Triple("Inter", 500, "Inter-Medium.ttf"),
            Triple("Inter", 600, "Inter-SemiBold.ttf"),
            Triple("Inter", 700, "Inter-Bold.ttf"),
            Triple("Inter", 800, "Inter-ExtraBold.ttf"),
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

    private fun buildRuntimeHelperScript(editedLayersJson: String?, previewFitViewport: Boolean): String {
        val layersScript = if (!editedLayersJson.isNullOrBlank()) {
            """
            window.__RAW_EDITED_LAYERS = $editedLayersJson;
            window.__applyEditedLayersIfPresent = function() {
              window.__captureOriginalLayers();
            };
            """.trimIndent()
        } else {
            """
            window.__RAW_EDITED_LAYERS = null;
            window.__applyEditedLayersIfPresent = function(){ window.__captureOriginalLayers(); };
            """.trimIndent()
        }

        val autoFitScript = if (previewFitViewport) {
            """
            window.__fitEditorStage = function(fallbackVw, fallbackVh) {
              var vw = document.documentElement.clientWidth || window.innerWidth || fallbackVw || 340;
              var vh = document.documentElement.clientHeight || window.innerHeight || fallbackVh || 191;
              if (vw <= 1 && fallbackVw > 1) vw = fallbackVw;
              if (vh <= 1 && fallbackVh > 1) vh = fallbackVh;
              if (vw <= 1 || vh <= 1) return false;
              var sc = Math.min(vw / 1920, vh / 1080);
              var offX = Math.max(0, (vw - 1920 * sc) * 0.5);
              var offY = Math.max(0, (vh - 1080 * sc) * 0.5);
              if (document.body) {
                document.body.style.transformOrigin = '0 0';
                document.body.style.width = '1920px';
                document.body.style.height = '1080px';
                document.body.style.transform = 'translate(' + offX + 'px,' + offY + 'px) scale(' + sc + ')';
              }
              return true;
            };
            window.addEventListener('resize', function() { window.__fitEditorStage(0, 0); });
            document.addEventListener('DOMContentLoaded', function() {
              window.__fitEditorStage(0, 0);
              if (window.RAW && typeof window.RAW.seek === 'function') {
                window.__fitEditorStage(0, 0);
              }
            });
            window.__pendingEditorSeek = null;
            window.__editorSeekRetry = 0;
            window.__editorSeek = function(editedList, t, fallbackVw, fallbackVh) {
              window.__pendingEditorSeek = { list: editedList, t: t, vw: fallbackVw || 0, vh: fallbackVh || 0 };
              window.__fitEditorStage(fallbackVw || 0, fallbackVh || 0);
              if (!window.RAW || typeof window.RAW.seek !== 'function') {
                if (window.__editorSeekRetry < 60) {
                  window.__editorSeekRetry++;
                  setTimeout(function() {
                    if (window.__pendingEditorSeek) {
                      var p = window.__pendingEditorSeek;
                      window.__editorSeek(p.list, p.t, p.vw, p.vh);
                    }
                  }, 30);
                }
                return;
              }
              window.__editorSeekRetry = 0;
              window.__syncEditedLayers(editedList, t);
              window.__fitEditorStage(fallbackVw || 0, fallbackVh || 0);
            };
            """.trimIndent()
        } else {
            "window.__fitEditorStage = function(){ return true; };"
        }

        return """
        <script id="raw-edit-runtime">
        $layersScript
        window.__rawOrigCaptured = false;
        window.__rawOriginalLayers = [];
        window.__rawOrigById = {};
        window.__captureOriginalLayers = function() {
          if (window.__rawOrigCaptured || !window.RAW || !Array.isArray(window.RAW.layers)) return;
          window.__rawOrigCaptured = true;
          window.__rawOriginalLayers = [];
          window.__rawOrigById = {};
          for (var i = 0; i < window.RAW.layers.length; i++) {
            var copy = Object.assign({}, window.RAW.layers[i]);
            window.__rawOriginalLayers.push(copy);
            if (copy && copy.id) window.__rawOrigById[copy.id] = copy;
          }
        };
        window.__syncEditedLayers = function(editedList, currentTime) {
          if (!window.RAW || typeof window.RAW.seek !== 'function') return;
          window.__captureOriginalLayers();
          var listToUse = Array.isArray(editedList) ? editedList : window.__RAW_EDITED_LAYERS;
          var hasEdits = Array.isArray(listToUse);
          var activeIds = {};
          var merged = [];

          if (hasEdits) {
            for (var i = 0; i < listToUse.length; i++) {
              var ed = listToUse[i];
              if (!ed || !ed.id) continue;
              activeIds[ed.id] = true;
              var orig = window.__rawOrigById[ed.id];
              var obj = orig ? Object.assign({}, orig, ed) : Object.assign({}, ed);
              merged.push(obj);
            }
            window.RAW.layers = merged;
          } else {
            merged = Array.isArray(window.RAW.layers) ? window.RAW.layers : [];
          }

          var dur = (typeof window.RAW.duration === 'number' && window.RAW.duration > 0) ? window.RAW.duration : currentTime;
          var safeT = Math.max(0, Math.min(currentTime, dur));
          window.RAW.seek(safeT);

          if (!hasEdits) return;
          var stage = document.getElementById('stage');

          // Hide any layer removed by the user in editor
          for (var oldId in window.__rawOrigById) {
            if (!activeIds[oldId]) {
              var removedEl = document.getElementById('L_' + oldId) || document.getElementById(oldId);
              if (removedEl) removedEl.style.display = 'none';
            }
          }

          // Enforce visibility timing, added images, geometry/opacity/z edits, and masks
          for (var k = 0; k < merged.length; k++) {
            var l = merged[k];
            var origL = window.__rawOrigById[l.id];
            var el = document.getElementById('L_' + l.id) || document.getElementById(l.id);

            if (!el && stage && l.type === 'image' && l.src) {
              el = document.createElement('div');
              el.id = 'L_' + l.id;
              el.style.position = 'absolute';
              el.style.transformOrigin = 'center center';
              var newImg = document.createElement('img');
              newImg.style.cssText = 'width:100%;height:100%;object-fit:contain;display:block;pointer-events:none;';
              newImg.setAttribute('src', l.src);
              el.appendChild(newImg);
              stage.appendChild(el);
            }
            if (!el) continue;

            if (l.type === 'image' && l.src) {
              var imgEl = el.tagName === 'IMG' ? el : el.querySelector('img');
              if (imgEl && imgEl.getAttribute('src') !== l.src) {
                imgEl.setAttribute('src', l.src);
              }
            }

            var inWindow = (safeT >= l.start && safeT <= l.end) && !l.hidden && l.opacity > 0;
            if (!inWindow) {
              el.style.display = 'none';
              continue;
            }

            if (el.style.display === 'none') {
              el.style.display = (l.type === 'text') ? 'flex' : 'block';
            }

            var isNew = !origL;
            var geomDirty = isNew ||
              Math.abs((origL.x || 0) - l.x) > 0.5 ||
              Math.abs((origL.y || 0) - l.y) > 0.5 ||
              Math.abs((origL.w || 0) - l.w) > 0.5 ||
              Math.abs((origL.h || 0) - l.h) > 0.5;
            if (geomDirty) {
              el.style.position = 'absolute';
              el.style.left = l.x + 'px';
              el.style.top = l.y + 'px';
              el.style.width = l.w + 'px';
              el.style.height = l.h + 'px';
            }

            if (isNew || origL.z !== l.z) {
              el.style.zIndex = String(l.z);
            }

            var origOp = (origL && origL.opacity != null) ? origL.opacity : 1;
            if (isNew || Math.abs(origOp - l.opacity) > 0.01) {
              var curOp = parseFloat(el.style.opacity);
              var factor = (!isNew && !isNaN(curOp) && origOp > 0) ? Math.min(1, curOp / origOp) : 1;
              el.style.opacity = String(Math.max(0, Math.min(1, l.opacity * factor)));
            }

            if (l.type === 'text' && !isNew && origL.text !== l.text) {
              if (el.childElementCount === 0) {
                el.textContent = l.text || '';
              }
            }

            var m = (l.mask || 'none').toLowerCase();
            var clipVal = '';
            if (m === 'rounded') {
              clipVal = 'inset(0 round 36px)';
            } else if (m === 'circle') {
              clipVal = 'circle(50% at 50% 50%)';
            } else if (m === 'pill') {
              clipVal = 'inset(0 round 999px)';
            } else if (m === 'cinema') {
              clipVal = 'inset(14% 0 14% 0)';
            } else if (m === 'wipe_left') {
              clipVal = 'inset(0 28% 0 0)';
            }
            el.style.clipPath = clipVal;
            el.style.webkitClipPath = clipVal;
          }
        };
        $autoFitScript
        window.__rawSeekAndWait = function(t, reqId) {
          try {
            if (!window.RAW || typeof window.RAW.seek !== 'function') {
              window.__rawDoneId = reqId;
              return;
            }
            window.__syncEditedLayers(window.__RAW_EDITED_LAYERS, t);
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
                // Never navigate away from the sandboxed local stage
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
    }
}