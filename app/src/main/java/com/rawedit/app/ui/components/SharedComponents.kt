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
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rawedit.app.R
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
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
import kotlin.math.roundToInt

enum class MainTab(val label: String, val icon: RawIconType) {
    HOME("Home", RawIconType.HOME),
    CREATE("Create", RawIconType.PLUS),
    REELS("Reels", RawIconType.FILM),
    PROFILE("Profile", RawIconType.PERSON)
}

@Composable
fun RawEditLogoBadge(
    modifier: Modifier = Modifier,
    showWordmark: Boolean = true,
    logoSize: Dp = 36.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_raw_edit),
            contentDescription = "raw_edit logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(logoSize)
                .clip(CircleShape)
                .border(1.dp, RawColors.Divider, CircleShape)
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
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "R"
    val hasFile = photoPath != null && File(photoPath).exists()

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(RawColors.RaisedCard)
            .border(1.dp, RawColors.PillGrey, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (hasFile) {
            AsyncImage(
                model = File(photoPath!!),
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Text(
                text = initial,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.42f).sp,
                color = RawColors.TextPrimary
            )
        }
    }
}

/**
 * Persistent liquid-sliding floating bottom navigation bar.
 * One shared indicator capsule smoothly glides & morphs across tabs with spring physics.
 */
@Composable
fun RawBottomNavBar(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    val density = LocalDensity.current
    // Stores exact (leftPx, widthPx) for each tab inside the nav bar row
    val tabBounds = remember { mutableStateMapOf<MainTab, Pair<Float, Float>>() }
    val activeBounds = tabBounds[currentTab]
    var hasInitialMeasurement by remember { mutableStateOf(false) }

    LaunchedEffect(activeBounds) {
        if (activeBounds != null && !hasInitialMeasurement) {
            hasInitialMeasurement = true
        }
    }

    val useSpring = animEnabled && hasInitialMeasurement
    val indicatorX by animateFloatAsState(
        targetValue = activeBounds?.first ?: 0f,
        animationSpec = if (useSpring) {
            spring(dampingRatio = 0.78f, stiffness = 340f)
        } else {
            tween(0)
        },
        label = "nav_glide_x"
    )
    val indicatorW by animateFloatAsState(
        targetValue = activeBounds?.second ?: 0f,
        animationSpec = if (useSpring) {
            spring(dampingRatio = 0.78f, stiffness = 340f)
        } else {
            tween(0)
        },
        label = "nav_glide_w"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(RawColors.CardSurface.copy(alpha = 0.96f))
                .border(1.dp, RawColors.Divider.copy(alpha = 0.72f), RoundedCornerShape(32.dp))
                .padding(6.dp)
        ) {
            // Gliding morph pill behind items
            if (indicatorW > 1f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(indicatorX.roundToInt(), 0) }
                        .width(with(density) { indicatorW.toDp() })
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(26.dp))
                        .background(RawColors.RaisedCard)
                        .border(1.dp, RawColors.PillGrey.copy(alpha = 0.52f), RoundedCornerShape(26.dp))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val slotWeight by animateFloatAsState(
                        targetValue = if (selected) 1.55f else 1f,
                        animationSpec = if (useSpring) {
                            spring(dampingRatio = 0.84f, stiffness = 420f)
                        } else {
                            tween(0)
                        },
                        label = "nav_slot_w_${tab.name}"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) RawColors.TextPrimary else RawColors.TextMuted,
                        animationSpec = tween(if (animEnabled) 240 else 0, easing = RawEasings.Morph),
                        label = "nav_tint_${tab.name}"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.08f else 0.96f,
                        animationSpec = if (animEnabled) {
                            spring(dampingRatio = 0.64f, stiffness = 420f)
                        } else {
                            tween(0)
                        },
                        label = "nav_icon_scale_${tab.name}"
                    )

                    Row(
                        modifier = Modifier
                            .weight(slotWeight)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(26.dp))
                            .onGloballyPositioned { coords ->
                                tabBounds[tab] = coords.positionInParent().x to coords.size.width.toFloat()
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectTab(tab) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        RawIcon(
                            type = tab.icon,
                            size = 20.dp,
                            tint = iconTint,
                            modifier = Modifier.graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(220, easing = RawEasings.Morph)) +
                                expandHorizontally(tween(240, easing = RawEasings.Morph)),
                            exit = fadeOut(tween(130)) +
                                shrinkHorizontally(tween(180, easing = RawEasings.Morph))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.label,
                                    style = RawTypography.labelSmall.copy(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = RawColors.TextPrimary,
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

@Composable
fun CircleActionButton(
    icon: RawIconType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    backgroundColor: Color = RawColors.HeroSurface,
    iconTint: Color = RawColors.TextPrimary,
    enabled: Boolean = true
) {
    RawPressButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(1.dp, RawColors.Divider.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(type = icon, size = size * 0.44f, tint = iconTint)
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
                targetValue = if (selected) RawColors.PrimaryBtnBg else RawColors.CardSurface,
                animationSpec = tween(if (animEnabled) 260 else 0, easing = RawEasings.Morph),
                label = "chip_bg_$idx"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) RawColors.PrimaryBtnText else RawColors.TextMuted,
                animationSpec = tween(if (animEnabled) 260 else 0, easing = RawEasings.Morph),
                label = "chip_fg_$idx"
            )
            val padH by animateDpAsState(
                targetValue = if (selected) 16.dp else 14.dp,
                animationSpec = if (animEnabled) spring(dampingRatio = 0.75f, stiffness = 380f) else tween(0),
                label = "chip_pad_$idx"
            )

            RawPressButton(onClick = { onSelect(idx) }) {
                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(bg)
                        .padding(horizontal = padH),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = label,
                        style = RawTypography.labelLarge.copy(fontSize = 13.sp),
                        color = fg
                    )
                    if (count != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) RawColors.PrimaryBtnText.copy(alpha = 0.12f)
                                    else RawColors.RaisedCard
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$count",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = fg
                            )
                        }
                    }
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
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (enabled) RawColors.PrimaryBtnBg else RawColors.PillGrey)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                RawIcon(
                    type = leadingIcon,
                    size = 20.dp,
                    tint = if (enabled) RawColors.PrimaryBtnText else RawColors.TextMuted
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = RawTypography.labelLarge,
                color = if (enabled) RawColors.PrimaryBtnText else RawColors.TextMuted
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
