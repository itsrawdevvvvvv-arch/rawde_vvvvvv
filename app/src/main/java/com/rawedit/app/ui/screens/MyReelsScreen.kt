package com.rawedit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.ui.components.DomeButtonKind
import com.rawedit.app.ui.components.DomeChip
import com.rawedit.app.ui.components.DomeChipRow
import com.rawedit.app.ui.components.DomeHeadPart
import com.rawedit.app.ui.components.DomeHeadline
import com.rawedit.app.ui.components.DomeMono
import com.rawedit.app.ui.components.DomeMuted
import com.rawedit.app.ui.components.DomePillButton
import com.rawedit.app.ui.components.RawConfirmDialog
import com.rawedit.app.ui.components.StaggerIn
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.RawColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    var filter by remember { mutableStateOf("All") }
    var renameTarget by remember { mutableStateOf<VideoEntity?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteVideoTarget by remember { mutableStateOf<VideoEntity?>(null) }
    var deleteProjectTarget by remember { mutableStateOf<ProjectEntity?>(null) }

    val drafts = projects
    val finished = videos
    val showDrafts = filter != "Finished"
    val showFinished = filter != "Drafts"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        StaggerIn(0) {
            DomeHeadline(
                parts = listOf(DomeHeadPart.Plain("My "), DomeHeadPart.Tag("reels")),
                size = 44.dp,
                lineHeightFactor = 0.95f
            )
        }

        StaggerIn(1) {
            DomeChipRow {
                listOf("All", "Drafts", "Finished").forEach { f ->
                    DomeChip(text = f, active = filter == f) { filter = f }
                }
            }
        }

        if (showDrafts && drafts.isEmpty() && showFinished && finished.isEmpty()) {
            StaggerIn(2) {
                EmptyReels()
            }
        }

        if (showDrafts) {
            drafts.forEachIndexed { index, project ->
                StaggerIn(2 + index) {
                    ReelCard(
                        badge = "Draft",
                        duration = formatClock(project.audioDurationSec),
                        headline = artLabel(project.title),
                        title = project.title.ifBlank { "Untitled reel" },
                        meta = buildString {
                            append(if (project.code.isNotBlank()) "Code ready" else "Waiting for code")
                            append(" · ")
                            append(relativeDate(project.updatedAt))
                        },
                        dark = true,
                        onOpen = { onOpenProject(project.id, Offset.Zero) },
                        onRename = null,
                        onDelete = { deleteProjectTarget = project }
                    )
                }
            }
        }

        if (showFinished) {
            finished.forEachIndexed { index, video ->
                StaggerIn(2 + drafts.size + index) {
                    ReelCard(
                        badge = "1080p",
                        duration = formatClock(video.durationSec),
                        headline = artLabel(video.title),
                        title = video.title.ifBlank { "Untitled reel" },
                        meta = "${formatSize(video.sizeBytes)} · ${relativeDate(video.createdAt)}",
                        dark = false,
                        onOpen = { onPlayVideo(video.id, Offset.Zero) },
                        onRename = {
                            renameTarget = video
                            renameDraft = video.title
                        },
                        onDelete = { deleteVideoTarget = video }
                    )
                }
            }
        }
    }

    renameTarget?.let { video ->
        RenameReelDialog(
            initial = renameDraft,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRenameVideo(video.id, newName)
                renameTarget = null
            }
        )
    }
    deleteVideoTarget?.let { video ->
        RawConfirmDialog(
            title = "Delete reel?",
            body = "The MP4 will be removed from your phone.",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteVideo(video)
                deleteVideoTarget = null
            },
            onDismiss = { deleteVideoTarget = null }
        )
    }
    deleteProjectTarget?.let { project ->
        RawConfirmDialog(
            title = "Delete draft?",
            body = "This removes the draft from your phone.",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteProject(project)
                deleteProjectTarget = null
            },
            onDismiss = { deleteProjectTarget = null }
        )
    }
}

@Composable
private fun ReelCard(
    badge: String,
    duration: String,
    headline: String,
    title: String,
    meta: String,
    dark: Boolean,
    onOpen: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(
                    if (dark) {
                        Brush.linearGradient(listOf(Color(0xFF101013), Color(0xFF1C1C20)))
                    } else {
                        Brush.linearGradient(listOf(Color(0x33C89F72), Color(0xFF171718)))
                    }
                )
                .clickable { onOpen() }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                DomeChip(text = badge, active = true)
                DomeMono(text = duration, size = 10, color = RawColors.Cream)
            }
            Text(
                text = headline,
                fontFamily = BricolageFamily,
                fontSize = 19.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RawColors.Cream,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.85f)
            )
        }
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                fontFamily = BricolageFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = RawColors.Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpen() }
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DomeMono(text = meta, size = 10.5f.toInt())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onRename != null) {
                        MiniAction(icon = RawIconType.EDIT, tint = RawColors.TextMuted, onClick = onRename)
                    }
                    MiniAction(icon = RawIconType.TRASH, tint = RawColors.Danger, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun MiniAction(icon: RawIconType, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(RawColors.RaisedCard)
            .border(1.dp, RawColors.Divider, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        RawIcon(type = icon, size = 14.dp, tint = tint, strokeWidth = 1.8.dp)
    }
}

@Composable
private fun EmptyReels() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DomeMuted("Nothing here yet — tap + to make your first reel.")
        Spacer(Modifier.height(14.dp))
        DomePillButton(
            text = "Start creating",
            kind = DomeButtonKind.Primary,
            trailingArrow = true,
            onClick = {}
        )
    }
}

private fun artLabel(title: String): String {
    val words = title
        .replace(Regex("[_\\-.]+"), " ")
        .replace(Regex("\\.(mp4|wav|m4a|mp3|aac|mov)$", RegexOption.IGNORE_CASE), "")
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(3)
    if (words.isEmpty()) return "RAW EDIT"
    return words.joinToString(" ") { it.uppercase() }
}

private fun formatClock(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}

private fun relativeDate(millis: Long): String =
    SimpleDateFormat("MMM d", Locale.US).format(Date(millis))

@Composable
private fun RenameReelDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "Rename reel",
                fontFamily = BricolageFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RawColors.Cream
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.rawedit.app.ui.theme.JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    color = RawColors.Cream
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(RawColors.Tan),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RawColors.RaisedCard)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(14.dp))
            DomePillButton(
                text = "Save",
                kind = DomeButtonKind.Primary,
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) }
            )
            Spacer(Modifier.height(8.dp))
            DomePillButton(text = "Cancel", kind = DomeButtonKind.Ghost, onClick = onDismiss)
        }
    }
}
