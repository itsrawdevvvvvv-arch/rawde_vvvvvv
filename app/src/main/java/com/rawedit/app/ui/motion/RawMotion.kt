package com.rawedit.app.ui.motion

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

object RawEasings {
    val Morph = CubicBezierEasing(0.22f, 0.90f, 0.24f, 1.0f)
    val SnappyOut = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)
    val OvershootSettle = CubicBezierEasing(0.22f, 1.25f, 0.36f, 1.0f)
}

private object AnimSettingCache {
    @Volatile
    private var cachedEnabled: Boolean? = null

    fun isEnabled(context: android.content.Context): Boolean {
        cachedEnabled?.let { return it }
        val scale = runCatching {
            Settings.Global.getFloat(
                context.applicationContext.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        return (scale > 0f).also { cachedEnabled = it }
    }
}

@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { AnimSettingCache.isEnabled(context) }
}

/**
 * Ultra-lightweight GPU spring press feedback using graphicsLayer only (zero layout work).
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
    val scaleState: State<Float> = animateFloatAsState(
        targetValue = if (pressed && enabled && animEnabled) 0.968f else 1f,
        animationSpec = if (animEnabled) {
            spring(dampingRatio = 0.72f, stiffness = 480f)
        } else {
            tween(0)
        },
        label = "press_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
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
 * Crisp morphing swap for dynamic button captions and status tags.
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
                (fadeIn(tween(180, easing = RawEasings.Morph)) +
                    scaleIn(initialScale = 0.93f, animationSpec = tween(180, easing = RawEasings.Morph)))
                    .togetherWith(
                        fadeOut(tween(120)) +
                            scaleOut(targetScale = 0.93f, animationSpec = tween(120))
                    )
            }
        },
        label = "morph_text"
    ) { target ->
        Text(text = target, style = style, color = color, maxLines = 1, softWrap = false)
    }
}

/**
 * Word-staggered kinetic headline powered by ONE shared Animatable and GPU graphicsLayer
 * (eliminates N parallel coroutines per heading for lag-free screen opens).
 */
@Composable
fun KineticHeading(
    words: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = RawTypography.headlineLarge,
    highlightLastWord: Boolean = false,
    triggerKey: Any = words
) {
    val animEnabled = rememberAnimationsEnabled()
    val master = remember(triggerKey) { Animatable(if (animEnabled) 0f else 1f) }

    LaunchedEffect(triggerKey, animEnabled) {
        if (animEnabled) {
            master.snapTo(0f)
            master.animateTo(1f, tween(durationMillis = 480, easing = RawEasings.Morph))
        } else {
            master.snapTo(1f)
        }
    }

    val count = words.size.coerceAtLeast(1)
    val safeLineHeight = if (style.fontSize.isSp) (style.fontSize.value * 1.08f).sp else style.lineHeight

    Layout(
        modifier = modifier,
        content = {
            words.forEachIndexed { idx, word ->
                val startAt = (idx.toFloat() / (count + 1.5f)).coerceAtMost(0.45f)
                val span = (1f - startAt).coerceAtLeast(0.35f)
                val isLast = idx == words.lastIndex
                val wordColor = if (highlightLastWord && isLast) RawColors.Mint else style.color
                Box(modifier = Modifier.clipToBounds()) {
                    Text(
                        text = word,
                        style = style.copy(lineHeight = safeLineHeight),
                        color = wordColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .graphicsLayer {
                                val p = ((master.value - startAt) / span).coerceIn(0f, 1f)
                                translationY = (1f - p) * 24f
                                alpha = p
                            }
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val hGap = 9.dp.roundToPx()
        val vGap = 2.dp.roundToPx()
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }

        val rows = ArrayList<MutableList<androidx.compose.ui.layout.Placeable>>()
        val rowWidths = ArrayList<Int>()
        val rowHeights = ArrayList<Int>()
        var curRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var curW = 0
        var curH = 0

        for (p in placeables) {
            val nextW = if (curRow.isEmpty()) p.width else curW + hGap + p.width
            if (curRow.isNotEmpty() && nextW > maxW) {
                rows += curRow
                rowWidths += curW
                rowHeights += curH
                curRow = mutableListOf(p)
                curW = p.width
                curH = p.height
            } else {
                curRow += p
                curW = nextW
                if (p.height > curH) curH = p.height
            }
        }
        if (curRow.isNotEmpty()) {
            rows += curRow
            rowWidths += curW
            rowHeights += curH
        }

        val totalW = (rowWidths.maxOrNull() ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val totalH = (rowHeights.sum() + (rows.size - 1).coerceAtLeast(0) * vGap)
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(totalW, totalH) {
            var y = 0
            rows.forEachIndexed { rIdx, row ->
                val rh = rowHeights[rIdx]
                var x = 0
                for (p in row) {
                    p.placeRelative(x, y + (rh - p.height) / 2)
                    x += p.width + hGap
                }
                y += rh + vGap
            }
        }
    }
}

/**
 * Lightweight GPU entrance wrapper for card groups.
 */
@Composable
fun CascadeSkewCard(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    val progress = remember { Animatable(if (animEnabled && index < 6) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (animEnabled && index < 6 && progress.value < 1f) {
            if (index > 0) delay(index * 36L)
            progress.animateTo(1f, tween(320, easing = RawEasings.Morph))
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            translationY = (1f - p) * 20f
            alpha = p.coerceIn(0f, 1f)
        }
    ) {
        content()
    }
}

/**
 * Single-Canvas fluid Mint pager dots (7dp -> 24dp expanding Mint pill) linked to finger drag.
 * Zero per-dot composables or per-dot animation state allocations.
 */
@Composable
fun LiquidPagerDots(
    totalCount: Int,
    currentIndex: Int,
    pageOffsetFraction: Float = 0f,
    modifier: Modifier = Modifier
) {
    val count = totalCount.coerceAtLeast(1)
    val dotHeightDp = 7.dp
    val maxPillDp = 24.dp
    val gapDp = 7.dp
    val totalWidthDp = (dotHeightDp * (count - 1)) + maxPillDp + (gapDp * (count - 1)) + 2.dp

    Canvas(modifier = modifier.width(totalWidthDp).height(dotHeightDp)) {
        val h = size.height
        val minW = dotHeightDp.toPx()
        val maxW = maxPillDp.toPx()
        val gap = gapDp.toPx()
        val radius = h * 0.5f
        val activePos = (currentIndex + pageOffsetFraction).coerceIn(0f, (count - 1).toFloat())

        var curX = 0f
        for (i in 0 until count) {
            val closeness = (1f - abs(activePos - i)).coerceIn(0f, 1f)
            val w = minW + (maxW - minW) * closeness
            val col = lerp(RawColors.PillGrey, RawColors.Mint, closeness)
            drawRoundRect(
                color = col,
                topLeft = Offset(curX, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(radius, radius)
            )
            curX += w + gap
        }
    }
}

/**
 * Signature Mint Swipe-to-create control (#sl2 in mockup):
 * Track fill, pulsing >>> chevrons, and knob travel all run inside draw/layer phases so dragging
 * produces zero recompositions even on older Android 9 GPUs.
 */
@Composable
fun SwipeToCreateSlider(
    unlocked: Boolean,
    rightTag: String = "1080p",
    onTriggered: (tapCenter: Offset) -> Unit,
    onLockedTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()

    var widthPx by remember { mutableFloatStateOf(1f) }
    val knobSizeDp = 52.dp
    val density = LocalDensity.current
    val padPx = with(density) { 4.dp.toPx() }
    val knobPx = with(density) { knobSizeDp.toPx() }
    val maxTravelPx = (widthPx - knobPx - padPx * 2f).coerceAtLeast(1f)

    val ratioAnim = remember { Animatable(0f) }
    var triggered by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }

    val chevronPhaseState: State<Float>? = if (unlocked && animEnabled) {
        val infTransition = rememberInfiniteTransition(label = "chevrons")
        infTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "chevron_wave"
        )
    } else {
        null
    }

    val pastMidpoint by remember(unlocked) {
        derivedStateOf { unlocked && ratioAnim.value > 0.35f }
    }
    val showReleaseLabel by remember(unlocked) {
        derivedStateOf { unlocked && ratioAnim.value > 0.78f }
    }
    val showRightTag by remember(unlocked) {
        derivedStateOf { unlocked && ratioAnim.value < 0.55f }
    }

    fun triggerLockedBump() {
        onLockedTap?.invoke()
        if (!animEnabled) return
        scope.launch {
            val hops = listOf(-14f, 14f, -8f, 6f, 0f)
            for (h in hops) {
                shakeOffset.animateTo(h, tween(55))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .graphicsLayer { translationX = shakeOffset.value }
            .clip(RoundedCornerShape(16.dp))
            .background(RawColors.RaisedCard)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
            .onSizeChanged { widthPx = it.width.toFloat() }
            .then(
                if (!unlocked) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { triggerLockedBump() }
                } else Modifier
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // GPU Draw-phase trail & chevrons (ZERO recompositions while dragging or idling!)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ratio = ratioAnim.value
            val fillWidth = (knobPx + padPx * 2f + maxTravelPx * ratio).coerceIn(knobPx, size.width)
            if (unlocked && ratio > 0.01f) {
                drawRoundRect(
                    color = RawColors.Mint,
                    topLeft = Offset.Zero,
                    size = Size(fillWidth, size.height),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            }
        }

        // Centered label + Mint >>> chevrons + right duration pill
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 62.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Canvas(modifier = Modifier.width(24.dp).height(14.dp)) {
                    val cy = size.height * 0.5f
                    val arm = 3.6.dp.toPx()
                    val step = 7.dp.toPx()
                    val phase = chevronPhaseState?.value ?: 0f
                    val ratio = ratioAnim.value
                    val baseCol = if (unlocked) {
                        if (ratio > 0.35f) RawColors.PrimaryBtnText else RawColors.Mint
                    } else {
                        Color(0xFF6E746E)
                    }
                    for (c in 0..2) {
                        val wave = ((phase * 3f - c + 3f) % 3f)
                        val a = if (unlocked) (1f - wave / 2f).coerceIn(0.28f, 1f) else 0.5f
                        val cx = 4.dp.toPx() + c * step
                        drawLine(baseCol.copy(alpha = a), Offset(cx - arm, cy - arm), Offset(cx, cy), 1.8.dp.toPx(), StrokeCap.Square)
                        drawLine(baseCol.copy(alpha = a), Offset(cx, cy), Offset(cx - arm, cy + arm), 1.8.dp.toPx(), StrokeCap.Square)
                    }
                }

                val labelText = if (showReleaseLabel) "Release to create" else "Swipe to create"
                Text(
                    text = labelText,
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = when {
                        !unlocked -> Color(0xFF6E746E)
                        pastMidpoint -> RawColors.PrimaryBtnText
                        else -> RawColors.TextMuted
                    }
                )
            }

            if (showRightTag) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RawColors.PillGrey)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = rightTag,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        color = RawColors.TextPrimary
                    )
                }
            }
        }

        // Circular Draggable Knob moved via GPU graphicsLayer (0 relayout, 0 recompose!)
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = ratioAnim.value * maxTravelPx }
                .size(knobSizeDp)
                .clip(CircleShape)
                .background(if (unlocked) RawColors.Mint else RawColors.PillGrey)
                .pointerInput(unlocked, maxTravelPx) {
                    if (!unlocked) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { triggered = false },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!triggered) {
                                val next = (ratioAnim.value + dragAmount / maxTravelPx).coerceIn(0f, 1f)
                                scope.launch { ratioAnim.snapTo(next) }
                                if (next >= 0.85f && !triggered) {
                                    triggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTriggered(Offset(widthPx * 0.85f, knobPx))
                                }
                            }
                        },
                        onDragEnd = {
                            if (!triggered) {
                                scope.launch {
                                    if (animEnabled) {
                                        ratioAnim.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 360f))
                                    } else {
                                        ratioAnim.snapTo(0f)
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            if (!triggered) {
                                scope.launch { ratioAnim.snapTo(0f) }
                            }
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
 * Pure GPU screen transition matching raw_edit.html cubic-bezier(.22,.9,.24,1).
 * Uses ONLY translation + scale + alpha (no Compose blur) for 60/120fps on Android 9+.
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
                        tween(280, easing = RawEasings.Morph)
                    ) { (it * 0.22f).roundToInt() } +
                        fadeIn(tween(210, easing = RawEasings.Morph)),
                    initialContentExit = slideOutHorizontally(
                        tween(250, easing = RawEasings.Morph)
                    ) { -(it * 0.12f).roundToInt() } +
                        fadeOut(tween(180)),
                    targetContentZIndex = 1f
                )
            } else {
                ContentTransform(
                    targetContentEnter = slideInHorizontally(
                        tween(260, easing = RawEasings.Morph)
                    ) { -(it * 0.12f).roundToInt() } +
                        fadeIn(tween(200, easing = RawEasings.Morph)),
                    initialContentExit = slideOutHorizontally(
                        tween(240, easing = RawEasings.Morph)
                    ) { (it * 0.22f).roundToInt() } +
                        fadeOut(tween(180)),
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
 * Directional glide + micro-scale transition between dock tabs.
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
                (slideInHorizontally(tween(220, easing = RawEasings.Morph)) { (it * 0.06f * enterSlideDir).roundToInt() } +
                    fadeIn(tween(190, easing = RawEasings.Morph)))
                    .togetherWith(
                        slideOutHorizontally(tween(180, easing = RawEasings.Morph)) { (it * 0.04f * exitSlideDir).roundToInt() } +
                            fadeOut(tween(150))
                    )
            }
        },
        label = "raw_tab_morph"
    ) { tab ->
        content(tab)
    }
}

