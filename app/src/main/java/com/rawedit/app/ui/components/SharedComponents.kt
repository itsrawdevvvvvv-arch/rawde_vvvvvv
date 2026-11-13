package com.rawedit.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.PixelRawEditLogo
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainTab(val label: String, val icon: RawIconType) {
    HOME("Home", RawIconType.HOME),
    PALETTE("Palette", RawIconType.PALETTE),
    CREATE("Create", RawIconType.PLUS),
    REELS("Reels", RawIconType.FILM),
    PROFILE("Profile", RawIconType.PERSON)
}

@Composable
fun RawEditLogoBadge(
    modifier: Modifier = Modifier,
    showWordmark: Boolean = false,
    logoSize: Dp = 36.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val h = logoSize * (22f / 28f)
        PixelRawEditLogo(
            width = logoSize + 6.dp,
            height = h + 5.dp
        )
        if (showWordmark) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "raw_edit",
                style = RawTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = RawColors.TextPrimary
            )
        }
    }
}

@Composable
fun UserAvatarView(
    name: String,
    photoPath: String?,
    size: Dp = 44.dp,
    onClick: (() -> Unit)? = null
) {
    val hasFile = remember(photoPath) { photoPath != null && File(photoPath).exists() }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(RawColors.PillGrey)
            .border(1.dp, RawColors.DividerStrong, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (hasFile && photoPath != null) {
            val context = LocalContext.current
            val req = remember(photoPath) {
                ImageRequest.Builder(context)
                    .data(File(photoPath))
                    .size(256, 256)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = "Profile photo of $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            RawPixelMascot(
                kind = PixelMascotKind.AVATAR_FACE,
                size = size * 0.62f,
                interactive = false,
                noBob = true,
                noBlink = true
            )
        }
    }
}

/**
 * Fixed 5-item Wallet floating pill dock (#dock + sliding Cream #cap):
 * - Zero width animations (fixed 60dp slots + 50dp center Tan '+' button).
 * - Capsule slides strictly via GPU `graphicsLayer { translationX }`.
 */
