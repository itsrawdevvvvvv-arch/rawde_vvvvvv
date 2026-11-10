package com.rawedit.app.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.MorphingFilterChips
import com.rawedit.app.ui.components.RawConfirmDialog
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
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
        // Header matching R.reels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                KineticHeading(
                    words = listOf("My", "reels"),
                    style = RawTypography.headlineLarge.copy(fontSize = 30.sp)
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "${inProgressProjects.size} DRAFTS  •  ${videos.size} FINISHED VIDEOS",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.0.sp,
                    color = RawColors.TextMuted
                )
            }
            RawPixelMascot(
                kind = PixelMascotKind.PHANTOM_BUDDY,
                size = 44.dp
            )
        }

        // All / Drafts / Finished Mint filter pills
        Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
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
                        else fadeIn(tween(200, easing = RawEasings.Morph)) togetherWith fadeOut(tween(130))
                    },
                    label = "empty_reels"
                ) { tabIdx ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        RawPixelMascot(
                            kind = PixelMascotKind.PEEKING_CAT,
                            size = 96.dp,
                            noBlink = true
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = when (tabIdx) {
                                1 -> "No drafts yet"
                                2 -> "No finished reels yet"
                                else -> "Nothing here yet"
                            },
                            style = RawTypography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Create to make your first reel. Drafts and finished videos will live here.",
                            style = RawTypography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = displayItems,
                    key = { item ->
                        when (item) {
                            is ProjectEntity -> "proj_${item.id}"
                            is VideoEntity -> "vid_${item.id}"
                            else -> "item_${item.hashCode()}"
                        }
                    }
                ) { item ->
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
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(RawColors.ErrorSoft)
                                            .border(1.dp, RawColors.ErrorBorder, RoundedCornerShape(22.dp))
                                            .padding(end = 22.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        RawIcon(RawIconType.TRASH, size = 20.dp, tint = RawColors.ErrorCoral)
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
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(RawColors.ErrorSoft)
                                            .border(1.dp, RawColors.ErrorBorder, RoundedCornerShape(22.dp))
                                            .padding(end = 22.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        RawIcon(RawIconType.TRASH, size = 20.dp, tint = RawColors.ErrorCoral)
                                    }
                                }
                            ) {
                                VideoLibraryRow(
                                    video = item,
                                    onClick = { onPlayVideo(item.id, Offset(540f, 900f)) },
                                    onLongClick = { actionTarget = item },
                                    onMoreClick = { actionTarget = item },
                                    onDelete = { deleteVideoConfirmTarget = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom Actions Sheet matching .pn.sheet + .srow
    actionTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            containerColor = RawColors.CardSurface,
            contentColor = RawColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = target.title,
                    style = RawTypography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
                Spacer(modifier = Modifier.height(10.dp))
                SheetActionRow(
                    icon = RawIconType.EDIT,
                    label = "Rename reel",
                    onClick = {
                        actionTarget = null
                        renameTarget = target
                    }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RawColors.Divider))
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
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RawColors.Divider))
                SheetActionRow(
                    icon = RawIconType.TRASH,
                    label = "Delete reel",
                    tint = RawColors.ErrorCoral,
                    onClick = {
                        actionTarget = null
                        deleteVideoConfirmTarget = target
                    }
                )
                Spacer(modifier = Modifier.height(22.dp))
            }
        }
    }

    // Custom dark obsidian Rename Dialog (.pn.dlg)
    renameTarget?.let { target ->
        var titleDraft by remember(target.id) { mutableStateOf(target.title) }
        Dialog(
            onDismissRequest = { renameTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.DividerStrong, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Rename reel",
                    style = RawTypography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { if (it.length <= 40) titleDraft = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.MintFocus,
                        unfocusedBorderColor = RawColors.Divider,
                        focusedContainerColor = RawColors.RaisedCard,
                        unfocusedContainerColor = RawColors.RaisedCard,
                        focusedTextColor = RawColors.TextPrimary,
                        unfocusedTextColor = RawColors.TextPrimary,
                        cursorColor = RawColors.Mint
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawSecondaryButton(
                        text = "Cancel",
                        height = 42.dp,
                        onClick = { renameTarget = null },
                        modifier = Modifier.weight(1f)
                    )
                    RawPressButton(
                        onClick = {
                            if (titleDraft.trim().isNotEmpty()) {
                                onRenameVideo(target.id, titleDraft.trim())
                            }
                            renameTarget = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(RawColors.Mint),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Save",
                                style = RawTypography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                color = RawColors.PrimaryBtnText
                            )
                        }
                    }
                }
            }
        }
    }

    deleteVideoConfirmTarget?.let { target ->
        RawConfirmDialog(
            title = "Delete this reel?",
            body = "The MP4 for \"${target.title}\" will be removed from your phone.",
            confirmLabel = "Delete",
            isDanger = true,
            onConfirm = {
                onDeleteVideo(target)
                deleteVideoConfirmTarget = null
            },
            onDismiss = { deleteVideoConfirmTarget = null }
        )
    }

    deleteProjectConfirmTarget?.let { target ->
        RawConfirmDialog(
            title = "Delete this draft?",
            body = "This removes \"${target.title.ifBlank { "Untitled Reel" }}\" from your phone.",
            confirmLabel = "Delete",
            isDanger = true,
            onConfirm = {
                onDeleteProject(target)
                deleteProjectConfirmTarget = null
            },
            onDismiss = { deleteProjectConfirmTarget = null }
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
            .clip(RoundedCornerShape(22.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RawColors.RaisedCard),
            contentAlignment = Alignment.Center
        ) {
            RawPixelMascot(
                kind = PixelMascotKind.CODE_BOT,
                size = 34.dp,
                interactive = false,
                noBob = true,
                noBlink = true
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = project.title.ifBlank { "Untitled Reel" },
                    style = RawTypography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(RawColors.RaisedCard)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "DRAFT",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.6.sp,
                        color = RawColors.TextMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            val sub = buildString {
                if (project.audioDurationSec > 0.0) {
                    append(FormatHelpers.formatDuration(project.audioDurationSec))
                } else {
                    append("No audio yet")
                }
                append("  •  ")
                append(if (project.code.isNotBlank()) "Code ready" else "Waiting for code")
            }
            Text(
                text = sub,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.5.sp,
                color = RawColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        CircleActionButton(
            icon = RawIconType.TRASH,
            size = 36.dp,
            iconTint = RawColors.ErrorCoral,
            onClick = onDelete
        )
        RawIcon(
            type = RawIconType.CHEVRON_RIGHT,
            size = 18.dp,
            tint = RawColors.TextMuted
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoLibraryRow(
    video: VideoEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val hasThumb = remember(video.thumbnailPath) {
        video.thumbnailPath.isNotBlank() && File(video.thumbnailPath).exists()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 16:9 thumbnail decoded cleanly to 320x180 + centered Mint play circle (.thb .pc)
        Box(
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasThumb) {
                val thumbReq = remember(video.thumbnailPath) {
                    ImageRequest.Builder(context)
                        .data(File(video.thumbnailPath))
                        .size(320, 180)
                        .build()
                }
                AsyncImage(
                    model = thumbReq,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                RawIcon(RawIconType.FILM, size = 22.dp, tint = RawColors.TextMuted)
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(RawColors.Mint),
                contentAlignment = Alignment.Center
            ) {
                RawIcon(
                    type = RawIconType.PLAY,
                    size = 12.dp,
                    tint = RawColors.PrimaryBtnText
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = RawTypography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${FormatHelpers.formatDuration(video.durationSec)}  •  ${FormatHelpers.formatFileSize(video.sizeBytes)}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.5.sp,
                color = RawColors.TextMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = FormatHelpers.formatDate(video.createdAt),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.5.sp,
                color = RawColors.TextMuted
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CircleActionButton(
                icon = RawIconType.TRASH,
                size = 36.dp,
                iconTint = RawColors.ErrorCoral,
                onClick = onDelete
            )
            CircleActionButton(
                icon = RawIconType.MORE_VERT,
                size = 36.dp,
                onClick = onMoreClick
            )
        }
    }
}

@Composable
fun SheetActionRow(
    icon: RawIconType,
    label: String,
    tint: Color = RawColors.TextPrimary,
    onClick: () -> Unit
) {
    val isBad = tint == RawColors.ErrorCoral
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isBad) RawColors.ErrorSoft else RawColors.RaisedCard)
                .border(1.dp, if (isBad) RawColors.ErrorBorder else RawColors.Divider, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(type = icon, size = 17.dp, tint = tint)
        }
        Text(
            text = label,
            style = RawTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tint
        )
    }
}
