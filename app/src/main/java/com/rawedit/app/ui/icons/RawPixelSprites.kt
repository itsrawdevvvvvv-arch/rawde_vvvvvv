package com.rawedit.app.ui.icons

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
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
    LENS_BOT
}

private val PE_WHITE = Color(0xFFFFFFFF)
private val PE_LIGHT = Color(0xFFD2D2D2)
private val PE_GREY = Color(0xFF7B7B7B)
private val PE_DARK = Color(0xFF141414)
private val PE_CORAL = Color(0xFFFF7285)
private val PE_MINT = Color(0xFF7BE0A0)

private val CAT_SLEEP_0 = listOf(
    "...WW............WW...",
    "..WGGW..........WGGW..",
    "..WGGWW........WWGGW..",
    "..WWWWWWWWWWWWWWWWWW..",
    ".WWWWWWWWWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWWWWWWWW.",
    ".WWWWDDDWWWWWWDDDWWWW.",
    ".WWWWWWWWWWWWWWWWWWWW.",
    "WW.WWWWWWWDDWWWWWWW.WW",
    ".WWWWWWWWWWWWWWWWWWWW.",
    ".DWWWDWWWWWWWWWWWDWWWD",
    ".LWWWL...........LWWWL"
)

private val CAT_BLINK_1 = listOf(
    "...WW............WW...",
    "..WGGW..........WGGW..",
    "..WGGWW........WWGGW..",
    "..WWWWWWWWWWWWWWWWWW..",
    ".WWWWWWWWWWWWWWWWWWWW.",
    ".WWWWDDWWWWWWWWDDWWWW.",
    ".WWWWDDWWWWWWWWDDWWWW.",
    ".WWWWWWWWWWWWWWWWWWWW.",
    "WW.WWWWWWWDDWWWWWWW.WW",
    ".WWWWWWWWWWWWWWWWWWWW.",
    ".DWWWDWWWWWWWWWWWDWWWD",
    ".LWWWL...........LWWWL"
)

private val CAT_HAPPY_TAP = listOf(
    "...WW............WW...",
    "..WGGW....RR....WGGW..",
    "..WGGWW..RRRR..WWGGW..",
    "..WWWWWWWWWWWWWWWWWW..",
    ".WWWWWWWWWWWWWWWWWWWW.",
    ".WWWWDWDWWWWWWWDWDWWW.",
    ".WWWDWWWDWWWWWDWWWDWW.",
    ".WWWWWWWWWWWWWWWWWWWW.",
    "WW.WWPWWWWDDWWWWPWW.WW",
    ".WWWWWWWWWWWWWWWWWWWW.",
    ".LWWWL..........LWWWL.",
    "..DDD............DDD.."
)

private val TV_BOT_0 = listOf(
    "...W..........W...",
    "....W........W....",
    ".....W......W.....",
    "......WWWWWW......",
    "..WWWWWWWWWWWWWW..",
    ".WWWWWWWWWWWWWWWW.",
    ".WWDDDDDDDDDDDDWW.",
    ".WWDDWWDDDDWWDDWW.",
    ".WWDDWWDDDDWWDDWW.",
    ".WWDDDDDDDDDDDDWW.",
    ".WWDDDDWWWWDDDDWW.",
    ".WWWWWWWWWWWWWWWW.",
    "..WWWWWWWWWWWWWW..",
    "....WW......WW....",
    "...WWW......WWW...",
    ".................."
)

private val TV_BOT_1 = listOf(
    "..................",
    "...W..........W...",
    "....W........W....",
    ".....WWWWWWWW.....",
    "..WWWWWWWWWWWWWW..",
    ".WWWWWWWWWWWWWWWW.",
    ".WWDDDDDDDDDDDDWW.",
    ".WWDDDDDDDDDDDDWW.",
    ".WWDDWWDDDDWWDDWW.",
    ".WWDDDDWWWWDDDDWW.",
    ".WWDDDDDDDDDDDDWW.",
    ".WWWWWWWWWWWWWWWW.",
    "..WWWWWWWWWWWWWW..",
    "....WW......WW....",
    "....WWW....WWW....",
    ".................."
)

