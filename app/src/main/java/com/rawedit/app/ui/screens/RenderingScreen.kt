package com.rawedit.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rawedit.app.render.RenderService
import com.rawedit.app.render.RenderStage
import com.rawedit.app.render.RenderStateHolder
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.theme.InterFontFamily
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

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        RenderService.start(context, projectId)
    }

    // Keep screen awake while rendering screen is active (Section 3.6)
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(projectId) {
        if (state.projectId != projectId ||
            state.stage == RenderStage.IDLE ||
            state.stage == RenderStage.CANCELLED
        ) {
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
        }
    }

    LaunchedEffect(state.stage, state.completedVideoId) {
        if (state.stage == RenderStage.DONE && state.completedVideoId != null) {
            onCompleted(state.completedVideoId!!)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Creating your reel",
                style = RawTypography.headlineLarge
            )

            // Live 16:9 preview frame of what is being drawn
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = state.previewBitmap
                if (bmp != null) {
                    val imgBmp = remember(bmp) { bmp.asImageBitmap() }
                    Image(
                        bitmap = imgBmp,
                        contentDescription = "Live render preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    RawIcon(RawIconType.FILM, size = 36.dp, tint = RawColors.TextMuted)
                }
            }

            // Giant ghost word filling bottom-to-top with real progress
            val fraction = (state.progressPercent / 100f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(RawColors.HeroSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Faded ghost word
                Text(
                    text = "RENDERING",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 48.sp,
                    letterSpacing = (-1.5).sp,
                    color = RawColors.TextPrimary.copy(alpha = 0.14f),
                    maxLines = 1
                )
                // White filled portion rising bottom-to-top
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(110.dp * fraction)
                        .clipToBounds(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "RENDERING",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 48.sp,
                            letterSpacing = (-1.5).sp,
                            color = RawColors.TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Mono status line + elapsed / ETA (after 5%)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.statusText,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 14.sp,
                        color = RawColors.TextPrimary
                    )
                    Text(
                        text = "${state.progressPercent}%",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = RawColors.TextPrimary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Elapsed: ${FormatHelpers.formatDuration(state.elapsedSeconds.toDouble())}",
                        style = RawTypography.labelMedium
                    )
                    val eta = state.etaSeconds
                    if (state.progressPercent >= 5 && eta != null && state.stage == RenderStage.DRAWING_FRAMES) {
                        Text(
                            text = "About ${FormatHelpers.formatDuration(eta.toDouble())} left",
                            style = RawTypography.labelMedium
                        )
                    }
                }
            }

            if (state.stage == RenderStage.ERROR) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RawColors.ErrorCoral.copy(alpha = 0.14f))
                        .border(1.dp, RawColors.ErrorCoral.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = state.errorMessage ?: "Rendering failed. Your project is saved.",
                        style = RawTypography.bodyMedium,
                        color = RawColors.ErrorCoral
                    )
                }
            }
        }

        if (state.stage == RenderStage.ERROR || state.stage == RenderStage.CANCELLED) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigPrimaryButton(
                    text = "Try again",
                    leadingIcon = RawIconType.BOLT,
                    onClick = { RenderService.start(context, projectId) }
                )
                RawPressButton(onClick = onCancelledOrBack) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(27.dp))
                            .background(RawColors.RaisedCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Back to steps", style = RawTypography.labelLarge)
                    }
                }
            }
        } else {
            RawPressButton(
                onClick = {
                    RenderService.cancel(context)
                    onCancelledOrBack()
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(29.dp))
                        .background(RawColors.RaisedCard)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(29.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", style = RawTypography.labelLarge, color = RawColors.ErrorCoral)
                }
            }
        }
    }
}
