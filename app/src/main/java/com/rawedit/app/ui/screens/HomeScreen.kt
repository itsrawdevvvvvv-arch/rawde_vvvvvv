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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.InstrumentSerifFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlin.math.PI
import kotlin.math.cos

private val TV_LINES = listOf(
    "Ready when you are",
    "Voice in, reel out",
    "No internet needed",
    "Typography is my love language"
)

private fun Modifier.vhRise(
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
    reelsCount: Int,
    onStartNewReel: (Offset) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    var tvIndex by remember { mutableIntStateOf(0) }

    val entranceClock = remember { Animatable(if (animEnabled) 0f else 840f) }
    val entranceState: State<Float>? = if (animEnabled) entranceClock.asState() else null
    LaunchedEffect(animEnabled) {
        if (animEnabled) {
            entranceClock.snapTo(0f)
            entranceClock.animateTo(840f, tween(durationMillis = 840, easing = LinearEasing))
        } else {
            entranceClock.snapTo(840f)
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

    val bubbleShape = remember {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
    }
    val headlineText = remember {
        buildAnnotatedString {
            append("Make\na ")
            withStyle(SpanStyle(color = RawColors.Mint)) {
                append("reel")
            }
        }
    }
    val displayName = userProfile.userName.ifBlank { "Creator" }
    val reelsWord = if (reelsCount == 1) "reel" else "reels"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val availableHeight = maxHeight
        val isCompact = availableHeight < 710.dp

        Column(
            modifier = Modifier
                .widthIn(max = 430.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = availableHeight)
                    .padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 104.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Top left Studio identity (.vh-hi)
                Column(
                    modifier = Modifier
                        .padding(top = if (isCompact) 20.dp else 34.dp)
                        .vhRise(entranceState, 20)
                ) {
                    val hiSize = if (isCompact) 42.sp else 60.sp
                    Text(
                        text = "Studio",
                        fontFamily = InstrumentSerifFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = hiSize,
                        lineHeight = hiSize * 0.98f,
                        letterSpacing = if (isCompact) (-1.1).sp else (-1.8).sp,
                        color = RawColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "$displayName \u00B7 $reelsCount $reelsWord \u00B7 offline",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        letterSpacing = 0.13.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        color = RawColors.TextMuted
                    )
                }

                // Center mascot stage (.vh-mid + .vh-stage)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 230.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = if (isCompact) 240.dp else 300.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .widthIn(max = 250.dp)
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
                                .padding(horizontal = 17.dp, vertical = 13.dp),
                            contentAlignment = Alignment.Center
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
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    lineHeight = 21.6.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        RawPixelMascot(
                            kind = PixelMascotKind.TV_BOT,
                            size = if (isCompact) 144.dp else 184.dp,
                            tint = RawColors.TextPrimary,
                            onTap = { tvIndex = (tvIndex + 1) % TV_LINES.size }
                        )
                    }
                }

                // Bottom-left editorial copy + full-width Start creating CTA (.vh-copy)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    val h1Size = if (isCompact) 44.sp else 64.sp
                    Text(
                        text = headlineText,
                        fontFamily = InstrumentSerifFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = h1Size,
                        lineHeight = h1Size * 0.98f,
                        letterSpacing = if (isCompact) (-1.1).sp else (-1.8).sp,
                        color = RawColors.TextPrimary,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.vhRise(entranceState, 100)
                    )

                    Text(
                        text = "Voice in, reel out, nothing leaves the phone",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        color = RawColors.TextMuted,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .widthIn(max = 340.dp)
                            .vhRise(entranceState, 160)
                    )

                    RawPressButton(
                        onClick = { onStartNewReel(Offset(540f, 820f)) },
                        modifier = Modifier
                            .padding(top = 22.dp)
                            .fillMaxWidth()
                            .vhRise(entranceState, 220)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(66.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(RawColors.Mint)
                                .padding(horizontal = 22.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Start creating",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                                color = RawColors.PrimaryBtnText,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            RawIcon(
                                type = RawIconType.PLAY,
                                size = 14.dp,
                                tint = RawColors.PrimaryBtnText
                            )
                        }
                    }
                }
            }
        }
    }
}
