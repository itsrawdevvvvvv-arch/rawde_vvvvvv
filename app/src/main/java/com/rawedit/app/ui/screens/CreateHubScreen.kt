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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.icons.IllustrationKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawLineArtIllustration
import com.rawedit.app.ui.motion.CascadeSkewCard
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.SwipeToCreateSlider
import com.rawedit.app.ui.theme.DotGridSurface
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography

private data class StepRowModel(
    val number: Int,
    val title: String,
    val subtitle: String,
    val statusText: String,
    val statusDone: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@Composable
fun CreateHubScreen(
    project: ProjectEntity,
    onBack: () -> Unit,
    onOpenAddVoice: () -> Unit,
    onOpenCopyPrompt: () -> Unit,
    onOpenCodePaste: () -> Unit,
    onOpenEditor: () -> Unit,
    onSwipeCreate: (Offset) -> Unit
) {
    val hasAudio = project.audioUri.isNotBlank() && project.audioDurationSec > 0.0
    val hasCode = project.code.isNotBlank()
    val canCreate = hasAudio && hasCode

    val steps = listOf(
        StepRowModel(
            number = 1,
            title = "Add voice",
            subtitle = if (hasAudio) {
                val srtNote = if (project.srtCueCount > 0) " • ${project.srtCueCount} SRT cues" else ""
                "${project.audioName}$srtNote"
            } else {
                "Pick MP3, M4A, WAV or AAC (optional SRT)"
            },
            statusText = if (hasAudio) "Ready" else "Needed",
            statusDone = hasAudio,
            enabled = true,
            onClick = onOpenAddVoice
        ),
        StepRowModel(
            number = 2,
            title = "Copy prompt",
            subtitle = "Paste into Claude and answer 3 quick questions",
            statusText = if (hasAudio) "Ready" else "Optional",
            statusDone = hasAudio,
            enabled = hasAudio,
            onClick = onOpenCopyPrompt
        ),
        StepRowModel(
            number = 3,
            title = "Have a code? Click me",
            subtitle = if (hasCode) {
                "Validated • ${FormatHelpers.formatDuration(project.codeDurationSec)}"
            } else {
                "Paste the HTML code returned by Claude"
            },
            statusText = if (hasCode) "Ready" else "Needed",
            statusDone = hasCode,
            enabled = true,
            onClick = onOpenCodePaste
        ),
        StepRowModel(
            number = 4,
            title = "Edit video",
            subtitle = when {
                !hasCode -> "Paste valid code first to unlock"
                !project.hasEditableLayers -> "No editable layers in this code"
                else -> "Move, resize, reorder, or add images"
            },
            statusText = "Optional",
            statusDone = false,
            enabled = hasCode,
            onClick = onOpenEditor
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CircleActionButton(
                    icon = RawIconType.BACK,
                    onClick = onBack,
                    size = 48.dp
                )
            }

            item {
                KineticHeading(
                    words = listOf("Typography", "Reel"),
                    style = RawTypography.displayLarge.copy(fontSize = 32.sp)
                )
            }

            // Hero illustration card
            item {
                DotGridSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(26.dp)),
                    backgroundColor = RawColors.HeroSurface
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        RawLineArtIllustration(
                            kind = IllustrationKind.HERO_TYPOGRAPHY,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }

            // Two stat pills: audio length and file size
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatPillCard(
                        icon = RawIconType.CLOCK,
                        text = if (hasAudio) FormatHelpers.formatDuration(project.audioDurationSec) else "no audio yet",
                        modifier = Modifier.weight(1f)
                    )
                    StatPillCard(
                        icon = RawIconType.BOLT,
                        text = if (hasAudio) FormatHelpers.formatFileSize(project.audioSizeBytes) else "no size yet",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Steps",
                        style = RawTypography.titleLarge
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(RawColors.HeroSurface)
                            .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (canCreate) "Ready to render" else "Steps 1 & 3 required",
                            style = RawTypography.labelSmall,
                            color = if (canCreate) RawColors.SuccessMint else RawColors.TextMuted
                        )
                    }
                }
            }

            itemsIndexed(steps) { index, step ->
                CascadeSkewCard(index = index) {
                    StepActionRow(step = step)
                }
            }
        }

        // Bottom Swipe-to-create bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(RawColors.BgPrimary.copy(alpha = 0.94f))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            SwipeToCreateSlider(
                unlocked = canCreate,
                onTriggered = onSwipeCreate
            )
        }
    }
}

@Composable
private fun StatPillCard(
    icon: RawIconType,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        RawIcon(type = icon, size = 16.dp, tint = RawColors.TextMuted)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = RawTypography.labelMedium,
            color = RawColors.TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun StepActionRow(step: StepRowModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(RawColors.CardSurface)
            .border(
                width = 1.dp,
                color = if (step.statusDone) RawColors.SuccessMint.copy(alpha = 0.45f) else RawColors.Divider,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(enabled = step.enabled, onClick = step.onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${step.number}. ${step.title}",
                    style = RawTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (step.enabled) RawColors.TextPrimary else RawColors.TextMuted,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (step.statusDone) RawColors.SuccessMint.copy(alpha = 0.16f) else RawColors.RaisedCard
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = step.statusText,
                        style = RawTypography.labelSmall,
                        color = if (step.statusDone) RawColors.SuccessMint else RawColors.TextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = step.subtitle,
                style = RawTypography.bodyMedium,
                maxLines = 2
            )
        }

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (step.enabled) RawColors.TextPrimary else RawColors.RaisedCard),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(
                type = if (step.statusDone) RawIconType.CHECK else RawIconType.PLAY,
                size = 18.dp,
                tint = if (step.enabled) RawColors.PrimaryBtnText else RawColors.TextMuted
            )
        }
    }
}
