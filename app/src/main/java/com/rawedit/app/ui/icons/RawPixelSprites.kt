package com.rawedit.app.ui.icons

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

enum class PixelMascotKind {
    PEEKING_CAT,
    TV_BOT,
    PHANTOM_BUDDY,
    SPEECH_PLAY,
    FOLDER_PLAY,
    MIC_BOT,
    ZIP_BOT,
    CODE_BOT,
    LENS_BOT,
    AVATAR_FACE,
    PIXEL_CURSOR
}

private const val COL_CREAM = 0xFFF7ECE6.toInt()
private const val COL_GREY = 0xFFA99D95.toInt()
private const val COL_EYE = 0xFF0D0D0F.toInt()
private const val COL_DARK = 0xFF3A3A3A.toInt()
private const val COL_OFF = 0xFF202023.toInt()
private const val COL_TAN = 0xFFCAA07D.toInt()
private const val COL_CORAL = 0xFFFF8F80.toInt()

// Every grid strictly maintains uniform row lengths so pixel baking never skews.
private val CAT_SLEEP = listOf(
    "#...#...........",
    "#####.#########.",
    "#eee##########gg",
    "###############g",
    ".#############g.",
    "..###########..."
)

private val CAT_AWAKE = listOf(
    "#...#...........",
    "#####.#########.",
    "#e#e##########gg",
    "###############g",
    ".#############g.",
    "..###########..."
)

private val CAT_HAPPY = listOf(
    "#...#..mm.......",
    "#####.#########.",
    "#e#e##########gg",
    "#r#r###########g",
    ".#############g.",
    "..###########..."
)

private val TV_OPEN = listOf(
    "..g......g..",
    "...g....g...",
    "....gggg....",
    "gggggggggggg",
    "g##########g",
    "g#ee####ee#g",
    "g#ee####ee#g",
    "g##e####e##g",
    "g###eeee###g",
    "gggggggggggg",
    ".gg......gg."
)

private val TV_BLINK = listOf(
    "..g......g..",
    "...g....g...",
    "....gggg....",
    "gggggggggggg",
    "g##########g",
    "g##########g",
    "g##########g",
    "g##########g",
    "g###eeee###g",
    "gggggggggggg",
    ".gg......gg."
)

private val TV_HAPPY = listOf(
    "..m......m..",
    "...g....g...",
    "....gggg....",
    "gggggggggggg",
    "g##########g",
    "g#m#####m##g",
    "g#e#m##m#e#g",
    "g##########g",
    "g###eeee###g",
    "gggggggggggg",
    ".gg......gg."
)

private val MIC_OPEN = listOf(
    "....####....",
    "...######...",
    "..##e##e##..",
    "..########..",
    "..###ee###..",
    "..########..",
    "...######...",
    ".g........g.",
    ".gg......gg.",
    "..gggggggg..",
    ".....gg.....",
    "...gggggg..."
)

private val MIC_BLINK = listOf(
    "....####....",
    "...######...",
    "..########..",
    "..########..",
    "..###ee###..",
    "..########..",
    "...######...",
    ".g........g.",
    ".gg......gg.",
    "..gggggggg..",
    ".....gg.....",
    "...gggggg..."
)

private val ZIP_OPEN = listOf(
    "gggggggggggg",
    "g####g#####g",
    "g#ee#gg#ee#g",
    "g####g#####g",
    "g####gg####g",
    "g####g#####g",
    "g####gg####g",
    "g####g#####g",
    "gggggggggggg",
    ".gg......gg."
)

private val ZIP_BLINK = listOf(
    "gggggggggggg",
    "g####g#####g",
    "g####gg####g",
    "g####g#####g",
    "g####gg####g",
    "g####g#####g",
    "g####gg####g",
    "g####g#####g",
    "gggggggggggg",
    ".gg......gg."
)

private val CODE_OPEN = listOf(
    "....g..g....",
    ".gggggggggg.",
    "g##########g",
    "g##e####e##g",
    "g#e######e#g",
    "g##e####e##g",
    "g##########g",
    "g###eeee###g",
    ".gggggggggg.",
    "..g......g.."
)

private val CODE_BLINK = listOf(
    "....g..g....",
    ".gggggggggg.",
    "g##########g",
    "g##########g",
    "g#e######e#g",
    "g##########g",
    "g##########g",
    "g###eeee###g",
    ".gggggggggg.",
    "..g......g.."
)

