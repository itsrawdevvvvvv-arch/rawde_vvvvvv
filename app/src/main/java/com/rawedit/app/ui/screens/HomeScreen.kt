package com.rawedit.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.motion.FeatheredScrollEdges
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.TiltedAccentPill
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors

@Composable
fun HomeScreen(
    userProfile: UserProfile,
    reelsCount: Int,
    onStartNewReel: (Offset) -> Unit,
    onOpenChatbot: () -> Unit = {},
    onOpenPalette: () -> Unit = {},
    onOpenOpenDesign: () -> Unit = {},
    onOpenReels: () -> Unit = {},
    recentDraftDurationSec: Double = 0.0
) {
    val scrollState = rememberScrollState()
    val headlineStyle = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 60.sp,
        lineHeight = 56.sp,
        letterSpacing = (-2.4).sp,
        color = RawColors.TextPrimary
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 430.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 225.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Top Row: Oversized "Make a reel" + Premium "AI Chat" pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Make",
                        style = headlineStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                            angleDeg = -1.5f,
                            backgroundColor = RawColors.Tan,
                            textColor = RawColors.Ink
                        )
                    }
                }

                RawPressButton(
                    onClick = onOpenChatbot,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0x38C89F72),
                                        Color(0xF51C1C20)
                                    )
                                )
                            )
                            .border(1.dp, RawColors.AccentBorder, CircleShape)
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(RawColors.TanButtonBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            RawIcon(RawIconType.SPARK, size = 11.dp, tint = RawColors.Ink)
                        }
                        Text(
                            text = "AI Chat",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = RawColors.Cream
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal Step Shortcut Chips matching Prototype
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                HomeChip(
                    text = "Voice",
                    icon = RawIconType.MIC,
                    active = true,
                    onClick = { onStartNewReel(Offset.Zero) }
                )
                HomeChip(
                    text = "Assets",
                    icon = RawIconType.BOX,
                    active = false,
                    onClick = { onStartNewReel(Offset.Zero) }
                )
                HomeChip(
                    text = "Palette",
                    icon = null,
                    active = false,
                    onClick = onOpenPalette
                )
                HomeChip(
                    text = "Open Design",
                    icon = null,
                    active = false,
                    onClick = onOpenOpenDesign
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Voice in, reel out. Nothing leaves the phone.",
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = RawColors.TextMuted
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Primary Warm Tan Gradient CTA
            BigPrimaryButton(
                text = "+ Start New Reel \u2192",
                onClick = { onStartNewReel(Offset.Zero) },
                height = 58.dp,
                containerColor = RawColors.Tan,
                contentColor = RawColors.Ink
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Recent Draft Pill with Live Animated Waveform Bars
            val draftLabel = if (recentDraftDurationSec > 0.0) {
                "Recent Draft \u00B7 ${FormatHelpers.formatDuration(recentDraftDurationSec)}"
            } else {
                val word = if (reelsCount == 1) "reel" else "reels"
                "My Reels \u00B7 $reelsCount $word"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, CircleShape)
                    .clickable { onOpenReels() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniAnimatedWaveform()
                    Text(
                        text = draftLabel,
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = RawColors.Cream
                    )
                }
                Text(
                    text = "Open \u2192",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    color = RawColors.TextMuted
                )
            }
        }

        FeatheredScrollEdges(
            scrollState = scrollState,
            topHeight = 28.dp,
            bottomHeight = 168.dp,
            alwaysShowBottom = true
        )
    }
}

@Composable
private fun HomeChip(
    text: String,
    icon: RawIconType?,
    active: Boolean,
    onClick: () -> Unit
) {
    RawPressButton(onClick = onClick) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .then(
                    if (active) Modifier.background(RawColors.TanButtonBrush)
                    else Modifier
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, CircleShape)
                )
                .padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                RawIcon(
                    type = icon,
                    size = 13.dp,
                    tint = if (active) RawColors.Ink else RawColors.TextMuted
                )
            }
            Text(
                text = text,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                color = if (active) RawColors.Ink else RawColors.TextMuted,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun MiniAnimatedWaveform() {
    val inf = rememberInfiniteTransition(label = "mini_wave")
    val delays = listOf(0, 150, 300, 450)
    Row(
        modifier = Modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        delays.forEachIndexed { idx, delayMs ->
            val h by inf.animateFloat(
                initialValue = 5f,
                targetValue = 16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = delayMs, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave_$idx"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(CircleShape)
                    .background(RawColors.Tan)
            )
        }
    }
}
