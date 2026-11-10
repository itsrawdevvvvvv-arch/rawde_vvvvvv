package com.rawedit.app.ui.motion

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Custom easing set documented per section 4:
 * - SnappyOut: crisp typographic arrival with strong deceleration.
 * - OvershootSettle: slight bounce overshoot for headline word drops.
 * - InkSpread: accelerates early then settles smoothly for circular bloom reveals.
 */
object RawEasings {
    val SnappyOut = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
    val OvershootSettle = CubicBezierEasing(0.22f, 1.35f, 0.36f, 1.0f)
    val InkSpread = CubicBezierEasing(0.20f, 0.85f, 0.25f, 1.0f)
}

@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        scale > 0f
    }
}

/**
 * Tactile press scale (quick squash with slight overshoot release).
 */
@Composable
fun RawPressButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && animEnabled) 0.94f else 1f,
        animationSpec = if (animEnabled) {
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
        } else {
            tween(0)
        },
        label = "press_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        content()
    }
}

/**
 * Split-flap style label swap (e.g. "Copy prompt" -> "Copied").
 */
@Composable
fun SplitFlapText(
    text: String,
    style: TextStyle,
    color: Color
) {
    val animEnabled = rememberAnimationsEnabled()
    var shownText by remember { mutableStateOf(text) }
    val flip = remember { Animatable(0f) }

    LaunchedEffect(text, animEnabled) {
        if (text != shownText) {
            if (animEnabled) {
                flip.animateTo(90f, tween(130, easing = RawEasings.SnappyOut))
                shownText = text
                flip.snapTo(-90f)
                flip.animateTo(0f, tween(160, easing = RawEasings.OvershootSettle))
            } else {
                shownText = text
            }
        }
    }

    Text(
        text = shownText,
        style = style,
        color = color,
        modifier = Modifier.graphicsLayer {
            rotationX = flip.value
            cameraDistance = 10f * density
        }
    )
}

/**
 * Kinetic headline: each word animates with varied physics (drop overshoot, mask slide, point scale).
 */
