package com.rawedit.app.ui.motion

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object RawEasings {
    val Morph = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    val SnappyOut = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
    val OvershootSettle = CubicBezierEasing(0.22f, 1.32f, 0.36f, 1.0f)
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
 * Tactile spring press micro-interaction for cards and buttons.
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
        targetValue = if (pressed && enabled && animEnabled) 0.96f else 1f,
        animationSpec = if (animEnabled) {
            spring(dampingRatio = 0.68f, stiffness = 420f)
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
 * Smooth morphing swap for labels and small icon badges.
 */
@Composable
fun SplitFlapText(
    text: String,
    style: TextStyle,
    color: Color
) {
    val animEnabled = rememberAnimationsEnabled()
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (!animEnabled) {
                ContentTransform(fadeIn(tween(0)), fadeOut(tween(0)))
            } else {
                (fadeIn(tween(200, easing = RawEasings.Morph)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(220, easing = RawEasings.Morph)))
                    .togetherWith(
                        fadeOut(tween(140)) +
                            scaleOut(targetScale = 0.92f, animationSpec = tween(140))
                    )
            }
        },
        label = "morph_text"
    ) { target ->
        Text(text = target, style = style, color = color)
    }
}

/**
 * Smooth kinetic display heading (gentle upward slide + opacity settle).
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
                    delay(idx * 40L)
                    progress.animateTo(1f, tween(durationMillis = 360, easing = RawEasings.Morph))
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
                        translationY = (1f - p) * 26f
                        scaleX = 0.94f + 0.06f * p
                        scaleY = 0.94f + 0.06f * p
                        alpha = p.coerceIn(0f, 1f)
                    }
                )
            }
        }
    }
}

/**
 * Smooth vertical reveal card for workspace / step lists.
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
            delay((index.coerceAtMost(6)) * 35L)
            progress.animateTo(1f, tween(340, easing = RawEasings.Morph))
        } else {
            progress.snapTo(1f)
        }
    }
    val p = progress.value
    Box(
        modifier = modifier.graphicsLayer {
            translationY = (1f - p) * 24f
            scaleX = 0.97f + 0.03f * p
            scaleY = 0.97f + 0.03f * p
            alpha = p.coerceIn(0f, 1f)
        }
    ) {
        content()
    }
}

/**
 * Continuous fluid pager dots linked directly to finger drag & page transitions.
 */
@Composable
fun LiquidPagerDots(
    totalCount: Int,
    currentIndex: Int,
    pageOffsetFraction: Float = 0f,
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalCount) {
            val activePos = (currentIndex + pageOffsetFraction).coerceIn(0f, (totalCount - 1).toFloat())
            val dist = kotlin.math.abs(activePos - i).coerceIn(0f, 1f)
            val closeness = 1f - dist

            val width by animateDpAsState(
                targetValue = (7f + 19f * closeness).dp,
                animationSpec = if (animEnabled && pageOffsetFraction == 0f) {
                    spring(dampingRatio = 0.72f, stiffness = 380f)
                } else {
                    tween(0)
                },
                label = "dot_w_$i"
            )
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        RawColors.TextPrimary.copy(alpha = 0.25f + 0.75f * closeness)
                    )
            )
        }
    }
}

/**
 * Minimal capsule Swipe-to-create control matching the clean bottom unlock bar aesthetic.
 */
