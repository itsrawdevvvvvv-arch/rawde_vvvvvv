package com.rawedit.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.rawedit.app.R
import com.rawedit.app.data.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class PaletteSwatch(
    val hex: String,
    val name: String
)

data class PaletteState(
    val number: Int = 1,
    val mood: String = "Earthy Editorial",
    val pairing: String = "Use 01 as kinetic headline on 03 dark sage, with 02 terracotta accent pills.",
    val colors: List<PaletteSwatch> = DEFAULT_SWATCHES
) {
    val numFormatted: String
        get() = String.format(Locale.US, "%02d", number.coerceAtLeast(1))

    companion object {
        val DEFAULT_SWATCHES = listOf(
            PaletteSwatch(hex = "#DCC9A9", name = "warm sand"),
            PaletteSwatch(hex = "#B83A2D", name = "terracotta"),
            PaletteSwatch(hex = "#4E6851", name = "forest sage")
        )

        val DEFAULT_01 = PaletteState(
            number = 1,
            mood = "Earthy Editorial",
            pairing = "Use 01 warm sand for kinetic display headings on 03 forest sage, with 02 terracotta pill highlights.",
            colors = DEFAULT_SWATCHES
        )
    }
}

object PaletteColorMath {
    private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

    fun normalizeHex(raw: String, fallback: String = "#DCC9A9"): String {
        val cleaned = raw.trim().let { if (it.startsWith("#")) it else "#$it" }.uppercase(Locale.US)
        return if (HEX_REGEX.matches(cleaned)) cleaned else fallback
    }

    fun luminance(hex: String): Float {
        val clean = normalizeHex(hex).removePrefix("#")
        val r = clean.substring(0, 2).toIntOrNull(16)?.div(255f) ?: 0.5f
        val g = clean.substring(2, 4).toIntOrNull(16)?.div(255f) ?: 0.5f
        val b = clean.substring(4, 6).toIntOrNull(16)?.div(255f) ?: 0.5f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    fun contrastInkArgb(hex: String): Long {
        // Dark #111112 on light & vibrant midtones (warm sand, terracotta, sage); Cream #F7ECE6 on deep darks
        return if (luminance(hex) > 0.25f) 0xFF111112L else 0xFFF7ECE6L
    }

    fun parseColorInt(hex: String, fallback: Int = AndroidColor.parseColor("#DCC9A9")): Int {
        return runCatching { AndroidColor.parseColor(normalizeHex(hex)) }.getOrDefault(fallback)
    }
}

object PaletteJsonCodec {
    fun encode(state: PaletteState): String {
        val root = JSONObject()
        root.put("number", state.number)
        root.put("mood", state.mood)
        root.put("pairing", state.pairing)
        val arr = JSONArray()
        state.colors.take(3).forEach { sw ->
            arr.put(
                JSONObject().apply {
                    put("hex", PaletteColorMath.normalizeHex(sw.hex))
                    put("name", sw.name.lowercase(Locale.US).trim())
                }
            )
        }
        root.put("colors", arr)
        return root.toString()
    }

    fun decode(raw: String): PaletteState? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            val number = obj.optInt("number", 1).coerceAtLeast(1)
            val mood = obj.optString("mood", "Editorial Motion").ifBlank { "Editorial Motion" }
            val pairing = obj.optString(
                "pairing",
                "Pair 01 & 03 for clean headline contrast with 02 as highlight accent."
            )
            val arr = obj.optJSONArray("colors") ?: return@runCatching null
            val list = mutableListOf<PaletteSwatch>()
            for (i in 0 until minOf(3, arr.length())) {
                val item = arr.optJSONObject(i) ?: continue
                val hex = PaletteColorMath.normalizeHex(item.optString("hex", ""))
                val name = item.optString("name", "tone").trim().lowercase(Locale.US).ifBlank { "tone" }
                list.add(PaletteSwatch(hex = hex, name = name))
            }
            while (list.size < 3) {
                list.add(PaletteState.DEFAULT_SWATCHES[list.size])
            }
            PaletteState(number = number, mood = mood, pairing = pairing, colors = list)
        }.getOrNull()
    }
}

object PaletteExporter {

    private const val CARD_W = 1080
    private const val CARD_H = 1440

