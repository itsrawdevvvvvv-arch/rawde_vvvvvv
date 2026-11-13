package com.rawedit.app.domain

import android.app.Presentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import com.rawedit.app.data.CacheManager
import com.rawedit.app.render.LocalAssetServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SheetSamplingMode(val label: String, val stepSec: Double, val maxFrames: Int) {
    STANDARD_05S("Every 0.5s", 0.5, 220),
    DENSE_025S("Dense 0.25s", 0.25, 280),
    SCENE_BEATS("Scene beats", 1.0, 120)
}

@Serializable
data class SceneSpan(
    val index: Int = 1,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val bg: String = ""
)

@Serializable
private data class FrameDiagnostics(
    val autoFitCount: Int = 0,
    val notes: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class KeyframeSample(
    val index: Int,
    val timeSec: Double,
    val sceneLabel: String,
    val thumbPath: String,
    val autoFitCount: Int,
    val autoFitNotes: List<String>,
    val warnings: List<String>
)

data class KeyframeSheetResult(
    val samples: List<KeyframeSample>,
    val durationSec: Double,
    val totalAutoFits: Int,
    val warningCount: Int,
    val elapsedMs: Long
)

data class ReviewCollageFrame(
    val bitmap: Bitmap,
    val t: Double,
    val badge: String,
    val rects: List<RectF>
)

data class ReviewCollageBundle(
    val jpegBytes: ByteArray,
    val pickedSamples: List<KeyframeSample>,
    val flagLines: List<String>,
    val flagTokens: List<String>
)

data class DomFlagBundle(
    val flagLines: List<String>,
    val flagTokens: List<String>,
    val rectsBySampleIndex: Map<Int, List<RectF>>
)

object KeyframeSheetGenerator {

    private const val STAGE_W = 1920
    private const val STAGE_H = 1080
    private const val THUMB_W = 480
    private const val THUMB_H = 270
    private const val SHEET_W = 1680
    private const val SHEET_H = 2160
    private const val COLS = 3
    private const val ROWS = 6
    private const val FRAMES_PER_PAGE = COLS * ROWS

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private class OffscreenSheetPresentation(
        outerContext: Context,
        display: Display,
        val webView: WebView
    ) : Presentation(outerContext, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val root = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(STAGE_W, STAGE_H)
            }
            webView.layoutParams = FrameLayout.LayoutParams(STAGE_W, STAGE_H)
            root.addView(webView)
            setContentView(root)
        }
    }

    fun buildSampleTimestamps(
        durationSec: Double,
        scenes: List<SceneSpan>,
        mode: SheetSamplingMode
    ): List<Double> {
        val dur = durationSec.coerceAtLeast(0.5)
        val raw = mutableListOf<Double>()

        when (mode) {
            SheetSamplingMode.SCENE_BEATS -> {
                if (scenes.isNotEmpty()) {
                    for (sc in scenes) {
                        val s = sc.start.coerceIn(0.0, dur)
                        val e = sc.end.coerceIn(s, dur)
                        raw.add((s + 0.14).coerceAtMost(e))
                        raw.add((s + e) * 0.5)
                        if (e - s > 0.6) {
                            raw.add((e - 0.14).coerceAtLeast(s))
                        }
                    }
                } else {
                    var t = 0.0
                    val step = maxOf(1.0, dur / mode.maxFrames.toDouble())
                    while (t < dur) {
                        raw.add(t)
                        t += step
                    }
                }
            }
            SheetSamplingMode.STANDARD_05S, SheetSamplingMode.DENSE_025S -> {
                val baseStep = mode.stepSec
                val adaptiveStep = maxOf(baseStep, dur / mode.maxFrames.toDouble())
                var t = 0.0
                while (t < dur - 0.05) {
                    raw.add(t)
                    t += adaptiveStep
                }
                for (sc in scenes) {
                    val s = sc.start.coerceIn(0.0, dur)
                    val e = sc.end.coerceIn(s, dur)
                    raw.add((s + 0.18).coerceAtMost(e))
                    raw.add((s + e) * 0.5)
                }
            }
        }

        raw.add(0.0)
        raw.add(maxOf(0.0, dur - 0.04))

        val minGap = if (mode == SheetSamplingMode.DENSE_025S) 0.09 else 0.14
        val sorted = raw.map { (it * 100.0).roundToInt() / 100.0 }.sorted()
        val deduped = mutableListOf<Double>()
        for (t in sorted) {
            if (deduped.isEmpty() || abs(t - deduped.last()) >= minGap || abs(t - maxOf(0.0, dur - 0.04)) < 0.02) {
                if (deduped.isEmpty() || abs(t - deduped.last()) > 0.015) {
                    deduped.add(t)
                }
            }
        }
        return deduped.take(mode.maxFrames + 12)
    }

    fun resolveSceneLabel(tSec: Double, scenes: List<SceneSpan>): String {
        val match = scenes.firstOrNull { sc ->
            if (sc.index == scenes.size) {
                tSec >= sc.start - 0.01 && tSec <= sc.end + 0.05
            } else {
                tSec >= sc.start - 0.01 && tSec < sc.end
            }
        } ?: return ""
        val tone = if (match.bg.isNotBlank()) " ${match.bg.uppercase(Locale.US)}" else ""
        return "S${match.index}$tone"
    }

    fun buildFixPrompt(
        samples: List<KeyframeSample>,
        title: String,
        durationSec: Double,
        validationError: String = "",
        htmlCode: String = ""
    ): String {
        val cleanTitle = title.trim().ifBlank { "Untitled Reel" }
        val durStr = String.format(Locale.US, "%.2f", durationSec)
        val warned = samples.filter { it.warnings.isNotEmpty() }
        val autoFitted = samples.filter { it.autoFitCount > 0 && it.warnings.isEmpty() }
        val cleanCount = (samples.size - warned.size - autoFitted.size).coerceAtLeast(0)
        val totalPages = ((samples.size + FRAMES_PER_PAGE - 1) / FRAMES_PER_PAGE).coerceAtLeast(1)

        val errLine = validationError.trim().ifBlank { "None reported" }
        val domWarnings = buildString {
            if (warned.isNotEmpty()) {
                warned.take(24).forEach { s ->
                    val ts = String.format(Locale.US, "%.2fs", s.timeSec)
                    appendLine("- WARN Frame #${s.index + 1} (t=$ts ${s.sceneLabel}): ${s.warnings.joinToString("; ")}")
                }
            }
            if (autoFitted.isNotEmpty()) {
                autoFitted.take(20).forEach { s ->
                    val ts = String.format(Locale.US, "%.2fs", s.timeSec)
                    appendLine("- FIT Frame #${s.index + 1} (t=$ts ${s.sceneLabel}): ${s.autoFitNotes.joinToString("; ")}")
                }
            }
            if (warned.isEmpty() && autoFitted.isEmpty()) {
                appendLine("None flagged by DOM check (inspect contact sheet visually for overlap, contrast, or pacing glitches).")
            }
        }.trim()

        val framesSummary = "${samples.size} frames across $totalPages page(s) ($cleanCount clean, ${warned.size} WARN, ${autoFitted.size} FIT)"

        val htmlSection = when {
            htmlCode.isBlank() -> "[Attach your current reel .html file with this prompt]"
            htmlCode.length <= 120_000 -> htmlCode
            else -> "[Current HTML is ${htmlCode.length} chars — attach your reel .html file alongside this prompt so nothing is truncated]"
        }

        return """
Fix the attached raw_edit reel. Spend tokens on fixing, not talking: no greeting, no progress notes, no recap. Do not rewrite code that works.
Go through the visuals in detail and give the final downloadable fixed html , fixing all the errors. There are more other errors than the listed find all and fix.

TITLE: $cleanTitle   DURATION: $durStr s (1920x1080, 30 fps)
VALIDATION / RUNTIME ERROR: $errLine
DOM WARNINGS (WARN = edge clip or card overlap; FIT = auto-shrunk, wrapped or lifted text):
$domWarnings
FRAMES SUMMARY: $framesSummary
Attached: ALL contact sheet pages (3x6 grids, 18 frames per page). Inspect every page, not just the first two.

## STEP 1: SILENT AUDIT
Find the root cause for each of these before editing anything:
1. Every WARN/FIT frame. Measure against the 1920x1080 grid with real numbers (element x, y, w, h and font-size from the HTML). Do not guess coordinates.
2. Every JS/validation error. Typical causes: unsupported element type in `build` (engine.js only supports ghost, kicker, sub, pill, headline, chips, card, row, phone, buttons, comment, round), bad string escaping, forbidden API or URL.
3. Blank or black frames, including the final frame at t = duration (scenes must be contiguous and the last scene inclusive).
4. Collisions: brackets extend 34 px outside cards, nothing may sit below y 930 (progress bar at 972..1080), and low-contrast text or icons.
5. Determinism: seek(t) must not mutate stage.innerHTML across repeated calls (no append, no counters, no Math.random, no Date.now).
6. Forbidden items still present: CSS transition/@keyframes/animation, rAF, setTimeout/setInterval, video.play(), external URLs, @font-face copied from tokens.css, non-bundled fonts.
7. Audio/SRT desync and missing assets/... paths.
Fix root causes (for example one bad class used by many scenes), not symptoms.

## STEP 2: OUTPUT (choose one)
A) PATCH MODE (default, whenever most scenes work). Output a one-to-eight-line list of root causes, then patches in exactly this form:
<<<SEARCH
exact text copied from the HTML, unique in the file
===REPLACE
new text
>>>
Each SEARCH must match exactly once. Keep patches minimal. Never patch by re-pasting large blocks.
B) FULL MODE, only if the fix needs structural changes (for example a missing renderer for a scene type). Output the complete <!DOCTYPE html> document with every scene from 0 to duration intact, nothing truncated.

Before sending, verify silently: every patch target exists, the last scene reaches duration, no forbidden API or URL was introduced, and no working scene was changed.

CURRENT HTML:
$htmlSection
        """.trimIndent()
    }

    suspend fun selectFlaggedReviewFrames(
        samples: List<KeyframeSample>,
        outDir: File,
        maxFrames: Int = GroqCodec.MAX_IMAGES_PER_REQUEST
    ): List<Pair<KeyframeSample, File>> = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext emptyList()
        val cap = maxFrames.coerceIn(1, GroqCodec.MAX_IMAGES_PER_REQUEST)
        val warned = samples.filter { it.warnings.isNotEmpty() }
        val fitted = samples.filter { it.autoFitCount > 0 && it.warnings.isEmpty() }
        val flagged = (warned + fitted).take(cap)

        val chosen = if (flagged.isNotEmpty()) {
            flagged
        } else {
            val pick = mutableListOf<KeyframeSample>()
            if (samples.size <= cap) {
                pick.addAll(samples)
            } else {
                for (i in 0 until cap) {
                    val idx = ((i.toDouble() / (cap - 1).coerceAtLeast(1)) * samples.lastIndex).roundToInt()
                    val candidate = samples[idx.coerceIn(0, samples.lastIndex)]
                    if (pick.none { it.index == candidate.index }) {
                        pick.add(candidate)
                    }
                }
            }
            pick
        }

        outDir.mkdirs()
        val frameW = 640
        val headerH = 32
        val stageH = 360
        val totalH = headerH + stageH
        val bgPaint = Paint().apply { color = Color.parseColor("#0D0D0F") }
        val hdrBgPaint = Paint().apply { color = Color.parseColor("#17171A") }
        val hdrTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7ECE6")
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF8F80")
            textSize = 17f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val fitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CAA07D")
            textSize = 17f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        val out = mutableListOf<Pair<KeyframeSample, File>>()
        chosen.forEachIndexed { idx, sample ->
            val src = runCatching { BitmapFactory.decodeFile(sample.thumbPath) }.getOrNull() ?: return@forEachIndexed
            val outBmp = Bitmap.createBitmap(frameW, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outBmp)
            canvas.drawRect(0f, 0f, frameW.toFloat(), totalH.toFloat(), bgPaint)
            canvas.drawRect(0f, 0f, frameW.toFloat(), headerH.toFloat(), hdrBgPaint)

            val tsStr = String.format(Locale.US, "t=%.2fs", sample.timeSec)
            val sceneStr = sample.sceneLabel.ifBlank { "SCENE" }
            canvas.drawText("#${sample.index + 1} $tsStr $sceneStr", 12f, 22f, hdrTextPaint)

            val (tag, tagPaint) = when {
                sample.warnings.isNotEmpty() -> "WARN" to warnPaint
                sample.autoFitCount > 0 -> "FIT" to fitPaint
                else -> "SAMPLE" to hdrTextPaint
            }
            val tw = tagPaint.measureText(tag)
            canvas.drawText(tag, frameW - tw - 12f, 22f, tagPaint)

            canvas.drawBitmap(src, null, RectF(0f, headerH.toFloat(), frameW.toFloat(), totalH.toFloat()), imgPaint)
            src.recycle()

            val file = File(outDir, String.format(Locale.US, "groq_frame_%02d.jpg", idx + 1))
            FileOutputStream(file).use { fos ->
                outBmp.compress(Bitmap.CompressFormat.JPEG, 86, fos)
            }
            outBmp.recycle()
            out.add(sample to file)
        }
        out
    }

    private val PROBE_JS = """
        (function() {
          try {
            var stage = document.getElementById('stage') || document.body;
            var sr = stage.getBoundingClientRect();
            var out = [];
            function eff(el) {
              var o = 1;
              for (var n = el; n && n !== document.documentElement; n = n.parentElement) {
                var cs = getComputedStyle(n);
                if (cs.display === 'none' || cs.visibility === 'hidden') return 0;
                o *= parseFloat(cs.opacity);
              }
              return o;
            }
            function rectOf(el) {
              var r = el.getBoundingClientRect();
              return { x: Math.round(r.left - sr.left), y: Math.round(r.top - sr.top), w: Math.round(r.width), h: Math.round(r.height) };
            }
            function desc(el, r) {
              var rawCls = (el.className && typeof el.className === 'string') ? el.className.trim() : '';
              var c = rawCls ? ('.' + rawCls.split(/\s+/).slice(0, 2).join('.')) : '';
              return (el.tagName || 'div').toLowerCase() + (el.id ? '#' + el.id : '') + c + '@[' + r.x + ',' + r.y + ',' + r.w + 'x' + r.h + ']';
            }
            function txt(el) { return (el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 22); }
            function parseRgb(s) {
              var m = /rgba?\(([^)]+)\)/.exec(s || '');
              if (!m) return null;
              var p = m[1].split(',').map(function(v) { return parseFloat(v); });
              return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
            }
            function lum(c) {
              function f(v) { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
              return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b);
            }
            var texts = [];
            var all = stage.querySelectorAll('*');
            for (var i = 0; i < all.length && texts.length < 80; i++) {
              var el = all[i];
              var tag = (el.tagName || '').toUpperCase();
              if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'SVG' || tag === 'PATH') continue;
              var cls = (el.className && typeof el.className === 'string') ? el.className.toLowerCase() : '';
              if (cls.indexOf('ghost') !== -1 || cls.indexOf('dust') !== -1 || cls.indexOf('progress') !== -1) continue;
              if (tag === 'IMG') {
                if (eff(el) > 0.2 && el.complete && el.naturalWidth === 0) {
                  var ir = rectOf(el);
                  out.push('Broken image ' + (el.getAttribute('src') || '').slice(0, 30) + ' (' + desc(el, ir) + ')');
                }
                continue;
              }
              var hasOwnText = false;
              for (var k = 0; k < el.childNodes.length; k++) {
                if (el.childNodes[k].nodeType === 3 && el.childNodes[k].nodeValue.trim().length > 0) { hasOwnText = true; break; }
              }
              if (!hasOwnText) continue;
              var op = eff(el);
              if (op < 0.35) continue;
              var r = rectOf(el);
              if (r.w < 8 || r.h < 8) continue;
              texts.push({ el: el, r: r, op: op });
              var cs = getComputedStyle(el);
              if ((el.scrollWidth > el.clientWidth + 6 && cs.overflowX !== 'visible') ||
                  (el.scrollHeight > el.clientHeight + 6 && cs.overflowY !== 'visible' && el.clientHeight > 0)) {
                out.push('Text clipped "' + txt(el) + '" (' + desc(el, r) + ')');
              }
              var fg = parseRgb(cs.color);
              if (fg && fg.a > 0.5) {
                var bg = null;
                for (var n = el; n; n = n.parentElement) {
                  var ncs = getComputedStyle(n);
                  if (ncs.backgroundImage && ncs.backgroundImage !== 'none') { bg = null; break; }
                  var b = parseRgb(ncs.backgroundColor);
                  if (b && b.a > 0.9) { bg = b; break; }
                }
                if (bg) {
                  var l1 = lum(fg), l2 = lum(bg);
                  var ratio = (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
                  if (ratio < 3.0) {
                    out.push('Low contrast ' + ratio.toFixed(1) + ':1 "' + txt(el) + '" fg ' + cs.color + ' bg ' + 'rgb(' + bg.r + ',' + bg.g + ',' + bg.b + ') (' + desc(el, r) + ')');
                  }
                }
              }
            }
            for (var a = 0; a < texts.length; a++) {
              for (var b2 = a + 1; b2 < texts.length; b2++) {
                var A = texts[a], B = texts[b2];
                if (A.el.contains(B.el) || B.el.contains(A.el)) continue;
                var ix = Math.min(A.r.x + A.r.w, B.r.x + B.r.w) - Math.max(A.r.x, B.r.x);
                var iy = Math.min(A.r.y + A.r.h, B.r.y + B.r.h) - Math.max(A.r.y, B.r.y);
                if (ix <= 4 || iy <= 4) continue;
                var minArea = Math.min(A.r.w * A.r.h, B.r.w * B.r.h);
                if ((ix * iy) / minArea > 0.25) {
                  out.push('Text overlap "' + txt(A.el) + '" (' + desc(A.el, A.r) + ') with "' + txt(B.el) + '" (' + desc(B.el, B.r).split('@')[0] + ')');
                }
              }
            }
            return JSON.stringify(out.slice(0, 6));
          } catch (e) { return '[]'; }
        })()
    """.trimIndent()

    private val FLAG_DESCRIBE_REGEX = Regex("""([A-Za-z0-9_#.\-]+)@\[(-?\d+),(-?\d+),(\d+)x(\d+)\]""")
    private val QUOTED_SNIPPET_REGEX = Regex("\"([^\"]{3,24})\"")

    private fun parseDomFlagDetails(raw: String, isWarning: Boolean): Triple<String, RectF?, String> {
        val m = FLAG_DESCRIBE_REGEX.find(raw)
        val selector = m?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val rect = if (m != null) {
            val x = m.groupValues[2].toFloatOrNull() ?: 0f
            val y = m.groupValues[3].toFloatOrNull() ?: 0f
            val w = m.groupValues[4].toFloatOrNull() ?: 0f
            val h = m.groupValues[5].toFloatOrNull() ?: 0f
            if (w > 0f && h > 0f) RectF(x, y, x + w, y + h) else null
        } else null

        val lower = raw.lowercase(Locale.US)
        val issueTag = when {
            lower.contains("edge clip") -> "edge-clip"
            lower.contains("low contrast") -> "contrast"
            lower.contains("broken image") -> "broken-img"
            lower.contains("text clipped") -> "text-clip"
            lower.contains("overlap") -> "overlap"
            lower.contains("resized") -> "auto-resize"
            lower.contains("wrapped") -> "auto-wrap"
            lower.contains("progress bar") || lower.contains("lifted") || lower.contains("raised") -> "above-bar"
            isWarning -> "warn"
            else -> "auto-fit"
        }
        return Triple(selector, rect, issueTag)
    }

    fun pickCollageFrames(
        samples: List<KeyframeSample>,
        durationSec: Double
    ): List<KeyframeSample> {
        if (samples.isEmpty()) return emptyList()
        val picked = mutableListOf<KeyframeSample>()
        fun tooClose(candidate: KeyframeSample): Boolean =
            picked.any { abs(it.timeSec - candidate.timeSec) < 1.0 }

        val warningsFirst = samples
            .filter { it.warnings.isNotEmpty() }
            .sortedWith(compareByDescending<KeyframeSample> { it.warnings.size }.thenByDescending { it.autoFitCount }.thenBy { it.timeSec })
        for (s in warningsFirst) {
            if (picked.size >= 4) break
            if (!tooClose(s)) picked.add(s)
        }

        val fitsNext = samples
            .filter { it.warnings.isEmpty() && it.autoFitCount > 0 }
            .sortedWith(compareByDescending<KeyframeSample> { it.autoFitCount }.thenBy { it.timeSec })
        for (s in fitsNext) {
            if (picked.size >= 4) break
            if (!tooClose(s)) picked.add(s)
        }

        if (picked.size < 4) {
            val anchorTargets = mutableListOf<KeyframeSample>()
            samples.firstOrNull()?.let { anchorTargets.add(it) }
            val tailTarget = (durationSec - 0.04).coerceAtLeast(0.0)
            samples.minByOrNull { abs(it.timeSec - tailTarget) }?.let { anchorTargets.add(it) }
            for (a in anchorTargets) {
                if (picked.size >= 4) break
                if (picked.none { it.index == a.index } && !tooClose(a)) {
                    picked.add(a)
                }
            }
        }

        if (picked.size < 4) {
            val slots = listOf(0.25, 0.50, 0.75, 0.12, 0.88)
            for (frac in slots) {
                if (picked.size >= 4) break
                val idx = (frac * samples.lastIndex).roundToInt().coerceIn(0, samples.lastIndex)
                val s = samples[idx]
                if (picked.none { it.index == s.index } && !tooClose(s)) {
                    picked.add(s)
                }
            }
        }

        if (picked.size < 4) {
            for (s in samples) {
                if (picked.size >= 4) break
                if (picked.none { it.index == s.index }) picked.add(s)
            }
        }

        return picked.take(4).sortedBy { it.timeSec }
    }

    fun extractDomFlagBundle(
        samples: List<KeyframeSample>,
        collageSamples: List<KeyframeSample> = emptyList()
    ): DomFlagBundle {
        val rectsMap = LinkedHashMap<Int, List<RectF>>()
        val lines = mutableListOf<String>()
        val tokens = LinkedHashSet<String>()

        fun addTokensFrom(raw: String, selector: String, timeSec: Double) {
            if (selector.isNotBlank()) {
                val idIdx = selector.indexOf('#')
                val dotIdx = selector.indexOf('.')
                if (idIdx >= 0) {
                    val endIdx = if (dotIdx > idIdx) dotIdx else selector.length
                    val idTok = selector.substring(idIdx, endIdx).trim()
                    if (idTok.length >= 2) tokens.add(idTok.removePrefix("#"))
                }
                if (dotIdx >= 0) {
                    selector.substring(dotIdx).split('.').filter { it.length >= 3 }.forEach { tokens.add(it) }
                }
            }
            QUOTED_SNIPPET_REGEX.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 3 }?.let { snippet ->
                tokens.add(snippet)
            }
            val tInt = timeSec.roundToInt()
            tokens.add("start: $tInt")
            tokens.add("start:$tInt")
        }

        val collageIdxBySampleIdx = collageSamples.mapIndexed { pos, s -> s.index to (pos + 1) }.toMap()
        val orderedFlagged = (collageSamples.filter { it.warnings.isNotEmpty() || it.autoFitCount > 0 } +
            samples.filter { (it.warnings.isNotEmpty() || it.autoFitCount > 0) && it.index !in collageIdxBySampleIdx })
            .distinctBy { it.index }

        for (s in orderedFlagged) {
            val sampleRects = mutableListOf<RectF>()
            val framePos = collageIdxBySampleIdx[s.index] ?: (s.index + 1)
            val entries = s.warnings.map { it to true } + s.autoFitNotes.map { it to false }
            for ((msg, isWarn) in entries) {
                val (sel, rect, issueTag) = parseDomFlagDetails(msg, isWarn)
                if (rect != null) sampleRects.add(rect)
                addTokensFrom(msg, sel, s.timeSec)
                val rectStr = if (rect != null) {
                    "${rect.left.roundToInt()},${rect.top.roundToInt()},${rect.width().roundToInt()}x${rect.height().roundToInt()}"
                } else {
                    "0,0,0x0"
                }
                val ts = String.format(Locale.US, "%.2f", s.timeSec)
                val line = "$framePos|$ts|${sel.ifBlank { "stage" }}|$rectStr|$issueTag"
                if (line !in lines) lines.add(line)
            }
            if (sampleRects.isNotEmpty()) {
                rectsMap[s.index] = sampleRects
            }
        }

        return DomFlagBundle(
            flagLines = lines.take(24),
            flagTokens = tokens.take(28),
            rectsBySampleIndex = rectsMap
        )
    }

    fun buildReviewCollage(frames: List<ReviewCollageFrame>): ByteArray {
        val totalW = 1280
        val totalH = 760
        val cw = 640
        val ch = 380
        val fh = 360
        val out = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)

        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val fill = Paint()
        val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        val red = Paint().apply {
            color = 0xFFE5484D.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val cyan = Paint().apply {
            color = 0xFF00E5FF.toInt()
            strokeWidth = 1.5f
            pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        }

        frames.take(4).forEachIndexed { i, f ->
            val ox = (i % 2) * cw
            val oy = (i / 2) * ch
            val srcH = minOf(f.bitmap.height, (f.bitmap.width * 9) / 16).coerceAtLeast(1)
            c.drawBitmap(
                f.bitmap,
                Rect(0, 0, f.bitmap.width, srcH),
                Rect(ox, oy, ox + cw, oy + fh),
                imgPaint
            )
            c.drawLine(ox.toFloat(), oy + 310f, (ox + cw).toFloat(), oy + 310f, cyan)
            f.rects.forEach { r ->
                c.drawRect(ox + r.left / 3f, oy + r.top / 3f, ox + r.right / 3f, oy + r.bottom / 3f, red)
            }

            fill.color = 0xFF111111.toInt()
            c.drawRect(ox.toFloat(), (oy + fh).toFloat(), (ox + cw).toFloat(), (oy + ch).toFloat(), fill)
            val labelText = "#${i + 1}  t=${String.format(Locale.US, "%.2f", f.t)}s"
            c.drawText(labelText, ox + 8f, oy + fh + 15f, txt)

            fill.color = when (f.badge) {
                "WARN" -> 0xFFE5484D.toInt()
                "FIT" -> 0xFFF5A524.toInt()
                else -> 0xFF2E9E5B.toInt()
            }
            c.drawRect(ox + cw - 70f, (oy + fh + 2).toFloat(), ox + cw - 6f, (oy + ch - 2).toFloat(), fill)
            c.drawText(f.badge, ox + cw - 62f, oy + fh + 15f, txt)
        }

        fill.color = Color.WHITE
        c.drawRect(cw - 1f, 0f, cw + 1f, totalH.toFloat(), fill)
        c.drawRect(0f, ch - 1f, totalW.toFloat(), ch + 1f, fill)

        val baos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        out.recycle()
        return baos.toByteArray()
    }

    suspend fun buildReviewCollageBundle(
        samples: List<KeyframeSample>,
        durationSec: Double
    ): ReviewCollageBundle = withContext(Dispatchers.IO) {
        val picked = pickCollageFrames(samples, durationSec)
        val domBundle = extractDomFlagBundle(samples, picked)
        val collageFrames = mutableListOf<ReviewCollageFrame>()
        val bitmapsToRecycle = mutableListOf<Bitmap>()
        try {
            for (s in picked) {
                val bmp = runCatching { BitmapFactory.decodeFile(s.thumbPath) }.getOrNull() ?: continue
                bitmapsToRecycle.add(bmp)
                val badge = when {
                    s.warnings.isNotEmpty() -> "WARN"
                    s.autoFitCount > 0 -> "FIT"
                    else -> "OK"
                }
                val rects = domBundle.rectsBySampleIndex[s.index].orEmpty()
                collageFrames.add(ReviewCollageFrame(bitmap = bmp, t = s.timeSec, badge = badge, rects = rects))
            }
            val jpegBytes = if (collageFrames.isNotEmpty()) buildReviewCollage(collageFrames) else ByteArray(0)
            ReviewCollageBundle(
                jpegBytes = jpegBytes,
                pickedSamples = picked,
                flagLines = domBundle.flagLines,
                flagTokens = domBundle.flagTokens
            )
        } finally {
            bitmapsToRecycle.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    suspend fun captureKeyframes(
        context: Context,
        cacheManager: CacheManager,
        projectId: Long,
        rawHtml: String,
        durationSec: Double,
        mode: SheetSamplingMode,
        onProgress: (done: Int, total: Int, timeSec: Double, latestThumbPath: String?) -> Unit,
        cancelled: () -> Boolean
    ): KeyframeSheetResult {
        val startedMs = System.currentTimeMillis()
        val sheetDir = cacheManager.resetSheetTempDir(projectId)

        val captureBmp = Bitmap.createBitmap(STAGE_W, STAGE_H, Bitmap.Config.ARGB_8888)
        val captureCanvas = Canvas(captureBmp)
        val thumbBmp = Bitmap.createBitmap(THUMB_W, THUMB_H, Bitmap.Config.ARGB_8888)
        val thumbCanvas = Canvas(thumbBmp)
        val scaleMatrix = Matrix().apply { setScale(THUMB_W.toFloat() / STAGE_W, THUMB_H.toFloat() / STAGE_H) }
        val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        var virtualDisplay: VirtualDisplay? = null
        var imageReader: ImageReader? = null
        var presentation: OffscreenSheetPresentation? = null
        var webView: WebView? = null

        try {
            var servedHtml = ""
            val assetServer = LocalAssetServer(context, cacheManager) { servedHtml }
            servedHtml = assetServer.buildInjectedHtml(rawHtml)

            val pageLoaded = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                val baseDisplayContext = create160DpiContext(context)
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val reader = ImageReader.newInstance(STAGE_W, STAGE_H, PixelFormat.RGBA_8888, 2)
                imageReader = reader

                val vd = dm.createVirtualDisplay(
                    "RawEditSheetStage",
                    STAGE_W,
                    STAGE_H,
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
                    val pres = OffscreenSheetPresentation(hostContext, vd.display, wv)
                    presentation = pres
                    pres.show()
                }
                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(STAGE_W, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(STAGE_H, View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, STAGE_W, STAGE_H)
                wv.resumeTimers()
                wv.onResume()
                wv.loadUrl(LocalAssetServer.ENTRY_URL)
            }

            pageLoaded.await()
            val activeWeb = requireNotNull(webView) { "WebView initialization failed" }
            waitForFontsAndRaw(activeWeb, cancelled)

            val scenes = extractScenes(activeWeb)
            val timestamps = buildSampleTimestamps(durationSec, scenes, mode)
            val samples = ArrayList<KeyframeSample>(timestamps.size)
            var totalAutoFits = 0
            var warningFrames = 0

            for ((idx, tSec) in timestamps.withIndex()) {
                if (cancelled()) throw IllegalStateException("Cancelled")
                evalAndWaitTwoFrames(activeWeb, idx + 1, tSec)

                withContext(Dispatchers.Main) {
                    activeWeb.draw(captureCanvas)
                }
                thumbCanvas.drawBitmap(captureBmp, scaleMatrix, filterPaint)

                val diag = readDiagnostics(activeWeb)
                if (diag.autoFitCount > 0) totalAutoFits += diag.autoFitCount
                if (diag.warnings.isNotEmpty()) warningFrames++

                val thumbFile = File(sheetDir, String.format(Locale.US, "frame_%04d.jpg", idx))
                withContext(Dispatchers.IO) {
                    FileOutputStream(thumbFile).use { fos ->
                        thumbBmp.compress(Bitmap.CompressFormat.JPEG, 86, fos)
                    }
                }

                samples.add(
                    KeyframeSample(
                        index = idx,
                        timeSec = tSec,
                        sceneLabel = resolveSceneLabel(tSec, scenes),
                        thumbPath = thumbFile.absolutePath,
                        autoFitCount = diag.autoFitCount,
                        autoFitNotes = diag.notes,
                        warnings = diag.warnings
                    )
                )

                onProgress(idx + 1, timestamps.size, tSec, thumbFile.absolutePath)
            }

            return KeyframeSheetResult(
                samples = samples,
                durationSec = durationSec,
                totalAutoFits = totalAutoFits,
                warningCount = warningFrames,
                elapsedMs = System.currentTimeMillis() - startedMs
            )
        } finally {
            withContext(Dispatchers.Main) {
                runCatching { presentation?.dismiss() }
                runCatching { webView?.stopLoading() }
                runCatching { webView?.destroy() }
                runCatching { virtualDisplay?.release() }
                runCatching { imageReader?.close() }
            }
            captureBmp.recycle()
            thumbBmp.recycle()
        }
    }

    suspend fun buildPdfContactSheet(
        samples: List<KeyframeSample>,
        title: String,
        outPdfFile: File
    ): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        try {
            val pages = samples.chunked(FRAMES_PER_PAGE).ifEmpty { listOf(emptyList()) }
            val paintPack = SheetPaintPack()
            pages.forEachIndexed { pageIdx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(SHEET_W, SHEET_H, pageIdx + 1).create()
                val page = doc.startPage(pageInfo)
                drawSheetPage(
                    canvas = page.canvas,
                    chunk = chunk,
                    pageIdx = pageIdx,
                    totalPages = pages.size,
                    totalFrames = samples.size,
                    title = title,
                    p = paintPack
                )
                doc.finishPage(page)
            }
            FileOutputStream(outPdfFile).use { fos ->
                doc.writeTo(fos)
            }
            outPdfFile
        } finally {
            doc.close()
        }
    }

    suspend fun buildContactSheetZip(
        samples: List<KeyframeSample>,
        title: String,
        durationSec: Double,
        outZipFile: File
    ): File = withContext(Dispatchers.IO) {
        val pages = samples.chunked(FRAMES_PER_PAGE).ifEmpty { listOf(emptyList()) }
        val pageBmp = Bitmap.createBitmap(SHEET_W, SHEET_H, Bitmap.Config.ARGB_8888)
        val pageCanvas = Canvas(pageBmp)
        val paintPack = SheetPaintPack()

        try {
            ZipOutputStream(FileOutputStream(outZipFile)).use { zos ->
                pages.forEachIndexed { pageIdx, chunk ->
                    drawSheetPage(
                        canvas = pageCanvas,
                        chunk = chunk,
                        pageIdx = pageIdx,
                        totalPages = pages.size,
                        totalFrames = samples.size,
                        title = title,
                        p = paintPack
                    )
                    val entryName = String.format(Locale.US, "sheet_page_%02d.jpg", pageIdx + 1)
                    zos.putNextEntry(ZipEntry(entryName))
                    pageBmp.compress(Bitmap.CompressFormat.JPEG, 90, zos)
                    zos.closeEntry()
                }

                zos.putNextEntry(ZipEntry("frame_report.txt"))
                val report = buildTextReport(samples, title, durationSec)
                zos.write(report.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            outZipFile
        } finally {
            pageBmp.recycle()
        }
    }

    suspend fun buildReviewSheetImages(
        samples: List<KeyframeSample>,
        title: String,
        outDir: File,
        maxPages: Int = 2
    ): List<File> = withContext(Dispatchers.IO) {
        val pages = samples.chunked(FRAMES_PER_PAGE).take(maxPages.coerceAtLeast(1))
        if (pages.isEmpty()) return@withContext emptyList()
        outDir.mkdirs()

        val pageBmp = Bitmap.createBitmap(SHEET_W, SHEET_H, Bitmap.Config.ARGB_8888)
        val pageCanvas = Canvas(pageBmp)
        val paintPack = SheetPaintPack()
        val outFiles = mutableListOf<File>()
        try {
            pages.forEachIndexed { pageIdx, chunk ->
                drawSheetPage(
                    canvas = pageCanvas,
                    chunk = chunk,
                    pageIdx = pageIdx,
                    totalPages = pages.size,
                    totalFrames = samples.size,
                    title = title,
                    p = paintPack
                )
                val scaled = Bitmap.createScaledBitmap(pageBmp, 1120, 1440, true)
                val outFile = File(outDir, "review_sheet_${pageIdx + 1}.jpg")
                FileOutputStream(outFile).use { fos ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 86, fos)
                }
                scaled.recycle()
                outFiles.add(outFile)
            }
            outFiles
        } finally {
            pageBmp.recycle()
        }
    }

    private fun buildTextReport(
        samples: List<KeyframeSample>,
        title: String,
        durationSec: Double
    ): String = buildString {
        appendLine("raw_edit Pre-Render Frame Report")
        appendLine("Title: $title")
        appendLine("Duration: ${String.format(Locale.US, "%.2f", durationSec)}s")
        appendLine("Captured frames: ${samples.size}")
        appendLine()
        samples.forEach { s ->
            val ts = String.format(Locale.US, "%.2fs", s.timeSec)
            val status = when {
                s.warnings.isNotEmpty() -> "WARN: ${s.warnings.joinToString("; ")}"
                s.autoFitCount > 0 -> "AUTO-FIT(${s.autoFitCount}): ${s.autoFitNotes.joinToString("; ")}"
                else -> "OK"
            }
            appendLine("#${String.format(Locale.US, "%03d", s.index + 1)} | t=$ts | ${s.sceneLabel} | $status")
        }
    }

    private class SheetPaintPack {
        val bgPaint = Paint().apply { color = Color.parseColor("#0D0D0F") }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#17171A") }
        val borderDefault = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2C2B2D")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val borderWarn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF8F80")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val borderFit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CAA07D")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7ECE6")
            textSize = 38f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A99D95")
            textSize = 21f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7ECE6")
            textSize = 21f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val statusOkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A99D95")
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val statusFitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CAA07D")
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val statusWarnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF8F80")
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 13, 13, 15)
        }
        val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    }

    private fun drawSheetPage(
        canvas: Canvas,
        chunk: List<KeyframeSample>,
        pageIdx: Int,
        totalPages: Int,
        totalFrames: Int,
        title: String,
        p: SheetPaintPack
    ) {
        canvas.drawRect(0f, 0f, SHEET_W.toFloat(), SHEET_H.toFloat(), p.bgPaint)

        val marginX = 36f
        val topHeaderH = 112f
        val colGap = 24f
        val rowGap = 20f
        val cardW = (SHEET_W - marginX * 2f - colGap * (COLS - 1)) / COLS
        val cardH = (SHEET_H - topHeaderH - 34f - rowGap * (ROWS - 1)) / ROWS

        canvas.drawText(
            "${title.ifBlank { "raw_edit Reel" }}  —  Contact Sheet (${pageIdx + 1}/$totalPages)",
            marginX,
            52f,
            p.titlePaint
        )
        canvas.drawText(
            "$totalFrames total keyframes  |  Cite exact timestamps (e.g. t=4.50s) to Cloud AI to fix only broken scenes",
            marginX,
            84f,
            p.subPaint
        )

        val thumbHeaderH = 40f
        val imgRect = RectF()
        val cardRect = RectF()

        chunk.forEachIndexed { i, sample ->
            val col = i % COLS
            val row = i / COLS
            val left = marginX + col * (cardW + colGap)
            val top = topHeaderH + row * (cardH + rowGap)
            cardRect.set(left, top, left + cardW, top + cardH)

            canvas.drawRoundRect(cardRect, 14f, 14f, p.cardPaint)

            val tsStr = String.format(Locale.US, "t=%.2fs", sample.timeSec)
            val headStr = "#${String.format(Locale.US, "%02d", sample.index + 1)}  $tsStr" +
                if (sample.sceneLabel.isNotBlank()) "  ${sample.sceneLabel}" else ""
            canvas.drawText(headStr, left + 12f, top + 27f, p.badgeTextPaint)

            val (stLabel, stPaint) = when {
                sample.warnings.isNotEmpty() -> "WARN" to p.statusWarnPaint
                sample.autoFitCount > 0 -> "FIT" to p.statusFitPaint
                else -> "OK" to p.statusOkPaint
            }
            val stW = stPaint.measureText(stLabel)
            canvas.drawText(stLabel, left + cardW - stW - 12f, top + 27f, stPaint)

            val imgPad = 8f
            val availW = cardW - imgPad * 2f
            val availH = cardH - thumbHeaderH - imgPad
            val targetH = minOf(availH, availW * 9f / 16f)
            val targetW = targetH * 16f / 9f
            val imgLeft = left + (cardW - targetW) * 0.5f
            val imgTop = top + thumbHeaderH + (availH - targetH) * 0.5f
            imgRect.set(imgLeft, imgTop, imgLeft + targetW, imgTop + targetH)

            val thumb = runCatching { BitmapFactory.decodeFile(sample.thumbPath) }.getOrNull()
            if (thumb != null) {
                canvas.drawBitmap(thumb, null, imgRect, p.imgPaint)
                thumb.recycle()
            }

            val overlayLabel = tsStr
            val ow = p.badgeTextPaint.measureText(overlayLabel)
            val pillRect = RectF(imgLeft + 6f, imgTop + targetH - 30f, imgLeft + ow + 18f, imgTop + targetH - 6f)
            canvas.drawRoundRect(pillRect, 6f, 6f, p.pillBgPaint)
            canvas.drawText(overlayLabel, imgLeft + 12f, imgTop + targetH - 11f, p.badgeTextPaint)

            val borderPaint = when {
                sample.warnings.isNotEmpty() -> p.borderWarn
                sample.autoFitCount > 0 -> p.borderFit
                else -> p.borderDefault
            }
            canvas.drawRoundRect(cardRect, 14f, 14f, borderPaint)
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
                      var raw = (window.__ensureRawContract && window.__ensureRawContract()) || window.RAW;
                      if (raw && typeof raw.seek === 'function') {
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
            if (cancelled()) throw IllegalStateException("Cancelled")
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

    private suspend fun extractScenes(webView: WebView): List<SceneSpan> = withContext(Dispatchers.Main) {
        val d = CompletableDeferred<String>()
        webView.evaluateJavascript("JSON.stringify((window.__rawExtractScenes && window.__rawExtractScenes()) || [])") { res ->
            d.complete(res ?: "\"[]\"")
        }
        val raw = d.await()
        val unquoted = runCatching { jsonParser.decodeFromString<String>(raw) }.getOrDefault("[]")
        runCatching { jsonParser.decodeFromString<List<SceneSpan>>(unquoted) }.getOrDefault(emptyList())
    }

    private suspend fun readDiagnostics(webView: WebView): FrameDiagnostics = withContext(Dispatchers.Main) {
        val d = CompletableDeferred<String>()
        webView.evaluateJavascript("JSON.stringify(window.__rawLastDiagnostics || {autoFitCount:0,notes:[],warnings:[]})") { res ->
            d.complete(res ?: "\"{}\"")
        }
        val raw = d.await()
        val unquoted = runCatching { jsonParser.decodeFromString<String>(raw) }.getOrDefault("{}")
        val base = runCatching { jsonParser.decodeFromString<FrameDiagnostics>(unquoted) }.getOrDefault(FrameDiagnostics())
        val probe = runProbe(webView)
        if (probe.isEmpty()) base else base.copy(warnings = (base.warnings + probe).distinct().take(8))
    }

    /** Extra deterministic checks (text-text overlap, contrast, clipped text, broken images). */
    private suspend fun runProbe(webView: WebView): List<String> = withContext(Dispatchers.Main) {
        val d = CompletableDeferred<String>()
        webView.evaluateJavascript(PROBE_JS) { res -> d.complete(res ?: "\"[]\"") }
        val raw = d.await()
        val unquoted = runCatching { jsonParser.decodeFromString<String>(raw) }.getOrDefault("[]")
        runCatching { jsonParser.decodeFromString<List<String>>(unquoted) }.getOrDefault(emptyList())
    }

    /** Issues that remain in the source code (warnings + runtime auto-fits). Used to verify AI patches. */
    fun countIssues(samples: List<KeyframeSample>): Int =
        samples.sumOf { it.warnings.size + it.autoFitNotes.size }

    /** Collapses per-frame flag lines into one line per (selector, issue): sel|x,y,wxh|issue|times. */
    fun compressFlagLines(lines: List<String>): String {
        val groups = LinkedHashMap<String, MutableList<String>>()
        val firstRect = HashMap<String, String>()
        for (line in lines) {
            val p = line.split('|')
            if (p.size < 5) continue
            val key = p[2] + "|" + p[4]
            groups.getOrPut(key) { mutableListOf() }.add(p[1])
            firstRect.putIfAbsent(key, p[3])
        }
        return groups.entries.joinToString("\n") { (key, times) ->
            val sel = key.substringBefore('|')
            val tag = key.substringAfter('|')
            val shown = times.take(3).joinToString(",") + if (times.size > 3) "(+${times.size - 3})" else ""
            sel + "|" + firstRect[key] + "|" + tag + "|t=" + shown
        }
    }

    private suspend fun evalAndWaitTwoFrames(webView: WebView, reqId: Int, tSec: Double) {
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript("window.__rawSeekAndWait($tSec, $reqId)", null)
        }
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
        withTimeoutOrNull(1200) {
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
