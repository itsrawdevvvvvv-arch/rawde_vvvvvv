package com.rawedit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** One entry in the reel carousel — a draft project or a rendered video. */
private data class ReelEntry(
    val id: Long,
    val isDraft: Boolean,
    val badge: String,
    val duration: String,
    val headline: String,
    val title: String,
    val meta: String,
    val thumbnail: String?,
    val project: ProjectEntity? = null,
    val video: VideoEntity? = null
)

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

    val entries = remember(projects, videos, filter) {
        buildList {
            if (filter != "Finished") {
                projects.forEach { p ->
                    add(
                        ReelEntry(
                            id = p.id,
                            isDraft = true,
                            badge = "Draft",
                            duration = formatClock(p.audioDurationSec),
                            headline = artLabel(p.title),
                            title = p.title.ifBlank { "Untitled reel" },
                            meta = buildString {
                                append(if (p.code.isNotBlank()) "Code ready" else "Waiting for code")
                                append(" · ")
                                append(shortDate(p.updatedAt))
                            },
                            thumbnail = null,
                            project = p
                        )
                    )
                }
            }
            if (filter != "Drafts") {
                videos.forEach { v ->
                    add(
                        ReelEntry(
                            id = v.id,
                            isDraft = false,
                            badge = "1080p",
                            duration = formatClock(v.durationSec),
                            headline = artLabel(v.title),
                            title = v.title.ifBlank { "Untitled reel" },
                            meta = "${formatSize(v.sizeBytes)} · ${shortDate(v.createdAt)}",
                            thumbnail = v.thumbnailPath.takeIf { it.isNotBlank() && File(it).exists() },
                            video = v
                        )
                    )
                }
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { entries.size.coerceAtLeast(1) })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        StaggerIn(0) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                DomeHeadline(
                    parts = listOf(DomeHeadPart.Plain("My "), DomeHeadPart.Tag("reels")),
                    size = 44.dp,
                    lineHeightFactor = 0.95f
                )
            }
        }

        StaggerIn(1) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                DomeChipRow {
                    listOf("All", "Drafts", "Finished").forEach { f ->
                        DomeChip(text = f, active = filter == f) { filter = f }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(18.dp))
                DomeMuted("Nothing here yet — tap + to make your first reel.")
                Spacer(Modifier.height(14.dp))
                DomePillButton(
                    text = "Start creating",
                    kind = DomeButtonKind.Primary,
                    trailingArrow = true,
                    onClick = {}
                )
            }
        } else {
            StaggerIn(2) {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val entry = entries.getOrNull(page) ?: return@HorizontalPager
                    ReelCarouselCard(
                        entry = entry,
                        onOpen = {
                            if (entry.isDraft) {
                                entry.project?.let { onOpenProject(it.id, Offset.Zero) }
                            } else {
                                entry.video?.let { onPlayVideo(it.id, Offset.Zero) }
                            }
                        },
                        onRename = {
                            entry.video?.let {
                                renameTarget = it
                                renameDraft = it.title
                            }
                        },
                        onDelete = {
                            if (entry.isDraft) {
                                entry.project?.let { deleteProjectTarget = it }
                            } else {
                                entry.video?.let { deleteVideoTarget = it }
                            }
                        }
                    )
                }
            }

            StaggerIn(3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    entries.indices.forEach { index ->
                        val active = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (active) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (active) RawColors.Tan else RawColors.DividerStrong)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    DomeMono(
                        text = "${pagerState.currentPage + 1} / ${entries.size} · swipe to browse",
                        size = 10
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
private fun ReelCarouselCard(
    entry: ReelEntry,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(26.dp))
    ) {
        // ---- thumbnail stage: the actual reel frame sits behind the labels
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(
                    if (entry.isDraft) {
                        Brush.linearGradient(listOf(Color(0xFF101013), Color(0xFF1C1C20)))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF23231F), Color(0xFF141416)))
                    }
                )
                .clickable { onOpen() }
        ) {
            val thumb = entry.thumbnail
            if (thumb != null) {
                AsyncImage(
                    model = File(thumb),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // scrim so the labels stay readable over real footage
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x66000000),
                                    Color(0x00000000),
                                    Color(0xB3000000)
                                )
                            )
                        )
                )
            } else {
                // no rendered frame yet (draft): show the art label on a soft grid
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0x33C89F72), Color(0x00000000))
                            )
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                DomeChip(text = entry.badge, active = true)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = entry.duration,
                        fontFamily = com.rawedit.app.ui.theme.JetBrainsMonoFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = RawColors.Cream
                    )
                }
            }

            Text(
                text = entry.headline,
                fontFamily = BricolageFamily,
                fontSize = 22.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RawColors.Cream,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .fillMaxWidth(0.9f)
            )

            // play affordance for finished reels
            if (!entry.isDraft) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0x59F3EFE6)),
                    contentAlignment = Alignment.Center
                ) {
                    RawIcon(type = RawIconType.PLAY, size = 22.dp, tint = RawColors.Cream)
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = entry.title,
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
                DomeMono(text = entry.meta, size = 10)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!entry.isDraft) {
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

/** Shared action row used by the profile and reels bottom sheets. */
@Composable
fun SheetActionRow(
    icon: RawIconType,
    label: String,
    tint: Color = RawColors.TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RawIcon(type = icon, size = 17.dp, tint = tint, strokeWidth = 1.9.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = BricolageFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

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

private fun shortDate(millis: Long): String =
    SimpleDateFormat("MMM d", Locale.US).format(Date(millis))