private val TV_BOT_HAPPY = listOf(
    "..W............W..",
    "...W...M..M...W...",
    "....W........W....",
    "......WWWWWW......",
    "..WWWWWWWWWWWWWW..",
    ".WWWWWWWWWWWWWWWW.",
    ".WWDDDDDDDDDDDDWW.",
    ".WWDDWDDDDDDWDDWW.",
    ".WWDWDWDDDDWDWDWW.",
    ".WWDDDDDDDDDDDDWW.",
    ".WWDDDDWWWWDDDDWW.",
    ".WWWWWWWWWWWWWWWW.",
    "..WWWWWWWWWWWWWW..",
    "...WW........WW...",
    "..WWW........WWW..",
    ".................."
)

private val PHANTOM_0 = listOf(
    ".....WWWWWW.....",
    "...WWWWWWWWWW...",
    "..WWWWWWWWWWWW..",
    ".WWWWWWWWWWWWWW.",
    ".WWWWDDWWWWDDWW.",
    ".WWWWDDWWWWDDWW.",
    ".WWWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWW.",
    ".WWWLWWWWWWLWWW.",
    ".WW.WWW..WWW.WW.",
    ".W...W....W...W."
)

private val PHANTOM_1 = listOf(
    ".....WWWWWW.....",
    "...WWWWWWWWWW...",
    "..WWWWWWWWWWWW..",
    ".WWWWWWWWWWWWWW.",
    ".WWWDDWWWWDDWWW.",
    ".WWWDDWWWWDDWWW.",
    ".WWWWWWWWWWWWWW.",
    ".WWWWWWDDWWWWWW.",
    ".WWWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWW.",
    ".WWWW.WWWW.WWWW.",
    "..WW...WW...WW..",
    "................"
)

private val PHANTOM_TAP = listOf(
    "......RRRR......",
    ".....RRRRRR.....",
    "...WWWWWWWWWW...",
    "..WWWWWWWWWWWW..",
    ".WWWWWWWWWWWWWW.",
    ".WWWDWDWWWDWDWW.",
    ".WWWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWW.",
    ".WW.WWW..WWW.WW.",
    ".W...W....W...W.",
    "................"
)

private val SPEECH_PLAY_0 = listOf(
    "..WWWWWWWWWWWWWW..",
    ".WDDDDDDDDDDDDDDW.",
    "WDDDDDDWDDDDDDDDDW",
    "WDDDDDDWWDDDDDDDDW",
    "WDDDDDDWWWDDDDDDDW",
    "WDDDDDDWWWWDDDDDDW",
    "WDDDDDDWWWDDDDDDDW",
    "WDDDDDDWWDDDDDDDDW",
    "WDDDDDDWDDDDDDDDDW",
    ".WDDDDDDDDDDDDDDW.",
    "..WWWWW..WWWWWWWW.",
    "...W..W...........",
    "....WW............"
)

private val SPEECH_PLAY_1 = listOf(
    "..WWWWWWWWWWWWWW..",
    ".WDDDDDDDDDDDDDDW.",
    "WDDDDDDDDDDDDDDDDW",
    "WDDDDDDDWDDDDDDDDW",
    "WDDDDDDDWWDDDDDDDW",
    "WDDDDDDDWWWDDDDDDW",
    "WDDDDDDDWWDDDDDDDW",
    "WDDDDDDDWDDDDDDDDW",
    "WDDDDDDDDDDDDDDDDW",
    ".WDDDDDDDDDDDDDDW.",
    "..WWWWWWWWWW..WWW.",
    "...........W..W...",
    "............WW...."
)

private val FOLDER_PLAY_0 = listOf(
    ".WWWWWW...........",
    "WDDDDDDW..........",
    "WDDDDDDWWWWWWWWWW.",
    "WDDDDDDDDDDDDDDDDW",
    "WDDDDDDWDDDDDDDDDW",
    "WDDDDDDWWDDDDDDDDW",
    "WDDDDDDWWWDDDDDDDW",
    "WDDDDDDWWDDDDDDDDW",
    "WDDDDDDWDDDDDDDDDW",
    "WDDDDDDDDDDDDDDDDW",
    ".WWWWWWWWWWWWWWWW.",
    ".................."
)

private val FOLDER_PLAY_1 = listOf(
    "..................",
    ".WWWWWW...........",
    "WDDDDDDW..........",
    "WDDDDDDWWWWWWWWWW.",
    "WDDDDDDDDDDDDDDDDW",
    "WDDDDDDDWDDDDDDDDW",
    "WDDDDDDDWWDDDDDDDW",
    "WDDDDDDDWWWDDDDDDW",
    "WDDDDDDDWWDDDDDDDW",
    "WDDDDDDDWDDDDDDDDW",
    "WDDDDDDDDDDDDDDDDW",
    ".WWWWWWWWWWWWWWWW."
)