@Composable
fun KineticHeading(
    words: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = RawTypography.headlineLarge,
    triggerKey: Any = words
) {
    val animEnabled = rememberAnimationsEnabled()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        words.forEachIndexed { idx, word ->
            val progress = remember(triggerKey, idx) { Animatable(if (animEnabled) 0f else 1f) }
            LaunchedEffect(triggerKey, animEnabled) {
                if (animEnabled) {
                    progress.snapTo(0f)
                    delay(idx * 55L)
                    val easing = when (idx % 3) {
                        0 -> RawEasings.OvershootSettle
                        1 -> RawEasings.SnappyOut
                        else -> RawEasings.InkSpread
                    }
                    progress.animateTo(1f, tween(durationMillis = 360, easing = easing))
                } else {
                    progress.snapTo(1f)
                }
            }
            val p = progress.value
            Box(modifier = Modifier.clipToBounds()) {
                Text(
                    text = word,
                    style = style,
                    modifier = Modifier.graphicsLayer {
                        when (idx % 3) {
                            0 -> {
                                translationY = (1f - p) * -42f
                                alpha = p.coerceIn(0f, 1f)
                            }
                            1 -> {
                                translationX = (1f - p) * 48f
                                alpha = p.coerceIn(0f, 1f)
                            }
                            else -> {
                                val sc = 0.65f + 0.35f * p
                                scaleX = sc
                                scaleY = sc
                                alpha = p.coerceIn(0f, 1f)
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Staggered skew-settle card entrance for lists.
 */
@Composable
fun CascadeSkewCard(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    val progress = remember(index) { Animatable(if (animEnabled) 0f else 1f) }

    LaunchedEffect(index, animEnabled) {
        if (animEnabled) {
            progress.snapTo(0f)
            delay((index.coerceAtMost(8)) * 45L)
            progress.animateTo(1f, tween(340, easing = RawEasings.SnappyOut))
        } else {
            progress.snapTo(1f)
        }
    }
    val p = progress.value
    Box(
        modifier = modifier.graphicsLayer {
            translationY = (1f - p) * 34f
            rotationZ = (1f - p) * if (index % 2 == 0) -2.2f else 2.2f
            alpha = p.coerceIn(0f, 1f)
        }
    ) {
        content()
    }
}

/**
 * Stretching liquid pager dots (active dot stretches cleanly during selection).
 */
@Composable
fun LiquidPagerDots(
    totalCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalCount) {
            val selected = i == currentIndex
            val width by animateDpAsState(
                targetValue = if (selected) 26.dp else 8.dp,
                animationSpec = if (animEnabled) {
                    spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
                } else {
                    tween(0)
                },
                label = "dot_w_$i"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (selected) RawColors.TextPrimary else RawColors.PillGrey)
            )
        }
    }
}

/**
 * Swipe-to-create slider bar (trailing bars become fill, snaps at 100% into ink-bloom).
 */
@Composable
fun SwipeToCreateSlider(
    unlocked: Boolean,
    onTriggered: (tapCenter: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val animEnabled = rememberAnimationsEnabled()
    var widthPx by remember { mutableFloatStateOf(1f) }
    val knobSizeDp = 56.dp
    val density = LocalDensity.current
    val knobPx = with(density) { knobSizeDp.toPx() }
    val maxTravelPx = (widthPx - knobPx - with(density) { 12.dp.toPx() }).coerceAtLeast(1f)
    var dragRatio by remember { mutableFloatStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }

    val animatedRatio by animateFloatAsState(
        targetValue = dragRatio,
        animationSpec = if (animEnabled) spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium) else tween(0),
        label = "swipe_ratio"
    )
    val effectiveRatio = if (dragRatio > 0f) dragRatio else animatedRatio

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(if (unlocked) RawColors.HeroSurface else RawColors.CardSurface)
            .border(1.dp, if (unlocked) RawColors.PillGrey else RawColors.Divider, RoundedCornerShape(34.dp))
            .onSizeChanged { widthPx = it.width.toFloat() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Trail of small bars + growing white fill
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fillWidth = (knobPx * 0.5f + maxTravelPx * effectiveRatio).coerceIn(0f, size.width)
            if (unlocked && effectiveRatio > 0.02f) {
                drawRoundRect(
                    color = RawColors.TextPrimary.copy(alpha = 0.92f),
                    topLeft = Offset(0f, 6.dp.toPx()),
                    size = Size(fillWidth, size.height - 12.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(30.dp.toPx())
                )
            }
            // Trailing rhythmic bars ahead of knob
            val barCount = 10
            val startX = knobPx + 22f
            val endX = size.width - 28f
            val step = ((endX - startX) / barCount).coerceAtLeast(8f)
            for (b in 0 until barCount) {
                val bx = startX + b * step
                if (bx > fillWidth) {
                    val barH = if (b % 2 == 0) 14.dp.toPx() else 9.dp.toPx()
                    drawLine(
                        color = if (unlocked) RawColors.PillGrey else RawColors.Divider,
                        start = Offset(bx, (size.height - barH) * 0.5f),
                        end = Offset(bx, (size.height + barH) * 0.5f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Prompt label
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val labelText = when {
                !unlocked -> "Add voice and code first"
                effectiveRatio > 0.72f -> "Release to render"
                else -> "Swipe to create"
            }
            Text(
                text = labelText,
                style = RawTypography.labelLarge,
                color = when {
                    !unlocked -> RawColors.TextMuted
                    effectiveRatio > 0.45f -> RawColors.PrimaryBtnText
                    else -> RawColors.TextPrimary
                },
                modifier = Modifier.alpha(if (unlocked) 1f else 0.7f)
            )
        }

        // Draggable knob
        val knobOffsetPx = (effectiveRatio * maxTravelPx).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffsetPx, 0) }
                .size(knobSizeDp)
                .clip(CircleShape)
                .background(if (unlocked) RawColors.TextPrimary else RawColors.RaisedCard)
                .pointerInput(unlocked, maxTravelPx) {
                    if (!unlocked) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { triggered = false },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!triggered) {
                                val next = (dragRatio + dragAmount / maxTravelPx).coerceIn(0f, 1f)
                                dragRatio = next
                                if (next >= 0.94f && !triggered) {
                                    triggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTriggered(Offset(widthPx * 0.85f, knobPx))
                                }
                            }
                        },
                        onDragEnd = {
                            if (!triggered) dragRatio = 0f
                        },
                        onDragCancel = {
                            if (!triggered) dragRatio = 0f
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            RawIcon(
                type = if (unlocked) RawIconType.ARROW_RIGHT else RawIconType.LOCK,
                tint = if (unlocked) RawColors.PrimaryBtnText else RawColors.TextMuted,
                size = 22.dp
            )
        }
    }
}

/**
 * Ghost-word wipe transition when switching top-level tabs ("HOME", "CREATE", "REELS", "PROFILE").
 */
@Composable
fun GhostWordWipeOverlay(
    word: String?,
    triggerKey: Int
) {
    val animEnabled = rememberAnimationsEnabled()
    if (!animEnabled || word.isNullOrBlank() || triggerKey == 0) return

    val progress = remember(triggerKey) { Animatable(0f) }
    LaunchedEffect(triggerKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 380, easing = RawEasings.SnappyOut))
    }

    val p = progress.value
    if (p < 0.99f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = word,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 112.sp,
                letterSpacing = (-3).sp,
                color = RawColors.TextPrimary.copy(alpha = (1f - p) * 0.14f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    translationX = (0.5f - p) * 420f
                }
            )
        }
    }
}

/**
 * Smooth, zero-flash screen transition tuned for 60fps on Android 9+ low-end devices.
 * Avoids black-screen flashes and heavy per-frame path clipping.
 */
@Composable
fun <T> RawScreenTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    AnimatedContent(
        targetState = targetState,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            if (!animEnabled) {
                ContentTransform(
                    targetContentEnter = fadeIn(tween(0)),
                    initialContentExit = fadeOut(tween(0))
                )
            } else {
                (fadeIn(tween(220, easing = RawEasings.SnappyOut)) +
                    scaleIn(initialScale = 0.965f, animationSpec = tween(240, easing = RawEasings.SnappyOut)))
                    .togetherWith(fadeOut(tween(160)))
            }
        },
        label = "raw_screen_transition"
    ) { state ->
        content(state)
    }
}

