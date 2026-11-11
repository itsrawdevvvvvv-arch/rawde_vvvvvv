package com.rawedit.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.PixelSparkle
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.FeatheredScrollEdges
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.TiltedAccentPill
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

private val TV_LINES = listOf(
    "Ready when you are",
    "Voice in, reel out",
    "No internet needed",
    "Typography is my love language"
)

private val HOME_STEP_CHIPS = listOf(
    "1 \u00B7 Voice",
    "2 \u00B7 Assets",
    "3 \u00B7 Style",
    "4 \u00B7 Code",
    "5 \u00B7 Reel"
)

private fun Modifier.vhRise(
    clockMs: State<Float>?,
    delayMs: Int
): Modifier = if (clockMs == null) {
    this
} else {
    graphicsLayer {
        val raw = ((clockMs.value - delayMs) / 620f).coerceIn(0f, 1f)
        val eased = RawEasings.Morph.transform(raw)
        alpha = eased
        translationY = (1f - eased) * 20.dp.toPx()
    }
}

@Composable
fun HomeScreen(
    userProfile: UserProfile,
    reelsCount: Int,
    onStartNewReel: (Offset) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    val scrollState = rememberScrollState()
    var tvIndex by remember { mutableIntStateOf(0) }

    val entranceClock = remember { Animatable(if (animEnabled) 0f else 950f) }
    val entranceState: State<Float>? = if (animEnabled) entranceClock.asState() else null
    LaunchedEffect(animEnabled) {
        if (animEnabled) {
            entranceClock.snapTo(0f)
            entranceClock.animateTo(950f, tween(durationMillis = 950, easing = LinearEasing))
        } else {
            entranceClock.snapTo(950f)
        }
    }

    val bubBobState: State<Float>? = if (animEnabled) {
        val inf = rememberInfiniteTransition(label = "vh_bub")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
            label = "vh_bob"
        )
    } else {
        null
    }

    val shimmerState: State<Float>? = if (animEnabled) {
        val inf = rememberInfiniteTransition(label = "home_shimmer")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmer_sweep"
        )
    } else {
        null
    }

    val bubbleShape = remember {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    }
    val displayName = userProfile.userName.ifBlank { "Creator" }
    val reelsWord = if (reelsCount == 1) "reel" else "reels"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val availableHeight = maxHeight
        val isCompact = availableHeight < 710.dp
        val h1Size = if (isCompact) 62.sp else 82.sp
        val headlineStyle = TextStyle(
            fontFamily = BricolageFamily,
            fontWeight = FontWeight.Bold,
            fontSize = h1Size,
            lineHeight = h1Size * 0.90f,
            letterSpacing = if (isCompact) (-2.8).sp else (-3.4).sp,
            color = RawColors.TextPrimary
        )

        Column(
            modifier = Modifier
                .widthIn(max = 430.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = availableHeight)
                    .navigationBarsPadding()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = if (isCompact) 14.dp else 28.dp,
                        bottom = 132.dp
                    ),
                horizontalAlignment = Alignment.Start
            ) {
                // Meta line (.e-meta)
                Text(
                    text = "Studio  \u00B7  $displayName  \u00B7  $reelsCount $reelsWord  \u00B7  offline",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = RawColors.TextMuted,
                    modifier = Modifier.vhRise(entranceState, 0)
                )

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 14.dp))

                // Big Type headline (.e-h1)
                Column(modifier = Modifier.vhRise(entranceState, 65)) {
                    Text(
                        text = "Make",
                        style = headlineStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
                    ) {
                        Text(
                            text = "a",
                            style = headlineStyle,
                            maxLines = 1,
                            softWrap = false
                        )
                        TiltedAccentPill(
                            text = "reel",
                            style = headlineStyle,
                            animateRock = true,
                            angleDeg = -2f,
                            backgroundColor = RawColors.Tan,
                            textColor = RawColors.Ink
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 18.dp else 26.dp))

                // Horizontal step chips (.e-chips)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .vhRise(entranceState, 130),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HOME_STEP_CHIPS.forEachIndexed { idx, chip ->
                        val active = idx == 0
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(if (active) RawColors.Tan else RawColors.RaisedCard)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chip,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = if (active) RawColors.Ink else RawColors.TextPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 18.dp))

                // Subtitle (.e-sub)
                Text(
                    text = "Voice in, reel out. Nothing leaves the phone.",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.5.sp,
                    lineHeight = 22.5.sp,
                    color = RawColors.TextMuted,
                    modifier = Modifier
                        .widthIn(max = 290.dp)
                        .vhRise(entranceState, 195)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Mascot stage (.e-stage)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompact) 152.dp else 176.dp)
                        .vhRise(entranceState, 260)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 14.dp)
                            .widthIn(max = 228.dp)
                            .graphicsLayer {
                                val phase = bubBobState?.value ?: 0f
                                val lift = ((1f - cos(phase)) * 0.5f) * 3.5.dp.toPx()
                                translationY = -lift
                            }
                            .clip(bubbleShape)
                            .background(RawColors.Cream)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { tvIndex = (tvIndex + 1) % TV_LINES.size }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        AnimatedContent(
                            targetState = TV_LINES[tvIndex],
                            transitionSpec = {
                                if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                else fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                            },
                            label = "vh_bub_text"
                        ) { line ->
                            Text(
                                text = line,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                lineHeight = 18.sp,
                                color = RawColors.Ink,
                                textAlign = TextAlign.Start
                            )
                        }
                    }

                    PixelSparkle(
                        size = 12.dp,
                        tint = RawColors.Tan,
                        phaseOffsetMs = 180,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = if (isCompact) 118.dp else 136.dp)
                    )

                    RawPixelMascot(
                        kind = PixelMascotKind.TV_BOT,
                        size = if (isCompact) 126.dp else 144.dp,
                        onTap = { tvIndex = (tvIndex + 1) % TV_LINES.size },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 2.dp)
                            .graphicsLayer { rotationZ = -8f }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Interactive slide-to-start bar (#eslide + #eknob)
                HomeSlideCreateBar(
                    shimmerState = shimmerState,
                    animEnabled = animEnabled,
                    onTriggered = onStartNewReel,
                    modifier = Modifier.vhRise(entranceState, 325)
                )
            }
        }

        FeatheredScrollEdges(
            scrollState = scrollState,
            topHeight = 0.dp,
            bottomHeight = 138.dp,
            alwaysShowBottom = true
        )
    }
}