private val LENS_OPEN = listOf(
    "...gggggg...",
    ".gg######gg.",
    ".g########g.",
    "g###e#ee###g",
    "g##eeeeee##g",
    "g##eeeeee##g",
    "g###eeee###g",
    ".g########g.",
    ".gg######gg.",
    "...gggggg...",
    "....g..g...."
)

private val LENS_BLINK = listOf(
    "...gggggg...",
    ".gg######gg.",
    ".g########g.",
    "g##########g",
    "g##########g",
    "g##eeeeee##g",
    "g##########g",
    ".g########g.",
    ".gg######gg.",
    "...gggggg...",
    "....g..g...."
)

private val GHO_OPEN = listOf(
    "...######...",
    "..########..",
    ".##########.",
    ".##ee##ee##.",
    ".##ee##ee##.",
    ".##########.",
    ".####ee####.",
    ".##########.",
    ".##########.",
    ".##.####.##.",
    ".#...##...#."
)

private val GHO_BLINK = listOf(
    "...######...",
    "..########..",
    ".##########.",
    ".##########.",
    ".##########.",
    ".##########.",
    ".####ee####.",
    ".##########.",
    ".##########.",
    ".##.####.##.",
    ".#...##...#."
)

private val FOLD_0 = listOf(
    "####........",
    "############",
    "############",
    "#####e######",
    "#####ee#####",
    "#####eee####",
    "#####ee#####",
    "#####e######",
    "############"
)

private val SPEECH_0 = listOf(
    ".##########.",
    "#eeeeeeeeee#",
    "#eeee#eeeee#",
    "#eeee##eeee#",
    "#eeee###eee#",
    "#eeee##eeee#",
    "#eeee#eeeee#",
    "#eeeeeeeeee#",
    ".###..#####.",
    "..#.#.......",
    "...##......."
)

private val FACE_STATIC = listOf(
    "...####...",
    "..######..",
    "..######..",
    "..######..",
    "...####...",
    "..........",
    ".########.",
    "##########",
    "##########"
)

private val CURSOR_0 = listOf(
    "g.........",
    "gg........",
    "g#g.......",
    "g##g......",
    "g###g.....",
    "g####g....",
    "g#####g...",
    "g##gg.....",
    "g#g.g.....",
    "gg...g...."
)

private val SPARKLE_5 = listOf(
    "..#..",
    ".###.",
    "#####",
    ".###.",
    "..#.."
)

// Cache native 1x pixel ImageBitmaps ONCE per sprite key so GPU draws each mascot in 1 drawImage call.
private object SpriteBitmapCache {
    private val map = ConcurrentHashMap<String, ImageBitmap>()

    fun get(key: String, rows: List<String>, primaryTintArgb: Int): ImageBitmap {
        val cacheKey = "${key}_$primaryTintArgb"
        map[cacheKey]?.let { return it }

        val h = rows.size.coerceAtLeast(1)
        val w = rows.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            val row = rows[y]
            for (x in 0 until row.length) {
                val col = when (row[x]) {
                    '#' -> primaryTintArgb
                    'g' -> COL_GREY
                    'e' -> COL_EYE
                    'd' -> COL_DARK
                    'o' -> COL_OFF
                    'm' -> COL_TAN
                    'r' -> COL_CORAL
                    else -> 0
                }
                if (col != 0) {
                    bmp.setPixel(x, y, col)
                }
            }
        }
        val img = bmp.asImageBitmap()
        map.putIfAbsent(cacheKey, img)
        return img
    }

    val logoBitmap: ImageBitmap by lazy {
        val w = 28
        val h = 22
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        fun r(x: Int, y: Int, rw: Int, rh: Int, col: Int) {
            for (yy in y until (y + rh).coerceAtMost(h)) {
                for (xx in x until (x + rw).coerceAtMost(w)) {
                    if (xx >= 0 && yy >= 0) bmp.setPixel(xx, yy, col)
                }
            }
        }
        val antennae = arrayOf(6 to 1, 7 to 2, 8 to 3, 21 to 1, 20 to 2, 19 to 3)
        antennae.forEach { (ax, ay) -> r(ax, ay, 1, 1, COL_GREY) }
        r(11, 2, 6, 2, COL_DARK)
        r(1, 4, 26, 17, COL_TAN)
        r(2, 5, 24, 15, COL_OFF)
        r(3, 6, 18, 13, COL_EYE)
        r(22, 8, 3, 3, COL_CREAM)
        r(22, 13, 3, 3, COL_CREAM)
        r(4, 21, 3, 1, COL_DARK)
        r(21, 21, 3, 1, COL_DARK)

        val font = mapOf(
            'R' to "110101110101101",
            'A' to "010101111101101",
            'W' to "101101101111101",
            'E' to "111100110100111",
            'D' to "110101101101110",
            'I' to "111010010010111",
            'T' to "111010010010010"
        )
        fun drawWord(x0: Int, y0: Int, text: String) {
            text.forEachIndexed { idx, ch ->
                val f = font[ch] ?: return@forEachIndexed
                for (i in 0 until 15) {
                    if (f[i] == '1') {
                        r(x0 + idx * 4 + (i % 3), y0 + (i / 3), 1, 1, COL_CREAM)
                    }
                }
            }
        }
        drawWord(6, 7, "RAW")
        drawWord(4, 13, "EDIT")
        bmp.asImageBitmap()
    }
}

