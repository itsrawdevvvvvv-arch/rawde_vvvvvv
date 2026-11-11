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
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.Locale

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
            if (path.startsWith("/assets/")) {
                return serveLocalLockerAsset(path.removePrefix("/assets/"), request.requestHeaders)
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
                val assetName = path.removePrefix("/assets/").removePrefix("/")
                return serveLocalLockerAsset(assetName, request.requestHeaders)
            }
        }

        return forbidden()
    }

    private fun serveLocalLockerAsset(
        assetName: String,
        requestHeaders: Map<String, String>?
    ): WebResourceResponse {
        val assetFile = cacheManager.getAssetFile(assetName) ?: return forbidden()
        val lowerName = assetFile.name.lowercase(Locale.US)
        val mime = when {
            lowerName.endsWith(".png") -> "image/png"
            lowerName.endsWith(".webp") -> "image/webp"
            lowerName.endsWith(".mp4") -> "video/mp4"
            lowerName.endsWith(".mov") -> "video/quicktime"
            lowerName.endsWith(".webm") -> "video/webm"
            else -> "image/jpeg"
        }
        val rangeHeader = requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Range", ignoreCase = true) }
            ?.value
            ?.trim()

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            return runCatching {
                servePartialRange(assetFile, mime, rangeHeader)
            }.getOrElse { forbidden() }
        }

        val totalLen = assetFile.length()
        val fullHeaders = CORS_HEADERS + mapOf(
            "Accept-Ranges" to "bytes",
            "Content-Length" to totalLen.toString()
        )
        return runCatching {
            WebResourceResponse(
                mime,
                null,
                200,
                "OK",
                fullHeaders,
                assetFile.inputStream()
            )
        }.getOrElse { forbidden() }
    }

    private fun servePartialRange(
        file: File,
        mime: String,
        rangeHeader: String
    ): WebResourceResponse {
        val total = file.length()
        if (total <= 0L) return forbidden()

        val spec = rangeHeader.removePrefix("bytes=").substringBefore(',').trim()
        val dashIdx = spec.indexOf('-')
        if (dashIdx < 0) return forbidden()

        val startPart = spec.substring(0, dashIdx).trim()
        val endPart = spec.substring(dashIdx + 1).trim()

        val start: Long
        val end: Long
        if (startPart.isEmpty()) {
            val suffix = endPart.toLongOrNull() ?: return forbidden()
            start = (total - suffix).coerceAtLeast(0L)
            end = total - 1L
        } else {
            start = startPart.toLongOrNull() ?: return forbidden()
            end = (endPart.toLongOrNull() ?: (total - 1L)).coerceAtMost(total - 1L)
        }
        if (start < 0L || end < start || start >= total) {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                416,
                "Range Not Satisfiable",
                CORS_HEADERS + mapOf("Content-Range" to "bytes */$total"),
                ByteArrayInputStream(ByteArray(0))
            )
        }

        val chunkLen = end - start + 1L
        val headers = CORS_HEADERS + mapOf(
            "Accept-Ranges" to "bytes",
            "Content-Range" to "bytes $start-$end/$total",
            "Content-Length" to chunkLen.toString()
        )
        val stream = BoundedRandomAccessInputStream(file, start, chunkLen)
        return WebResourceResponse(
            mime,
            null,
            206,
            "Partial Content",
            headers,
            stream
        )
    }

    private class BoundedRandomAccessInputStream(
        file: File,
        startOffset: Long,
        private var remaining: Long
    ) : InputStream() {
        private val raf = RandomAccessFile(file, "r").apply { seek(startOffset) }

        override fun read(): Int {
            if (remaining <= 0L) return -1
            val b = raf.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val n = raf.read(b, off, toRead)
            if (n > 0) remaining -= n.toLong()
            return n
        }

        override fun close() {
            runCatching { raf.close() }
        }
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
        window.__rawSyncVideo = function(videoEl, t, start, end, speed) {
          if (!videoEl) return Promise.resolve();
          var s = Number.isFinite(start) ? start : 0;
          var e = (end == null || !Number.isFinite(end)) ? Infinity : end;
          var sp = (speed == null || !Number.isFinite(speed) || speed <= 0) ? 1 : speed;
          videoEl.muted = true;
          videoEl.playsInline = true;
          videoEl.preload = 'auto';
          if (!videoEl.paused) {
            try { videoEl.pause(); } catch (_) {}
          }
          function computeTarget() {
            var dur = (Number.isFinite(videoEl.duration) && videoEl.duration > 0) ? videoEl.duration : 60;
            var clampedGlobal = Math.max(s, Math.min(t, e));
            var localT = Math.max(0, (clampedGlobal - s) * sp);
            return Math.min(localT, Math.max(0, dur - 0.033));
          }
          function performSeek() {
            var target = computeTarget();
            if (Math.abs((videoEl.currentTime || 0) - target) < 0.015 && videoEl.readyState >= 2) {
              return Promise.resolve();
            }
            return new Promise(function(resolve) {
              var done = false;
              var timer = setTimeout(finish, 260);
              function finish() {
                if (done) return;
                done = true;
                clearTimeout(timer);
                videoEl.removeEventListener('seeked', finish);
                resolve();
              }
              videoEl.addEventListener('seeked', finish, { once: true });
              try {
                videoEl.currentTime = target;
              } catch (_) {
                finish();
              }
            });
          }
          var p;
          if (videoEl.readyState < 1) {
            p = new Promise(function(resolve) {
              var settled = false;
              var metaTimer = setTimeout(go, 340);
              function go() {
                if (settled) return;
                settled = true;
                clearTimeout(metaTimer);
                videoEl.removeEventListener('loadedmetadata', go);
                videoEl.removeEventListener('error', go);
                performSeek().then(resolve);
              }
              videoEl.addEventListener('loadedmetadata', go, { once: true });
              videoEl.addEventListener('error', go, { once: true });
            });
          } else {
            p = performSeek();
          }
          videoEl.__rawSeekPromise = p;
          return p;
        };
        function __attachRawHelpers(obj) {
          if (!obj || typeof obj !== 'object') return obj;
          if (typeof obj.syncVideo !== 'function') obj.syncVideo = window.__rawSyncVideo;
          if (!obj.assets) obj.assets = window.__RAW_ASSETS__ || {};
          if (typeof obj.asset !== 'function') {
            obj.asset = function(p) { return String(p || '').replace(/^\.?\//, ''); };
          }
          return obj;
        }
        (function() {
          var _store = __attachRawHelpers(window.RAW || {});
          try {
            Object.defineProperty(window, 'RAW', {
              configurable: true,
              enumerable: true,
              get: function() { return _store; },
              set: function(next) {
                if (next && typeof next === 'object') {
                  _store = __attachRawHelpers(next);
                }
              }
            });
          } catch (_) {
            window.RAW = _store;
          }
        })();
        window.__ensureRawContract = function() {
          if (window.RAW && typeof window.RAW.seek === 'function' && typeof window.RAW.duration === 'number' && window.RAW.duration > 0) {
            return __attachRawHelpers(window.RAW);
          }
          if (typeof window.seek === 'function') {
            var dur = (window.PROJECT && typeof window.PROJECT.duration === 'number' && window.PROJECT.duration > 0)
              ? window.PROJECT.duration
              : ((typeof window.PROJECT_DURATION === 'number' && window.PROJECT_DURATION > 0) ? window.PROJECT_DURATION : 0);
            if (dur > 0) {
              window.RAW = __attachRawHelpers({
                duration: dur,
                width: 1920,
                height: 1080,
                fps: 30,
                seek: function(t) { return window.seek(t); }
              });
              return window.RAW;
            }
          }
          return null;
        };
        window.__rawLastDiagnostics = { autoFitCount: 0, notes: [], warnings: [] };
        window.__rawAutoFitStage = function() {
          var stage = document.getElementById('stage');
          if (!stage) {
            var empty = { autoFitCount: 0, notes: [], warnings: [] };
            window.__rawLastDiagnostics = empty;
            return empty;
          }
          stage.style.width = '1920px';
          stage.style.height = '1080px';
          stage.style.overflow = 'hidden';

          var activeContainer = stage;
          var scenes = stage.querySelectorAll('.scene');
          for (var si = 0; si < scenes.length; si++) {
            if (getComputedStyle(scenes[si]).display !== 'none') {
              activeContainer = scenes[si];
              break;
            }
          }

          var allEls = stage.querySelectorAll('*');
          for (var ri = 0; ri < allEls.length; ri++) {
            var rel = allEls[ri];
            if (rel.dataset && rel.dataset.rawSaved === '1') {
              rel.style.fontSize = rel.dataset.rawOrigFs || '';
              rel.style.maxWidth = rel.dataset.rawOrigMw || '';
              rel.style.whiteSpace = rel.dataset.rawOrigWs || '';
              rel.style.overflowWrap = rel.dataset.rawOrigOw || '';
              rel.style.top = rel.dataset.rawOrigTop || '';
            }
          }

          function isSkipped(el) {
            if (!el || el.nodeType !== 1) return true;
            var cs = getComputedStyle(el);
            if (cs.display === 'none' || cs.visibility === 'hidden') return true;
            var cls = (el.className && typeof el.className === 'string') ? el.className.toLowerCase() : '';
            var id = (el.id || '').toLowerCase();
            if (cls.indexOf('ghost') !== -1 || id.indexOf('ghost') !== -1) return true;
            if (cls.indexOf('dust') !== -1 || cls.indexOf('progress') !== -1 || cls.indexOf('brackets') !== -1) return true;
            if (cls === 'w' || cls === 'lab' || cls === 'bar' || cls === 'fill' || cls === 'seg') return true;
            var tag = (el.tagName || '').toUpperCase();
            if (tag === 'SVG' || tag === 'PATH' || tag === 'CIRCLE' || tag === 'RECT' || tag === 'STYLE' || tag === 'SCRIPT' || tag === 'VIDEO' || tag === 'IMG') return true;
            if (el.offsetWidth >= 1750 && el.offsetHeight >= 900) return true;
            var baseOp = parseFloat(cs.opacity);
            if (!isNaN(baseOp) && baseOp > 0 && baseOp <= 0.25 && cls.indexOf('headline') === -1 && cls.indexOf('card') === -1) return true;
            return false;
          }

          function saveOrig(el) {
            if (el.dataset.rawSaved === '1') return;
            el.dataset.rawSaved = '1';
            el.dataset.rawOrigFs = el.style.fontSize || '';
            el.dataset.rawOrigMw = el.style.maxWidth || '';
            el.dataset.rawOrigWs = el.style.whiteSpace || '';
            el.dataset.rawOrigOw = el.style.overflowWrap || '';
            el.dataset.rawOrigTop = el.style.top || '';
          }

          var rightBlockers = [];
          var children = activeContainer.children;
          for (var ci = 0; ci < children.length; ci++) {
            var ch = children[ci];
            if (isSkipped(ch)) continue;
            var cLeft = ch.offsetLeft;
            var cTop = ch.offsetTop;
            var cW = ch.offsetWidth;
            var cH = ch.offsetHeight;
            var cCls = (ch.className && typeof ch.className === 'string') ? ch.className.toLowerCase() : '';
            if ((cCls.indexOf('card') !== -1 || cCls.indexOf('phone') !== -1 || cCls.indexOf('round') !== -1) && cLeft >= 920 && cW >= 160) {
              var pad = (cCls.indexOf('card') !== -1) ? 44 : 24;
              rightBlockers.push({ left: Math.max(0, cLeft - pad), top: Math.max(0, cTop - 20), bottom: cTop + cH + 20, right: cLeft + cW + pad });
            }
          }

          var hasProgress = !!stage.querySelector('.progress');
          var maxSafeBottom = hasProgress ? 972 : 1020;
          var notes = [];
          var warnings = [];
          var autoFitCount = 0;

          for (var i = 0; i < children.length; i++) {
            var el = children[i];
            if (isSkipped(el)) continue;
            var cls = (el.className && typeof el.className === 'string') ? el.className.toLowerCase() : '';
            var isTextBlock = cls.indexOf('headline') !== -1 || cls.indexOf('kicker') !== -1 || cls.indexOf('sub') !== -1 ||
              cls.indexOf('pill') !== -1 || cls.indexOf('chips') !== -1 || (' ' + cls + ' ').indexOf(' t ') !== -1 ||
              (el.children.length === 0 && (el.textContent || '').trim().length > 0);
            if (!isTextBlock) {
              var bEdge = el.offsetTop + el.offsetHeight;
              if (bEdge > maxSafeBottom && el.offsetHeight < 920) {
                saveOrig(el);
                var shiftUp = Math.min(el.offsetTop - 40, bEdge - maxSafeBottom);
                if (shiftUp > 4) {
                  el.style.top = Math.round(el.offsetTop - shiftUp) + 'px';
                  autoFitCount++;
                  notes.push('Raised element above progress bar by ' + Math.round(shiftUp) + 'px');
                }
              }
              continue;
            }

            var cs = getComputedStyle(el);
            var left = el.offsetLeft;
            var top = el.offsetTop;
            var isCentered = cs.textAlign === 'center' || (el.style.transform || '').indexOf('translateX(-50%)') !== -1 ||
              (el._base && String(el._base).indexOf('translateX(-50%)') !== -1);

            var allowedWidth = 1660;
            var allowedRight = 1800;
            if (isCentered) {
              allowedWidth = 1640;
            } else if (left < 920) {
              var minBlockerLeft = 1800;
              for (var bi = 0; bi < rightBlockers.length; bi++) {
                var rb = rightBlockers[bi];
                if (top + Math.max(el.offsetHeight, 90) >= rb.top && top <= rb.bottom) {
                  if (rb.left < minBlockerLeft) minBlockerLeft = rb.left;
                }
              }
              allowedRight = minBlockerLeft;
              allowedWidth = Math.max(360, allowedRight - left);
            } else {
              allowedWidth = Math.max(240, 1800 - left);
            }

            var naturalW = Math.max(el.offsetWidth, el.scrollWidth);
            if (naturalW > allowedWidth) {
              saveOrig(el);
              var hasWords = (el.textContent || '').trim().indexOf(' ') !== -1;
              if (hasWords && cs.whiteSpace === 'nowrap') {
                el.style.whiteSpace = 'normal';
                el.style.maxWidth = Math.round(allowedWidth) + 'px';
                el.style.overflowWrap = 'break-word';
                autoFitCount++;
                notes.push('Wrapped "' + (el.textContent || '').trim().slice(0, 22) + '" (' + Math.round(allowedWidth) + 'px)');
              } else {
                el.style.maxWidth = Math.round(allowedWidth) + 'px';
              }

              var afterW = Math.max(el.offsetWidth, el.scrollWidth);
              if (afterW > allowedWidth) {
                var origFs = parseFloat(cs.fontSize) || 64;
                var ratio = Math.max(0.42, (allowedWidth / afterW) * 0.96);
                var nextFs = Math.max(24, Math.floor(origFs * ratio));
                if (nextFs < origFs) {
                  el.style.fontSize = nextFs + 'px';
                  autoFitCount++;
                  notes.push('Resized "' + (el.textContent || '').trim().slice(0, 22) + '" ' + Math.round(origFs) + '->' + nextFs + 'px');
                }
              }
            }

            var newBottom = el.offsetTop + el.offsetHeight;
            if (newBottom > maxSafeBottom && el.offsetHeight < 860) {
              saveOrig(el);
              var nudge = Math.min(el.offsetTop - 50, newBottom - maxSafeBottom);
              if (nudge > 4) {
                el.style.top = Math.round(el.offsetTop - nudge) + 'px';
                autoFitCount++;
                notes.push('Lifted "' + (el.textContent || '').trim().slice(0, 20) + '" above progress bar');
              }
            }

            var finalW = Math.max(el.offsetWidth, el.scrollWidth);
            var actualLeft = isCentered ? (left - finalW / 2) : left;
            var actualRight = isCentered ? (left + finalW / 2) : (left + finalW);
            if (actualLeft < 40 || actualRight > 1880) {
              warnings.push('Edge clip on "' + (el.textContent || '').trim().slice(0, 24) + '"');
            } else if (!isCentered && left < 920 && actualRight > allowedRight + 8) {
              warnings.push('Card overlap near "' + (el.textContent || '').trim().slice(0, 24) + '"');
            }
          }

          var out = { autoFitCount: autoFitCount, notes: notes, warnings: warnings };
          window.__rawLastDiagnostics = out;
          return out;
        };
        window.__rawExtractScenes = function() {
          try {
            if (window.PROJECT && Array.isArray(window.PROJECT.scenes)) {
              return window.PROJECT.scenes.map(function(s, idx) {
                return { index: idx + 1, start: Number(s.start) || 0, end: Number(s.end) || 0, bg: String(s.bg || '') };
              });
            }
          } catch (e) {}
          return [];
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
            var seekRet = raw.seek(safeT);
            window.__rawAutoFitStage();

            var waitPromises = [];
            if (seekRet && typeof seekRet.then === 'function') {
              waitPromises.push(seekRet.catch(function(){}));
            }

            var vids = document.querySelectorAll('video');
            for (var vi = 0; vi < vids.length; vi++) {
              var v = vids[vi];
              if (v.__rawSeekPromise && typeof v.__rawSeekPromise.then === 'function') {
                waitPromises.push(v.__rawSeekPromise.catch(function(){}));
              } else if (v.seeking) {
                waitPromises.push(new Promise(function(resolve) {
                  var timer = setTimeout(doneV, 280);
                  function doneV() {
                    clearTimeout(timer);
                    v.removeEventListener('seeked', doneV);
                    resolve();
                  }
                  v.addEventListener('seeked', doneV, { once: true });
                }));
              }
            }

            var imgs = Array.prototype.slice.call(document.querySelectorAll('img')).filter(function(img) {
              return img && img.getAttribute('src') && img.style.display !== 'none' && (!img.complete || img.naturalWidth === 0);
            });
            for (var ii = 0; ii < imgs.length; ii++) {
              if (typeof imgs[ii].decode === 'function') {
                waitPromises.push(imgs[ii].decode().catch(function(){}));
              }
            }

            Promise.all(waitPromises).then(function() {
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
        webView.setBackgroundColor(Color.parseColor("#0D0D0F"))
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
            mediaPlaybackRequiresUserGesture = false
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
