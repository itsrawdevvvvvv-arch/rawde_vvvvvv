package com.rawedit.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.ResultCelebrationOverlay
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun ResultScreen(
    video: VideoEntity,
    cacheManager: CacheManager,
    playCelebration: Boolean,
    onSavedToGallery: (galleryUri: String) -> Unit,
    onEditInTimeline: (projectId: Long) -> Unit,
    onMakeAnother: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()

    var saving by remember { mutableStateOf(false) }
    var saveFeedback by remember { mutableStateOf<String?>(null) }
    var celebratedOnce by remember(video.id) { mutableStateOf(false) }

    val printReveal = remember(video.id) { Animatable(if (playCelebration && animEnabled) 0f else 1f) }
    LaunchedEffect(video.id, playCelebration) {
        if (playCelebration && !celebratedOnce) {
            celebratedOnce = true
            if (animEnabled) {
                printReveal.snapTo(0f)
                printReveal.animateTo(1f, tween(480, easing = RawEasings.SnappyOut))
            }
        }
    }

    val player = remember(video.fileUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(video.fileUri))))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack)
                Text("1920x1080  •  30 fps", style = RawTypography.labelMedium)
            }

            KineticHeading(
                words = listOf("Your", "reel", "is", "ready"),
                style = RawTypography.headlineLarge
            )

            // ExoPlayer 16:9 frame with snapping corner brackets celebration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .graphicsLayer {
                        val p = printReveal.value
                        scaleY = 0.85f + 0.15f * p
                        alpha = p
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = player
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Snapping corner brackets
                val bracketScale = 1f + (1f - printReveal.value) * 0.25f
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pad = 10.dp.toPx() * bracketScale
                    val len = 18.dp.toPx()
                    val sw = 2.5.dp.toPx()
                    val c = RawColors.TextPrimary
                    // TL
                    drawLine(c, Offset(pad, pad), Offset(pad + len, pad), sw, StrokeCap.Round)
                    drawLine(c, Offset(pad, pad), Offset(pad, pad + len), sw, StrokeCap.Round)
                    // TR
                    drawLine(c, Offset(size.width - pad, pad), Offset(size.width - pad - len, pad), sw, StrokeCap.Round)
                    drawLine(c, Offset(size.width - pad, pad), Offset(size.width - pad, pad + len), sw, StrokeCap.Round)
                    // BL
                    drawLine(c, Offset(pad, size.height - pad), Offset(pad + len, size.height - pad), sw, StrokeCap.Round)
                    drawLine(c, Offset(pad, size.height - pad), Offset(pad, size.height - pad - len), sw, StrokeCap.Round)
                    // BR
                    drawLine(c, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - len, size.height - pad), sw, StrokeCap.Round)
                    drawLine(c, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - len), sw, StrokeCap.Round)
                }
            }

            // Title and stats
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(video.title, style = RawTypography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${FormatHelpers.formatDuration(video.durationSec)}  •  ${FormatHelpers.formatFileSize(video.sizeBytes)}  •  MP4 (H.264 + AAC)",
                    style = RawTypography.labelMedium
                )
            }

            saveFeedback?.let { msg ->
                Text(
                    text = msg,
                    style = RawTypography.bodyMedium,
                    color = if (msg.startsWith("Saved")) RawColors.SuccessMint else RawColors.ErrorCoral
                )
            }

            // Save to phone + Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RawPressButton(
                    enabled = !saving && video.savedToGalleryUri == null,
                    onClick = {
                        saving = true
                        scope.launch {
                            val res = cacheManager.saveVideoToPhoneGallery(File(video.fileUri), video.title)
                            saving = false
                            res.onSuccess { uri ->
                                onSavedToGallery(uri.toString())
                                saveFeedback = "Saved to Movies/raw_edit"
                            }.onFailure { err ->
                                saveFeedback = err.message ?: "Could not save video."
                            }
                        }
                    },
                    modifier = Modifier.weight(1.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(29.dp))
                            .background(
                                if (video.savedToGalleryUri != null) RawColors.SuccessMint else RawColors.PrimaryBtnBg
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RawIcon(
                            type = if (video.savedToGalleryUri != null) RawIconType.CHECK else RawIconType.DOWNLOAD,
                            size = 20.dp,
                            tint = RawColors.PrimaryBtnText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                saving -> "Saving..."
                                video.savedToGalleryUri != null -> "Saved"
                                else -> "Save to phone"
                            },
                            style = RawTypography.labelLarge,
                            color = RawColors.PrimaryBtnText
                        )
                    }
                }

                RawPressButton(
                    onClick = {
                        val file = File(video.fileUri)
                        if (file.exists()) {
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share reel"))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(29.dp))
                            .background(RawColors.RaisedCard),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RawIcon(RawIconType.SHARE, size = 20.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share", style = RawTypography.labelLarge)
                    }
                }
            }

            // Edit in multi-track timeline button (right after render)
            if (video.projectId > 0L) {
                RawPressButton(
                    onClick = {
                        player.pause()
                        onEditInTimeline(video.projectId)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(29.dp))
                            .background(RawColors.HeroSurface)
                            .border(1.dp, RawColors.PillGrey, RoundedCornerShape(29.dp))
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RawIcon(RawIconType.LAYERS, size = 20.dp, tint = RawColors.TextPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Edit in timeline (position, images, mask)",
                            style = RawTypography.labelLarge
                        )
                    }
                }
            }

            BigPrimaryButton(
                text = "Make another",
                leadingIcon = RawIconType.PLUS,
                onClick = onMakeAnother
            )
        }

        ResultCelebrationOverlay(
            play = playCelebration,
            modifier = Modifier.fillMaxSize()
        )
    }
}