/**
 * Retro pixel CRT TV logo badge with "RAW / EDIT" screen and gentle wobble.
 */
@Composable
fun PixelRawEditLogo(
    modifier: Modifier = Modifier,
    width: Dp = 42.dp,
    height: Dp = 33.dp
) {
    val animEnabled = rememberAnimationsEnabled()
    val rotState: State<Float>? = if (animEnabled) {
        val inf = rememberInfiniteTransition(label = "logo_wobble")
        inf.animateFloat(
            initialValue = -2.8f,
            targetValue = 2.8f,
            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
            label = "wob"
        )
    } else {
        null
    }

    val bmp = remember { SpriteBitmapCache.logoBitmap }
    Canvas(
        modifier = modifier
            .size(width, height)
            .graphicsLayer { rotationZ = rotState?.value ?: 0f }
    ) {
        drawImage(
            image = bmp,
            srcSize = IntSize(bmp.width, bmp.height),
            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
            filterQuality = FilterQuality.None
        )
    }
}

@Composable
fun RawPixelMascot(
    kind: PixelMascotKind,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    tint: Color = RawColors.TextPrimary,
    interactive: Boolean = true,
    noBob: Boolean = false,
    noBlink: Boolean = false,
    awakeOverride: Boolean = false,
    onTap: (() -> Unit)? = null
) {
    val animEnabled = rememberAnimationsEnabled()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tintArgb = remember(tint) { tint.toArgb() }

    var isBlinking by remember(kind) { mutableStateOf(false) }
    var tappedMood by remember(kind) { mutableIntStateOf(0) }
    val hopAnim = remember(kind) { Animatable(0f) }

    val staticSprite = kind == PixelMascotKind.AVATAR_FACE || kind == PixelMascotKind.PIXEL_CURSOR

    LaunchedEffect(kind, animEnabled, noBlink) {
        if (!animEnabled || staticSprite || noBlink) {
            isBlinking = false
            return@LaunchedEffect
        }
        while (true) {
            delay(if (kind == PixelMascotKind.PEEKING_CAT) 4400L else 3600L)
            isBlinking = true
            delay(150L)
            isBlinking = false
        }
    }

    val bobPhaseState: State<Float>? = if (animEnabled && !noBob && !staticSprite && kind != PixelMascotKind.PEEKING_CAT) {
        val inf = rememberInfiniteTransition(label = "pixel_bob_${kind.name}")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
            label = "bob"
        )
    } else {
        null
    }

    val activeRows: List<String> = when (kind) {
        PixelMascotKind.PEEKING_CAT -> when {
            awakeOverride || tappedMood > 0 -> CAT_HAPPY
            else -> CAT_SLEEP
        }
        PixelMascotKind.TV_BOT -> when {
            tappedMood > 0 -> TV_HAPPY
            isBlinking -> TV_BLINK
            else -> TV_OPEN
        }
        PixelMascotKind.MIC_BOT -> if (isBlinking) MIC_BLINK else MIC_OPEN
        PixelMascotKind.ZIP_BOT -> if (isBlinking) ZIP_BLINK else ZIP_OPEN
        PixelMascotKind.CODE_BOT -> if (isBlinking) CODE_BLINK else CODE_OPEN
        PixelMascotKind.LENS_BOT -> if (isBlinking) LENS_BLINK else LENS_OPEN
        PixelMascotKind.PHANTOM_BUDDY -> if (isBlinking) GHO_BLINK else GHO_OPEN
        PixelMascotKind.FOLDER_PLAY -> FOLD_0
        PixelMascotKind.SPEECH_PLAY -> SPEECH_0
        PixelMascotKind.AVATAR_FACE -> FACE_STATIC
        PixelMascotKind.PIXEL_CURSOR -> CURSOR_0
    }

    val stateTag = when {
        tappedMood > 0 || awakeOverride -> "alt"
        isBlinking -> "blk"
        else -> "std"
    }
    val bmp = remember(kind, stateTag, tintArgb) {
        SpriteBitmapCache.get("${kind.name}_$stateTag", activeRows, tintArgb)
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val bobPx = if (bobPhaseState != null) sin(bobPhaseState.value) * 6f else 0f
                translationY = bobPx - hopAnim.value * 18f
                scaleX = 1f + hopAnim.value * 0.09f
                scaleY = 1f - hopAnim.value * 0.05f
            }
            .let { base ->
                if (interactive && !staticSprite) {
                    base.clickable(interactionSource = interactionSource, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tappedMood = 1
                        onTap?.invoke()
                        scope.launch {
                            hopAnim.snapTo(0f)
                            hopAnim.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 460f))
                            hopAnim.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 340f))
                            delay(850)
                            tappedMood = 0
                        }
                    }
                } else {
                    base
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val scale = minOf(this.size.width / bmp.width.toFloat(), this.size.height / bmp.height.toFloat())
            val drawW = (bmp.width * scale).roundToInt().coerceAtLeast(1)
            val drawH = (bmp.height * scale).roundToInt().coerceAtLeast(1)
            val ox = ((this.size.width - drawW) * 0.5f).roundToInt()
            val oy = if (kind == PixelMascotKind.PEEKING_CAT) {
                (this.size.height - drawH).roundToInt()
            } else {
                ((this.size.height - drawH) * 0.5f).roundToInt()
            }
            drawImage(
                image = bmp,
                srcSize = IntSize(bmp.width, bmp.height),
                dstOffset = IntOffset(ox, oy),
                dstSize = IntSize(drawW, drawH),
                filterQuality = FilterQuality.None
            )
        }
    }
}