private val MIC_BOT_0 = listOf(
    ".....WWWWWW.....",
    "....WDDDDDDW....",
    "....WDWDDWDW....",
    "....WDDDDDDW....",
    "....WDDDDDDW....",
    ".....WWWWWW.....",
    "..W..........W..",
    "..WWDDDDDDDDWW..",
    "...WWWWWWWWWW...",
    ".......WW.......",
    ".......WW.......",
    "....WWWWWWWW...."
)

private val MIC_BOT_1 = listOf(
    "....W......W....",
    ".....WWWWWW.....",
    "....WDDDDDDW....",
    "....WDDDDDDW....",
    "....WDWDDWDW....",
    "....WDDDDDDW....",
    ".....WWWWWW.....",
    "..WWDDDDDDDDWW..",
    "...WWWWWWWWWW...",
    ".......WW.......",
    "....WWWWWWWW....",
    "................"
)

private val ZIP_BOT_0 = listOf(
    "..WWWWWWWWWWWW..",
    ".WDDDDWWDDDDDDW.",
    ".WDDDDDDWWDDDDW.",
    ".WDDDDWWDDDDDDW.",
    ".WDDDDDDDDDDDDW.",
    ".WDDWWDDDDWWDDW.",
    ".WDDWWDDDDWWDDW.",
    ".WDDDDWWWWDDDDW.",
    ".WDDDDDDDDDDDDW.",
    "..WWWWWWWWWWWW..",
    "....WW....WW....",
    "...WWW....WWW..."
)

private val ZIP_BOT_1 = listOf(
    "..WWWWWWWWWWWW..",
    ".WDDDDWWDDDDDDW.",
    ".WDDDDDDWWDDDDW.",
    ".WDDDDWWDDDDDDW.",
    ".WDDDDDDDDDDDDW.",
    ".WDDDDDDDDDDDDW.",
    ".WDDWWDDDDWWDDW.",
    ".WDDDDWWWWDDDDW.",
    ".WDDDDDDDDDDDDW.",
    "..WWWWWWWWWWWW..",
    ".....WW..WW.....",
    "....WWW..WWW...."
)

private val CODE_BOT_0 = listOf(
    ".WWWWWWWWWWWWWW.",
    "WDDDDDDDDDDDDDDW",
    "WDDWDDDDDDDDWDDW",
    "WDDWDDWWWWDDWDDW",
    "WDDDDDWDDWDDDDDW",
    "WDDWDDWWWWDDWDDW",
    "WDDWDDDDDDDDWDDW",
    "WDDDDDDDDDDDDDDW",
    ".WWWWWWWWWWWWWW.",
    "....WW....WW....",
    "...WWW....WWW..."
)

private val CODE_BOT_1 = listOf(
    ".WWWWWWWWWWWWWW.",
    "WDDDDDDDDDDDDDDW",
    "WDDDDDDDDDDDDDDW",
    "WDDWWDDDDDDWWDDW",
    "WDWDDWDDDDWDDWDW",
    "WDDDDWWWWWWDDDDW",
    "WDDDDDDDDDDDDDDW",
    "WDDDDDDDDDDDDDDW",
    ".WWWWWWWWWWWWWW.",
    "...WW......WW...",
    "..WWW......WWW.."
)

private val LENS_BOT_0 = listOf(
    "...WWWWWW.......",
    "..WDDDDDDW......",
    ".WDDWWWWDDW.....",
    ".WDDWDDWDDW.....",
    ".WDDWDDWDDW.....",
    ".WDDWWWWDDW.....",
    "..WDDDDDDW......",
    "...WWWWWWWW.....",
    "........WWWWW...",
    "..........WWWW..",
    "...........WW..."
)

private val LENS_BOT_1 = listOf(
    "...WWWWWW.......",
    "..WDDDDDDW......",
    ".WDDDDDDDDW.....",
    ".WDDWWWWDDW.....",
    ".WDDDDDDDDW.....",
    ".WDDWWWWDDW.....",
    "..WDDDDDDW......",
    "...WWWWWWWW.....",
    "........WWWWW...",
    "..........WWWW..",
    "...........WW..."
)

