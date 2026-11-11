package com.rawedit.app.domain

import java.net.URLEncoder
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class CutoutPadRatios(
    val top: Float = 0.04f,
    val right: Float = 0.04f,
    val bottom: Float = 0.04f,
    val left: Float = 0.04f
) {
    fun clamped(): CutoutPadRatios = CutoutPadRatios(
        top = top.coerceIn(0f, 0.28f),
        right = right.coerceIn(0f, 0.28f),
        bottom = bottom.coerceIn(0f, 0.28f),
        left = left.coerceIn(0f, 0.28f)
    )

    fun expand(
        extraTop: Float = 0f,
        extraRight: Float = 0f,
        extraBottom: Float = 0f,
        extraLeft: Float = 0f
    ): CutoutPadRatios = CutoutPadRatios(
        top = (top + extraTop.coerceAtLeast(0f)).coerceIn(0f, 0.28f),
        right = (right + extraRight.coerceAtLeast(0f)).coerceIn(0f, 0.28f),
        bottom = (bottom + extraBottom.coerceAtLeast(0f)).coerceIn(0f, 0.28f),
        left = (left + extraLeft.coerceAtLeast(0f)).coerceIn(0f, 0.28f)
    )
}

data class CutoutAuditResult(
    val status: String,
    val diagnosis: String,
    val keepOriginal: Boolean,
    val recommendedTolerance: Int,
    val extraPadTop: Float,
    val extraPadRight: Float,
    val extraPadBottom: Float,
    val extraPadLeft: Float,
    val needsReprocess: Boolean
)

data class CutoutAdjustedParams(
    val pad: CutoutPadRatios,
    val tolerance: Int,
    val keepOriginal: Boolean
)

object CutoutAuditMath {
    fun applyAdjustment(
        currentPad: CutoutPadRatios,
        currentTolerance: Int,
        isSvgSource: Boolean,
        audit: CutoutAuditResult
    ): CutoutAdjustedParams {
        val nextPad = currentPad.expand(
            extraTop = audit.extraPadTop,
            extraRight = audit.extraPadRight,
            extraBottom = audit.extraPadBottom,
            extraLeft = audit.extraPadLeft
        )
        val nextTol = if (isSvgSource) 0 else audit.recommendedTolerance.coerceIn(4, 60)
        return CutoutAdjustedParams(
            pad = nextPad,
            tolerance = nextTol,
            keepOriginal = audit.keepOriginal
        )
    }
}

object SvgSanitizerAndPadding {

    private val SCRIPT_BLOCK_REGEX = Regex("(?is)<script\\b[^>]*>[\\s\\S]*?</script>")
    private val SCRIPT_SELF_REGEX = Regex("(?is)<script\\b[^>]*/\\s*>")
    private val FOREIGN_BLOCK_REGEX = Regex("(?is)<foreignObject\\b[^>]*>[\\s\\S]*?</foreignObject>")
    private val IFRAME_BLOCK_REGEX = Regex("(?is)<(?:iframe|object|embed)\\b[^>]*>[\\s\\S]*?</(?:iframe|object|embed)>")
    private val EVENT_ATTR_REGEX = Regex("(?is)\\s+on[a-z0-9_]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)")
    private val HREF_EXTERNAL_REGEX = Regex("(?is)(xlink:href|href|src)\\s*=\\s*([\"'])\\s*(https?://|javascript:|data:text/html)[^\"']*\\2")
    private val SVG_OPEN_REGEX = Regex("(?is)<svg\\b[^>]*>")
    private val VIEWBOX_ATTR_REGEX = Regex("(?is)\\bviewBox\\s*=\\s*([\"'])([^\"']+)\\1")
    private val WIDTH_ATTR_REGEX = Regex("(?is)\\s+width\\s*=\\s*([\"'])([^\"']*)\\1")
    private val HEIGHT_ATTR_REGEX = Regex("(?is)\\s+height\\s*=\\s*([\"'])([^\"']*)\\1")
    private val PAR_ATTR_REGEX = Regex("(?is)\\s+preserveAspectRatio\\s*=\\s*([\"'])[^\"']*\\1")
    private val COLOR_ATTR_REGEX = Regex("(?is)\\s+color\\s*=\\s*([\"'])[^\"']*\\1")
    private val NUMBER_REGEX = Regex("-?\\d+(?:\\.\\d+)?")

