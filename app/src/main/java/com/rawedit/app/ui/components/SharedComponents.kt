package com.rawedit.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rawedit.app.R
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainTab(val label: String, val ghostWord: String, val icon: RawIconType) {
    HOME("Home", "HOME", RawIconType.HOME),
    CREATE("Create", "CREATE", RawIconType.PLUS),
    REELS("My reels", "REELS", RawIconType.FILM),
    PROFILE("Profile", "PROFILE", RawIconType.PERSON)
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
            .border(1.5.dp, RawColors.PillGrey, CircleShape)
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

@Composable
fun RawBottomNavBar(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(33.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(33.dp))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == currentTab
                val pillWidth by animateDpAsState(
                    targetValue = if (selected) 118.dp else 56.dp,
                    animationSpec = if (animEnabled) {
                        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
                    } else {
                        tween(0)
                    },
                    label = "nav_w_${tab.name}"
                )
                val pillBg by animateColorAsState(
                    targetValue = if (selected) RawColors.RaisedCard else Color.Transparent,
                    animationSpec = tween(if (animEnabled) 220 else 0),
                    label = "nav_bg_${tab.name}"
                )

                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .width(pillWidth)
                        .clip(RoundedCornerShape(24.dp))
                        .background(pillBg)
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    RawIcon(
                        type = tab.icon,
                        size = 20.dp,
                        tint = if (selected) RawColors.TextPrimary else RawColors.TextMuted
                    )
                    if (selected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.label,
                            style = RawTypography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = RawColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
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
    size: Dp = 48.dp,
    backgroundColor: Color = RawColors.RaisedCard,
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
                .border(1.dp, RawColors.Divider, CircleShape),
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
    modifier: Modifier = Modifier
) {
    val animEnabled = rememberAnimationsEnabled()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (selected) RawColors.PrimaryBtnBg else RawColors.HeroSurface,
                animationSpec = tween(if (animEnabled) 240 else 0),
                label = "chip_bg_$idx"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) RawColors.PrimaryBtnText else RawColors.TextMuted,
                animationSpec = tween(if (animEnabled) 240 else 0),
                label = "chip_fg_$idx"
            )
            val padH by animateDpAsState(
                targetValue = if (selected) 20.dp else 16.dp,
                animationSpec = if (animEnabled) spring(dampingRatio = 0.7f) else tween(0),
                label = "chip_pad_$idx"
            )

            RawPressButton(onClick = { onSelect(idx) }) {
                Box(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(bg)
                        .border(1.dp, if (selected) Color.Transparent else RawColors.Divider, RoundedCornerShape(21.dp))
                        .padding(horizontal = padH),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = RawTypography.labelLarge.copy(fontSize = 14.sp),
                        color = fg
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
                .height(58.dp)
                .clip(RoundedCornerShape(29.dp))
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
