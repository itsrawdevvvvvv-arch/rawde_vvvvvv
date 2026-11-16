package com.rawedit.app.domain

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.WebView
import com.rawedit.app.data.CacheManager
import com.rawedit.app.render.LocalAssetServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class OpenDesignDeliverableType(val id: String, val label: String) {
    IMAGE("image", "Image / Poster"),
    MOTION("motion", "Motion Video"),
    DECK("deck", "Slide Deck"),
    PROTOTYPE("prototype", "UI / Landing Page");

    companion object {
        fun fromId(id: String): OpenDesignDeliverableType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: IMAGE
    }
}

object OpenDesignEngine {

    fun loadDesignSystemSummary(context: Context): String {
        val designMd = runCatching {
            context.assets.open("open-design/DESIGN.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
        val motionMd = runCatching {
            context.assets.open("open-design/Motion.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
        return "$designMd\n\n$motionMd".trim()
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun renderHtmlToPng(
        context: Context,
        cacheManager: CacheManager,
        rawHtml: String,
        width: Int,
        height: Int,
        fileNamePrefix: String = "opendesign"
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val safeW = width.coerceIn(320, 2160)
            val safeH = height.coerceIn(320, 2160)
            val outFile = File(cacheManager.sharedPacksDir, "${fileNamePrefix}_${System.currentTimeMillis()}.png")
            outFile.parentFile?.mkdirs()

            var servedHtml = ""
            val server = LocalAssetServer(context, cacheManager, safeW, safeH) { servedHtml }
            servedHtml = server.buildInjectedHtml(rawHtml)

            val ready = CompletableDeferred<Unit>()
            var wv: WebView? = null
            withContext(Dispatchers.Main) {
                val config = Configuration(context.resources.configuration).apply {
                    densityDpi = DisplayMetrics.DENSITY_DEFAULT
                    fontScale = 1.0f
                }
                val ctx160 = context.createConfigurationContext(config)
                val webView = WebView(ctx160)
                wv = webView
                server.configureSandboxedWebView(webView) {
                    if (!ready.isCompleted) ready.complete(Unit)
                }
                webView.setInitialScale(100)
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(safeW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(safeH, View.MeasureSpec.EXACTLY)
                )
                webView.layout(0, 0, safeW, safeH)
                webView.loadUrl(LocalAssetServer.ENTRY_URL)
            }

            ready.await()
            delay(250)

            val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            withContext(Dispatchers.Main) {
                val webView = wv
                if (webView != null) {
                    webView.evaluateJavascript(
                        "(function(){ var r=(window.__ensureRawContract&&window.__ensureRawContract())||window.RAW; if(r&&typeof r.seek==='function'){ r.seek(0.5); } })()",
                        null
                    )
                }
            }
            delay(150)
            withContext(Dispatchers.Main) {
                wv?.draw(canvas)
                runCatching { wv?.destroy() }
            }

            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 96, fos)
            }
            bmp.recycle()
            outFile
        }
    }

    fun buildManualDesignHtml(
        type: OpenDesignDeliverableType,
        width: Int,
        height: Int,
        kicker: String,
        headline: String,
        subtext: String,
        bgHex: String,
        accentHex: String,
        textHex: String,
        durationSec: Double = 6.0
    ): String {
        val escKicker = escapeHtml(kicker)
        val escHead = escapeHtml(headline)
        val escSub = escapeHtml(subtext)
        val safeDur = String.format(Locale.US, "%.2f", durationSec.coerceIn(2.0, 30.0))

        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            html, body { width: ${width}px; height: ${height}px; overflow: hidden; background: $bgHex; color: $textHex; font-family: 'Montserrat', 'Inter', sans-serif; }
            #stage {
              position: relative; width: ${width}px; height: ${height}px; overflow: hidden;
              display: flex; flex-direction: column; justify-content: center; padding: 8%;
              background: radial-gradient(circle at 80% 20%, ${accentHex}26 0%, transparent 55%), $bgHex;
            }
            .grid {
              position: absolute; inset: 0; opacity: 0.08;
              background-image: linear-gradient($textHex 1px, transparent 1px), linear-gradient(90deg, $textHex 1px, transparent 1px);
              background-size: 56px 56px;
            }
            .kicker {
              display: inline-block; align-self: flex-start; padding: 10px 22px; border-radius: 999px;
              background: $accentHex; color: #111114; font-weight: 800; font-size: ${(width * 0.018).toInt().coerceIn(16, 28)}px;
              letter-spacing: 0.08em; text-transform: uppercase; margin-bottom: 28px;
            }
            .headline {
              font-weight: 900; font-size: ${(width * 0.068).toInt().coerceIn(42, 110)}px; line-height: 1.05;
              letter-spacing: -0.03em; margin-bottom: 24px; max-width: 92%;
            }
            .sub {
              font-size: ${(width * 0.025).toInt().coerceIn(20, 40)}px; line-height: 1.45; opacity: 0.82; max-width: 82%;
            }
            .badge {
              position: absolute; right: 7%; bottom: 7%; padding: 12px 24px; border-radius: 16px;
              border: 1.5px solid ${accentHex}66; background: ${bgHex}CC; font-family: 'JetBrains Mono', monospace;
              font-size: ${(width * 0.015).toInt().coerceIn(14, 24)}px; color: $accentHex;
            }
          </style>
        </head>
        <body>
          <div id="stage">
            <div class="grid"></div>
            <div id="k" class="kicker">$escKicker</div>
            <div id="h" class="headline">$escHead</div>
            <div id="s" class="sub">$escSub</div>
            <div id="b" class="badge">OPEN DESIGN • ${type.label.uppercase(Locale.US)}</div>
          </div>
          <script>
            window.RAW = {
              duration: $safeDur,
              width: $width,
              height: $height,
              fps: 30,
              seek: function(t) {
                var u = Math.max(0, Math.min(1, t / 0.8));
                var e = 1 - Math.pow(1 - u, 3);
                var floatY = Math.sin(t * 2.1) * 8;
                var k = document.getElementById('k');
                var h = document.getElementById('h');
                var s = document.getElementById('s');
                if (k) { k.style.opacity = e; k.style.transform = 'translateY(' + ((1 - e) * 28) + 'px)'; }
                if (h) { h.style.opacity = e; h.style.transform = 'translateY(' + ((1 - e) * 44 + floatY * 0.4) + 'px)'; }
                if (s) { s.style.opacity = Math.max(0, Math.min(1, (t - 0.15) / 0.7)) * 0.85; s.style.transform = 'translateY(' + ((1 - e) * 32) + 'px)'; }
              }
            };
            window.RAW.seek(0.6);
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
