package com.rawedit.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.ui.components.UserAvatarView
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.PixelSparkle
import com.rawedit.app.ui.icons.PixelTypeMarqueeBadge
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.DotGridSurface
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    userProfile: UserProfile,
    onOpenProfile: () -> Unit,
    onStartNewReel: (Offset) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    val scope = rememberCoroutineScope()
    val ringDraw = remember { Animatable(if (animEnabled) 0f else 1f) }
    var kittyLine by remember { mutableStateOf<String?>(null) }
    var tvBotLine by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(animEnabled) {
        if (animEnabled) {
            ringDraw.snapTo(0f)
            ringDraw.animateTo(1f, tween(650, easing = RawEasings.Morph))
        }
    }

    DotGridSurface(
        modifier = Modifier.fillMaxSize(),
        backgroundColor = RawColors.BgPrimary,
        dotColor = RawColors.HeroSurface.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 114.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Top minimal header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hello, ${userProfile.userName.ifBlank { "Creator" }}",
                            style = RawTypography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Offline Reel Studio",
                            style = RawTypography.bodyMedium
                        )
                    }
                    UserAvatarView(
                        name = userProfile.userName.ifBlank { "R" },
                        photoPath = userProfile.profilePhotoPath,
                        size = 46.dp,
                        onClick = onOpenProfile
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Typography Hero Headline with hand-drawn oval highlight, burst rays & pixel stars
                Box(modifier = Modifier.fillMaxWidth()) {
                    PixelSparkle(
                        size = 16.dp,
                        phaseOffsetMs = 0,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 6.dp, y = (-12).dp)
                    )
                    PixelSparkle(
                        size = 12.dp,
                        phaseOffsetMs = 450,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-10).dp, y = 4.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        KineticHeading(
                            words = listOf("Let's", "make"),
                            style = RawTypography.displayLarge.copy(
                                fontSize = 38.sp,
                                lineHeight = 42.sp,
                                fontStyle = FontStyle.Italic
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "a",
                                style = RawTypography.displayLarge.copy(
                                    fontSize = 38.sp,
                                    lineHeight = 42.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            )
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "reel!",
                                    style = RawTypography.displayLarge.copy(
                                        fontSize = 38.sp,
                                        lineHeight = 42.sp,
                                        fontStyle = FontStyle.Italic
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                )
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val sweep = 360f * ringDraw.value
                                    drawArc(
                                        color = RawColors.TextPrimary.copy(alpha = 0.92f),
                                        startAngle = -140f,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        topLeft = Offset(-2.dp.toPx(), 1.dp.toPx()),
                                        size = Size(size.width + 4.dp.toPx(), size.height - 2.dp.toPx()),
                                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
                            // Comic ray ticks beside "reel!"
                            Canvas(modifier = Modifier.size(26.dp, 26.dp)) {
                                val a = ringDraw.value
                                drawLine(
                                    color = RawColors.TextPrimary.copy(alpha = 0.9f * a),
                                    start = Offset(2f, size.height * 0.48f),
                                    end = Offset(size.width * 0.78f, 3f),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = RawColors.TextPrimary.copy(alpha = 0.9f * a),
                                    start = Offset(4f, size.height * 0.85f),
                                    end = Offset(size.width * 0.92f, size.height * 0.65f),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Subtext row + interactive retro pixel TV Bot + speech play bubble
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f).padding(bottom = 14.dp)) {
                        Text(
                            text = "Turn your voice & ideas\ninto crisp 1080p edits.",
                            style = RawTypography.bodyLarge.copy(lineHeight = 22.sp),
                            color = RawColors.TextMuted
                        )
                        AnimatedVisibility(
                            visible = tvBotLine != null,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(180))
                        ) {
                            Text(
                                text = tvBotLine.orEmpty(),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = RawColors.SuccessMint,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.padding(end = 8.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        RawPixelMascot(
                            kind = PixelMascotKind.TV_BOT,
                            size = 76.dp,
                            onTap = {
                                scope.launch {
                                    tvBotLine = "ready to render 1080p!"
                                    delay(1700)
                                    tvBotLine = null
                                }
                            }
                        )
                        RawPixelMascot(
                            kind = PixelMascotKind.SPEECH_PLAY,
                            size = 32.dp,
                            modifier = Modifier.offset(x = 12.dp, y = (-18).dp)
                        )
                    }
                }

                // Single Hero Typography Creation Card (NO workspace/draft clutter!)
                RawPressButton(
                    onClick = { onStartNewReel(Offset(540f, 820f)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DotGridSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { rotationZ = -1.6f }
                            .clip(RoundedCornerShape(30.dp))
                            .border(1.dp, RawColors.PillGrey.copy(alpha = 0.7f), RoundedCornerShape(30.dp)),
                        backgroundColor = RawColors.HeroSurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Typography",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 31.sp,
                                        lineHeight = 33.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = RawColors.TextPrimary
                                    )
                                    Text(
                                        text = "Reel",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 36.sp,
                                        lineHeight = 38.sp,
                                        color = RawColors.TextMuted.copy(alpha = 0.78f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "16:9 1080p MP4 offline",
                                        style = RawTypography.bodyMedium
                                    )
                                }

                                Box(contentAlignment = Alignment.TopEnd) {
                                    PixelTypeMarqueeBadge(size = 98.dp)
                                    PixelSparkle(
                                        size = 13.dp,
                                        phaseOffsetMs = 220,
                                        modifier = Modifier.offset(x = 6.dp, y = (-8).dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            // High-contrast crisp pill button matching design
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(29.dp))
                                    .background(RawColors.PrimaryBtnBg)
                                    .padding(start = 24.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Start creating",
                                    style = RawTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = RawColors.PrimaryBtnText
                                )
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(RawColors.BgPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RawIcon(
                                        type = RawIconType.PLAY,
                                        size = 17.dp,
                                        tint = RawColors.TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            // Playful bottom retro pixel companions above the dock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawPixelMascot(
                        kind = PixelMascotKind.PEEKING_CAT,
                        size = 78.dp,
                        onTap = {
                            scope.launch {
                                kittyLine = "purr~ tap Reels tab for saved drafts"
                                delay(1900)
                                kittyLine = null
                            }
                        }
                    )
                    AnimatedVisibility(
                        visible = kittyLine != null,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(180))
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 26.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(RawColors.CardSurface)
                                .border(1.dp, RawColors.Divider, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = kittyLine.orEmpty(),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = RawColors.TextPrimary
                            )
                        }
                    }
                }

                Box(contentAlignment = Alignment.TopEnd) {
                    RawPixelMascot(
                        kind = PixelMascotKind.FOLDER_PLAY,
                        size = 48.dp
                    )
                    PixelSparkle(
                        size = 11.dp,
                        phaseOffsetMs = 600,
                        modifier = Modifier.offset(x = 6.dp, y = (-8).dp)
                    )
                }
            }
        }
    }
}