private data class ConfettiPiece(
    val xFrac: Float,
    val startY: Float,
    val speed: Float,
    val sizeDp: Float,
    val alpha: Float,
    val isMint: Boolean
)

/**
 * Crisp retro square pixel confetti matching #cf in raw_edit.html, allocated ONCE and
 * auto-stopping cleanly after 1.9 seconds so idle Result screen consumes 0% GPU.
 */
@Composable
fun ResultCelebrationOverlay(
    play: Boolean,
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    if (!play || !animEnabled) return

    val particles = remember {
        List(56) { i ->
            val seed = (i * 9301 + 49297) % 233280
            val r1 = seed / 233280f
            val r2 = ((seed * 7 + 13) % 997) / 997f
            ConfettiPiece(
                xFrac = ((i + 0.5f) / 56f + (r1 - 0.5f) * 0.06f).coerceIn(0.02f, 0.98f),
                startY = -r2 * 0.42f,
                speed = 0.75f + r1 * 0.65f,
                sizeDp = 4f + (i % 4) * 1.6f,
                alpha = if (i % 3 == 0) 0.9f else 0.45f,
                isMint = i % 5 == 0
            )
        }
    }

    val progress = remember { Animatable(0f) }
    var finished by remember(play) { mutableStateOf(false) }
    LaunchedEffect(play) {
        finished = false
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1900, easing = LinearEasing))
        finished = true
    }

    if (finished) return

    Canvas(modifier = modifier) {
        val p = progress.value
        val w = size.width
        val h = size.height
        val fadeOut = (1f - ((p - 0.65f) / 0.35f).coerceIn(0f, 1f))
        val densityPx = density

        for (pt in particles) {
            val yNorm = pt.startY + p * pt.speed * 1.35f
            if (yNorm < -0.05f || yNorm > 1.02f) continue
            val yPx = yNorm * h
            val xPx = pt.xFrac * w + sin(yPx / 38f) * 10f
            val side = pt.sizeDp * densityPx
            val col = (if (pt.isMint) RawColors.Mint else Color.White).copy(alpha = pt.alpha * fadeOut)
            drawRect(
                color = col,
                topLeft = Offset(xPx, yPx),
                size = Size(side, side)
            )
        }
    }
}
