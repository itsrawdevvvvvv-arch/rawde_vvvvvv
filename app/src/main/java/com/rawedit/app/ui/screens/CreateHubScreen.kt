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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
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
import com.rawedit.app.ui.motion.CascadeSkewCard
import com.rawedit.app.ui.motion.FeatheredScrollEdges
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.SwipeToCreateSlider
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.motion.walletCardColors
import com.rawedit.app.ui.theme.BricolageFamily
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
    attachedAssetCount: Int,
    onBack: () -> Unit,
    onDeleteProject: () -> Unit,
    onOpenAddVoice: () -> Unit,
    onOpenAssetLocker: () -> Unit,
    onOpenCopyPrompt: () -> Unit,
    onOpenCodePaste: () -> Unit,
    onOpenKeyframeSheet: () -> Unit,
    onSwipeCreate: (Offset) -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    val listState = rememberLazyListState()
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
                val srtNote = if (project.srtCueCount > 0) " \u2022 ${project.srtCueCount} SRT cues" else ""
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
            icon = RawIconType.BOX,
            title = "Asset locker",
            subtitle = if (attachedAssetCount > 0) {
                "$attachedAssetCount local media attached \u2022 offline"
            } else {
                "Images, PNG cutouts & video clips"
            },
            statusText = if (attachedAssetCount > 0) "Attached ($attachedAssetCount)" else "Optional",
            statusDone = attachedAssetCount > 0,
            enabled = true,
            onClick = onOpenAssetLocker
        ),
        StepRowModel(
            number = 3,
            icon = RawIconType.ZIP,
            title = "Copy prompt",
            subtitle = "Download style pack ZIP + copy prompt into Cloud AI",
            statusText = if (hasAudio) "Ready" else "Step 1 first",
            statusDone = hasAudio,
            enabled = hasAudio,
            onClick = onOpenCopyPrompt
        ),
        StepRowModel(
            number = 4,
            icon = RawIconType.CODE,
            title = "Paste code",
            subtitle = if (hasCode) {
                "Validated \u2022 ${FormatHelpers.formatDuration(project.codeDurationSec)}"
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
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleActionButton(
                        icon = RawIconType.BACK,
                        onClick = onBack,
                        size = 48.dp
                    )
                    CircleActionButton(
                        icon = RawIconType.TRASH,
                        onClick = { showDeleteConfirm = true },
                        size = 48.dp,
                        backgroundColor = RawColors.ErrorSoft,
                        iconTint = RawColors.ErrorCoral
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KineticHeading(
                        words = listOf("Typography", "Reel"),
                        style = RawTypography.headlineLarge,
                        highlightLastWord = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RawPixelMascot(
                        kind = PixelMascotKind.PHANTOM_BUDDY,
                        size = 50.dp
                    )
                }
            }

            // Warm Tan line-art stage banner (.ban, 30dp borderless)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(RawColors.CardSurface)
                ) {
                    HubBannerLineArt(modifier = Modifier.fillMaxSize())

                    RawPixelMascot(
                        kind = PixelMascotKind.TV_BOT,
                        size = 44.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 8.dp)
                    )
                    RawPixelMascot(
                        kind = PixelMascotKind.LENS_BOT,
                        size = 44.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 6.dp)
                    )
                    PixelSparkle(
                        size = 12.dp,
                        tint = RawColors.Tan,
                        phaseOffsetMs = 260,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 64.dp)
                    )
                    PixelTypeMarqueeBadge(
                        size = 24.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 16.dp)
                    )
                }
            }

            // Borderless stat pills (.stats / .stat 28dp)
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

            // Reel Steps heading + pill readiness badge
            item {
                val badgeBg by animateColorAsState(
                    targetValue = if (canCreate) RawColors.AccentSoft else RawColors.RaisedCard,
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
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 23.sp,
                        letterSpacing = (-0.5).sp,
                        color = RawColors.TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(badgeBg)
                            .padding(horizontal = 13.dp, vertical = 7.dp)
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
                                text = if (ready) "Ready to create" else "Steps 1 & 4 needed",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                color = if (ready) RawColors.Tan else RawColors.TextMuted
                            )
                        }
                    }
                }
            }

            itemsIndexed(steps, key = { _, s -> s.number }) { idx, step ->
                CascadeSkewCard(index = idx) {
                    StepActionCard(step = step, index = idx)
                }
            }

            // Contact Sheet preview card when validated code is present
            if (hasCode) {
                item {
                    RawPressButton(onClick = onOpenKeyframeSheet) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(RawColors.CardSurface)
                                .border(1.dp, RawColors.AccentBorder, RoundedCornerShape(28.dp))
                                .padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RawPixelMascot(
                                kind = PixelMascotKind.LENS_BOT,
                                size = 38.dp,
                                interactive = false,
                                noBob = true,
                                noBlink = true
                            )
                            Text(
                                text = "Preview contact sheet (auto-fit & PDF)",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.5.sp,
                                color = RawColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            RawIcon(
                                type = RawIconType.CHEVRON_RIGHT,
                                size = 18.dp,
                                tint = RawColors.Tan
                            )
                        }
                    }
                }
            }
        }

        FeatheredScrollEdges(
            listState = listState,
            topHeight = 46.dp,
            bottomHeight = 124.dp,
            alwaysShowBottom = true
        )

        // Sticky footer + SwipeToCreateSlider
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
        ) {
            SwipeToCreateSlider(
                unlocked = canCreate,
                rightTag = if (hasAudio) FormatHelpers.formatDuration(project.audioDurationSec) else "1080p",
                onTriggered = onSwipeCreate,
                onLockedTap = { toastMsg = "Finish steps 1 and 4 first" }
            )
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 92.dp)
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
        val strokeCol = RawColors.Tan.copy(alpha = 0.45f)
        val waveCol = RawColors.Tan.copy(alpha = 0.28f)
        val sw = 2.dp.toPx()

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

        playTri.reset()
        val cx = w * 0.5f
        val cy = ry + rh * 0.5f
        val pr = 11.dp.toPx()
        playTri.moveTo(cx - pr * 0.65f, cy - pr)
        playTri.lineTo(cx + pr, cy)
        playTri.lineTo(cx - pr * 0.65f, cy + pr)
        playTri.close()
        drawPath(playTri, RawColors.Tan, style = Fill)

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
            .clip(RoundedCornerShape(28.dp))
            .background(RawColors.CardSurface)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RawIcon(type = icon, size = 18.dp, tint = RawColors.Tan)
        Text(
            text = text,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            style = TextStyle(fontFeatureSettings = "tnum"),
            color = RawColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StepActionCard(
    step: StepRowModel,
    index: Int
) {
    val palette = walletCardColors(index)
    val isDarkCard = ((index % 3) + 3) % 3 == 2
    val effectiveAlpha = if (step.enabled) 1f else 0.52f

    val pillBg = when {
        isDarkCard && step.statusDone -> RawColors.AccentSoft
        isDarkCard -> RawColors.RaisedCard
        step.statusDone -> RawColors.Ink
        else -> palette.badgeBg
    }
    val pillText = when {
        isDarkCard && step.statusDone -> RawColors.Tan
        isDarkCard -> RawColors.TextMuted
        step.statusDone -> RawColors.Cream
        else -> palette.badgeText
    }

    RawPressButton(
        onClick = step.onClick,
        enabled = step.enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(palette.containerColor.copy(alpha = if (step.enabled) 1f else 0.65f))
                .padding(horizontal = 17.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(palette.iconBoxBg),
                contentAlignment = Alignment.Center
            ) {
                RawIcon(
                    type = step.icon,
                    size = 21.dp,
                    tint = palette.iconTint.copy(alpha = effectiveAlpha)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${step.number}. ${step.title}",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = palette.titleColor.copy(alpha = effectiveAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = step.subtitle,
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    color = palette.subtitleColor.copy(alpha = effectiveAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(pillBg)
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = step.statusText,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    color = pillText,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