    fun sanitizeSvg(rawSvg: String, tintHex: String? = null): String {
        val startIdx = rawSvg.indexOf("<svg", ignoreCase = true)
        if (startIdx < 0) return ""
        val endIdx = rawSvg.lastIndexOf("</svg>", ignoreCase = true)
        var cleaned = if (endIdx > startIdx) {
            rawSvg.substring(startIdx, endIdx + 6)
        } else {
            rawSvg.substring(startIdx)
        }

        cleaned = SCRIPT_BLOCK_REGEX.replace(cleaned, "")
        cleaned = SCRIPT_SELF_REGEX.replace(cleaned, "")
        cleaned = FOREIGN_BLOCK_REGEX.replace(cleaned, "")
        cleaned = IFRAME_BLOCK_REGEX.replace(cleaned, "")
        cleaned = EVENT_ATTR_REGEX.replace(cleaned, "")
        cleaned = HREF_EXTERNAL_REGEX.replace(cleaned) { mr ->
            val rawVal = mr.value
            if (rawVal.contains("http://www.w3.org/", ignoreCase = true) ||
                rawVal.contains("https://www.w3.org/", ignoreCase = true)
            ) {
                rawVal
            } else {
                "${mr.groupValues[1]}=\"\""
            }
        }

        val normalizedTint = tintHex?.trim()?.takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }
        if (normalizedTint != null) {
            cleaned = cleaned.replace("currentColor", normalizedTint, ignoreCase = true)
            cleaned = run {
                val openMatch = SVG_OPEN_REGEX.find(cleaned)
                if (openMatch == null) return@run cleaned
                val tag = COLOR_ATTR_REGEX.replace(openMatch.value, "")
                val rebuilt = if (tag.endsWith("/>")) {
                    tag.dropLast(2) + " color=\"$normalizedTint\" fill=\"$normalizedTint\" />"
                } else {
                    tag.dropLast(1) + " color=\"$normalizedTint\">"
                }
                cleaned.replaceRange(openMatch.range, rebuilt)
            }
        }
        return cleaned.trim()
    }

    fun expandSvgViewBox(svg: String, pad: CutoutPadRatios): String {
        val safePad = pad.clamped()
        val openMatch = SVG_OPEN_REGEX.find(svg) ?: return svg
        var openTag = openMatch.value

        val vbMatch = VIEWBOX_ATTR_REGEX.find(openTag)
        val nums = if (vbMatch != null) {
            NUMBER_REGEX.findAll(vbMatch.groupValues[2]).mapNotNull { it.value.toFloatOrNull() }.toList()
        } else {
            emptyList()
        }

        val (vx, vy, vw, vh) = if (nums.size >= 4 && nums[2] > 0f && nums[3] > 0f) {
            listOf(nums[0], nums[1], nums[2], nums[3])
        } else {
            val wAttr = WIDTH_ATTR_REGEX.find(openTag)?.groupValues?.get(2)
            val hAttr = HEIGHT_ATTR_REGEX.find(openTag)?.groupValues?.get(2)
            val parsedW = wAttr?.let { NUMBER_REGEX.find(it)?.value?.toFloatOrNull() }?.takeIf { it > 0f } ?: 24f
            val parsedH = hAttr?.let { NUMBER_REGEX.find(it)?.value?.toFloatOrNull() }?.takeIf { it > 0f } ?: parsedW
            listOf(0f, 0f, parsedW, parsedH)
        }

        val padL = vw * safePad.left
        val padT = vh * safePad.top
        val padR = vw * safePad.right
        val padB = vh * safePad.bottom

        val newX = vx - padL
        val newY = vy - padT
        val newW = vw + padL + padR
        val newH = vh + padT + padB
        val newVbStr = String.format(Locale.US, "%.3f %.3f %.3f %.3f", newX, newY, newW, newH)

        openTag = WIDTH_ATTR_REGEX.replace(openTag, "")
        openTag = HEIGHT_ATTR_REGEX.replace(openTag, "")
        openTag = PAR_ATTR_REGEX.replace(openTag, "")
        openTag = VIEWBOX_ATTR_REGEX.replace(openTag, "")

        val suffix = if (openTag.endsWith("/>")) "/>" else ">"
        val head = openTag.dropLast(suffix.length).trimEnd()
        val updatedOpen = "$head viewBox=\"$newVbStr\" width=\"100%\" height=\"100%\" preserveAspectRatio=\"xMidYMid meet\"$suffix"

        return svg.substring(0, openMatch.range.first) +
            updatedOpen +
            svg.substring(openMatch.range.last + 1)
    }
}