/**
 * Result celebration: snapping corner brackets + drawn geometric confetti lines/dots (no emoji).
 */
@Composable
fun ResultCelebrationOverlay(
    play: Boolean,
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    if (!play || !animEnabled) return

    val progress = remember { Animatable(0f) }
    LaunchedEffect(play) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 950, easing = RawEasings.SnappyOut))
    }

    val p = progress.value
    if (p >= 0.99f) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.36f
        val alpha = (1f - p).coerceIn(0f, 1f)

        val pieces = 18
        for (i in 0 until pieces) {
            val angle = (i.toFloat() / pieces) * (2f * PI.toFloat()) + 0.2f
            val dist = (40f + (i % 3) * 45f) + p * (190f + (i % 4) * 55f)
            val px = cx + cos(angle) * dist
            val py = cy + sin(angle) * dist + p * p * 85f
            val color = when (i % 3) {
                0 -> RawColors.TextPrimary.copy(alpha = alpha)
                1 -> RawColors.SuccessMint.copy(alpha = alpha)
                else -> RawColors.TextMuted.copy(alpha = alpha)
            }
            if (i % 2 == 0) {
                val len = 14f * (1f - p * 0.4f)
                drawLine(
                    color = color,
                    start = Offset(px - cos(angle) * len, py - sin(angle) * len),
                    end = Offset(px + cos(angle) * len, py + sin(angle) * len),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            } else {
                drawCircle(color = color, radius = 4.5f * (1f - p * 0.3f), center = Offset(px, py))
            }
        }
    }
}
