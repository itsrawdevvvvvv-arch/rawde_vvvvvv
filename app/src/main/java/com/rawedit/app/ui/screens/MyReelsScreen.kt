package com.rawedit.app.ui.screens

import android.content.Intent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.icons.IllustrationKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawLineArtIllustration
import com.rawedit.app.ui.motion.CascadeSkewCard
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MyReelsScreen(
    videos: List<VideoEntity>,
    onPlayVideo: (Long, Offset) -> Unit,
    onRenameVideo: (Long, String) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit
) {
    val context = LocalContext.current
    var actionTarget by remember { mutableStateOf<VideoEntity?>(null) }
    var renameTarget by remember { mutableStateOf<VideoEntity?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<VideoEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            KineticHeading(
                words = listOf("My", "reels"),
                style = RawTypography.displayLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (videos.isEmpty()) "Your rendered MP4 videos live here" else "${videos.size} finished videos",
                style = RawTypography.bodyMedium
            )
        }

        if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    RawLineArtIllustration(
                        kind = IllustrationKind.EMPTY_REELS,
                        modifier = Modifier.size(180.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No reels yet", style = RawTypography.headlineMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Finished 1080p reels appear here. Long-press or swipe any item later to manage it.",
                        style = RawTypography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(videos, key = { _, v -> v.id }) { index, video ->
                    val dismissState = rememberSwipeToDismissBoxState()

                    LaunchedEffect(deleteConfirmTarget) {
                        if (deleteConfirmTarget == null && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                            dismissState.reset()
                        }
                    }

                    Box(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(240, easing = RawEasings.Morph),
                            fadeOutSpec = tween(180),
                            placementSpec = spring(dampingRatio = 0.8f, stiffness = 360f)
                        )
                    ) {
                        CascadeSkewCard(index = index) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                onDismiss = { direction ->
                                    if (direction == SwipeToDismissBoxValue.EndToStart) {
                                        deleteConfirmTarget = video
                                    }
                                },
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(RawColors.ErrorCoral.copy(alpha = 0.22f))
                                            .padding(end = 22.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        RawIcon(RawIconType.TRASH, size = 22.dp, tint = RawColors.ErrorCoral)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(RawColors.CardSurface)
                                        .border(1.dp, RawColors.Divider.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                                        .combinedClickable(
                                            onClick = { onPlayVideo(video.id, Offset(540f, 900f)) },
                                            onLongClick = { actionTarget = video }
                                        )
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(96.dp)
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(RawColors.HeroSurface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (video.thumbnailPath.isNotBlank() && File(video.thumbnailPath).exists()) {
                                            AsyncImage(
                                                model = File(video.thumbnailPath),
                                                contentDescription = video.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            RawIcon(RawIconType.FILM, size = 24.dp, tint = RawColors.TextMuted)
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = video.title,
                                            style = RawTypography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${FormatHelpers.formatDuration(video.durationSec)}  •  ${FormatHelpers.formatFileSize(video.sizeBytes)}",
                                            style = RawTypography.labelMedium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = FormatHelpers.formatDate(video.createdAt),
                                            style = RawTypography.labelSmall
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(RawColors.TextPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RawIcon(RawIconType.PLAY, size = 16.dp, tint = RawColors.PrimaryBtnText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            containerColor = RawColors.HeroSurface,
            contentColor = RawColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(target.title, style = RawTypography.headlineMedium)
                SheetActionRow(
                    icon = RawIconType.EDIT,
                    label = "Rename reel",
                    onClick = {
                        actionTarget = null
                        renameTarget = target
                    }
                )
                SheetActionRow(
                    icon = RawIconType.SHARE,
                    label = "Share MP4",
                    onClick = {
                        actionTarget = null
                        val file = File(target.fileUri)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share reel"))
                        }
                    }
                )
                SheetActionRow(
                    icon = RawIconType.TRASH,
                    label = "Delete reel",
                    tint = RawColors.ErrorCoral,
                    onClick = {
                        actionTarget = null
                        deleteConfirmTarget = target
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    renameTarget?.let { target ->
        var titleDraft by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = RawColors.HeroSurface,
            titleContentColor = RawColors.TextPrimary,
            textContentColor = RawColors.TextPrimary,
            title = { Text("Rename reel") },
            text = {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { if (it.length <= 60) titleDraft = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.TextPrimary,
                        unfocusedBorderColor = RawColors.Divider,
                        focusedTextColor = RawColors.TextPrimary,
                        unfocusedTextColor = RawColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (titleDraft.trim().isNotEmpty()) {
                            onRenameVideo(target.id, titleDraft.trim())
                        }
                        renameTarget = null
                    }
                ) {
                    Text("Save", color = RawColors.TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = RawColors.TextMuted)
                }
            }
        )
    }

    deleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            containerColor = RawColors.HeroSurface,
            titleContentColor = RawColors.TextPrimary,
            textContentColor = RawColors.TextMuted,
            title = { Text("Delete this reel?") },
            text = { Text("This deletes \"${target.title}\" from raw_edit. Any copy already saved to your phone's Movies folder stays untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteVideo(target)
                        deleteConfirmTarget = null
                    }
                ) {
                    Text("Delete", color = RawColors.ErrorCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Cancel", color = RawColors.TextPrimary)
                }
            }
        )
    }
}

@Composable
fun SheetActionRow(
    icon: RawIconType,
    label: String,
    tint: androidx.compose.ui.graphics.Color = RawColors.TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RawColors.CardSurface)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RawIcon(type = icon, size = 20.dp, tint = tint)
        Text(text = label, style = RawTypography.titleMedium, color = tint)
    }
}
