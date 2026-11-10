package com.rawedit.app.ui.screens

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
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.DotGridSurface
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
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
    val animEnabled = rememberAnimationsEnabled()
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Minimal greeting top bar echoing Phone 2
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
                        text = "Offline Reel Studio",
                        style = RawTypography.bodyMedium
                    )
                }
                UserAvatarView(
                    name = userProfile.userName.ifBlank { "R" },
                    photoPath = userProfile.profilePhotoPath,
                    size = 46.dp,
                    onClick = onOpenProfile
                )
            }
        }

        // Crisp 2-line minimal display heading
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KineticHeading(
                    words = listOf("Let's", "make"),
                    style = RawTypography.displayLarge.copy(fontSize = 33.sp, lineHeight = 38.sp)
                )
                KineticHeading(
                    words = listOf("a", "reel!"),
                    style = RawTypography.displayLarge.copy(fontSize = 33.sp, lineHeight = 38.sp)
                )
            }
        }

        // Minimal pill filters with inline numeric counts (Phone 2 layout)
        item {
            MorphingFilterChips(
                options = listOf("All", "Drafts", "Finished"),
                selectedIndex = filterIndex,
                counts = listOf(inProgressProjects.size + videos.size, inProgressProjects.size, videos.size),
                onSelect = { filterIndex = it }
            )
        }

        // Featured hero card + tilted overlapping second card (Phone 2 layout)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                RawPressButton(onClick = { onStartNewReel(Offset(500f, 700f)) }) {
                    DotGridSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .border(1.dp, RawColors.Divider.copy(alpha = 0.65f), RoundedCornerShape(28.dp)),
                        backgroundColor = RawColors.HeroSurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Typography\nReel",
                                        style = RawTypography.headlineLarge.copy(fontSize = 27.sp, lineHeight = 31.sp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "16:9 1080p MP4 offline",
                                        style = RawTypography.bodyMedium
                                    )
                                }
                                RawLineArtIllustration(
                                    kind = IllustrationKind.HERO_TYPOGRAPHY,
                                    modifier = Modifier.size(116.dp),
                                    scrollOffsetPx = scrollOffsetPx
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Inset pill button matching reference card CTA
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(27.dp))
                                    .background(RawColors.BgPrimary)
                                    .border(1.dp, RawColors.Divider.copy(alpha = 0.7f), RoundedCornerShape(27.dp))
                                    .padding(start = 18.dp, end = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Start creating",
                                    style = RawTypography.labelLarge
                                )
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(RawColors.TextPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RawIcon(
                                        type = RawIconType.PLAY,
                                        size = 16.dp,
                                        tint = RawColors.PrimaryBtnText
                                    )
                                }
                            }
                        }
                    }
                }

                // Slightly overlapping tilted card echoing Phone 2 bottom card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-10).dp)
                        .graphicsLayer {
                            rotationZ = -2.0f
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
                AnimatedContent(
                    targetState = filterIndex,
                    transitionSpec = {
                        if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else fadeIn(tween(220, easing = RawEasings.Morph)) togetherWith fadeOut(tween(140))
                    },
                    label = "workspace_title_morph"
                ) { f ->
                    Text(
                        text = when (f) {
                            1 -> "In progress"
                            2 -> "Finished reels"
                            else -> "Workspace"
                        },
                        style = RawTypography.titleLarge
                    )
                }
            }

            itemsIndexed(
                items = displayItems,
                key = { _, item ->
                    when (item) {
                        is ProjectEntity -> "p_${item.id}"
                        is VideoEntity -> "v_${item.id}"
                        else -> "x_${item.hashCode()}"
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
}

@Composable
private fun RecentReelOverlapCard(
    video: VideoEntity,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(RawColors.RaisedCard)
            .border(1.dp, RawColors.PillGrey.copy(alpha = 0.75f), RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RECENT REEL",
                    style = RawTypography.labelSmall,
                    color = RawColors.TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.title,
                    style = RawTypography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${FormatHelpers.formatDuration(video.durationSec)} • ${FormatHelpers.formatFileSize(video.sizeBytes)}",
                    style = RawTypography.labelMedium
                )
            }

            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                    RawIcon(RawIconType.FILM, size = 22.dp, tint = RawColors.TextMuted)
                }
            }
        }

        // Inset dark pill button echoing Phone 2 card CTA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(RawColors.BgPrimary)
                .padding(start = 16.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Watch reel",
                style = RawTypography.labelLarge
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(RawColors.TextPrimary),
                contentAlignment = Alignment.Center
            ) {
                RawIcon(RawIconType.PLAY, size = 14.dp, tint = RawColors.PrimaryBtnText)
            }
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
            .border(1.dp, RawColors.Divider.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RawLineArtIllustration(
            kind = IllustrationKind.EMPTY_REELS,
            modifier = Modifier.size(62.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "No finished reels yet",
                style = RawTypography.titleMedium
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Tap Start creating above to render your first reel.",
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
            .border(1.dp, RawColors.Divider.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
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
                .size(40.dp)
                .clip(CircleShape)
                .background(RawColors.RaisedCard),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(RawIconType.ARROW_RIGHT, size = 16.dp)
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
            .border(1.dp, RawColors.Divider.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
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
                .size(40.dp)
                .clip(CircleShape)
                .background(RawColors.TextPrimary),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(RawIconType.PLAY, size = 16.dp, tint = RawColors.PrimaryBtnText)
        }
    }
}