@Composable
fun SwipeToCreateSlider(
    unlocked: Boolean,
    rightTag: String = "1080p",
    onTriggered: (tapCenter: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val animEnabled = rememberAnimationsEnabled()
    var widthPx by remember { mutableFloatStateOf(1f) }
    val knobSizeDp = 50.dp
    val density = LocalDensity.current
    val knobPx = with(density) { knobSizeDp.toPx() }
    val maxTravelPx = (widthPx - knobPx - with(density) { 12.dp.toPx() }).coerceAtLeast(1f)
    var dragRatio by remember { mutableFloatStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }

    val animatedRatio by animateFloatAsState(
        targetValue = dragRatio,
        animationSpec = if (animEnabled) spring(dampingRatio = 0.75f, stiffness = 360f) else tween(0),
        label = "swipe_ratio"
    )
    val effectiveRatio = if (dragRatio > 0f) dragRatio else animatedRatio

    val infTransition = rememberInfiniteTransition(label = "chevrons")
    val chevronPhase by infTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "chevron_wave"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(if (unlocked) RawColors.HeroSurface else RawColors.CardSurface)
            .border(
                1.dp,
                if (unlocked) RawColors.PillGrey.copy(alpha = 0.85f) else RawColors.Divider,
                RoundedCornerShape(31.dp)
            )
            .onSizeChanged { widthPx = it.width.toFloat() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Fill trail & pulsing chevron tracks
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fillWidth = (knobPx * 0.5f + maxTravelPx * effectiveRatio).coerceIn(0f, size.width)
            if (unlocked && effectiveRatio > 0.02f) {
                drawRoundRect(
                    color = RawColors.TextPrimary.copy(alpha = 0.94f),
                    topLeft = Offset(0f, 4.dp.toPx()),
                    size = Size(fillWidth, size.height - 8.dp.toPx()),
                    cornerRadius = CornerRadius(27.dp.toPx())
                )
            }
            if (unlocked && effectiveRatio < 0.45f) {
                val chevStart = size.width * 0.65f
                val chevStep = 10.dp.toPx()
                for (c in 0..2) {
                    val wave = ((chevronPhase * 3f - c + 3f) % 3f)
                    val a = (1f - (wave / 2f)).coerceIn(0.18f, 0.85f)
                    val cx = chevStart + c * chevStep
                    val cy = size.height * 0.5f
                    val arm = 4.dp.toPx()
                    drawLine(
                        color = RawColors.TextMuted.copy(alpha = a),
                        start = Offset(cx - arm, cy - arm),
                        end = Offset(cx, cy),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = RawColors.TextMuted.copy(alpha = a),
                        start = Offset(cx, cy),
                        end = Offset(cx - arm, cy + arm),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 60.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val labelText = when {
                !unlocked -> "Complete steps 1 & 3"
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
                modifier = Modifier.alpha(if (unlocked) 1f else 0.75f)
            )
            if (unlocked && effectiveRatio < 0.6f) {
                Text(
                    text = rightTag,
                    style = RawTypography.labelMedium,
                    color = RawColors.TextMuted
                )
            }
        }

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
                                if (next >= 0.93f && !triggered) {
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
                size = 20.dp
            )
        }
    }
}

/**
 * Silky directional morphing transition for push / pop screens.
 */
@Composable
fun <T> RawScreenTransition(
    targetState: T,
    isForward: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    AnimatedContent(
        targetState = targetState,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            if (!animEnabled) {
                ContentTransform(fadeIn(tween(0)), fadeOut(tween(0)))
            } else if (isForward) {
                ContentTransform(
                    targetContentEnter = slideInHorizontally(
                        tween(360, easing = RawEasings.Morph)
                    ) { (it * 0.14f).roundToInt() } +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(360, easing = RawEasings.Morph)) +
                        fadeIn(tween(260, easing = RawEasings.Morph)),
                    initialContentExit = slideOutHorizontally(
                        tween(300, easing = RawEasings.Morph)
                    ) { -(it * 0.06f).roundToInt() } +
                        scaleOut(targetScale = 0.965f, animationSpec = tween(300, easing = RawEasings.Morph)) +
                        fadeOut(tween(200)),
                    targetContentZIndex = 1f
                )
            } else {
                ContentTransform(
                    targetContentEnter = slideInHorizontally(
                        tween(340, easing = RawEasings.Morph)
                    ) { -(it * 0.08f).roundToInt() } +
                        scaleIn(initialScale = 0.97f, animationSpec = tween(340, easing = RawEasings.Morph)) +
                        fadeIn(tween(260, easing = RawEasings.Morph)),
                    initialContentExit = slideOutHorizontally(
                        tween(290, easing = RawEasings.Morph)
                    ) { (it * 0.16f).roundToInt() } +
                        scaleOut(targetScale = 0.955f, animationSpec = tween(290, easing = RawEasings.Morph)) +
                        fadeOut(tween(200)),
                    targetContentZIndex = 0f
                )
            }
        },
        label = "raw_morph_screen"
    ) { state ->
        content(state)
    }
}

/**
 * Fluid directional glide + micro-scale morph between main bottom-nav tabs.
 */
@Composable
fun <T : Enum<T>> RawTabMorphTransition(
    targetTab: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    AnimatedContent(
        targetState = targetTab,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            if (!animEnabled) {
                ContentTransform(fadeIn(tween(0)), fadeOut(tween(0)))
            } else {
                val movingRight = targetState.ordinal >= initialState.ordinal
                val enterSlideDir = if (movingRight) 1 else -1
                val exitSlideDir = -enterSlideDir
                (slideInHorizontally(tween(320, easing = RawEasings.Morph)) { (it * 0.075f * enterSlideDir).roundToInt() } +
                    scaleIn(initialScale = 0.98f, animationSpec = tween(320, easing = RawEasings.Morph)) +
                    fadeIn(tween(260, easing = RawEasings.Morph)))
                    .togetherWith(
                        slideOutHorizontally(tween(260, easing = RawEasings.Morph)) { (it * 0.055f * exitSlideDir).roundToInt() } +
                            scaleOut(targetScale = 0.985f, animationSpec = tween(260, easing = RawEasings.Morph)) +
                            fadeOut(tween(190))
                    )
            }
        },
        label = "raw_tab_morph"
    ) { tab ->
        content(tab)
    }
}

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
        progress.animateTo(1f, tween(durationMillis = 900, easing = RawEasings.Morph))
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
