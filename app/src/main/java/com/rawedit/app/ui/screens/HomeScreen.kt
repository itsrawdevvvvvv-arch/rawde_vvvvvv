package com.rawedit.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlin.math.PI
import kotlin.math.cos

private val TV_LINES = listOf(
    "Beep! Ready when you are.",
    "Voice in, reel out.",
    "No internet needed!",
    "Typography is my love language."
)

private val ART_SKIP_WORDS = setOf(
    "a", "an", "the", "and", "or", "of", "in", "on", "to", "for",
    "with", "by", "my", "your", "why", "how", "what", "when", "into", "is"
)

private fun buildReelArtLabel(title: String): String {
    val words = title.trim()
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return "REEL"
    val chosen = words.firstOrNull {
        it.length >= 3 && it.lowercase() !in ART_SKIP_WORDS && !it.all(Char::isDigit)
    } ?: words.firstOrNull { !it.all(Char::isDigit) } ?: words.first()
    return chosen.uppercase().take(8)
}

private fun Modifier.hcRise(
    clockMs: State<Float>?,
    delayMs: Int
): Modifier = if (clockMs == null) {
    this
} else {
    graphicsLayer {
        val raw = ((clockMs.value - delayMs) / 600f).coerceIn(0f, 1f)
        val eased = RawEasings.Morph.transform(raw)
        alpha = eased
        translationY = (1f - eased) * 16.dp.toPx()
    }
}

