package com.rawedit.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.PixelRawEditLogo
import com.rawedit.app.ui.icons.PixelSparkle
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.LiquidPagerDots
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.SplitFlapText
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

private data class OnboardingPageData(
    val titleWords: List<String>,
    val subtitle: String,
    val chipTop: String,
    val chipBottom: String
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPageData(
        titleWords = listOf("Add", "your", "voice"),
        subtitle = "Pick an MP3 voiceover and add optional SRT captions. Everything lives on your phone.",
        chipTop = "00:01 hello",
        chipBottom = "SRT - 24 cues"
    ),
    OnboardingPageData(
        titleWords = listOf("Copy", "to", "AI"),
        subtitle = "Download the bundled kinetic-explainer-style.zip and copy the prompt into your Cloud AI.",
        chipTop = "PROMPT.txt",
        chipBottom = "552 KB zip"
    ),
    OnboardingPageData(
        titleWords = listOf("Paste", "and", "create!"),
        subtitle = "Paste the code back. Get a sharp 1080p typography reel - free and offline.",
        chipTop = "1080p MP4",
        chipBottom = "offline"
    )
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES.size })
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()

    // Single lightweight phase driving slide artwork cleanly in draw/layer phase (zero recompositions)
    val artPhaseState: State<Float>? = if (animEnabled) {
        val inf = rememberInfiniteTransition(label = "ob_art_phase")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
    ) {
        // Top minimal header matching R.onb
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelRawEditLogo(width = 42.dp, height = 33.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RawPixelMascot(kind = PixelMascotKind.TV_BOT, size = 30.dp)
                PixelSparkle(size = 12.dp, phaseOffsetMs = 200)
            }
        }

        // Upper artwork pager with smooth drag parallax
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val data = ONBOARDING_PAGES[page]
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val pageMascot = when (page) {
                0 -> PixelMascotKind.MIC_BOT
                1 -> PixelMascotKind.ZIP_BOT
                else -> PixelMascotKind.TV_BOT
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .graphicsLayer {
                        translationX = pageOffset * -42f
                        alpha = (1f - abs(pageOffset) * 0.45f).coerceIn(0f, 1f)
                        val s = 1f - abs(pageOffset) * 0.06f
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center
            ) {
                // Vector backdrop outlines + slide-specific bottom motion cue in ONE Canvas
                OnboardingSlideCanvas(
                    page = page,
                    phaseState = artPhaseState,
                    modifier = Modifier.fillMaxSize()
                )

                // Stage preview card on slide 2 matching .stack .fr
                if (page == 2) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 34.dp)
                            .size(width = 124.dp, height = 70.dp)
                            .graphicsLayer {
                                translationY = if (artPhaseState != null) sin(artPhaseState.value * Math.PI.toFloat() * 2f) * 5f else 0f
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .border(1.dp, RawColors.DividerStrong, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "RAW",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                            letterSpacing = (-1.5).sp,
                            color = Color.White
                        )
                    }
                }

                // Centered floating retro pixel mascot
                RawPixelMascot(
                    kind = pageMascot,
                    size = 122.dp,
                    modifier = Modifier.offset(y = (-24).dp)
                )

                // Floating mono info chips (.chip.c1 / .chip.c2)
                OnboardingFloatingChip(
                    text = data.chipTop,
                    textColor = RawColors.Mint,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 42.dp)
                        .graphicsLayer {
                            translationY = if (artPhaseState != null) sin(artPhaseState.value * Math.PI.toFloat() * 2f) * 3.5f else 0f
                        }
                )

                OnboardingFloatingChip(
                    text = data.chipBottom,
                    textColor = RawColors.TextPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 74.dp)
                        .graphicsLayer {
                            translationY = if (artPhaseState != null) -sin(artPhaseState.value * Math.PI.toFloat() * 2f) * 3.5f else 0f
                        }
                )
            }
        }

        // Bottom rounded dark obsidian card (#obc)
        val current = ONBOARDING_PAGES[pagerState.currentPage]
        val isLast = pagerState.currentPage == ONBOARDING_PAGES.lastIndex

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(RawColors.CardSurface)
                .border(
                    width = 1.dp,
                    color = RawColors.Divider,
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 26.dp)
        ) {
            KineticHeading(
                words = current.titleWords,
                style = RawTypography.displayLarge,
                triggerKey = pagerState.currentPage
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.height(54.dp)) {
                AnimatedContent(
                    targetState = current.subtitle,
                    transitionSpec = {
                        if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else fadeIn(tween(220, easing = RawEasings.Morph)) togetherWith fadeOut(tween(140))
                    },
                    label = "onb_sub"
                ) { sub ->
                    Text(
                        text = sub,
                        style = RawTypography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = RawColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            LiquidPagerDots(
                totalCount = ONBOARDING_PAGES.size,
                currentIndex = pagerState.currentPage,
                pageOffsetFraction = pagerState.currentPageOffsetFraction
            )
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Skip",
                    style = RawTypography.titleMedium.copy(fontSize = 15.sp),
                    color = RawColors.TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onFinished
                        )
                        .padding(horizontal = 6.dp, vertical = 10.dp)
                )

                RawPressButton(
                    onClick = {
                        if (isLast) {
                            onFinished()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 150.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(RawColors.Mint)
                            .animateContentSize(
                                animationSpec = if (animEnabled) spring(dampingRatio = 0.78f, stiffness = 360f) else tween(0)
                            )
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SplitFlapText(
                            text = if (isLast) "Get started" else "Next",
                            style = RawTypography.labelLarge,
                            color = RawColors.PrimaryBtnText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingFloatingChip(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(RawColors.RaisedCard)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun OnboardingSlideCanvas(
    page: Int,
    phaseState: State<Float>?,
    modifier: Modifier = Modifier
) {
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 14f), 0f) }
    val cloudPath = remember { Path() }

    Canvas(modifier = modifier) {
        val phase = phaseState?.value ?: 0.5f
        val w = size.width
        val h = size.height
        val lineCol = RawColors.TextPrimary.copy(alpha = 0.20f)
        val sw = 1.6.dp.toPx()

        when (page) {
            0 -> {
                val c = Offset(w * 0.5f, h * 0.44f)
                drawCircle(lineCol, radius = w * 0.20f, center = c, style = Stroke(sw))
                drawCircle(lineCol, radius = w * 0.33f, center = c, style = Stroke(sw))
                drawCircle(lineCol, radius = w * 0.46f, center = c, style = Stroke(sw))

                // 16 Mint equalizer bars at bottom (.wv)
                val barCount = 16
                val barW = 5.dp.toPx()
                val gap = 5.dp.toPx()
                val totalW = barCount * barW + (barCount - 1) * gap
                val startX = (w - totalW) * 0.5f
                val bottomY = h - 36.dp.toPx()
                val maxH = 52.dp.toPx()
                for (i in 0 until barCount) {
                    val v = (0.25f + 0.75f * abs(sin((phase * 2f + i * 0.14f) * Math.PI).toFloat()))
                    val bh = (maxH * v).coerceAtLeast(8.dp.toPx())
                    val bx = startX + i * (barW + gap)
                    drawRoundRect(
                        color = RawColors.Mint,
                        topLeft = Offset(bx, bottomY - bh),
                        size = Size(barW, bh),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }
            }

            1 -> {
                drawLine(lineCol, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.38f, h * 0.28f), sw, pathEffect = dashEffect)
                drawLine(lineCol, Offset(w * 0.62f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), sw, pathEffect = dashEffect)
                drawLine(lineCol, Offset(w * 0.16f, h * 0.72f), Offset(w * 0.84f, h * 0.72f), sw, pathEffect = dashEffect)

                // Cloud icon outline top-right
                cloudPath.reset()
                val cx = w * 0.68f
                val cy = h * 0.20f
                val s = w * 0.0022f
                cloudPath.moveTo(cx - 26f * s, cy + 12f * s)
                cloudPath.cubicTo(cx - 42f * s, cy + 6f * s, cx - 36f * s, cy - 16f * s, cx - 16f * s, cy - 14f * s)
                cloudPath.cubicTo(cx - 12f * s, cy - 34f * s, cx + 22f * s, cy - 34f * s, cx + 26f * s, cy - 12f * s)
                cloudPath.cubicTo(cx + 44f * s, cy - 10f * s, cx + 50f * s, cy + 8f * s, cx + 32f * s, cy + 12f * s)
                cloudPath.close()
                drawPath(cloudPath, RawColors.TextPrimary.copy(alpha = 0.85f), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))

                // Animated typing code lines (.ln)
                val lx = 36.dp.toPx()
                val maxLineW = w - lx * 2f
                val baseY = h - 78.dp.toPx()
                val fracs = listOf(0.90f, 0.70f, 0.80f)
                fracs.forEachIndexed { idx, f ->
                    val seg = ((phase * 1.4f - idx * 0.15f) % 1f).let { p ->
                        if (p < 0.6f) p / 0.6f else 1f
                    }.coerceIn(0.15f, 1f)
                    val ly = baseY + idx * 13.dp.toPx()
                    drawRoundRect(
                        color = RawColors.TextPrimary.copy(alpha = 0.45f),
                        topLeft = Offset(lx, ly),
                        size = Size(maxLineW * f * seg, 6.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }
            }

            else -> {
                drawLine(lineCol, Offset(w * 0.06f, h * 0.20f), Offset(w * 0.22f, h * 0.20f), sw)
                drawLine(lineCol, Offset(w * 0.78f, h * 0.20f), Offset(w * 0.94f, h * 0.20f), sw)
                drawLine(lineCol, Offset(w * 0.12f, h * 0.82f), Offset(w * 0.88f, h * 0.82f), sw)
                drawRect(
                    color = lineCol,
                    topLeft = Offset(w * 0.12f, h * 0.30f),
                    size = Size(w * 0.76f, h * 0.40f),
                    style = Stroke(sw)
                )
            }
        }
    }
}
