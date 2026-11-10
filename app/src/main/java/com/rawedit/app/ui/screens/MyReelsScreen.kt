package com.rawedit.app.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.MorphingFilterChips
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.PixelSparkle
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.CascadeSkewCard
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MyReelsScreen(
    projects: List<ProjectEntity>,
    videos: List<VideoEntity>,
    onOpenProject: (Long, Offset) -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit,
    onPlayVideo: (Long, Offset) -> Unit,
    onRenameVideo: (Long, String) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit
) {
    val context = LocalContext.current
    val animEnabled = rememberAnimationsEnabled()

    var filterIndex by remember { mutableIntStateOf(0) }
    var actionTarget by remember { mutableStateOf<VideoEntity?>(null) }
    var renameTarget by remember { mutableStateOf<VideoEntity?>(null) }
    var deleteVideoConfirmTarget by remember { mutableStateOf<VideoEntity?>(null) }
    var deleteProjectConfirmTarget by remember { mutableStateOf<ProjectEntity?>(null) }

    val inProgressProjects = remember(projects) {
        projects.filter { it.state == ProjectEntity.STATE_IN_PROGRESS }
    }
    val displayItems: List<Any> = remember(filterIndex, inProgressProjects, videos) {
        when (filterIndex) {
            1 -> inProgressProjects
            2 -> videos
            else -> inProgressProjects + videos
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
    ) {
        // Header with animated pixel mascot buddy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                KineticHeading(
                    words = listOf("My", "reels"),
                    style = RawTypography.displayLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${inProgressProjects.size} drafts  •  ${videos.size} finished videos",
                    style = RawTypography.bodyMedium
                )
            }
            Box(contentAlignment = Alignment.TopEnd) {
                RawPixelMascot(
                    kind = PixelMascotKind.PHANTOM_BUDDY,
                    size = 52.dp
                )
                PixelSparkle(size = 11.dp, phaseOffsetMs = 180)
            }
        }

        // All / Drafts / Finished morphing filter chips (all workspace & reels organized here!)
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            MorphingFilterChips(
                options = listOf("All", "Drafts", "Finished"),
                selectedIndex = filterIndex,
                counts = listOf(inProgressProjects.size + videos.size, inProgressProjects.size, videos.size),
                onSelect = { filterIndex = it }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (displayItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = filterIndex,
                    transitionSpec = {
                        if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else fadeIn(tween(220, easing = RawEasings.Morph)) togetherWith fadeOut(tween(140))
                    },
                    label = "empty_reels_morph"
                ) { tabIdx ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PixelSparkle(size = 14.dp, phaseOffsetMs = 100)
                            RawPixelMascot(kind = PixelMascotKind.PEEKING_CAT, size = 92.dp)
                            PixelSparkle(size = 14.dp, phaseOffsetMs = 500)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (tabIdx) {
                                1 -> "No drafts yet"
                                2 -> "No finished reels yet"
                                else -> "Nothing in your library yet"
                            },
                            style = RawTypography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Start creating on Home or the + tab. All drafts and 1080p MP4 reels live here.",
                            style = RawTypography.bodyMedium
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 114.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = displayItems,
                    key = { _, item ->
                        when (item) {
                            is ProjectEntity -> "proj_${item.id}"
                            is VideoEntity -> "vid_${item.id}"
                            else -> "item_${item.hashCode()}"
                        }
                    }
                ) { index, item ->
                    Box(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(240, easing = RawEasings.Morph),
                            fadeOutSpec = tween(180),
                            placementSpec = spring(dampingRatio = 0.8f, stiffness = 360f)
                        )
                    ) {
                        CascadeSkewCard(index = index) {
                            when (item) {
                                is ProjectEntity -> {
                                    val dismissState = rememberSwipeToDismissBoxState()
                                    LaunchedEffect(deleteProjectConfirmTarget) {
                                        if (deleteProjectConfirmTarget == null && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                            dismissState.reset()
                                        }
                                    }
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = false,
                                        onDismiss = { direction ->
                                            if (direction == SwipeToDismissBoxValue.EndToStart) {
                                                deleteProjectConfirmTarget = item
                                            }
                                        },
                                        backgroundContent = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(RawColors.ErrorCoral.copy(alpha = 0.22f))
                                                    .padding(end = 22.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                RawIcon(RawIconType.TRASH, size = 22.dp, tint = RawColors.ErrorCoral)
                                            }
                                        }
                                    ) {
                                        DraftProjectRow(
                                            project = item,
                                            onOpen = { onOpenProject(item.id, Offset(540f, 900f)) },
                                            onDelete = { deleteProjectConfirmTarget = item }
                                        )
                                    }
                                }

                                is VideoEntity -> {
                                    val dismissState = rememberSwipeToDismissBoxState()
                                    LaunchedEffect(deleteVideoConfirmTarget) {
                                        if (deleteVideoConfirmTarget == null && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                            dismissState.reset()
                                        }
                                    }
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = false,
                                        onDismiss = { direction ->
                                            if (direction == SwipeToDismissBoxValue.EndToStart) {
                                                deleteVideoConfirmTarget = item
                                            }
                                        },
                                        backgroundContent = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(RawColors.ErrorCoral.copy(alpha = 0.22f))
                                                    .padding(end = 22.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                RawIcon(RawIconType.TRASH, size = 22.dp, tint = RawColors.ErrorCoral)
                                            }
                                        }
                                    ) {
                                        VideoLibraryRow(
                                            video = item,
                                            onClick = { onPlayVideo(item.id, Offset(540f, 900f)) },
                                            onLongClick = { actionTarget = item },
                                            onDelete = { deleteVideoConfirmTarget = item }
                                        )
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
                        deleteVideoConfirmTarget = target
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

    deleteVideoConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteVideoConfirmTarget = null },
            containerColor = RawColors.HeroSurface,
            titleContentColor = RawColors.TextPrimary,
            textContentColor = RawColors.TextMuted,
            title = { Text("Delete this reel?") },
            text = { Text("Deletes \"${target.title}\" and its local MP4 from raw_edit. Any copy saved to your phone's Movies folder stays untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteVideo(target)
                        deleteVideoConfirmTarget = null
                    }
                ) {
                    Text("Delete", color = RawColors.ErrorCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteVideoConfirmTarget = null }) {
                    Text("Cancel", color = RawColors.TextPrimary)
                }
            }
        )
    }

    deleteProjectConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteProjectConfirmTarget = null },
            containerColor = RawColors.HeroSurface,
            titleContentColor = RawColors.TextPrimary,
            textContentColor = RawColors.TextMuted,
            title = { Text("Delete this draft?") },
            text = { Text("Deletes project \"${target.title.ifBlank { "Untitled Reel" }}\", its imported audio, subtitles, and HTML code from raw_edit.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProject(target)
                        deleteProjectConfirmTarget = null
                    }
                ) {
                    Text("Delete", color = RawColors.ErrorCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteProjectConfirmTarget = null }) {
                    Text("Cancel", color = RawColors.TextPrimary)
                }
            }
        )
    }
}

