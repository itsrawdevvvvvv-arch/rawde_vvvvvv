package com.rawedit.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rawedit.app.ui.components.RawEditLogoBadge
import com.rawedit.app.ui.icons.IllustrationKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawLineArtIllustration
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.LiquidPagerDots
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.SplitFlapText
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.DotGridSurface
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class OnboardingPageData(
    val titleWords: List<String>,
    val subtitle: String,
    val illustration: IllustrationKind
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPageData(
        titleWords = listOf("Add", "your", "voice"),
        subtitle = "Drop in your MP3 voiceover and optional SRT captions right on your phone.",
        illustration = IllustrationKind.ONBOARDING_VOICE
    ),
    OnboardingPageData(
        titleWords = listOf("Copy", "to", "Claude"),
        subtitle = "Copy our ready-made prompt, answer 3 quick questions, and grab the code.",
        illustration = IllustrationKind.ONBOARDING_PROMPT
    ),
    OnboardingPageData(
        titleWords = listOf("Paste", "and", "create!"),
        subtitle = "Turn your voice into a sharp 1080p typography reel. Free and offline.",
        illustration = IllustrationKind.ONBOARDING_RENDER
    )
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES.size })
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()

    DotGridSurface(
        modifier = Modifier.fillMaxSize(),
        backgroundColor = RawColors.BgPrimary,
        dotColor = RawColors.HeroSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top-left logo + crisp wordmark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RawEditLogoBadge(logoSize = 36.dp)
            }

            // Upper half animated line-art pager with subtle drag parallax
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val data = ONBOARDING_PAGES[page]
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .graphicsLayer {
                            translationX = pageOffset * -42f
                            alpha = (1f - abs(pageOffset) * 0.45f).coerceIn(0f, 1f)
                            val scale = 1f - abs(pageOffset) * 0.08f
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    RawLineArtIllustration(
                        kind = data.illustration,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(265.dp),
                        restartKey = page == pagerState.currentPage
                    )
                }
            }

            // Minimal lower sheet card matching reference layout
            val current = ONBOARDING_PAGES[pagerState.currentPage]
            val isLast = pagerState.currentPage == ONBOARDING_PAGES.lastIndex

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(RawColors.HeroSurface)
                    .navigationBarsPadding()
                    .padding(horizontal = 26.dp, vertical = 30.dp)
            ) {
                KineticHeading(
                    words = current.titleWords,
                    style = RawTypography.displayLarge,
                    triggerKey = pagerState.currentPage
                )
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedContent(
                    targetState = current.subtitle,
                    transitionSpec = {
                        if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else fadeIn(tween(240, easing = RawEasings.Morph)) togetherWith fadeOut(tween(150))
                    },
                    label = "onboard_sub_morph"
                ) { sub ->
                    Text(
                        text = sub,
                        style = RawTypography.bodyLarge,
                        color = RawColors.TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(34.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidPagerDots(
                        totalCount = ONBOARDING_PAGES.size,
                        currentIndex = pagerState.currentPage,
                        pageOffsetFraction = pagerState.currentPageOffsetFraction
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
                        Row(
                            modifier = Modifier
                                .height(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(RawColors.PrimaryBtnBg)
                                .animateContentSize(
                                    animationSpec = if (animEnabled) {
                                        spring(dampingRatio = 0.78f, stiffness = 360f)
                                    } else {
                                        tween(0)
                                    }
                                )
                                .padding(start = 24.dp, end = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SplitFlapText(
                                text = if (isLast) "Get started" else "Next",
                                style = RawTypography.labelLarge,
                                color = RawColors.PrimaryBtnText
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(RawColors.PrimaryBtnText),
                                contentAlignment = Alignment.Center
                            ) {
                                RawIcon(
                                    type = RawIconType.ARROW_RIGHT,
                                    size = 18.dp,
                                    tint = RawColors.PrimaryBtnBg
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
