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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.MorphingFilterChips
import com.rawedit.app.ui.components.UserAvatarView
import com.rawedit.app.ui.icons.IllustrationKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawLineArtIllustration
import com.rawedit.app.ui.motion.CascadeSkewCard
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.theme.DotGridSurface
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import java.io.File

@Composable
fun HomeScreen(
    userProfile: UserProfile,
    projects: List<ProjectEntity>,
    videos: List<VideoEntity>,
    onOpenProfile: () -> Unit,
    onStartNewReel: (Offset) -> Unit,
    onOpenProject: (Long, Offset) -> Unit,
    onOpenVideoResult: (Long, Offset) -> Unit
) {
    var filterIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scrollOffsetPx = listState.firstVisibleItemScrollOffset.toFloat()

    val inProgressProjects = remember(projects) {
        projects.filter { it.state == ProjectEntity.STATE_IN_PROGRESS }
    }
    val recentVideo = videos.firstOrNull()

    val displayItems: List<Any> = remember(filterIndex, inProgressProjects, videos) {
        when (filterIndex) {
            1 -> inProgressProjects
            2 -> videos
            else -> inProgressProjects + videos
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Greeting top bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, ${userProfile.userName.ifBlank { "Creator" }}",
                        style = RawTypography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Reel maker",
                        style = RawTypography.bodyMedium
                    )
                }
                UserAvatarView(
                    name = userProfile.userName.ifBlank { "R" },
                    photoPath = userProfile.profilePhotoPath,
                    size = 48.dp,
                    onClick = onOpenProfile
                )
            }
        }

        // Big kinetic heading
        item {
            Column {
                KineticHeading(
                    words = listOf("Let's", "make", "a", "reel!"),
                    style = RawTypography.displayLarge.copy(fontSize = 34.sp)
                )
            }
        }

        // Filter chips
        item {
            MorphingFilterChips(
                options = listOf("All", "In progress", "Finished"),
                selectedIndex = filterIndex,
                onSelect = { filterIndex = it }
            )
        }

        // Large hero card + tilted overlapping recent card
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Hero card
                RawPressButton(onClick = { onStartNewReel(Offset(500f, 700f)) }) {
                    DotGridSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .border(1.dp, RawColors.Divider, RoundedCornerShape(28.dp)),
                        backgroundColor = RawColors.HeroSurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Typography\nReel",
                                        style = RawTypography.headlineLarge.copy(fontSize = 28.sp, lineHeight = 32.sp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "16:9 1080p MP4 on your phone",
                                        style = RawTypography.bodyMedium
                                    )
                                }
                                RawLineArtIllustration(
                                    kind = IllustrationKind.HERO_TYPOGRAPHY,
                                    modifier = Modifier.size(124.dp),
                                    scrollOffsetPx = scrollOffsetPx
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Dark pill button with round white play circle (matches reference layout)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(29.dp))
                                    .background(RawColors.BgPrimary)
                                    .border(1.dp, RawColors.Divider, RoundedCornerShape(29.dp))
                                    .padding(start = 20.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Start creating",
                                    style = RawTypography.labelLarge
                                )
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(RawColors.TextPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RawIcon(
                                        type = RawIconType.PLAY,
                                        size = 18.dp,
                                        tint = RawColors.PrimaryBtnText
                                    )
                                }
                            }
                        }
                    }
                }

                // Slightly overlapping tilted recent reel card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-12).dp)
                        .graphicsLayer {
                            rotationZ = -2.2f
                        }
                        .padding(horizontal = 6.dp)
                ) {
                    if (recentVideo != null) {
                        RecentReelOverlapCard(
                            video = recentVideo,
                            onClick = { onOpenVideoResult(recentVideo.id, Offset(500f, 1100f)) }
                        )
                    } else {
                        EmptyRecentOverlapCard(
                            onClick = { onStartNewReel(Offset(500f, 1100f)) }
                        )
                    }
                }
            }
        }

        // Filtered projects / finished videos list
        if (displayItems.isNotEmpty()) {
            item {
                Text(
                    text = when (filterIndex) {
                        1 -> "In progress"
                        2 -> "Finished reels"
                        else -> "Your workspace"
                    },
                    style = RawTypography.titleLarge
                )
            }

            itemsIndexed(
                items = displayItems,
                key = { idx, item ->
                    when (item) {
                        is ProjectEntity -> "p_${item.id}_$idx"
                        is VideoEntity -> "v_${item.id}_$idx"
                        else -> "x_$idx"
                    }
                }
            ) { index, item ->
                CascadeSkewCard(index = index) {
                    when (item) {
                        is ProjectEntity -> ProjectSummaryRow(
                            project = item,
                            onClick = { onOpenProject(item.id, Offset(540f, 1200f)) }
                        )
                        is VideoEntity -> FinishedVideoRow(
                            video = item,
                            onClick = { onOpenVideoResult(item.id, Offset(540f, 1200f)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentReelOverlapCard(
    video: VideoEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RawColors.RaisedCard)
            .border(1.dp, RawColors.PillGrey, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RawColors.CardSurface),
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
                text = "RECENT REEL",
                style = RawTypography.labelSmall,
                color = RawColors.TextMuted
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = video.title,
                style = RawTypography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = FormatHelpers.formatDuration(video.durationSec),
                style = RawTypography.labelMedium
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(RawColors.TextPrimary),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(RawIconType.PLAY, size = 18.dp, tint = RawColors.PrimaryBtnText)
        }
    }
}

@Composable
private fun EmptyRecentOverlapCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RawLineArtIllustration(
            kind = IllustrationKind.EMPTY_REELS,
            modifier = Modifier.size(64.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "No finished reels yet",
                style = RawTypography.titleMedium
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Tap Start creating above to turn your voiceover into a video.",
                style = RawTypography.bodyMedium
            )
        }
    }
}

@Composable
private fun ProjectSummaryRow(
    project: ProjectEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = project.title.ifBlank { "Typography Reel" },
                    style = RawTypography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                .size(44.dp)
                .clip(CircleShape)
                .background(RawColors.RaisedCard),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(RawIconType.ARROW_RIGHT, size = 18.dp)
        }
    }
}

@Composable
private fun FinishedVideoRow(
    video: VideoEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(10.dp))
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
                RawIcon(RawIconType.FILM, size = 20.dp, tint = RawColors.TextMuted)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = RawTypography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${FormatHelpers.formatDuration(video.durationSec)}  •  ${FormatHelpers.formatFileSize(video.sizeBytes)}",
                style = RawTypography.labelMedium
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(RawColors.TextPrimary),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(RawIconType.PLAY, size = 18.dp, tint = RawColors.PrimaryBtnText)
        }
    }
}