object BackgroundKeyMath {

    fun colorDistanceSq(a: Int, b: Int): Int {
        val dr = ((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)
        val dg = ((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    fun toleranceToDistSq(tolerance: Int): Pair<Int, Int> {
        val t = tolerance.coerceIn(2, 70).toFloat()
        val inner = (t * 1.9f).let { (it * it).roundToInt() }
        val outer = (t * 3.1f).let { (it * it).roundToInt() }
        return inner to max(inner + 120, outer)
    }

    fun smoothAlphaFromDistSq(distSq: Int, innerTolSq: Int, outerTolSq: Int): Int {
        if (distSq <= innerTolSq) return 0
        if (distSq >= outerTolSq) return 255
        val frac = (distSq - innerTolSq).toFloat() / max(1, outerTolSq - innerTolSq).toFloat()
        val smooth = frac * frac * (3f - 2f * frac)
        return (smooth * 255f).roundToInt().coerceIn(0, 255)
    }

    fun hasMeaningfulTransparency(argbPixels: IntArray): Boolean {
        if (argbPixels.isEmpty()) return false
        val step = max(1, argbPixels.size / 4096)
        var total = 0
        var transparentCount = 0
        var opaqueCount = 0
        var i = 0
        while (i < argbPixels.size) {
            val a = (argbPixels[i] ushr 24) and 0xFF
            if (a < 215) transparentCount++
            if (a >= 215) opaqueCount++
            total++
            i += step
        }
        return total > 0 && transparentCount * 100 >= total * 4 && opaqueCount * 100 >= total * 2
    }

    fun detectUniformCornerBackground(argbPixels: IntArray, width: Int, height: Int): Int? {
        if (width < 4 || height < 4 || argbPixels.size < width * height) return null
        val samples = ArrayList<Int>(20)
        val coords = listOf(
            0 to 0,
            width - 1 to 0,
            0 to height - 1,
            width - 1 to height - 1,
            width / 2 to 0,
            width / 2 to height - 1,
            0 to height / 2,
            width - 1 to height / 2,
            width / 4 to 0,
            (width * 3) / 4 to 0,
            width / 4 to height - 1,
            (width * 3) / 4 to height - 1
        )
        for ((x, y) in coords) {
            val px = argbPixels[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
            if (((px ushr 24) and 0xFF) > 180) {
                samples.add(px)
            }
        }
        if (samples.size < 6) return null

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        for (px in samples) {
            sumR += (px ushr 16) and 0xFF
            sumG += (px ushr 8) and 0xFF
            sumB += px and 0xFF
        }
        val avgR = (sumR / samples.size).toInt()
        val avgG = (sumG / samples.size).toInt()
        val avgB = (sumB / samples.size).toInt()
        val avgColor = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB

        var matching = 0
        for (px in samples) {
            if (colorDistanceSq(px, avgColor) <= 3200) matching++
        }
        return if (matching * 100 >= samples.size * 70) avgColor else null
    }

    fun computePaddedBounds(
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
        imgW: Int,
        imgH: Int,
        pad: CutoutPadRatios
    ): IntArray {
        val safePad = pad.clamped()
        val contentW = max(1, maxX - minX + 1)
        val contentH = max(1, maxY - minY + 1)
        val padL = ceil(contentW * safePad.left).toInt()
        val padT = ceil(contentH * safePad.top).toInt()
        val padR = ceil(contentW * safePad.right).toInt()
        val padB = ceil(contentH * safePad.bottom).toInt()

        val left = (minX - padL).coerceAtLeast(0)
        val top = (minY - padT).coerceAtLeast(0)
        val right = (maxX + 1 + padR).coerceAtMost(imgW)
        val bottom = (maxY + 1 + padB).coerceAtMost(imgH)
        return intArrayOf(left, top, max(left + 1, right), max(top + 1, bottom))
    }
}

data class WebUiElementBox(
    val label: String,
    val role: String,
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int
)

object WebCaptureHelper {

    fun normalizeUrlInput(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        val lower = trimmed.lowercase(Locale.US)
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed
        }
        val looksLikeHost = !trimmed.contains(' ') &&
            (trimmed.contains('.') || lower.startsWith("localhost"))
        return if (looksLikeHost) {
            "https://$trimmed"
        } else {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            "https://duckduckgo.com/?q=$encoded"
        }
    }

    fun extractHostSlug(url: String): String {
        val withoutProto = url.substringAfter("://", url)
        val hostOnly = withoutProto.substringBefore('/').substringBefore(':').lowercase(Locale.US)
        val clean = hostOnly.removePrefix("www.")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(28)
        return clean.ifBlank { "webpage" }
    }

    fun suggestSafeZoneForBoxes(
        imgW: Int,
        imgH: Int,
        elements: List<WebUiElementBox>
    ): String {
        if (elements.isEmpty()) {
            return "Overlay callout arrows and blurred spotlight rings directly over UI targets; reserve top margin or opposite edge for bold kinetic titles."
        }
        val topOccupied = elements.any { it.topPx < (imgH * 0.22f) }
        val bottomOccupied = elements.any { (it.topPx + it.heightPx) > (imgH * 0.78f) }
        val areaNote = when {
            !topOccupied -> "Top band (< ${floor(imgH * 0.22).toInt()}px) is relatively clear for section headlines;"
            !bottomOccupied -> "Bottom band (> ${floor(imgH * 0.78).toInt()}px) is relatively clear for subtitles or step labels;"
            else -> "Frame screenshot in a desktop/phone mockup card and place big kinetic headlines beside it;"
        }
        return "$areaNote draw cursor taps or highlight boxes strictly on the recorded Interactive UI targets."
    }

    fun formatScreenshotMetadata(
        url: String,
        pageTitle: String,
        imgW: Int,
        imgH: Int,
        elements: List<WebUiElementBox>
    ): Pair<String, String> {
        val host = url.substringAfter("://", url).substringBefore('/').removePrefix("www.").ifBlank { "webpage" }
        val title = pageTitle.trim().take(60).ifBlank { host }
        val validBoxes = elements
            .filter { it.widthPx >= 18 && it.heightPx >= 14 && it.leftPx < imgW && it.topPx < imgH }
            .take(8)
        val boxSummary = if (validBoxes.isNotEmpty()) {
            val formatted = validBoxes.joinToString("; ") { b ->
                val cleanLbl = b.label.replace(Regex("\\s+"), " ").trim().take(26).ifBlank { b.role }
                "\"$cleanLbl\" (${b.role}) [x=${b.leftPx},y=${b.topPx},${b.widthPx}x${b.heightPx}]"
            }
            " Interactive UI targets (px): $formatted."
        } else {
            ""
        }
        val desc = "Web UI screenshot of \"$title\" ($host, ${imgW}x${imgH}).$boxSummary"
        val safe = suggestSafeZoneForBoxes(imgW, imgH, validBoxes)
        return desc to safe
    }
}
