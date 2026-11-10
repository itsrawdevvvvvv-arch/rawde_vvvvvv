package com.rawedit.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rawedit.app.render.RenderService
import com.rawedit.app.render.RenderStage
import com.rawedit.app.render.RenderStateHolder
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.RawConfirmDialog
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.theme.BricolageCondensedFamily
import com.rawedit.app.ui.theme.InstrumentSerifFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography

@Composable
fun RenderingScreen(
    projectId: Long,
    onCompleted: (Long) -> Unit,
    onCancelledOrBack: () -> Unit
) {
    val context = LocalContext.current
    val state by RenderStateHolder.state.collectAsState()
    var showCancelConfirm by remember { mutableStateOf(false) }
    var readyForCompletion by remember(projectId) { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        RenderService.start(context, projectId)
    }

    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(projectId) {
        val cur = RenderStateHolder.state.value
        val alreadyRunningForProject = cur.projectId == projectId && (
            cur.stage == RenderStage.PREPARING ||
                cur.stage == RenderStage.DRAWING_FRAMES ||
                cur.stage == RenderStage.ADDING_VOICE ||
                cur.stage == RenderStage.MUXING
            )
        if (!alreadyRunningForProject) {
            RenderStateHolder.resetForNewRender(projectId)
            readyForCompletion = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    RenderService.start(context, projectId)
                }
            } else {
                RenderService.start(context, projectId)
            }
        } else {
            readyForCompletion = true
        }
    }

    LaunchedEffect(readyForCompletion, state.projectId, state.stage, state.completedVideoId) {
        if (readyForCompletion &&
            state.projectId == projectId &&
            state.stage == RenderStage.DONE &&
            state.completedVideoId != null
        ) {
            onCompleted(state.completedVideoId!!)
        }
    }

    val isFailed = readyForCompletion && (state.stage == RenderStage.ERROR || state.stage == RenderStage.CANCELLED)

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
                .padding(start = 18.dp, end = 18.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header (.bar + .hi)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Creating your reel",
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 31.sp,
                    lineHeight = 33.sp,
                    letterSpacing = (-0.3).sp,
                    color = RawColors.TextPrimary
                )
                RawPixelMascot(kind = PixelMascotKind.TV_BOT, size = 40.dp)
            }

            if (isFailed) {
                // Error empty state matching R.render err
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawPixelMascot(kind = PixelMascotKind.CODE_BOT, size = 86.dp)
                    Text(
                        text = if (state.stage == RenderStage.CANCELLED) "Render cancelled" else "Render failed",
                        fontFamily = InstrumentSerifFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 34.sp,
                        lineHeight = 36.sp,
                        color = RawColors.TextPrimary
                    )
                    Text(
                        text = state.errorMessage ?: "Your files are safe on your phone.",
                        style = RawTypography.bodyLarge.copy(fontSize = 16.sp),
                        color = RawColors.TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BigPrimaryButton(
                            text = "Try again",
                            height = 60.dp,
                            onClick = {
                                RenderStateHolder.resetForNewRender(projectId)
                                RenderService.start(context, projectId)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        RawSecondaryButton(
                            text = "Back to steps",
                            height = 60.dp,
                            onClick = onCancelledOrBack,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Live 16:9 frame preview (.fr)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = state.previewBitmap
                    if (bmp != null && !bmp.isRecycled) {
                        val imgBmp = remember(bmp) { bmp.asImageBitmap() }
                        Image(
                            bitmap = imgBmp,
                            contentDescription = "Live render preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        RawIcon(RawIconType.FILM, size = 34.dp, tint = RawColors.TextMuted)
                    }
                }

                // Giant condensed "REN / DER / ING" + 4dp Mint progress underline (.gi:after)
                val targetFraction = (state.progressPercent / 100f).coerceIn(0f, 1f)
                val smoothFractionState = animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = tween(durationMillis = 200),
                    label = "gi_fill"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "REN\nDER\nING",
                            fontFamily = BricolageCondensedFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 74.sp,
                            lineHeight = 68.sp,
                            letterSpacing = (-2.0).sp,
                            textAlign = TextAlign.Center,
                            color = RawColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        ) {
                            val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            drawRoundRect(
                                color = RawColors.PillGrey,
                                topLeft = Offset.Zero,
                                size = size,
                                cornerRadius = r
                            )
                            val fillW = (size.width * smoothFractionState.value).coerceIn(0f, size.width)
                            if (fillW > 0f) {
                                drawRoundRect(
                                    color = RawColors.Mint,
                                    topLeft = Offset.Zero,
                                    size = Size(fillW, size.height),
                                    cornerRadius = r
                                )
                            }
                        }
                    }
                }

                // Telemetry card (.tele) with PHANTOM_BUDDY top-right
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    RawPixelMascot(
                        kind = PixelMascotKind.PHANTOM_BUDDY,
                        size = 34.dp,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 42.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = state.statusText,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = RawColors.TextMuted
                        )
                        Text(
                            text = "${state.progressPercent}%",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 22.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = RawColors.TextPrimary
                        )
                        val etaText = if (state.progressPercent >= 5 && state.etaSeconds != null && state.stage == RenderStage.DRAWING_FRAMES) {
                            "  \u2022  About ${FormatHelpers.formatDuration(state.etaSeconds!!.toDouble())} left"
                        } else ""
                        Text(
                            text = "Elapsed: ${FormatHelpers.formatDuration(state.elapsedSeconds.toDouble())}$etaText",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = RawColors.TextMuted
                        )
                    }
                }
            }
        }

        if (!isFailed) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, RawColors.BgPrimary.copy(alpha = 0.94f), RawColors.BgPrimary)
                        )
                    )
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp)
            ) {
                RawSecondaryButton(
                    text = "Cancel",
                    height = 60.dp,
                    onClick = { showCancelConfirm = true }
                )
            }
        }
    }

    if (showCancelConfirm) {
        RawConfirmDialog(
            title = "Cancel rendering?",
            body = "Progress will be lost.",
            confirmLabel = "Cancel render",
            isDanger = true,
            onConfirm = {
                showCancelConfirm = false
                RenderService.cancel(context)
                onCancelledOrBack()
            },
            onDismiss = { showCancelConfirm = false }
        )
    }
}
