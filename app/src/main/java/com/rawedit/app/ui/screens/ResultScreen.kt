package com.rawedit.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.RawConfirmDialog
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.FeatheredScrollEdges
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.ResultCelebrationOverlay
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun ResultScreen(
    video: VideoEntity,
    cacheManager: CacheManager,
    playCelebration: Boolean,
    onSavedToGallery: (galleryUri: String) -> Unit,
    onMakeAnother: () -> Unit,
    onDeleteVideo: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()
    val scrollState = rememberScrollState()

    var saving by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var celebratedOnce by remember(video.id) { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(1900)
            toastMsg = null
        }
    }

    val printReveal = remember(video.id) { Animatable(if (playCelebration && animEnabled) 0f else 1f) }
    LaunchedEffect(video.id, playCelebration) {
        if (playCelebration && !celebratedOnce) {
            celebratedOnce = true
            if (animEnabled) {
                printReveal.snapTo(0f)
                printReveal.animateTo(1f, tween(420, easing = RawEasings.SnappyOut))
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
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
                .verticalScroll(scrollState)
                .padding(start = 18.dp, end = 18.dp, bottom = 122.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 48.dp)

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RawColors.RaisedCard)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "1920x1080  \u2022  30 fps",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        color = RawColors.TextMuted
                    )
                }

                CircleActionButton(
                    icon = RawIconType.TRASH,
                    onClick = { showDeleteConfirm = true },
                    size = 48.dp,
                    backgroundColor = RawColors.ErrorSoft,
                    iconTint = RawColors.ErrorCoral
                )
            }

            // Headline + PHANTOM_BUDDY
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                KineticHeading(
                    words = listOf("Your", "reel", "is", "ready"),
                    style = RawTypography.headlineLarge.copy(fontSize = 44.sp, lineHeight = 46.sp),
                    highlightLastWord = true,
                    modifier = Modifier.weight(1f)
                )
                RawPixelMascot(
                    kind = PixelMascotKind.PHANTOM_BUDDY,
                    size = 64.dp
                )
            }

            // Native hardware ExoPlayer 16:9 stage (24dp borderless)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .graphicsLayer {
                        val p = printReveal.value
                        scaleY = 0.88f + 0.12f * p
                        alpha = p
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
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
            }

            // Video metadata card (28dp borderless)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(RawColors.CardSurface)
                    .padding(18.dp)
            ) {
                Text(
                    text = video.title,
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = RawColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${FormatHelpers.formatDuration(video.durationSec)}  \u2022  ${FormatHelpers.formatFileSize(video.sizeBytes)}  \u2022  MP4 (H.264 + AAC)",
                    style = RawTypography.labelMedium
                )
            }

            // Save to phone (morphs Cream -> RaisedCard pill) + Share
            val isSaved = video.savedToGalleryUri != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val saveBg by animateColorAsState(
                    targetValue = if (isSaved) RawColors.RaisedCard else RawColors.Cream,
                    animationSpec = tween(if (animEnabled) 220 else 0, easing = RawEasings.Morph),
                    label = "res_save_bg"
                )
                val saveFg = if (isSaved) RawColors.TextPrimary else RawColors.PrimaryBtnText

                RawPressButton(
                    enabled = !saving && !isSaved,
                    onClick = {
                        saving = true
                        scope.launch {
                            val res = cacheManager.saveVideoToPhoneGallery(File(video.fileUri), video.title)
                            saving = false
                            res.onSuccess { uri ->
                                onSavedToGallery(uri.toString())
                                toastMsg = "Saved to Movies/raw_edit"
                            }.onFailure { err ->
                                toastMsg = err.message ?: "Could not save video"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(CircleShape)
                            .background(saveBg)
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RawIcon(
                            type = if (isSaved) RawIconType.CHECK else RawIconType.DOWNLOAD,
                            size = 17.dp,
                            tint = saveFg
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        val labelStr = when {
                            saving -> "Saving..."
                            isSaved -> "Saved"
                            else -> "Save to phone"
                        }
                        AnimatedContent(
                            targetState = labelStr,
                            transitionSpec = {
                                if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                else fadeIn(tween(180)) togetherWith fadeOut(tween(130))
                            },
                            label = "res_save_lbl"
                        ) { t ->
                            Text(
                                text = t,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = saveFg,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                RawSecondaryButton(
                    text = "Share",
                    height = 58.dp,
                    leadingIcon = RawIconType.SHARE,
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
                )
            }
        }

        FeatheredScrollEdges(
            scrollState = scrollState,
            topHeight = 40.dp,
            bottomHeight = 114.dp,
            alwaysShowBottom = true
        )

        // Sticky gradient bottom CTA
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
            BigPrimaryButton(
                text = "Make another",
                height = 60.dp,
                leadingIcon = RawIconType.PLUS,
                onClick = onMakeAnother
            )
        }

        ResultCelebrationOverlay(
            play = playCelebration,
            modifier = Modifier.fillMaxSize()
        )

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 94.dp)
        )
    }

    if (showDeleteConfirm) {
        RawConfirmDialog(
            title = "Delete this reel?",
            body = "The MP4 will be removed from your phone. Any copy saved to Movies stays untouched.",
            confirmLabel = "Delete",
            isDanger = true,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteVideo()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
