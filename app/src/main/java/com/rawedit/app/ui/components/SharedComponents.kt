package com.rawedit.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainTab(val label: String, val icon: RawIconType) {
    HOME("Home", RawIconType.HOME),
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
        val h = (logoSize.value * (22f / 28f)).dp
        PixelRawEditLogo(
            width = logoSize + 6.dp,
            height = h + 5.dp
        )
        if (showWordmark) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "raw_edit",
                style = RawTypography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
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
    val hasFile = photoPath != null && File(photoPath).exists()

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(RawColors.PillGrey)
            .border(1.dp, RawColors.DividerStrong, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (hasFile) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(photoPath!!))
                    .size(256, 256)
                    .build(),
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
 * Compact floating obsidian dock (#dock + #cap) matching raw_edit.html:
 * - Gliding Mint #8CE3A8 capsule behind [Home] / [Reels] / [Profile] driven via graphicsLayer
 * - Dedicated Mint rounded-square [+] Create button beside Home
 */
@Composable
fun RawBottomNavBar(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    val density = LocalDensity.current
    val tabBounds = remember { mutableStateMapOf<MainTab, Pair<Float, Float>>() }
    val activeBounds = if (currentTab != MainTab.CREATE) tabBounds[currentTab] else null
    var hasInitialMeasurement by remember { mutableStateOf(false) }

    LaunchedEffect(activeBounds) {
        if (activeBounds != null && !hasInitialMeasurement) {
            hasInitialMeasurement = true
        }
    }

    val useSpring = animEnabled && hasInitialMeasurement
    val indicatorX by animateFloatAsState(
        targetValue = activeBounds?.first ?: 0f,
        animationSpec = if (useSpring) spring(dampingRatio = 0.78f, stiffness = 360f) else tween(0),
        label = "dock_cap_x"
    )
    val indicatorW by animateFloatAsState(
        targetValue = activeBounds?.second ?: 0f,
        animationSpec = if (useSpring) spring(dampingRatio = 0.78f, stiffness = 360f) else tween(0),
        label = "dock_cap_w"
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
                .width(IntrinsicSize.Min)
                .height(58.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(RawColors.DockBg)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(30.dp))
                .padding(6.dp)
        ) {
            // Gliding Mint active capsule moved on GPU graphicsLayer
            if (indicatorW > 1f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer { translationX = indicatorX }
                        .width(with(density) { indicatorW.toDp() })
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(23.dp))
                        .background(RawColors.Mint)
                )
            }

            Row(
                modifier = Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    if (tab == MainTab.CREATE) {
                        RawPressButton(
                            onClick = { onSelectTab(MainTab.CREATE) },
                            modifier = Modifier.padding(horizontal = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(RawColors.Mint),
                                contentAlignment = Alignment.Center
                            ) {
                                RawIcon(
                                    type = RawIconType.PLUS,
                                    size = 22.dp,
                                    tint = RawColors.PrimaryBtnText,
                                    strokeWidth = 2.4.dp
                                )
                            }
                        }
                    } else {
                        val selected = tab == currentTab
                        val iconTint by animateColorAsState(
                            targetValue = if (selected) RawColors.PrimaryBtnText else RawColors.TextMuted,
                            animationSpec = tween(if (animEnabled) 220 else 0, easing = RawEasings.Morph),
                            label = "dock_tint_${tab.name}"
                        )
                        Row(
                            modifier = Modifier
                                .height(46.dp)
                                .clip(RoundedCornerShape(23.dp))
                                .onGloballyPositioned { coords ->
                                    tabBounds[tab] = coords.positionInParent().x to coords.size.width.toFloat()
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelectTab(tab) }
                                .padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            RawIcon(
                                type = tab.icon,
                                size = 20.dp,
                                tint = iconTint
                            )
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(tween(200, easing = RawEasings.Morph)) +
                                    expandHorizontally(tween(220, easing = RawEasings.Morph)),
                                exit = fadeOut(tween(120)) +
                                    shrinkHorizontally(tween(160, easing = RawEasings.Morph))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Text(
                                        text = tab.label,
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = RawColors.PrimaryBtnText,
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
    }
}

/**
 * Blocky squircle action button (.ib / .ib.s / .ib.bad from raw_edit.html).
 */
@Composable
fun CircleActionButton(
    icon: RawIconType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    backgroundColor: Color = RawColors.CardSurface,
    iconTint: Color = RawColors.TextPrimary,
    borderColor: Color = if (iconTint == RawColors.ErrorCoral) RawColors.ErrorBorder else RawColors.Divider,
    enabled: Boolean = true
) {
    val corner = if (size <= 38.dp) 12.dp else 14.dp
    val effectiveBg = if (iconTint == RawColors.ErrorCoral && backgroundColor == RawColors.CardSurface) {
        RawColors.ErrorSoft
    } else {
        backgroundColor
    }
    RawPressButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(effectiveBg)
                .border(1.dp, borderColor, RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(type = icon, size = size * 0.48f, tint = iconTint)
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
                targetValue = if (selected) RawColors.Mint else Color.Transparent,
                animationSpec = tween(if (animEnabled) 220 else 0, easing = RawEasings.Morph),
                label = "chip_bg_$idx"
            )
            val border by animateColorAsState(
                targetValue = if (selected) RawColors.Mint else RawColors.Divider,
                animationSpec = tween(if (animEnabled) 220 else 0, easing = RawEasings.Morph),
                label = "chip_bd_$idx"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) RawColors.PrimaryBtnText else RawColors.TextMuted,
                animationSpec = tween(if (animEnabled) 220 else 0, easing = RawEasings.Morph),
                label = "chip_fg_$idx"
            )
            val padH by animateDpAsState(
                targetValue = if (selected) 16.dp else 14.dp,
                animationSpec = if (animEnabled) spring(dampingRatio = 0.78f, stiffness = 400f) else tween(0),
                label = "chip_pad_$idx"
            )

            RawPressButton(onClick = { onSelect(idx) }) {
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(99.dp))
                        .padding(horizontal = padH),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (count != null) "$label $count" else label,
                        style = RawTypography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
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
    enabled: Boolean = true,
    leadingIcon: RawIconType? = null
) {
    RawPressButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .alpha(if (enabled) 1f else 0.30f)
                .clip(RoundedCornerShape(99.dp))
                .background(RawColors.Mint)
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                RawIcon(
                    type = leadingIcon,
                    size = 18.dp,
                    tint = RawColors.PrimaryBtnText
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = RawTypography.labelLarge,
                color = RawColors.PrimaryBtnText,
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
    height: Dp = 52.dp,
    enabled: Boolean = true,
    leadingIcon: RawIconType? = null,
    isDanger: Boolean = false,
    fillWidth: Boolean = true
) {
    val bg = if (isDanger) RawColors.ErrorSoft else RawColors.RaisedCard
    val border = if (isDanger) RawColors.ErrorBorder else RawColors.Divider
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
                .alpha(if (enabled) 1f else 0.4f)
                .clip(RoundedCornerShape(99.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(99.dp))
                .padding(horizontal = if (height <= 44.dp) 14.dp else 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                RawIcon(type = leadingIcon, size = if (height <= 44.dp) 16.dp else 18.dp, tint = fg)
                Spacer(modifier = Modifier.width(7.dp))
            }
            Text(
                text = text,
                style = RawTypography.labelLarge.copy(
                    fontSize = if (height <= 44.dp) 13.sp else 14.5.sp,
                    fontWeight = if (height <= 44.dp) FontWeight.SemiBold else FontWeight.Bold
                ),
                color = fg,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Custom dark obsidian dialog matching .pn.dlg from raw_edit.html.
 */
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
                .padding(horizontal = 26.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.DividerStrong, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Text(
                text = title,
                style = RawTypography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = RawTypography.bodyMedium
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawSecondaryButton(
                    text = cancelLabel,
                    height = 42.dp,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                if (isDanger) {
                    RawSecondaryButton(
                        text = confirmLabel,
                        height = 42.dp,
                        isDanger = true,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    RawPressButton(
                        onClick = onConfirm,
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
                                text = confirmLabel,
                                style = RawTypography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                color = RawColors.PrimaryBtnText
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating dark capsule toast banner (#toast in raw_edit.html).
 */
@Composable
fun RawToastBanner(
    message: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220, easing = RawEasings.Morph)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.94f, animationSpec = tween(160)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(RawColors.RaisedCard)
                .border(1.dp, RawColors.DividerStrong, RoundedCornerShape(99.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.orEmpty(),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = RawColors.TextPrimary,
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