private fun drawPixelGrid(
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    rows: List<String>,
    tint: Color = PE_WHITE
) {
    if (rows.isEmpty()) return
    val gridH = rows.size
    val gridW = rows.maxOf { it.length }.coerceAtLeast(1)
    val cell = minOf(drawScope.size.width / gridW, drawScope.size.height / gridH)
    val ox = (drawScope.size.width - cell * gridW) * 0.5f
    val oy = (drawScope.size.height - cell * gridH) * 0.5f
    val pad = 0.35f

    for (r in 0 until gridH) {
        val row = rows[r]
        for (c in 0 until row.length) {
            val ch = row[c]
            val color = when (ch) {
                'W' -> tint
                'L' -> PE_LIGHT
                'G' -> PE_GREY
                'D' -> PE_DARK
                'R', 'P' -> PE_CORAL
                'M' -> PE_MINT
                else -> continue
            }
            drawScope.drawRect(
                color = color,
                topLeft = Offset(ox + c * cell, oy + r * cell),
                size = Size(cell + pad, cell + pad)
            )
        }
    }
}

@Composable
fun RawPixelMascot(
    kind: PixelMascotKind,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    tint: Color = PE_WHITE,
    interactive: Boolean = true,
    onTap: (() -> Unit)? = null
) {
    val animEnabled = rememberAnimationsEnabled()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var frameTick by remember(kind) { mutableIntStateOf(0) }
    var tappedMood by remember(kind) { mutableIntStateOf(0) }
    val hopAnim = remember(kind) { Animatable(0f) }

    LaunchedEffect(kind, animEnabled) {
        if (!animEnabled) return@LaunchedEffect
        while (true) {
            delay(if (kind == PixelMascotKind.PEEKING_CAT) 720L else 480L)
            frameTick = (frameTick + 1) % 4
        }
    }

    val inf = rememberInfiniteTransition(label = "pixel_bob_${kind.name}")
    val bobPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "bob"
    )
    val bobDp = if (animEnabled && kind != PixelMascotKind.PEEKING_CAT) {
        (sin(bobPhase) * 2.2f).dp
    } else {
        0.dp
    }

    val frames: List<List<String>> = when (kind) {
        PixelMascotKind.PEEKING_CAT -> listOf(CAT_SLEEP_0, CAT_SLEEP_0, CAT_BLINK_1, CAT_SLEEP_0)
        PixelMascotKind.TV_BOT -> listOf(TV_BOT_0, TV_BOT_1, TV_BOT_0, TV_BOT_1)
        PixelMascotKind.PHANTOM_BUDDY -> listOf(PHANTOM_0, PHANTOM_1, PHANTOM_0, PHANTOM_1)
        PixelMascotKind.SPEECH_PLAY -> listOf(SPEECH_PLAY_0, SPEECH_PLAY_1, SPEECH_PLAY_0, SPEECH_PLAY_1)
        PixelMascotKind.FOLDER_PLAY -> listOf(FOLDER_PLAY_0, FOLDER_PLAY_1, FOLDER_PLAY_0, FOLDER_PLAY_1)
        PixelMascotKind.MIC_BOT -> listOf(MIC_BOT_0, MIC_BOT_1, MIC_BOT_0, MIC_BOT_1)
        PixelMascotKind.ZIP_BOT -> listOf(ZIP_BOT_0, ZIP_BOT_1, ZIP_BOT_0, ZIP_BOT_1)
        PixelMascotKind.CODE_BOT -> listOf(CODE_BOT_0, CODE_BOT_1, CODE_BOT_0, CODE_BOT_1)
        PixelMascotKind.LENS_BOT -> listOf(LENS_BOT_0, LENS_BOT_1, LENS_BOT_0, LENS_BOT_1)
    }
    val tapGrid: List<String> = when (kind) {
        PixelMascotKind.PEEKING_CAT -> CAT_HAPPY_TAP
        PixelMascotKind.TV_BOT -> TV_BOT_HAPPY
        PixelMascotKind.PHANTOM_BUDDY -> PHANTOM_TAP
        else -> frames[1]
    }

    val activeRows = if (tappedMood > 0) tapGrid else frames[frameTick % frames.size]
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .offset(y = bobDp)
            .graphicsLayer {
                translationY = -hopAnim.value * 16f
                scaleX = 1f + hopAnim.value * 0.10f
                scaleY = 1f - hopAnim.value * 0.05f
            }
            .let { base ->
                if (interactive) {
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
            drawPixelGrid(this, activeRows, tint)

            // Cute floating Zzz above sleepy cat when snoozing
            if (kind == PixelMascotKind.PEEKING_CAT && tappedMood == 0 && animEnabled) {
                val p = (sin(bobPhase) * 0.5f + 0.5f)
                val zAlpha = 0.45f + 0.50f * p
                val u = this.size.width / 22f
                val zColor = RawColors.TextPrimary.copy(alpha = zAlpha)
                val zx = this.size.width * 0.72f
                val zy = this.size.height * (0.14f - p * 0.08f)
                drawRect(zColor, Offset(zx, zy), Size(u * 2.1f, u * 0.55f))
                drawRect(zColor, Offset(zx + u * 0.7f, zy + u * 0.65f), Size(u * 0.7f, u * 0.55f))
                drawRect(zColor, Offset(zx, zy + u * 1.25f), Size(u * 2.1f, u * 0.55f))
            }
        }
    }
}

