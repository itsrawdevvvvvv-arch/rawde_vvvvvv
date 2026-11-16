package com.rawedit.app.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DomeSpeechLines = listOf(
    "Voice in, reel out",
    "Hehe! Ready to edit!",
    "1080p offline magic",
    "Tap + for reel levels",
    "Looking sharp today!"
)

/**
 * Cute Interactive Dome Character matching prototype CSS:
 * - Exact single-arch oval geometry (fill + Cream top rim share the exact same top curve!)
 * - 60fps idle breathing loop on GPU graphicsLayer
 * - Jelly squash-and-stretch spring on tap
 * - Catchlight blinking eyes, rosy cheeks, specular shine, orbiting pixel sparkles
 * - Floating Cream speech pill
 * - Auto-slides smoothly down off-screen when [visible] is false (AI Chat / Timeline)
 */
@Composable
fun CuteDomeCharacter(
    speechText: String,
    visible: Boolean = true,
    isHappyState: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val slideProgress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (animEnabled) 520 else 0,
            easing = RawEasings.Morph
        ),
        label = "dome_slide"
    )

    val inf = rememberInfiniteTransition(label = "dome_idle")
    val idleFloat by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_float"
    )
    val sparkFloat by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spark_float"
    )
    val blinkScaleY by inf.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3860
                0.08f at 3990
                1f at 4150
            }
        ),
        label = "eye_blink"
    )

    val jellyScaleX = remember { Animatable(1f) }
    val jellyScaleY = remember { Animatable(1f) }
    val jellyOffsetY = remember { Animatable(0f) }
    var tappedHappy by remember { mutableStateOf(false) }
    var lineIdx by remember { mutableIntStateOf(0) }
    var customSpeech by remember(speechText) { mutableStateOf(speechText) }

    fun triggerJellyTap() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tappedHappy = true
        lineIdx = (lineIdx + 1) % DomeSpeechLines.size
        customSpeech = DomeSpeechLines[lineIdx]
        scope.launch {
            launch {
                jellyScaleX.animateTo(1.13f, tween(110, easing = FastOutSlowInEasing))
                jellyScaleX.animateTo(0.93f, tween(140, easing = FastOutSlowInEasing))
                jellyScaleX.animateTo(1.04f, tween(130, easing = FastOutSlowInEasing))
                jellyScaleX.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
            }
            launch {
                jellyScaleY.animateTo(0.83f, tween(110, easing = FastOutSlowInEasing))
                jellyScaleY.animateTo(1.09f, tween(140, easing = FastOutSlowInEasing))
                jellyScaleY.animateTo(0.96f, tween(130, easing = FastOutSlowInEasing))
                jellyScaleY.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
            }
            launch {
                jellyOffsetY.animateTo(5f, tween(110))
                jellyOffsetY.animateTo(-7f, tween(140))
                jellyOffsetY.animateTo(2f, tween(130))
                jellyOffsetY.animateTo(0f, tween(160))
            }
            delay(640)
            tappedHappy = false
        }
    }

    val idleY = if (animEnabled) -4f * idleFloat else 0f
    val idleSx = if (animEnabled) 1f - 0.012f * idleFloat else 1f
    val idleSy = if (animEnabled) 1f + 0.020f * idleFloat else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .graphicsLayer {
                translationY = slideProgress * 280.dp.toPx()
                alpha = 1f - slideProgress
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Grounded soft dark ambient shadow beneath Dome (zero hard box edges)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to RawColors.BgPrimary.copy(alpha = 0.78f),
                        1f to RawColors.BgPrimary
                    )
                )
        )

        // Interactive Dome Body (flush to bottom)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(208.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleX = idleSx * jellyScaleX.value
                    scaleY = idleSy * jellyScaleY.value
                    translationY = idleY + jellyOffsetY.value
                }
                .clickable(
                    enabled = visible,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { triggerJellyTap() },
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val strokePx = 3.dp.toPx()
                val topInset = strokePx * 0.5f
                val archRect = Rect(
                    left = strokePx,
                    top = topInset,
                    right = w - strokePx,
                    bottom = h * 1.92f
                )

                // Single exact arch path for fill
                val domePath = Path().apply {
                    moveTo(archRect.left, h)
                    arcTo(
                        rect = archRect,
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    lineTo(archRect.right, h)
                    close()
                }
                drawPath(
                    path = domePath,
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFDAB68B), RawColors.Tan, Color(0xFFA98055)),
                        center = Offset(w * 0.35f, h * 0.22f),
                        radius = w * 0.80f
                    )
                )

                // Exact matching Cream top rim along the same archRect
                drawArc(
                    color = RawColors.Cream,
                    startAngle = 182f,
                    sweepAngle = 176f,
                    useCenter = false,
                    topLeft = Offset(archRect.left, archRect.top),
                    size = Size(archRect.width, archRect.height),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Specular shine pill
                drawRoundRect(
                    color = RawColors.Cream.copy(alpha = 0.55f),
                    topLeft = Offset(w - 94.dp.toPx(), 18.dp.toPx()),
                    size = Size(26.dp.toPx(), 8.dp.toPx()),
                    cornerRadius = CornerRadius(99f, 99f)
                )

                // Orbiting pixel sparkles
                val spOffset = -9.dp.toPx() * sparkFloat
                drawRoundRect(
                    color = RawColors.Cream.copy(alpha = 0.45f + 0.55f * sparkFloat),
                    topLeft = Offset(46.dp.toPx(), 18.dp.toPx() + spOffset),
                    size = Size(6.dp.toPx(), 6.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                drawRoundRect(
                    color = RawColors.Cream.copy(alpha = 0.45f + 0.55f * (1f - sparkFloat)),
                    topLeft = Offset(w - 50.dp.toPx(), 24.dp.toPx() - spOffset * 0.8f),
                    size = Size(5.dp.toPx(), 5.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                )
            }

            // Face: Blinking Catchlight Eyes + Rosy Cheeks + Mouth
            Column(
                modifier = Modifier.padding(top = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val eyeScale = if (tappedHappy) 1.14f else 1f
                val eyeYScale = if (tappedHappy) 0.90f else blinkScaleY
                Row(
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(width = 17.dp, height = 18.dp)
                                .graphicsLayer {
                                    scaleX = eyeScale
                                    scaleY = eyeYScale
                                }
                                .clip(RoundedCornerShape(5.dp))
                                .background(RawColors.Ink)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.5.dp, start = 2.5.dp)
                                    .size(4.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(RawColors.Cream)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 2.5.dp, end = 2.5.dp)
                                    .size(2.5.dp)
                                    .clip(CircleShape)
                                    .background(RawColors.Cream.copy(alpha = 0.7f))
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(70.dp)
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(width = 14.dp, height = 7.dp)
                                .clip(CircleShape)
                                .background(RawColors.Blush.copy(alpha = 0.65f))
                        )
                    }
                }
                if (tappedHappy || isHappyState) {
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(width = 18.dp, height = 10.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                            .background(RawColors.Ink)
                    )
                } else {
                    Canvas(modifier = Modifier.padding(top = 2.dp).size(width = 18.dp, height = 8.dp)) {
                        drawArc(
                            color = RawColors.Ink,
                            startAngle = 15f,
                            sweepAngle = 150f,
                            useCenter = false,
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        // Floating Cream Speech Pill
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .graphicsLayer {
                    translationY = idleY + jellyOffsetY.value * 0.6f
                }
                .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(RawColors.Cream)
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = customSpeech,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                color = RawColors.Ink,
                maxLines = 1
            )
        }
    }
}

/**
 * Kinetic Intro Splash: RAW EDIT reveal + Warm Tan pill + progress sweep + rising Dome.
 */
@Composable
fun KineticIntroSplash(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    var progressTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            progressTarget = 1f
            delay(1850)
            onDismiss()
        }
    }
    val sweep by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(1650, easing = FastOutSlowInEasing),
        label = "intro_sweep"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(420))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RawColors.BgPrimary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            Text(
                text = "1080P OFFLINE ENGINE",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                letterSpacing = 1.8.sp,
                color = RawColors.Tan,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 46.dp)
            )

            Column(
                modifier = Modifier.align(Alignment.Center).padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "RAW",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 46.sp,
                        letterSpacing = (-2).sp,
                        color = RawColors.Cream
                    )
                    TiltedAccentPill(
                        text = "EDIT",
                        angleDeg = -2.2f
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Voice in, kinetic reel out",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = RawColors.TextMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(RawColors.Cream.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(sweep)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(RawColors.Tan, RawColors.Cream)))
                    )
                }
            }

            CuteDomeCharacter(
                speechText = "Tap anywhere to start",
                visible = true,
                isHappyState = true,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            )
        }
    }
}

/**
 * Animated flowing dotted SVG-style connector arrow between Reel Level steps.
 */
@Composable
fun AnimatedDottedStepArrow(
    modifier: Modifier = Modifier,
    height: Dp = 24.dp
) {
    val inf = rememberInfiniteTransition(label = "dotted_flow")
    val phase by inf.animateFloat(
        initialValue = 14f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dash_phase"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
            .height(height),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.size(width = 24.dp, height = height)) {
            val path = Path().apply {
                moveTo(size.width * 0.5f, 0f)
                lineTo(size.width * 0.5f, size.height - 4.dp.toPx())
            }
            drawPath(
                path = path,
                color = RawColors.Tan,
                style = Stroke(
                    width = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), phase)
                )
            )
            val tipY = size.height - 1.dp.toPx()
            val midX = size.width * 0.5f
            val head = Path().apply {
                moveTo(midX - 4.dp.toPx(), tipY - 5.dp.toPx())
                lineTo(midX, tipY)
                lineTo(midX + 4.dp.toPx(), tipY - 5.dp.toPx())
            }
            drawPath(
                path = head,
                color = RawColors.Tan,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