@Composable
fun HomeScreen(
    userProfile: UserProfile,
    recentVideos: List<VideoEntity>,
    onStartNewReel: (Offset) -> Unit,
    onOpenVoiceStep: () -> Unit,
    onOpenPromptStep: () -> Unit,
    onOpenCodeStep: () -> Unit,
    onOpenReel: (Long) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    val isCompact = LocalConfiguration.current.screenHeightDp <= 700
    var tvIndex by remember { mutableIntStateOf(0) }

    val entranceClock = remember { Animatable(if (animEnabled) 0f else 980f) }
    val entranceState: State<Float>? = if (animEnabled) entranceClock.asState() else null
    LaunchedEffect(animEnabled) {
        if (animEnabled) {
            entranceClock.snapTo(0f)
            entranceClock.animateTo(980f, tween(durationMillis = 980, easing = LinearEasing))
        } else {
            entranceClock.snapTo(980f)
        }
    }

    val inf = if (animEnabled) rememberInfiniteTransition(label = "hc_loops") else null
    val bubBobState: State<Float>? = if (inf != null) {
        inf.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
            label = "hc_bub"
        )
    } else {
        null
    }
    val artShineState: State<Float>? = if (inf != null && recentVideos.isNotEmpty()) {
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Restart),
            label = "hc_shine"
        )
    } else {
        null
    }

    val bubbleShape = remember {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp)
    }
    val headlineText = remember {
        buildAnnotatedString {
            append("Let's make\na ")
            withStyle(SpanStyle(color = RawColors.Mint)) {
                append("reel!")
            }
        }
    }
    val latestTwo = remember(recentVideos) { recentVideos.take(2) }
    val displayName = userProfile.userName.ifBlank { "there" }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val availableHeight = maxHeight

        Column(
            modifier = Modifier
                .widthIn(max = 430.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = availableHeight)
                    .padding(start = 20.dp, end = 20.dp, bottom = 104.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top section: centered greeting (.hc-hi) + mascot stage (.hc-stage)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .hcRise(entranceState, 20),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Hello, $displayName",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            letterSpacing = (-0.6).sp,
                            color = RawColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "OFFLINE REEL STUDIO",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            letterSpacing = 1.6.sp,
                            color = RawColors.TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Mascot stage: tap bubble or TV mascot to cycle quip (.hc-stage)
                    Column(
                        modifier = Modifier
                            .padding(top = if (isCompact) 12.dp else 22.dp)
                            .width(214.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .widthIn(max = 190.dp)
                                .graphicsLayer {
                                    val phase = bubBobState?.value ?: 0f
                                    val lift = ((1f - cos(phase)) * 0.5f) * 4.dp.toPx()
                                    translationY = -lift
                                }
                                .clip(bubbleShape)
                                .background(RawColors.RaisedCard)
                                .border(1.dp, RawColors.Divider, bubbleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { tvIndex = (tvIndex + 1) % TV_LINES.size }
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = TV_LINES[tvIndex],
                                transitionSpec = {
                                    if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                    else fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                                },
                                label = "hc_bub_text"
                            ) { line ->
                                Text(
                                    text = line,
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = RawColors.TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        RawPixelMascot(
                            kind = PixelMascotKind.TV_BOT,
                            size = 120.dp,
                            onTap = { tvIndex = (tvIndex + 1) % TV_LINES.size }
                        )
                    }
                }

                // Centered main block (.hc-main): headline + sub + Start creating + 3 quick steps
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val h1Size = if (isCompact) 32.sp else 38.sp
                    Text(
                        text = headlineText,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = h1Size,
                        lineHeight = h1Size * 1.02f,
                        letterSpacing = (-1.9).sp,
                        color = RawColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = if (isCompact) 12.dp else 18.dp)
                            .hcRise(entranceState, 100)
                    )

                    Text(
                        text = "Voice in, reel out. Everything stays on your phone.",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 19.5.sp,
                        color = RawColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = if (isCompact) 8.dp else 10.dp)
                            .widthIn(max = 230.dp)
                            .hcRise(entranceState, 160)
                    )

                    // Primary Mint pill CTA (.hc-cta)
                    RawPressButton(
                        onClick = { onStartNewReel(Offset(540f, 820f)) },
                        modifier = Modifier
                            .padding(top = if (isCompact) 14.dp else 20.dp)
                            .widthIn(max = 250.dp)
                            .fillMaxWidth()
                            .hcRise(entranceState, 220)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(RawColors.Mint),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Start creating",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = RawColors.PrimaryBtnText
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            RawIcon(
                                type = RawIconType.PLAY,
                                size = 12.dp,
                                tint = RawColors.PrimaryBtnText
                            )
                        }
                    }

                    // 3 Quick Step buttons (.hc-quick)
                    Row(
                        modifier = Modifier
                            .padding(top = if (isCompact) 12.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeQuickStepButton(
                            kind = PixelMascotKind.MIC_BOT,
                            label = "VOICE",
                            onClick = onOpenVoiceStep,
                            modifier = Modifier.hcRise(entranceState, 260)
                        )
                        HomeQuickStepButton(
                            kind = PixelMascotKind.ZIP_BOT,
                            label = "PROMPT",
                            onClick = onOpenPromptStep,
                            modifier = Modifier.hcRise(entranceState, 300)
                        )
                        HomeQuickStepButton(
                            kind = PixelMascotKind.CODE_BOT,
                            label = "CODE",
                            onClick = onOpenCodeStep,
                            modifier = Modifier.hcRise(entranceState, 340)
                        )
                    }
                }

                // Bottom recent 2 reels strip (.hc-strip)
                if (latestTwo.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (isCompact) 14.dp else 18.dp)
                            .hcRise(entranceState, 380),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        latestTwo.forEach { video ->
                            HomeRecentReelCard(
                                video = video,
                                shineState = artShineState,
                                onClick = { onOpenReel(video.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (latestTwo.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeQuickStepButton(
    kind: PixelMascotKind,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RawPressButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RawPixelMascot(
                kind = kind,
                size = 24.dp,
                interactive = false,
                noBob = true,
                noBlink = true
            )
            Text(
                text = label,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.5.sp,
                letterSpacing = 0.8.sp,
                color = RawColors.TextMuted,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun HomeRecentReelCard(
    video: VideoEntity,
    shineState: State<Float>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artLabel = remember(video.id, video.title) { buildReelArtLabel(video.title) }
    val artBg = remember {
        Brush.linearGradient(
            colors = listOf(Color(0xFF22392C), Color(0xFF131A16)),
            start = Offset.Zero,
            end = Offset(420f, 160f)
        )
    }
    val shineColor = remember { Color(0x17FFFFFF) }
    val shinePath = remember { Path() }

    RawPressButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp))
                .padding(9.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clipToBounds()
                    .background(artBg),
                contentAlignment = Alignment.Center
            ) {
                if (shineState != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val p = shineState.value
                        val w = size.width
                        val h = size.height
                        val travel = (-0.55f + 2.1f * p) * w
                        val bandW = w * 0.24f
                        val skew = h * 0.36f
                        shinePath.reset()
                        shinePath.moveTo(travel, 0f)
                        shinePath.lineTo(travel + bandW, 0f)
                        shinePath.lineTo(travel + bandW - skew, h)
                        shinePath.lineTo(travel - skew, h)
                        shinePath.close()
                        drawPath(shinePath, shineColor)
                    }
                }
                Text(
                    text = artLabel,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = (-0.2).sp,
                    color = Color(0xFFE8F3EB),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = video.title.ifBlank { "Untitled Reel" },
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                color = RawColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = FormatHelpers.formatDuration(video.durationSec),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
                color = RawColors.TextMuted
            )
        }
    }
}
