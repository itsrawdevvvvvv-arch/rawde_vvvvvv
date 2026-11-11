package com.rawedit.app.domain

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class CutoutProcessOutput(
    val resultBitmap: Bitmap,
    val hadExistingTransparency: Boolean,
    val detectedUniformMatte: Boolean,
    val bgRemoved: Boolean,
    val toleranceUsed: Int,
    val padUsed: CutoutPadRatios
)

object CutoutProcessor {

    private val svgRenderMutex = Mutex()

    private class OffscreenSvgPresentation(
        outerContext: Context,
        display: Display,
        val webView: WebView,
        val widthPx: Int,
        val heightPx: Int
    ) : Presentation(outerContext, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val root = FrameLayout(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(widthPx, heightPx)
            }
            webView.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
            root.addView(webView)
            setContentView(root)
        }
    }

    suspend fun rasterizeSingleSvgToCutout(
        context: Context,
        rawSvg: String,
        pad: CutoutPadRatios = CutoutPadRatios(0.05f, 0.05f, 0.05f, 0.05f),
        tintHex: String? = null,
        sizePx: Int = 1024
    ): CutoutProcessOutput {
        val clampedSize = sizePx.coerceIn(256, 1536)
        val safePad = pad.clamped()
        val rawTiles = rasterizeSvgGrid(
            context = context,
            rawSvgs = listOf(rawSvg),
            tilePx = clampedSize,
            pad = safePad,
            tintHex = tintHex,
            cols = 1
        )
        val rendered = rawTiles.firstOrNull()
            ?: Bitmap.createBitmap(clampedSize, clampedSize, Bitmap.Config.ARGB_8888)
        val trimmed = trimTransparentBitmap(rendered, safePad)
        if (trimmed !== rendered) rendered.recycle()
        return CutoutProcessOutput(
            resultBitmap = trimmed,
            hadExistingTransparency = true,
            detectedUniformMatte = false,
            bgRemoved = false,
            toleranceUsed = 0,
            padUsed = safePad
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun rasterizeSvgGrid(
        context: Context,
        rawSvgs: List<String>,
        tilePx: Int = 144,
        pad: CutoutPadRatios = CutoutPadRatios(0.08f, 0.08f, 0.08f, 0.08f),
        tintHex: String? = null,
        cols: Int = 6
    ): List<Bitmap?> = svgRenderMutex.withLock {
        if (rawSvgs.isEmpty()) return emptyList()

        val safeCols = cols.coerceIn(1, 8)
        val safeTile = tilePx.coerceIn(64, 1536)
        val rows = ceil(rawSvgs.size.toDouble() / safeCols.toDouble()).toInt().coerceAtLeast(1)
        val totalW = safeCols * safeTile
        val totalH = rows * safeTile

        val cellsHtml = rawSvgs.joinToString("\n") { raw ->
            val sanitized = SvgSanitizerAndPadding.sanitizeSvg(raw, tintHex)
            val expanded = if (sanitized.isNotBlank()) {
                SvgSanitizerAndPadding.expandSvgViewBox(sanitized, pad)
            } else {
                ""
            }
            "<div class=\"cell\">$expanded</div>"
        }

        val html = """
            <!DOCTYPE html>
            <html style="background:transparent;">
            <head>
              <meta charset="utf-8" />
              <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                html, body {
                  width: ${totalW}px;
                  height: ${totalH}px;
                  background: transparent !important;
                  overflow: hidden;
                }
                .grid {
                  display: grid;
                  grid-template-columns: repeat($safeCols, ${safeTile}px);
                  grid-auto-rows: ${safeTile}px;
                  width: ${totalW}px;
                  height: ${totalH}px;
                  background: transparent;
                }
                .cell {
                  width: ${safeTile}px;
                  height: ${safeTile}px;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  padding: ${max(4, (safeTile * 0.04f).toInt())}px;
                  overflow: hidden;
                  background: transparent;
                }
                .cell svg {
                  width: 100%;
                  height: 100%;
                  display: block;
                }
              </style>
            </head>
            <body><div class="grid">$cellsHtml</div></body>
            </html>
        """.trimIndent()

        var virtualDisplay: VirtualDisplay? = null
        var imageReader: ImageReader? = null
        var presentation: OffscreenSvgPresentation? = null
        var webView: WebView? = null
        var sheetBmp: Bitmap? = null

        try {
            val pageFinished = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                val base160 = create160DpiContext(context)
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val reader = ImageReader.newInstance(totalW, totalH, PixelFormat.RGBA_8888, 2)
                imageReader = reader

                val vd = dm.createVirtualDisplay(
                    "RawEditSvgBatch",
                    totalW,
                    totalH,
                    DisplayMetrics.DENSITY_DEFAULT,
                    reader.surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                )
                virtualDisplay = vd

                val hostCtx = createPresentationContext(base160, vd.display)
                val wv = WebView(hostCtx)
                webView = wv
                wv.setBackgroundColor(Color.TRANSPARENT)
                wv.settings.apply {
                    javaScriptEnabled = false
                    blockNetworkLoads = true
                    blockNetworkImage = true
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportMultipleWindows(false)
                    domStorageEnabled = false
                    databaseEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest
                    ): WebResourceResponse {
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            403,
                            "Blocked",
                            emptyMap(),
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (!pageFinished.isCompleted) pageFinished.complete(Unit)
                    }
                }

                runCatching {
                    val pres = OffscreenSvgPresentation(hostCtx, vd.display, wv, totalW, totalH)
                    presentation = pres
                    pres.show()
                }
                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(totalW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(totalH, View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, totalW, totalH)
                wv.loadDataWithBaseURL("https://raw.local/", html, "text/html", "UTF-8", null)
            }

            withTimeoutOrNull(2500) { pageFinished.await() }
            withTimeoutOrNull(900) {
                withContext(Dispatchers.Main) {
                    val painted = CompletableDeferred<Unit>()
                    webView?.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            if (!painted.isCompleted) painted.complete(Unit)
                        }
                    })
                    painted.await()
                }
            }
            delay(55)

            val fullBmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
            fullBmp.eraseColor(Color.TRANSPARENT)
            sheetBmp = fullBmp
            withContext(Dispatchers.Main) {
                webView?.draw(Canvas(fullBmp))
            }

            return withContext(Dispatchers.Default) {
                rawSvgs.indices.map { idx ->
                    val col = idx % safeCols
                    val row = idx / safeCols
                    val x = col * safeTile
                    val y = row * safeTile
                    if (x + safeTile <= fullBmp.width && y + safeTile <= fullBmp.height) {
                        Bitmap.createBitmap(fullBmp, x, y, safeTile, safeTile)
                    } else {
                        null
                    }
                }
            }
        } finally {
            sheetBmp?.recycle()
            withContext(Dispatchers.Main) {
                runCatching { webView?.stopLoading() }
                runCatching { presentation?.dismiss() }
                runCatching { webView?.destroy() }
                runCatching { virtualDisplay?.release() }
                runCatching { imageReader?.close() }
            }
        }
    }

    suspend fun processBitmapToCutout(
        source: Bitmap,
        keepOriginal: Boolean = false,
        removeBackground: Boolean = true,
        tolerance: Int = 26,
        pad: CutoutPadRatios = CutoutPadRatios(0.05f, 0.05f, 0.05f, 0.05f)
    ): CutoutProcessOutput = withContext(Dispatchers.Default) {
        val safePad = pad.clamped()
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val argbSrc = if (source.config == Bitmap.Config.ARGB_8888 && source.isMutable.not()) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val pixels = IntArray(w * h)
        argbSrc.getPixels(pixels, 0, w, 0, 0, w, h)
        if (argbSrc !== source) argbSrc.recycle()

        val hasAlpha = BackgroundKeyMath.hasMeaningfulTransparency(pixels)
        val uniformBg = if (hasAlpha) null else BackgroundKeyMath.detectUniformCornerBackground(pixels, w, h)

        if (keepOriginal) {
            val copy = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            copy.setPixels(pixels, 0, w, 0, 0, w, h)
            return@withContext CutoutProcessOutput(
                resultBitmap = copy,
                hadExistingTransparency = hasAlpha,
                detectedUniformMatte = uniformBg != null,
                bgRemoved = false,
                toleranceUsed = tolerance,
                padUsed = safePad
            )
        }

        if (hasAlpha) {
            val out = createTrimmedBitmapFromPixels(pixels, w, h, safePad)
            return@withContext CutoutProcessOutput(
                resultBitmap = out,
                hadExistingTransparency = true,
                detectedUniformMatte = false,
                bgRemoved = false,
                toleranceUsed = tolerance,
                padUsed = safePad
            )
        }

        if (!removeBackground) {
            val copy = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            copy.setPixels(pixels, 0, w, 0, 0, w, h)
            return@withContext CutoutProcessOutput(
                resultBitmap = copy,
                hadExistingTransparency = false,
                detectedUniformMatte = uniformBg != null,
                bgRemoved = false,
                toleranceUsed = tolerance,
                padUsed = safePad
            )
        }

        val seedColor = uniformBg ?: pixels[0]
        val (innerSq, outerSq) = BackgroundKeyMath.toleranceToDistSq(tolerance)
        val visited = BooleanArray(w * h)
        val erased = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var qHead = 0
        var qTail = 0

        fun trySeed(idx: Int) {
            if (visited[idx]) return
            val c = pixels[idx]
            val a = (c ushr 24) and 0xFF
            if (a < 20) {
                visited[idx] = true
                erased[idx] = true
                queue[qTail++] = idx
                return
            }
            if (BackgroundKeyMath.colorDistanceSq(c, seedColor) <= innerSq) {
                visited[idx] = true
                erased[idx] = true
                queue[qTail++] = idx
            }
        }

        for (x in 0 until w) {
            trySeed(x)
            trySeed((h - 1) * w + x)
        }
        for (y in 0 until h) {
            trySeed(y * w)
            trySeed(y * w + (w - 1))
        }

        while (qHead < qTail) {
            val cur = queue[qHead++]
            val x = cur % w
            val y = cur / w

            if (x > 0) trySeed(cur - 1)
            if (x + 1 < w) trySeed(cur + 1)
            if (y > 0) trySeed(cur - w)
            if (y + 1 < h) trySeed(cur + w)
        }

        var erasedCount = 0
        for (i in pixels.indices) {
            if (erased[i]) {
                pixels[i] = pixels[i] and 0x00FFFFFF
                erasedCount++
            }
        }

        // Feather immediate 1px boundary around erased pixels so contours look anti-aliased
        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val idx = rowOff + x
                if (erased[idx]) continue
                val touchesErased =
                    (x > 0 && erased[idx - 1]) ||
                    (x + 1 < w && erased[idx + 1]) ||
                    (y > 0 && erased[idx - w]) ||
                    (y + 1 < h && erased[idx + w])
                if (touchesErased) {
                    val c = pixels[idx]
                    val dist = BackgroundKeyMath.colorDistanceSq(c, seedColor)
                    if (dist < outerSq) {
                        val origAlpha = (c ushr 24) and 0xFF
                        val softA = min(origAlpha, BackgroundKeyMath.smoothAlphaFromDistSq(dist, innerSq, outerSq))
                        pixels[idx] = (softA shl 24) or (c and 0x00FFFFFF)
                    }
                }
            }
        }

        // Guard against over-erasing >= 97% of the entire image on flat shapes
        if (erasedCount >= (w * h * 0.985).toInt()) {
            val untouched = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            argbSrcCopy(source, untouched)
            return@withContext CutoutProcessOutput(
                resultBitmap = untouched,
                hadExistingTransparency = false,
                detectedUniformMatte = uniformBg != null,
                bgRemoved = false,
                toleranceUsed = tolerance,
                padUsed = safePad
            )
        }

        val cropped = createTrimmedBitmapFromPixels(pixels, w, h, safePad)
        CutoutProcessOutput(
            resultBitmap = cropped,
            hadExistingTransparency = false,
            detectedUniformMatte = uniformBg != null,
            bgRemoved = erasedCount > 0,
            toleranceUsed = tolerance,
            padUsed = safePad
        )
    }

    private fun argbSrcCopy(source: Bitmap, dest: Bitmap) {
        val c = Canvas(dest)
        c.drawBitmap(source, 0f, 0f, null)
    }

    fun trimTransparentBitmap(source: Bitmap, pad: CutoutPadRatios): Bitmap {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        return createTrimmedBitmapFromPixels(pixels, w, h, pad)
    }

    private fun createTrimmedBitmapFromPixels(
        pixels: IntArray,
        w: Int,
        h: Int,
        pad: CutoutPadRatios
    ): Bitmap {
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val a = (pixels[rowOff + x] ushr 24) and 0xFF
                if (a > 10) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            full.setPixels(pixels, 0, w, 0, 0, w, h)
            return full
        }

        val contentW = (maxX - minX + 1).coerceAtLeast(1)
        val contentH = (maxY - minY + 1).coerceAtLeast(1)
        val safePad = pad.clamped()
        val extraL = ceil(contentW * safePad.left).toInt()
        val extraT = ceil(contentH * safePad.top).toInt()
        val extraR = ceil(contentW * safePad.right).toInt()
        val extraB = ceil(contentH * safePad.bottom).toInt()

        val outW = (contentW + extraL + extraR).coerceIn(1, 2048)
        val outH = (contentH + extraT + extraB).coerceIn(1, 2048)

        val outBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        outBmp.eraseColor(Color.TRANSPARENT)

        val copyPixels = IntArray(contentW * contentH)
        for (row in 0 until contentH) {
            val srcStart = (minY + row) * w + minX
            System.arraycopy(pixels, srcStart, copyPixels, row * contentW, contentW)
        }
        val destX = extraL.coerceAtMost(max(0, outW - contentW))
        val destY = extraT.coerceAtMost(max(0, outH - contentH))
        outBmp.setPixels(copyPixels, 0, contentW, destX, destY, min(contentW, outW - destX), min(contentH, outH - destY))
        return outBmp
    }

    suspend fun buildComparisonSheetFile(
        cacheDir: File,
        originalBmp: Bitmap?,
        cutoutBmp: Bitmap
    ): File = withContext(Dispatchers.IO) {
        val sheetW = 1024
        val sheetH = 520
        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.parseColor("#0D0D0F"))

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7ECE6")
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CAA07D")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val leftRect = RectF(24f, 64f, 496f, 492f)
        val rightRect = RectF(528f, 64f, 1000f, 492f)

        canvas.drawText("LEFT: SOURCE ORIGINAL", 26f, 40f, headerPaint)
        canvas.drawText("RIGHT: PNG CUTOUT (CHECKERBOARD)", 530f, 40f, headerPaint)

        // Left matte
        val solidBg = Paint().apply { color = Color.parseColor("#202024") }
        canvas.drawRect(leftRect, solidBg)
        canvas.drawRect(leftRect, borderPaint)

        // Right high-contrast checkerboard so accidental erasures are obvious to Puter Vision AI
        val lightCell = Paint().apply { color = Color.parseColor("#47474E") }
        val darkCell = Paint().apply { color = Color.parseColor("#232328") }
        val cell = 18f
        var rowIdx = 0
        var y = rightRect.top
        while (y < rightRect.bottom) {
            var colIdx = 0
            var x = rightRect.left
            while (x < rightRect.right) {
                val p = if ((rowIdx + colIdx) % 2 == 0) lightCell else darkCell
                canvas.drawRect(x, y, min(rightRect.right, x + cell), min(rightRect.bottom, y + cell), p)
                x += cell
                colIdx++
            }
            y += cell
            rowIdx++
        }
        canvas.drawRect(rightRect, borderPaint)

        val srcToDraw = originalBmp ?: cutoutBmp
        drawFittedBitmap(canvas, srcToDraw, leftRect, imgPaint)
        val cutoutDst = drawFittedBitmap(canvas, cutoutBmp, rightRect, imgPaint)
        // Subtle dashed/thin guide around final cutout bounds to spotlight edge clipping
        val cropGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#88CAA07D")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRect(cutoutDst, cropGuidePaint)

        val outFile = File(cacheDir, "cutout_audit_compare.jpg")
        FileOutputStream(outFile).use { fos ->
            sheet.compress(Bitmap.CompressFormat.JPEG, 88, fos)
        }
        sheet.recycle()
        outFile
    }

    private fun drawFittedBitmap(canvas: Canvas, bmp: Bitmap, box: RectF, paint: Paint): RectF {
        val innerPad = 14f
        val availW = (box.width() - innerPad * 2f).coerceAtLeast(16f)
        val availH = (box.height() - innerPad * 2f).coerceAtLeast(16f)
        val scale = min(availW / bmp.width.coerceAtLeast(1), availH / bmp.height.coerceAtLeast(1))
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val dx = box.left + (box.width() - drawW) * 0.5f
        val dy = box.top + (box.height() - drawH) * 0.5f
        val dst = RectF(dx, dy, dx + drawW, dy + drawH)
        val src = Rect(0, 0, bmp.width, bmp.height)
        canvas.drawBitmap(bmp, src, dst, paint)
        return dst
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
}