@Composable
private fun HomeSlideCreateBar(
    shimmerState: State<Float>?,
    animEnabled: Boolean,
    onTriggered: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var widthPx by remember { mutableFloatStateOf(1f) }
    val knobSizeDp = 56.dp
    val padPx = with(density) { 5.dp.toPx() }
    val knobPx = with(density) { knobSizeDp.toPx() }
    val maxTravelPx = (widthPx - knobPx - padPx * 2f).coerceAtLeast(1f)

    val ratioAnim = remember { Animatable(0f) }
    var triggered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(CircleShape)
            .background(RawColors.RaisedCard)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (triggered) return@clickable
                triggered = true
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    if (animEnabled) {
                        ratioAnim.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 420f))
                    } else {
                        ratioAnim.snapTo(1f)
                    }
                    onTriggered(Offset(widthPx * 0.85f, knobPx))
                    delay(350)
                    ratioAnim.snapTo(0f)
                    triggered = false
                }
            }
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 52.dp, end = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Slide to start creating",
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                color = RawColors.TextMuted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (shimmerState != null) {
                            val p = shimmerState.value
                            val band = size.width * 0.45f
                            val cx = lerp(-band, size.width + band, p)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.5f to RawColors.Cream,
                                    1f to Color.Transparent,
                                    startX = cx - band,
                                    endX = cx + band
                                ),
                                blendMode = BlendMode.SrcAtop
                            )
                        }
                    }
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = ratioAnim.value * maxTravelPx }
                .size(knobSizeDp)
                .clip(CircleShape)
                .background(RawColors.Tan)
                .pointerInput(maxTravelPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { triggered = false },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!triggered) {
                                val next = (ratioAnim.value + dragAmount / maxTravelPx).coerceIn(0f, 1f)
                                scope.launch { ratioAnim.snapTo(next) }
                                if (next >= 0.65f && !triggered) {
                                    triggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        if (animEnabled) {
                                            ratioAnim.animateTo(1f, tween(110, easing = RawEasings.Morph))
                                        } else {
                                            ratioAnim.snapTo(1f)
                                        }
                                        onTriggered(Offset(widthPx * 0.85f, knobPx))
                                        delay(350)
                                        ratioAnim.snapTo(0f)
                                        triggered = false
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (!triggered) {
                                scope.launch {
                                    if (animEnabled) {
                                        ratioAnim.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 360f))
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
                type = RawIconType.ARROW_RIGHT,
                size = 24.dp,
                tint = RawColors.Ink
            )
        }
    }
}