    fun renderPaletteBitmap(context: Context, palette: PaletteState): Bitmap {
        val bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val boldType = runCatching { ResourcesCompat.getFont(context, R.font.bricolage_grotesque_bold) }.getOrNull()
            ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val semiType = runCatching { ResourcesCompat.getFont(context, R.font.bricolage_grotesque_semibold) }.getOrNull()
            ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val monoType = runCatching { ResourcesCompat.getFont(context, R.font.jetbrains_mono_semibold) }.getOrNull()
            ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#050506") }
        canvas.drawRect(0f, 0f, CARD_W.toFloat(), CARD_H.toFloat(), bgPaint)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#0D0D0D") }
        canvas.drawRoundRect(RectF(36f, 36f, CARD_W - 36f, CARD_H - 36f), 64f, 64f, cardPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(20, 247, 236, 230)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(RectF(37f, 37f, CARD_W - 37f, CARD_H - 37f), 64f, 64f, borderPaint)

        val c0 = palette.colors.firstOrNull()?.hex ?: "#DCC9A9"
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PaletteColorMath.parseColorInt(c0)
            typeface = boldType
            textSize = 112f
            textAlign = Paint.Align.LEFT
        }
        val numStr = palette.numFormatted
        val numBounds = android.graphics.Rect()
        numPaint.getTextBounds(numStr, 0, numStr.length, numBounds)
        canvas.drawText(numStr, 92f, 184f, numPaint)
        val nw = numPaint.measureText(numStr)

        val inspPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#E6E0D8")
            typeface = semiType
            textSize = 27f
            letterSpacing = 0.04f
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("COLOR INSPIRATION", 92f + nw + 24f, 128f, inspPaint)

        val trPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#E6E0D8")
            typeface = semiType
            textSize = 27f
            letterSpacing = 0.04f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("COLOR", CARD_W - 92f, 128f, trPaint)
        canvas.drawText("PALETTE", CARD_W - 92f, 164f, trPaint)

        val sx = 92f
        val sw = CARD_W - 184f
        val sh = 292f
        val gap = 34f

        for (i in 0 until 3) {
            val swatch = palette.colors.getOrElse(i) { PaletteState.DEFAULT_SWATCHES[i] }
            val sy = 268f + i * (sh + gap)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = PaletteColorMath.parseColorInt(swatch.hex)
            }
            canvas.drawRoundRect(RectF(sx, sy, sx + sw, sy + sh), 44f, 44f, fillPaint)

            val inkInt = PaletteColorMath.contrastInkArgb(swatch.hex).toInt()
            val idxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkInt
                typeface = boldType
                textSize = 198f
                textAlign = Paint.Align.LEFT
            }
            val idxLabel = String.format(Locale.US, "%02d", i + 1)
            val fm = idxPaint.fontMetrics
            val centerY = sy + sh * 0.5f - (fm.ascent + fm.descent) * 0.5f
            canvas.drawText(idxLabel, sx + 44f, centerY, idxPaint)

            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkInt
                typeface = boldType
                textSize = 52f
                textAlign = Paint.Align.RIGHT
            }
            val cleanName = swatch.name.lowercase(Locale.US).take(22)
            canvas.drawText(cleanName, sx + sw - 44f, sy + 88f, namePaint)

            val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkInt
                alpha = 235
                typeface = monoType
                textSize = 44f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(PaletteColorMath.normalizeHex(swatch.hex), sx + sw - 44f, sy + sh - 44f, hexPaint)
        }

        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#C9C2BA")
            typeface = semiType
            textSize = 27f
            letterSpacing = 0.04f
        }
        footPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("DESIGN — RAW_EDIT", 92f, CARD_H - 84f, footPaint)
        footPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("@raw_edit", CARD_W - 92f, CARD_H - 84f, footPaint)

        return bmp
    }

    suspend fun exportPalettePngToTemp(
        context: Context,
        cacheManager: CacheManager,
        palette: PaletteState
    ): File = withContext(Dispatchers.IO) {
        val file = File(cacheManager.sharedPacksDir, "raw_edit-palette-${palette.numFormatted}.png")
        val bmp = renderPaletteBitmap(context, palette)
        try {
            FileOutputStream(file).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } finally {
            bmp.recycle()
        }
        file
    }
}
