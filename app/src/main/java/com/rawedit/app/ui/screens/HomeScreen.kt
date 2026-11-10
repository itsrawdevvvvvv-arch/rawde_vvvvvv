package com.rawedit.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.ui.components.UserAvatarView
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.PixelTypeMarqueeBadge
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TV_LINES = listOf(
    "Beep! Ready when you are.",
    "Voice in, reel out.",
    "No internet needed!",
    "Typography is my love language."
)

@Composable
fun HomeScreen(
    userProfile: UserProfile,
    onOpenProfile: () -> Unit,
    onStartNewReel: (Offset) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    val scope = rememberCoroutineScope()

    val ringDraw = remember { Animatable(if (animEnabled) 0f else 1f) }
    var tvIndex by remember { mutableIntStateOf(0) }
    var catAwake by remember { mutableStateOf(false) }

    LaunchedEffect(animEnabled) {
        if (animEnabled) {
            ringDraw.snapTo(0f)
            ringDraw.animateTo(1f, tween(620, easing = RawEasings.Morph))
        }
    }

    val rayAlpha = if (animEnabled) {
        val inf = rememberInfiniteTransition(label = "home_rays")
        val a by inf.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "ray"
        )
        a
    } else {
        1f
    }

    val zzPhase = if (animEnabled && !catAwake) {
        val inf = rememberInfiniteTransition(label = "cat_zz")
        val z by inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
            label = "z"
        )
        z
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Top minimal header (.bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hello, ${userProfile.userName.ifBlank { "there" }}",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = (-0.8).sp,
                            color = RawColors.TextPrimary
                        )
                        Text(
                            text = "OFFLINE REEL STUDIO",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            letterSpacing = 1.1.sp,
                            color = RawColors.TextMuted
                        )
                    }
                    UserAvatarView(
                        name = userProfile.userName.ifBlank { "R" },
                        photoPath = userProfile.profilePhotoPath,
                        size = 44.dp,
                        onClick = onOpenProfile
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Giant display headline ("Let's make / a reel!") with Mint oval + Mint rays
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KineticHeading(
                        words = listOf("Let's", "make"),
                        style = RawTypography.displayLarge.copy(
                            fontSize = 48.sp,
                            lineHeight = 48.sp,
                            letterSpacing = (-2.4).sp
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "a ",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 48.sp,
                            lineHeight = 48.sp,
                            letterSpacing = (-2.4).sp,
                            color = RawColors.TextPrimary
                        )
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "reel!",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 48.sp,
                                lineHeight = 48.sp,
                                letterSpacing = (-2.4).sp,
                                color = RawColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val sweep = 360f * ringDraw.value
                                drawArc(
                                    color = RawColors.Mint,
                                    startAngle = -140f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(-2.dp.toPx(), 2.dp.toPx()),
                                    size = Size(size.width + 4.dp.toPx(), size.height - 4.dp.toPx()),
                                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        // 3 comic Mint rays beside "reel!"
                        Canvas(
                            modifier = Modifier
                                .offset(x = 2.dp, y = (-18).dp)
                                .size(30.dp)
                        ) {
                            val col = RawColors.Mint.copy(alpha = rayAlpha * ringDraw.value)
                            val sw = 3.2.dp.toPx()
                            drawLine(col, Offset(size.width * 0.45f, size.height * 0.12f), Offset(size.width * 0.45f, size.height * 0.36f), sw, StrokeCap.Square)
                            drawLine(col, Offset(size.width * 0.84f, size.height * 0.24f), Offset(size.width * 0.68f, size.height * 0.42f), sw, StrokeCap.Square)
                            drawLine(col, Offset(size.width * 0.94f, size.height * 0.60f), Offset(size.width * 0.72f, size.height * 0.60f), sw, StrokeCap.Square)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Turn your voice & ideas into\ncrisp 1080p edits.",
                    style = RawTypography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = RawColors.TextMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive TV Bot + speech bubble cycling witty offline quips (.botrow + .bub)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(122.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RawPixelMascot(
                        kind = PixelMascotKind.TV_BOT,
                        size = 104.dp,
                        onTap = { tvIndex = (tvIndex + 1) % TV_LINES.size }
                    )

                    Box(
                        modifier = Modifier
                            .padding(bottom = 52.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 14.dp,
                                    topEnd = 14.dp,
                                    bottomEnd = 14.dp,
                                    bottomStart = 4.dp
                                )
                            )
                            .background(RawColors.RaisedCard)
                            .border(
                                1.dp,
                                RawColors.Divider,
                                RoundedCornerShape(
                                    topStart = 14.dp,
                                    topEnd = 14.dp,
                                    bottomEnd = 14.dp,
                                    bottomStart = 4.dp
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { tvIndex = (tvIndex + 1) % TV_LINES.size }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        AnimatedContent(
                            targetState = TV_LINES[tvIndex],
                            transitionSpec = {
                                if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                else fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                            },
                            label = "tv_bubble"
                        ) { line ->
                            Text(
                                text = line,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = RawColors.TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Single Hero Typography Creation Card (.ctac — zero workspace/list clutter)
                RawPressButton(
                    onClick = { onStartNewReel(Offset(540f, 820f)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(RawColors.CardSurface)
                            .border(1.dp, RawColors.Divider, RoundedCornerShape(24.dp))
                            .padding(18.dp)
                    ) {
                        PixelTypeMarqueeBadge(
                            size = 38.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 6.dp)
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Typography Reel",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp,
                                letterSpacing = (-1.0).sp,
                                color = RawColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = "16:9 1080p MP4 offline",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = RawColors.TextMuted
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Mint primary capsule button with Play badge (.btn + .pc)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(RawColors.Mint)
                                    .padding(horizontal = 22.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Start creating",
                                    style = RawTypography.labelLarge,
                                    color = RawColors.PrimaryBtnText
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(RawColors.PrimaryBtnText),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RawIcon(
                                        type = RawIconType.PLAY,
                                        size = 13.dp,
                                        tint = RawColors.Mint
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Floor companion: Interactive sleeping/waking retro pixel cat (.floor + .zz)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!catAwake) {
                            scope.launch {
                                catAwake = true
                                delay(2800)
                                catAwake = false
                            }
                        }
                    }
                ) {
                    RawPixelMascot(
                        kind = PixelMascotKind.PEEKING_CAT,
                        size = 82.dp,
                        noBlink = !catAwake,
                        awakeOverride = catAwake
                    )
                }

                if (!catAwake && animEnabled) {
                    val a1 = if (zzPhase < 0.2f) zzPhase / 0.2f else 1f - zzPhase
                    Text(
                        text = "Z",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = RawColors.TextMuted,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = 64.dp, y = (-26).dp)
                            .graphicsLayer {
                                translationX = zzPhase * 16f
                                translationY = -zzPhase * 30f
                                scaleX = 1f + zzPhase * 0.45f
                                scaleY = 1f + zzPhase * 0.45f
                                alpha = a1.coerceIn(0f, 1f)
                            }
                    )
                    val p2 = (zzPhase + 0.45f) % 1f
                    val a2 = if (p2 < 0.2f) p2 / 0.2f else 1f - p2
                    Text(
                        text = "z",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = RawColors.TextMuted,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = 76.dp, y = (-34).dp)
                            .graphicsLayer {
                                translationX = p2 * 14f
                                translationY = -p2 * 26f
                                scaleX = 1f + p2 * 0.4f
                                scaleY = 1f + p2 * 0.4f
                                alpha = a2.coerceIn(0f, 1f)
                            }
                    )
                }

                AnimatedVisibility(
                    visible = catAwake,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 34.dp, y = (-42).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomEnd = 12.dp,
                                    bottomStart = 4.dp
                                )
                            )
                            .background(RawColors.RaisedCard)
                            .border(
                                1.dp,
                                RawColors.Divider,
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomEnd = 12.dp,
                                    bottomStart = 4.dp
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Mrrp... five more minutes?",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = RawColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}