@Composable
private fun DraftProjectRow(
    project: ProjectEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RawColors.HeroSurface),
            contentAlignment = Alignment.Center
        ) {
            RawPixelMascot(
                kind = PixelMascotKind.CODE_BOT,
                size = 34.dp,
                interactive = false
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = project.title.ifBlank { "Untitled Reel" },
                    style = RawTypography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RawColors.RaisedCard)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Draft",
                        style = RawTypography.labelSmall,
                        color = RawColors.TextPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val sub = buildString {
                if (project.audioDurationSec > 0.0) {
                    append(FormatHelpers.formatDuration(project.audioDurationSec))
                } else {
                    append("No audio yet")
                }
                append("  •  ")
                append(if (project.code.isNotBlank()) "Code ready" else "Waiting for code")
            }
            Text(text = sub, style = RawTypography.bodyMedium)
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(RawColors.ErrorCoral.copy(alpha = 0.14f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(RawIconType.TRASH, size = 16.dp, tint = RawColors.ErrorCoral)
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(RawColors.RaisedCard),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(RawIconType.ARROW_RIGHT, size = 16.dp)
        }
    }
}

@Composable
private fun VideoLibraryRow(
    video: VideoEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(88.dp)
                .height(52.dp)
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
                RawIcon(RawIconType.FILM, size = 22.dp, tint = RawColors.TextMuted)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = RawTypography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
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
                .size(38.dp)
                .clip(CircleShape)
                .background(RawColors.ErrorCoral.copy(alpha = 0.14f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(RawIconType.TRASH, size = 16.dp, tint = RawColors.ErrorCoral)
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(RawColors.TextPrimary),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(RawIconType.PLAY, size = 15.dp, tint = RawColors.PrimaryBtnText)
        }
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