@Composable
fun RawBottomNavBar(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    val density = LocalDensity.current

    val tabWidthDp = 60.dp
    val plusSizeDp = 50.dp
    val gapDp = 4.dp
    val dockPadDp = 7.dp

    val targetOffsetDp = when (currentTab) {
        MainTab.HOME -> 0.dp
        MainTab.PALETTE -> tabWidthDp + gapDp
        MainTab.CREATE -> 0.dp
        MainTab.REELS -> (tabWidthDp + gapDp) * 2 + plusSizeDp + gapDp
        MainTab.PROFILE -> (tabWidthDp + gapDp) * 3 + plusSizeDp + gapDp
    }
    val targetOffsetPx = with(density) { targetOffsetDp.toPx() }
    val capsuleXState = animateFloatAsState(
        targetValue = targetOffsetPx,
        animationSpec = if (animEnabled) {
            spring(dampingRatio = 0.78f, stiffness = 430f)
        } else {
            tween(0)
        },
        label = "dock_cap_x"
    )
    val capsuleAlphaState = animateFloatAsState(
        targetValue = if (currentTab == MainTab.CREATE) 0f else 1f,
        animationSpec = tween(if (animEnabled) 180 else 0),
        label = "dock_cap_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .shadow(14.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(RawColors.DockBg)
                .padding(dockPadDp)
        ) {
            // Sliding Cream capsule strictly moved in GPU transform phase
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = capsuleXState.value
                        alpha = capsuleAlphaState.value
                    }
                    .size(width = tabWidthDp, height = 50.dp)
                    .clip(CircleShape)
                    .background(RawColors.Cream)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(gapDp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    if (tab == MainTab.CREATE) {
                        RawPressButton(onClick = { onSelectTab(MainTab.CREATE) }) {
                            Box(
                                modifier = Modifier
                                    .size(plusSizeDp)
                                    .clip(CircleShape)
                                    .background(RawColors.Tan),
                                contentAlignment = Alignment.Center
                            ) {
                                RawIcon(
                                    type = RawIconType.PLUS,
                                    size = 24.dp,
                                    tint = RawColors.Ink,
                                    strokeWidth = 2.4.dp
                                )
                            }
                        }
                    } else {
                        val selected = tab == currentTab
                        val contentColor = if (selected) RawColors.Ink else RawColors.TextMuted
                        Column(
                            modifier = Modifier
                                .size(width = tabWidthDp, height = 50.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelectTab(tab) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            RawIcon(
                                type = tab.icon,
                                size = 19.dp,
                                tint = contentColor,
                                strokeWidth = 2.1.dp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                color = contentColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Circular action icon button (.ib / .ib.s / .ib.bad in raw_edit_ui.html).
 */
@Composable
fun CircleActionButton(
    icon: RawIconType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    backgroundColor: Color = RawColors.RaisedCard,
    iconTint: Color = RawColors.TextPrimary,
    borderColor: Color = Color.Transparent,
    enabled: Boolean = true,
    isDanger: Boolean = false
) {
    val dangerTint = isDanger && iconTint == RawColors.TextPrimary
    val effectiveBg = if (isDanger || (iconTint == RawColors.ErrorCoral && backgroundColor == RawColors.RaisedCard)) {
        RawColors.ErrorSoft
    } else {
        backgroundColor
    }
    val effectiveTint = if (dangerTint) RawColors.ErrorCoral else iconTint
    RawPressButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(effectiveBg)
                .then(
                    if (borderColor != Color.Transparent) {
                        Modifier.border(1.dp, borderColor, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(
                type = icon,
                size = if (size >= 46.dp) 21.dp else size * 0.48f,
                tint = effectiveTint,
                strokeWidth = if (size <= 36.dp) 1.9.dp else 2.2.dp
            )
        }
    }
}

@Composable
fun MorphingFilterChips(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    counts: List<Int?> = emptyList(),
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            val count = counts.getOrNull(idx)
            val bg by animateColorAsState(
                targetValue = if (selected) RawColors.Tan else RawColors.RaisedCard,
                animationSpec = tween(if (animEnabled) 200 else 0, easing = RawEasings.Morph),
                label = "chip_bg_$idx"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) RawColors.Ink else RawColors.TextMuted,
                animationSpec = tween(if (animEnabled) 200 else 0, easing = RawEasings.Morph),
                label = "chip_fg_$idx"
            )

            RawPressButton(onClick = { onSelect(idx) }) {
                Row(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (count != null) "$label $count" else label,
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = fg,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
fun BigPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
    enabled: Boolean = true,
    leadingIcon: RawIconType? = null,
    containerColor: Color = RawColors.Cream,
    contentColor: Color = RawColors.PrimaryBtnText
) {
    RawPressButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer { alpha = if (enabled) 1f else 0.32f }
                .clip(CircleShape)
                .background(containerColor)
                .padding(horizontal = if (height <= 48.dp) 14.dp else 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                RawIcon(
                    type = leadingIcon,
                    size = if (height <= 48.dp) 16.dp else 18.dp,
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = if (height <= 48.dp) 14.5.sp else 17.sp,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun RawSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
    enabled: Boolean = true,
    leadingIcon: RawIconType? = null,
    isDanger: Boolean = false,
    fillWidth: Boolean = true
) {
    val bg = if (isDanger) RawColors.ErrorSoft else RawColors.RaisedCard
    val fg = if (isDanger) RawColors.ErrorCoral else RawColors.TextPrimary

    RawPressButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(height)
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                .clip(CircleShape)
                .background(bg)
                .padding(horizontal = if (height <= 48.dp) 14.dp else 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                RawIcon(type = leadingIcon, size = if (height <= 48.dp) 15.dp else 18.dp, tint = fg)
                Spacer(modifier = Modifier.width(7.dp))
            }
            Text(
                text = text,
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = if (height <= 42.dp) 13.5.sp else if (height <= 48.dp) 14.5.sp else 17.sp,
                color = fg,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun RawConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDanger: Boolean = true,
    cancelLabel: String = "Cancel"
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(RawColors.CardSurface)
                .padding(24.dp)
        ) {
            Text(
                text = title,
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 29.sp,
                letterSpacing = (-0.8).sp,
                color = RawColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = body,
                style = RawTypography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 22.5.sp),
                color = RawColors.TextMuted
            )
            Spacer(modifier = Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawSecondaryButton(
                    text = cancelLabel,
                    height = 48.dp,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                if (isDanger) {
                    RawSecondaryButton(
                        text = confirmLabel,
                        height = 48.dp,
                        isDanger = true,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    BigPrimaryButton(
                        text = confirmLabel,
                        height = 48.dp,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Floating Cream pill toast banner (#toast in raw_edit_ui.html: Cream pill with dark bold ink).
 */
@Composable
fun RawToastBanner(
    message: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, easing = RawEasings.Morph)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.92f, animationSpec = tween(160)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(RawColors.Cream)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.orEmpty(),
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = RawColors.Ink,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

object FormatHelpers {
    fun formatDuration(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0L)
        val mins = total / 60L
        val secs = total % 60L
        return if (mins >= 60) {
            val hrs = mins / 60L
            val remMin = mins % 60L
            String.format(Locale.US, "%d:%02d:%02d", hrs, remMin, secs)
        } else {
            String.format(Locale.US, "%d:%02d", mins, secs)
        }
    }

    fun formatDurationLong(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0L)
        val hrs = total / 3600L
        val mins = (total % 3600L) / 60L
        val secs = total % 60L
        return when {
            hrs > 0 -> "${hrs}h ${mins}m"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return "Today"
        return SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(millis))
    }
}
