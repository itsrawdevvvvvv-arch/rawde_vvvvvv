package com.rawedit.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.RawConfirmDialog
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.PixelSparkle
import com.rawedit.app.ui.icons.PixelTypeMarqueeBadge
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.SwipeToCreateSlider
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay

private data class StepRowModel(
    val number: Int,
    val icon: RawIconType,
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
    onDeleteProject: () -> Unit,
    onOpenAddVoice: () -> Unit,
    onOpenCopyPrompt: () -> Unit,
    onOpenCodePaste: () -> Unit,
    onOpenKeyframeSheet: () -> Unit,
    onSwipeCreate: (Offset) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    val hasAudio = project.audioUri.isNotBlank() && project.audioDurationSec > 0.0
    val hasCode = project.code.isNotBlank()
    val canCreate = hasAudio && hasCode
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(1900)
            toastMsg = null
        }
    }

    val steps = listOf(
        StepRowModel(
            number = 1,
            icon = RawIconType.MIC,
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
            icon = RawIconType.ZIP,
            title = "Copy prompt",
            subtitle = "Download style pack ZIP + copy prompt into Cloud AI",
            statusText = if (hasAudio) "Ready" else "Step 1 first",
            statusDone = hasAudio,
            enabled = hasAudio,
            onClick = onOpenCopyPrompt
        ),
        StepRowModel(
            number = 3,
            icon = RawIconType.CODE,
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
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top bar with Back squircle + Trash squircle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleActionButton(
                        icon = RawIconType.BACK,
                        onClick = onBack,
                        size = 42.dp
                    )
                    CircleActionButton(
                        icon = RawIconType.TRASH,
                        onClick = { showDeleteConfirm = true },
                        size = 42.dp,
                        iconTint = RawColors.ErrorCoral
                    )
                }
            }

            // Title + peeking phantom mascot
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KineticHeading(
                        words = listOf("Typography", "Reel"),
                        style = RawTypography.headlineLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RawPixelMascot(
                        kind = PixelMascotKind.PHANTOM_BUDDY,
                        size = 50.dp
                    )
                }
            }

            // Mint line-art stage banner (.ban)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                ) {
                    HubBannerLineArt(modifier = Modifier.fillMaxSize())

                    RawPixelMascot(
                        kind = PixelMascotKind.TV_BOT,
                        size = 44.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 6.dp)
                    )
                    RawPixelMascot(
                        kind = PixelMascotKind.LENS_BOT,
                        size = 44.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 4.dp)
                    )
                    PixelSparkle(
                        size = 12.dp,
                        phaseOffsetMs = 260,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 64.dp)
                    )
                    PixelTypeMarqueeBadge(
                        size = 24.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 14.dp)
                    )
                }
            }

            // Two outlined stat pills (.stats / .stat)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HubStatPill(
                        icon = RawIconType.MIC,
                        text = if (hasAudio) FormatHelpers.formatDuration(project.audioDurationSec) else "No audio yet",
                        modifier = Modifier.weight(1f)
                    )
                    HubStatPill(
                        icon = RawIconType.FILE,
                        text = if (hasAudio) FormatHelpers.formatFileSize(project.audioSizeBytes) else "1080p MP4",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Reel Steps heading + .sp readiness pill
            item {
                val badgeBg by animateColorAsState(
                    targetValue = if (canCreate) RawColors.MintSoft else RawColors.RaisedCard,
                    animationSpec = tween(if (animEnabled) 260 else 0, easing = RawEasings.Morph),
                    label = "hub_sp_bg"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 2.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reel Steps",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        letterSpacing = (-0.5).sp,
                        color = RawColors.TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(badgeBg)
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        AnimatedContent(
                            targetState = canCreate,
                            transitionSpec = {
                                if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                else (fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(180)))
                                    .togetherWith(fadeOut(tween(130)) + scaleOut(targetScale = 0.94f, animationSpec = tween(130)))
                            },
                            label = "hub_ready_pill"
                        ) { ready ->
                            Text(
                                text = if (ready) "READY TO CREATE" else "STEPS 1 & 3 NEEDED",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.6.sp,
                                color = if (ready) RawColors.Mint else RawColors.TextMuted
                            )
                        }
                    }
                }
            }

            itemsIndexed(steps, key = { _, s -> s.number }) { _, step ->
                StepActionCard(step = step, animEnabled = animEnabled)
            }

            // Crisp white-bordered Contact Sheet step card when validated code is present
            if (hasCode) {
                item {
                    RawPressButton(onClick = onOpenKeyframeSheet) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(RawColors.CardSurface)
                                .border(1.dp, Color.White, RoundedCornerShape(22.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RawPixelMascot(
                                kind = PixelMascotKind.LENS_BOT,
                                size = 34.dp,
                                interactive = false,
                                noBob = true,
                                noBlink = true
                            )
                            Text(
                                text = "Preview contact sheet (auto-fit & PDF)",
                                style = RawTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            RawIcon(
                                type = RawIconType.CHEVRON_RIGHT,
                                size = 18.dp,
                                tint = RawColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Sticky gradient bottom footer (.stk) + SwipeToCreateSlider
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
            SwipeToCreateSlider(
                unlocked = canCreate,
                rightTag = if (hasAudio) FormatHelpers.formatDuration(project.audioDurationSec) else "1080p",
                onTriggered = onSwipeCreate,
                onLockedTap = { toastMsg = "Finish steps 1 and 3 first" }
            )
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 86.dp)
        )
    }

    if (showDeleteConfirm) {
        RawConfirmDialog(
            title = "Delete this draft?",
            body = "This removes the draft and its voiceover from your phone.",
            confirmLabel = "Delete",
            isDanger = true,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteProject()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun HubBannerLineArt(modifier: Modifier = Modifier) {
    val playTri = remember { Path() }
    val waveLeft = remember { Path() }
    val waveRight = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeCol = RawColors.Mint.copy(alpha = 0.45f)
        val waveCol = RawColors.Mint.copy(alpha = 0.28f)
        val sw = 2.dp.toPx()

        // Centered 16:9 stage outline
        val rw = w * 0.35f
        val rh = h * 0.52f
        val rx = (w - rw) * 0.5f
        val ry = h * 0.17f
        drawRect(
            color = strokeCol,
            topLeft = Offset(rx, ry),
            size = Size(rw, rh),
            style = Stroke(sw)
        )

        // Filled Mint play triangle center
        playTri.reset()
        val cx = w * 0.5f
        val cy = ry + rh * 0.5f
        val pr = 11.dp.toPx()
        playTri.moveTo(cx - pr * 0.65f, cy - pr)
        playTri.lineTo(cx + pr, cy)
        playTri.lineTo(cx - pr * 0.65f, cy + pr)
        playTri.close()
        drawPath(playTri, RawColors.Mint, style = Fill)

        // Crisp Mint waveform spikes left & right
        val by = h * 0.86f
        waveLeft.reset()
        waveLeft.moveTo(w * 0.06f, by)
        waveLeft.lineTo(w * 0.24f, by)
        waveLeft.lineTo(w * 0.26f, by - 12.dp.toPx())
        waveLeft.lineTo(w * 0.28f, by + 9.dp.toPx())
        waveLeft.lineTo(w * 0.30f, by - 8.dp.toPx())
        waveLeft.lineTo(w * 0.32f, by)
        waveLeft.lineTo(w * 0.44f, by)
        drawPath(waveLeft, waveCol, style = Stroke(sw, cap = StrokeCap.Square, join = StrokeJoin.Miter))

        waveRight.reset()
        waveRight.moveTo(w * 0.59f, by)
        waveRight.lineTo(w * 0.68f, by)
        waveRight.lineTo(w * 0.70f, by - 12.dp.toPx())
        waveRight.lineTo(w * 0.72f, by + 9.dp.toPx())
        waveRight.lineTo(w * 0.74f, by - 8.dp.toPx())
        waveRight.lineTo(w * 0.76f, by)
        waveRight.lineTo(w * 0.94f, by)
        drawPath(waveRight, waveCol, style = Stroke(sw, cap = StrokeCap.Square, join = StrokeJoin.Miter))
    }
}

@Composable
private fun HubStatPill(
    icon: RawIconType,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RawIcon(type = icon, size = 18.dp, tint = RawColors.TextMuted)
        Text(
            text = text,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = RawColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StepActionCard(
    step: StepRowModel,
    animEnabled: Boolean
) {
    val badgeBg by animateColorAsState(
        targetValue = if (step.statusDone) RawColors.MintSoft else RawColors.RaisedCard,
        animationSpec = tween(if (animEnabled) 240 else 0, easing = RawEasings.Morph),
        label = "step_bdg_${step.number}"
    )

    RawPressButton(
        onClick = step.onClick,
        enabled = step.enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(RawColors.RaisedCard)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                RawIcon(
                    type = step.icon,
                    size = 20.dp,
                    tint = if (step.enabled) RawColors.TextPrimary else RawColors.TextMuted
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${step.number}. ${step.title}",
                    style = RawTypography.titleMedium.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Bold),
                    color = if (step.enabled) RawColors.TextPrimary else RawColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = step.subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = RawColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(badgeBg)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = step.statusText.uppercase(),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.7.sp,
                    color = if (step.statusDone) RawColors.Mint else RawColors.TextMuted,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
