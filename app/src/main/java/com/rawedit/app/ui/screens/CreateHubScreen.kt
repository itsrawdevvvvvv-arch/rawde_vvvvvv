package com.rawedit.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
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
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.icons.IllustrationKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawLineArtIllustration
import com.rawedit.app.ui.motion.CascadeSkewCard
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.SwipeToCreateSlider
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
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
    onSwipeCreate: (Offset) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
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
            subtitle = "Download style pack ZIP + copy prompt into Cloud AI",
            statusText = if (hasAudio) "Ready" else "Step 1 first",
            statusDone = hasAudio,
            enabled = hasAudio,
            onClick = onOpenCopyPrompt
        ),
        StepRowModel(
            number = 3,
            title = "Paste code",
            subtitle = if (hasCode) {
                "Validated • ${FormatHelpers.formatDuration(project.codeDurationSec)}"
            } else {
                "Paste unlimited HTML code returned by Cloud AI"
            },
            statusText = if (hasCode) "Ready" else "Needed",
            statusDone = hasCode,
            enabled = true,
            onClick = onOpenCodePaste
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 126.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CircleActionButton(
                    icon = RawIconType.BACK,
                    onClick = onBack,
                    size = 44.dp
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KineticHeading(
                        words = listOf("Typography"),
                        style = RawTypography.displayLarge.copy(fontSize = 31.sp, lineHeight = 36.sp)
                    )
                    KineticHeading(
                        words = listOf("Reel"),
                        style = RawTypography.displayLarge.copy(fontSize = 31.sp, lineHeight = 36.sp)
                    )
                }
            }

            // Minimal hero banner echoing reference upper card
            item {
                DotGridSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(166.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, RawColors.Divider.copy(alpha = 0.65f), RoundedCornerShape(24.dp)),
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
                                .height(148.dp)
                        )
                    }
                }
            }

            // Two minimal stat pills directly below preview banner
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatPillCard(
                        icon = RawIconType.CLOCK,
                        text = if (hasAudio) FormatHelpers.formatDuration(project.audioDurationSec) else "No audio yet",
                        modifier = Modifier.weight(1f)
                    )
                    StatPillCard(
                        icon = RawIconType.BOLT,
                        text = if (hasAudio) FormatHelpers.formatFileSize(project.audioSizeBytes) else "1080p MP4",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                val badgeBg by animateColorAsState(
                    targetValue = if (canCreate) RawColors.SuccessMint.copy(alpha = 0.16f) else RawColors.HeroSurface,
                    animationSpec = tween(if (animEnabled) 260 else 0, easing = RawEasings.Morph),
                    label = "hub_badge_bg"
                )
                val badgeBorder by animateColorAsState(
                    targetValue = if (canCreate) RawColors.SuccessMint.copy(alpha = 0.45f) else RawColors.Divider.copy(alpha = 0.7f),
                    animationSpec = tween(if (animEnabled) 260 else 0, easing = RawEasings.Morph),
                    label = "hub_badge_border"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reel Steps",
                        style = RawTypography.titleLarge
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(badgeBg)
                            .border(1.dp, badgeBorder, RoundedCornerShape(15.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        AnimatedContent(
                            targetState = canCreate,
                            transitionSpec = {
                                if (!animEnabled) {
                                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                } else {
                                    (fadeIn(tween(200, easing = RawEasings.Morph)) +
                                        scaleIn(initialScale = 0.94f, animationSpec = tween(200)))
                                        .togetherWith(
                                            fadeOut(tween(140)) +
                                                scaleOut(targetScale = 0.94f, animationSpec = tween(140))
                                        )
                                }
                            },
                            label = "hub_ready_text"
                        ) { ready ->
                            Text(
                                text = if (ready) "Ready to create" else "Steps 1 & 3 needed",
                                style = RawTypography.labelSmall,
                                color = if (ready) RawColors.SuccessMint else RawColors.TextMuted
                            )
                        }
                    }
                }
            }

            itemsIndexed(steps, key = { _, s -> s.number }) { index, step ->
                CascadeSkewCard(index = index) {
                    StepActionRow(step = step, animEnabled = animEnabled)
                }
            }
        }

        // Bottom Swipe-to-create bar with subtle fade scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            RawColors.BgPrimary.copy(alpha = 0.88f),
                            RawColors.BgPrimary
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            SwipeToCreateSlider(
                unlocked = canCreate,
                rightTag = if (hasAudio) FormatHelpers.formatDuration(project.audioDurationSec) else "1080p",
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
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        RawIcon(type = icon, size = 15.dp, tint = RawColors.TextMuted)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = RawTypography.labelMedium,
            color = RawColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StepActionRow(
    step: StepRowModel,
    animEnabled: Boolean
) {
    val borderColor by animateColorAsState(
        targetValue = if (step.statusDone) RawColors.SuccessMint.copy(alpha = 0.42f) else RawColors.Divider.copy(alpha = 0.6f),
        animationSpec = tween(if (animEnabled) 260 else 0, easing = RawEasings.Morph),
        label = "step_border_${step.number}"
    )
    val badgeBg by animateColorAsState(
        targetValue = if (step.statusDone) RawColors.SuccessMint.copy(alpha = 0.16f) else RawColors.RaisedCard,
        animationSpec = tween(if (animEnabled) 260 else 0, easing = RawEasings.Morph),
        label = "step_badge_${step.number}"
    )
    val circleBg by animateColorAsState(
        targetValue = if (step.enabled) RawColors.TextPrimary else RawColors.RaisedCard,
        animationSpec = tween(if (animEnabled) 240 else 0, easing = RawEasings.Morph),
        label = "step_circle_${step.number}"
    )

    RawPressButton(
        onClick = step.onClick,
        enabled = step.enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
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
                        style = RawTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (step.enabled) RawColors.TextPrimary else RawColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBg)
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(circleBg),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = step.statusDone,
                    transitionSpec = {
                        if (!animEnabled) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (scaleIn(initialScale = 0.7f, animationSpec = tween(220, easing = RawEasings.Morph)) +
                                fadeIn(tween(200)))
                                .togetherWith(
                                    scaleOut(targetScale = 0.7f, animationSpec = tween(140)) +
                                        fadeOut(tween(140))
                                )
                        }
                    },
                    label = "step_icon_${step.number}"
                ) { done ->
                    RawIcon(
                        type = if (done) RawIconType.CHECK else RawIconType.PLAY,
                        size = 16.dp,
                        tint = if (step.enabled) RawColors.PrimaryBtnText else RawColors.TextMuted
                    )
                }
            }
        }
    }
}
