package com.rawedit.app.render

import android.app.Presentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import com.rawedit.app.data.CacheManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.ceil

class FrameRenderer(
    private val context: Context,
    private val cacheManager: CacheManager
) {

    private class OffscreenStagePresentation(
        outerContext: Context,
        display: Display,
        val webView: WebView
    ) : Presentation(outerContext, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val root = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(WIDTH, HEIGHT)
            }
            webView.layoutParams = FrameLayout.LayoutParams(WIDTH, HEIGHT)
            root.addView(webView)
            setContentView(root)
        }
    }

    suspend fun renderVideoOnly(
        rawHtml: String,
        editedLayersJson: String?,
        durationSec: Double,
        outputVideoOnlyMp4: File,
        onProgress: (percent: Int, currentFrame: Int, totalFrames: Int) -> Unit,
        onPreviewFrame: (Bitmap) -> Unit,
        cancelled: () -> Boolean
    ) {
        val totalFrames = ceil(durationSec * FPS).toInt().coerceAtLeast(1)
        val encoderDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "raw-gl-encoder")
        }.asCoroutineDispatcher()

        val captureBmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val captureCanvas = Canvas(captureBmp)

        var virtualDisplay: VirtualDisplay? = null
        var imageReader: ImageReader? = null
        var presentation: OffscreenStagePresentation? = null
        var webView: WebView? = null
        var encoder: SurfaceEncoder? = null

        try {
            encoder = withContext(encoderDispatcher) {
                SurfaceEncoder(
                    outputVideoFile = outputVideoOnlyMp4,
                    width = WIDTH,
                    height = HEIGHT,
                    fps = FPS,
                    durationSec = durationSec
                )
            }

            var servedHtml = ""
            val assetServer = LocalAssetServer(context, cacheManager) { servedHtml }
            servedHtml = assetServer.buildInjectedHtml(rawHtml, editedLayersJson)

            val pageLoaded = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                val baseDisplayContext = create160DpiContext(context)
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val reader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2)
                imageReader = reader

                val vd = dm.createVirtualDisplay(
                    "RawEditRenderStage",
                    WIDTH,
                    HEIGHT,
                    DisplayMetrics.DENSITY_DEFAULT,
                    reader.surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                )
                virtualDisplay = vd

                val hostContext = createPresentationContext(baseDisplayContext, vd.display)
                val wv = WebView(hostContext)
                webView = wv
                assetServer.configureSandboxedWebView(wv) {
                    if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                }

                runCatching {
                    val pres = OffscreenStagePresentation(hostContext, vd.display, wv)
                    presentation = pres
                    pres.show()
                }
                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, WIDTH, HEIGHT)

                wv.resumeTimers()
                wv.onResume()
                wv.loadUrl(LocalAssetServer.ENTRY_URL)
            }

            pageLoaded.await()

            val activeWeb = requireNotNull(webView) { "WebView initialization failed" }
            waitForFontsAndRaw(activeWeb, cancelled)

            for (i in 0 until totalFrames) {
                if (cancelled()) {
                    throw RenderCancelledException()
                }
                val t = minOf(i / FPS.toDouble(), durationSec)
                evalAndWaitTwoFrames(activeWeb, i, t)

                withContext(Dispatchers.Main) {
                    activeWeb.draw(captureCanvas)
                }

                val ptsNs = i * 1_000_000_000L / FPS
                withContext(encoderDispatcher) {
                    encoder.drawBitmapAndSubmit(captureBmp, ptsNs)
                    encoder.drainEncoder(endOfStream = false)
                }

                if (i == 0 || i % 12 == 0) {
                    val preview = Bitmap.createScaledBitmap(captureBmp, 480, 270, true)
                    onPreviewFrame(preview)
                }

                if (i % 4 == 0 || i == totalFrames - 1) {
                    val pct = ((i + 1) * 100 / totalFrames).coerceIn(0, 100)
                    onProgress(pct, i + 1, totalFrames)
                }
            }

            withContext(encoderDispatcher) {
                encoder.finish()
                encoder = null
            }
        } finally {
            withContext(Dispatchers.Main) {
                runCatching { presentation?.dismiss() }
                runCatching { webView?.stopLoading() }
                runCatching { webView?.destroy() }
                runCatching { virtualDisplay?.release() }
                runCatching { imageReader?.close() }
            }
            withContext(encoderDispatcher) {
                runCatching { encoder?.release() }
            }
            encoderDispatcher.close()
            captureBmp.recycle()
        }
    }

    private suspend fun waitForFontsAndRaw(webView: WebView, cancelled: () -> Boolean) {
        withContext(Dispatchers.Main) {
            val initJs = """
                (function() {
                  window.__rawReady = false;
                  Promise.resolve(document.fonts && document.fonts.ready).then(function() {
                    var tries = 0;
                    function check() {
                      if (window.RAW && typeof window.RAW.seek === 'function') {
                        if (typeof window.__applyEditedLayersIfPresent === 'function') {
                          window.__applyEditedLayersIfPresent();
                        }
                        window.__rawReady = true;
                        return;
                      }
                      if (++tries < 200) setTimeout(check, 25);
                    }
                    check();
                  });
                })();
            """.trimIndent()
            webView.evaluateJavascript(initJs, null)
        }

        repeat(400) {
            if (cancelled()) throw RenderCancelledException()
            val ready = withContext(Dispatchers.Main) {
                val d = CompletableDeferred<Boolean>()
                webView.evaluateJavascript("Boolean(window.__rawReady)") { res ->
                    d.complete(res == "true")
                }
                d.await()
            }
            if (ready) return
            delay(20)
        }
        error("Timed out waiting for bundled fonts and window.RAW")
    }

    private suspend fun evalAndWaitTwoFrames(webView: WebView, reqId: Int, tSec: Double) {
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript("window.__rawSeekAndWait($tSec, $reqId)", null)
        }

        // Wait until the JS promise finishes decoding visible images + 2 rAFs
        var spins = 0
        while (spins < 500) {
            val done = withContext(Dispatchers.Main) {
                val d = CompletableDeferred<Boolean>()
                webView.evaluateJavascript("window.__rawDoneId === $reqId") { res ->
                    d.complete(res == "true")
                }
                d.await()
            }
            if (done) break
            spins++
            delay(4)
        }

        // Also await WebView visual state commit for the current frame
        withContext(Dispatchers.Main) {
            val committed = CompletableDeferred<Unit>()
            webView.postVisualStateCallback(reqId.toLong(), object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (!committed.isCompleted) committed.complete(Unit)
                }
            })
            committed.await()
        }
    }

    private fun create160DpiContext(base: Context): Context {
        val config = Configuration(base.resources.configuration).apply {
            densityDpi = DisplayMetrics.DENSITY_DEFAULT
            fontScale = 1.0f
        }
        return base.createConfigurationContext(config)
    }

    private fun createPresentationContext(base160: Context, display: Display): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                base160.createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION, null)
            }.getOrElse {
                base160.createDisplayContext(display)
            }
        } else {
            base160.createDisplayContext(display)
        }
    }

    class RenderCancelledException : RuntimeException("Render cancelled")

    companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val FPS = 30
    }
}
