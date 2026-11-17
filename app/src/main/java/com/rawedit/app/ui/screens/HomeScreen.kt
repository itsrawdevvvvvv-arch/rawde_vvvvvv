package com.rawedit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.ui.components.DomeCard
import com.rawedit.app.ui.components.DomeChip
import com.rawedit.app.ui.components.DomeChipRow
import com.rawedit.app.ui.components.DomeEyebrow
import com.rawedit.app.ui.components.DomeHeadPart
import com.rawedit.app.ui.components.DomeHeadline
import com.rawedit.app.ui.components.DomeMono
import com.rawedit.app.ui.components.DomeMuted
import com.rawedit.app.ui.components.DomePillButton
import com.rawedit.app.ui.components.DomePillCard
import com.rawedit.app.ui.components.DomeButtonKind
import com.rawedit.app.ui.components.StaggerIn
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    userProfile: UserProfile,
    reelsCount: Int,
    onStartNewReel: (Offset) -> Unit,
    onOpenChatbot: () -> Unit = {},
    onOpenPalette: () -> Unit = {},
    onOpenOpenDesign: () -> Unit = {},
    onOpenReels: () -> Unit = {},
    recentDraftDurationSec: Double = 0.0
) {
    val firstName = userProfile.userName.trim().ifBlank { "Creator" }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        StaggerIn(0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DomeHeadline(
                    parts = listOf(
                        DomeHeadPart.Plain("Make\na "),
                        DomeHeadPart.Tag("reel")
                    ),
                    size = 54.dp,
                    lineHeightFactor = 0.93f,
                    modifier = Modifier.weight(1f)
                )
                AiChatButton(onClick = onOpenChatbot)
            }
        }

        StaggerIn(1) {
            DomeChipRow {
                DomeChip(text = "Voice", icon = RawIconType.MIC, active = true) { onStartNewReel(Offset.Zero) }
                DomeChip(text = "Palette") { onOpenPalette() }
                DomeChip(text = "Open Design") { onOpenOpenDesign() }
                DomeChip(text = "Reels") { onOpenReels() }
            }
        }

        StaggerIn(2) {
            DomeMuted(
                text = "Voice in, reel out. Nothing leaves the phone.",
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        StaggerIn(3) {
            DomePillButton(
                text = "Start New Reel",
                kind = DomeButtonKind.Primary,
                icon = RawIconType.PLUS,
                trailingArrow = true,
                onClick = { onStartNewReel(Offset.Zero) }
            )
            Spacer(Modifier.height(10.dp))
        }

        if (recentDraftDurationSec > 0.0) {
            StaggerIn(4) {
                DomePillCard(
                    title = "Recent draft · ${formatClock(recentDraftDurationSec)}",
                    subtitle = "Pick up where you left off",
                    trailing = "Open",
                    trailingIcon = RawIconType.CHEVRON_RIGHT,
                    leading = { MiniWave() },
                    onClick = onOpenReels
                )
            }
        }

        StaggerIn(5) {
            DomeCard(tan = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(RawColors.Tan),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials(firstName),
                            fontFamily = BricolageFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RawColors.Ink
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = firstName,
                            fontFamily = BricolageFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RawColors.Cream
                        )
                        DomeMono(text = "$reelsCount reels on device", size = 10)
                    }
                    DomeChip(text = "Offline", active = false)
                }
            }
        }

        StaggerIn(6) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeStat("1080p", "H.264 out", Modifier.weight(1f))
                HomeStat("30 fps", "render", Modifier.weight(1f))
                HomeStat("0", "bytes uploaded", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeStat(label: String, caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontFamily = BricolageFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = RawColors.Cream
        )
        DomeMono(text = caption, size = 9.5f.toInt())
    }
}

@Composable
private fun AiChatButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0x38C89F72), Color(0xF51C1C20))
                )
            )
            .border(1.dp, Color(0x73C89F72), CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(RawColors.TanButtonBrush),
            contentAlignment = Alignment.Center
        ) {
            RawIcon(type = RawIconType.SPARK, size = 11.dp, tint = RawColors.Ink, strokeWidth = 2.2.dp)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "AI Chat",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = RawColors.Cream
        )
    }
}

@Composable
private fun MiniWave() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(7.dp, 13.dp, 9.dp, 15.dp).forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(CircleShape)
                    .background(RawColors.Tan)
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "R" }

private fun formatClock(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
