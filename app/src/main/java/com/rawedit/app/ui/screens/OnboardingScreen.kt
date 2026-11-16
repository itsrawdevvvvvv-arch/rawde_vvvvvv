package com.rawedit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rawedit.app.ui.components.DomeButtonKind
import com.rawedit.app.ui.components.DomeChip
import com.rawedit.app.ui.components.DomeHeadPart
import com.rawedit.app.ui.components.DomeHeadline
import com.rawedit.app.ui.components.DomeMuted
import com.rawedit.app.ui.components.DomePillButton
import com.rawedit.app.ui.components.DomePillCard
import com.rawedit.app.ui.components.StaggerIn
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.RawColors

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 26.dp, bottom = 30.dp)
    ) {
        StaggerIn(0) {
            DomeChip(text = "RAW_EDIT · STUDIO", active = true)
            Spacer(Modifier.height(16.dp))
        }

        StaggerIn(1) {
            DomeHeadline(
                parts = listOf(
                    DomeHeadPart.Plain("Voice in,\n"),
                    DomeHeadPart.Tag("reel"),
                    DomeHeadPart.Plain(" out.")
                ),
                size = 44.dp,
                lineHeightFactor = 0.95f
            )
        }

        StaggerIn(2) {
            DomeMuted(
                text = "Create kinetic 1080p reels completely offline on your phone.",
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }

        StaggerIn(3) {
            DomePillCard(
                title = "20ms Voice & SRT sync",
                subtitle = "Frame-accurate cue timing",
                leading = { PointDot(RawColors.Tan) },
                trailingIcon = RawIconType.MIC
            )
        }

        StaggerIn(4) {
            DomePillCard(
                title = "1080p offline MP4 engine",
                subtitle = "H.264 + AAC, straight to Movies/raw_edit",
                leading = { PointDot(RawColors.Cream) },
                trailingIcon = RawIconType.FILM
            )
            Spacer(Modifier.height(10.dp))
        }

        StaggerIn(5) {
            DomePillButton(
                text = "Get Started",
                kind = DomeButtonKind.Primary,
                trailingArrow = true,
                onClick = onFinished
            )
        }

        StaggerIn(6) {
            Spacer(Modifier.height(12.dp))
            DomeMuted("No account. No upload. No ads — the studio lives on your device.")
        }
    }
}

@Composable
private fun PointDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}