/**
 * Retro pixel [T] marquee box + clicking pixel arrow cursor (straight from typography reel aesthetic).
 */
@Composable
fun PixelTypeMarqueeBadge(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    val animEnabled = rememberAnimationsEnabled()
    val inf = rememberInfiniteTransition(label = "marquee_t")
    val pulse by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    var tapped by remember { mutableIntStateOf(0) }
    val scale = if (tapped > 0) 1.07f else 1f
    val scope = rememberCoroutineScope()

    val boxGrid = listOf(
        "WW..WW..WW..WW..",
        "WW..........WW..",
        "................",
        "W..WWWWWWWWWW..W",
        "W..WWWWWWWWWW..W",
        "...W...WW...W...",
        ".......WW.......",
        "W......WW......W",
        "W......WW......W",
        ".......WW.......",
        ".......WW.......",
        "W.....WWWW.....W",
        "W..............W",
        "................",
        "WW..WW..WW..WW..",
        "WW..........WW.."
    )

    val cursorGrid = listOf(
        "W.......",
        "WW......",
        "WWD.....",
        "WWDD....",
        "WWDDD...",
        "WWDDDD..",
        "WWWWW...",
        "..WW....",
        "...WW..."
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                tapped = 1
                scope.launch {
                    delay(260)
                    tapped = 0
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val mainArea = this.size.width * 0.78f
            val u = mainArea / 16f
            val ox = this.size.width * 0.04f
            val oy = this.size.height * 0.08f
            for (r in boxGrid.indices) {
                val row = boxGrid[r]
                for (c in row.indices) {
                    if (row[c] == 'W') {
                        drawRect(
                            color = PE_WHITE,
                            topLeft = Offset(ox + c * u, oy + r * u),
                            size = Size(u + 0.35f, u + 0.35f)
                        )
                    }
                }
            }

            // Animated tapping white pixel arrow cursor overlapping lower-right corner
            val clickDelta = if (animEnabled) pulse * 4.5f else 0f
            val cu = this.size.width * 0.046f
            val cx = this.size.width * 0.62f - clickDelta
            val cy = this.size.height * 0.56f - clickDelta
            for (r in cursorGrid.indices) {
                val row = cursorGrid[r]
                for (c in row.indices) {
                    val col = when (row[c]) {
                        'W' -> PE_WHITE
                        'D' -> PE_DARK
                        else -> continue
                    }
                    drawRect(
                        color = col,
                        topLeft = Offset(cx + c * cu, cy + r * cu),
                        size = Size(cu + 0.35f, cu + 0.35f)
                    )
                }
            }
        }
    }
}

/**
 * Twinkling white 4-point retro pixel sparkle star.
 */
@Composable
fun PixelSparkle(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    phaseOffsetMs: Int = 0
) {
    val animEnabled = rememberAnimationsEnabled()
    val inf = rememberInfiniteTransition(label = "spark_$phaseOffsetMs")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1500, delayMillis = phaseOffsetMs % 700, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "sp"
    )
    val scale = if (animEnabled) 0.72f + 0.42f * phase else 1f
    val alpha = if (animEnabled) 0.45f + 0.55f * phase else 0.9f

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    ) {
        val u = this.size.width / 5f
        val star = listOf(
            "..W..",
            "..W..",
            "WWWWW",
            "..W..",
            "..W.."
        )
        for (r in 0..4) {
            for (c in 0..4) {
                if (star[r][c] == 'W') {
                    drawRect(PE_WHITE, Offset(c * u, r * u), Size(u + 0.3f, u + 0.3f))
                }
            }
        }
    }
}