@Composable
fun PixelTypeMarqueeBadge(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val animEnabled = rememberAnimationsEnabled()
    val phaseState: State<Float>? = if (animEnabled) {
        val inf = rememberInfiniteTransition(label = "cur_click")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
            label = "clk"
        )
    } else {
        null
    }

    val bmp = remember { SpriteBitmapCache.get("cur_marq", CURSOR_0, COL_CREAM) }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val phase = phaseState?.value ?: 0f
                when {
                    phase < 0.35f -> {
                        val k = phase / 0.35f
                        translationX = -22f * k
                        translationY = 8f * k
                        scaleX = 1f
                        scaleY = 1f
                    }
                    phase < 0.45f -> {
                        translationX = -22f
                        translationY = 12f
                        scaleX = 0.85f
                        scaleY = 0.85f
                    }
                    phase < 0.55f -> {
                        translationX = -22f
                        translationY = 8f
                        scaleX = 1f
                        scaleY = 1f
                    }
                    else -> {
                        val k = (phase - 0.55f) / 0.45f
                        translationX = -22f * (1f - k)
                        translationY = 8f * (1f - k)
                        scaleX = 1f
                        scaleY = 1f
                    }
                }
            }
    ) {
        val s = minOf(this.size.width / bmp.width.toFloat(), this.size.height / bmp.height.toFloat())
        val dw = (bmp.width * s).roundToInt().coerceAtLeast(1)
        val dh = (bmp.height * s).roundToInt().coerceAtLeast(1)
        drawImage(
            image = bmp,
            srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset(((this.size.width - dw) * 0.5f).roundToInt(), ((this.size.height - dh) * 0.5f).roundToInt()),
            dstSize = IntSize(dw, dh),
            filterQuality = FilterQuality.None
        )
    }
}

@Composable
fun PixelSparkle(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    tint: Color = RawColors.Cream,
    phaseOffsetMs: Int = 0
) {
    val animEnabled = rememberAnimationsEnabled()
    val tintArgb = remember(tint) { tint.toArgb() }
    val phaseState: State<Float>? = if (animEnabled) {
        val inf = rememberInfiniteTransition(label = "spark_$phaseOffsetMs")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1500, delayMillis = phaseOffsetMs % 700, easing = LinearEasing),
                RepeatMode.Reverse
            ),
            label = "sp"
        )
    } else {
        null
    }
    val bmp = remember(tintArgb) { SpriteBitmapCache.get("spk", SPARKLE_5, tintArgb) }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val p = phaseState?.value ?: 1f
                val sc = 0.65f + 0.40f * p
                scaleX = sc
                scaleY = sc
                alpha = 0.40f + 0.60f * p
            }
    ) {
        drawImage(
            image = bmp,
            srcSize = IntSize(bmp.width, bmp.height),
            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
            filterQuality = FilterQuality.None
        )
    }
}
