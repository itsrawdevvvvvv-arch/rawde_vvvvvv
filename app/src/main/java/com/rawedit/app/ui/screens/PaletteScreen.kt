package com.rawedit.app.ui.screens

import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.InstantPaletteEngine
import com.rawedit.app.domain.PaletteColorMath
import com.rawedit.app.domain.PaletteExporter
import com.rawedit.app.domain.PaletteJsonCodec
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.ui.components.DomeButtonKind
import com.rawedit.app.ui.components.DomeChip
import com.rawedit.app.ui.components.DomeHeadPart
import com.rawedit.app.ui.components.DomeHeadline
import com.rawedit.app.ui.components.DomeMono
import com.rawedit.app.ui.components.DomeMuted
import com.rawedit.app.ui.components.DomePillButton
import com.rawedit.app.ui.components.StaggerIn
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PaletteScreen(
    userProfile: UserProfile,
    cacheManager: CacheManager,
    onSavePaletteState: (counter: Int, json: String) -> Unit,
    geminiRouter: GeminiWaterfallRouter? = null,
    onOpenGeminiKey: () -> Unit = {},
    onOpenGroqKey: () -> Unit = onOpenGeminiKey,
    onSwitchGroqKey: () -> Unit = onOpenGeminiKey,
    onRefreshGroqQuota: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currentPalette = remember(userProfile.paletteJson, userProfile.paletteCounter) {
        PaletteJsonCodec.decode(userProfile.paletteJson)
            ?: PaletteState.DEFAULT_01.copy(number = userProfile.paletteCounter.coerceAtLeast(1))
    }

    var generating by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var pendingPngFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(1900)
            toastMsg = null
        }
    }

    val pngCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { destUri ->
        val src = pendingPngFile
        pendingPngFile = null
        if (destUri != null && src != null && src.exists()) {
            exporting = true
            scope.launch {
                val res = cacheManager.saveFileToUri(src, destUri)
                exporting = false
                res.onSuccess {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    toastMsg = "Saved ${src.name}"
                }.onFailure { err ->
                    toastMsg = err.message ?: "Could not save PNG"
                }
            }
        }
    }

    fun regeneratePalette() {
        if (generating) return
        generating = true
        errorMsg = null
        val targetNumber = if (userProfile.paletteJson.isBlank()) {
            userProfile.paletteCounter.coerceAtLeast(1)
        } else {
            maxOf(userProfile.paletteCounter, currentPalette.number + 1)
        }
        val palette = InstantPaletteEngine.generate(
            nextNumber = targetNumber,
            previousMood = currentPalette.mood
        )
        onSavePaletteState(targetNumber + 1, PaletteJsonCodec.encode(palette))
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        toastMsg = "Palette #${palette.numFormatted} ready"
        generating = false
    }

    fun exportPng(share: Boolean) {
        if (exporting) return
        exporting = true
        errorMsg = null
        scope.launch {
            runCatching {
                PaletteExporter.exportPalettePngToTemp(context, cacheManager, currentPalette)
            }.onSuccess { pngFile ->
                if (share) {
                    exporting = false
                    val shareUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        pngFile
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        putExtra(Intent.EXTRA_SUBJECT, pngFile.name)
                        clipData = ClipData.newRawUri(pngFile.name, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Palette PNG"))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dlRes = cacheManager.saveFileToDownloads(pngFile, pngFile.name, "image/png")
                    exporting = false
                    dlRes.onSuccess {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        toastMsg = "Saved Downloads/raw_edit/${pngFile.name}"
                    }.onFailure {
                        pendingPngFile = pngFile
                        pngCreateLauncher.launch(pngFile.name)
                    }
                } else {
                    exporting = false
                    pendingPngFile = pngFile
                    pngCreateLauncher.launch(pngFile.name)
                }
            }.onFailure { err ->
                exporting = false
                errorMsg = err.message ?: "Could not render PNG"
            }
        }
    }

    val swatches = currentPalette.colors
    val roles = listOf("primary", "accent", "surface")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 28.dp)
    ) {
        StaggerIn(0) {
            DomeHeadline(
                parts = listOf(DomeHeadPart.Plain("Palette")),
                size = 44.dp,
                lineHeightFactor = 0.95f
            )
        }

        StaggerIn(1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${currentPalette.numFormatted} · ${currentPalette.mood}",
                    fontFamily = BricolageFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = RawColors.Cream
                )
                DomeChip(text = "Copy", active = true) {
                    clipboard.setText(AnnotatedString(currentPalette.pairing))
                    toastMsg = "Prompt copied"
                }
            }
        }

        swatches.take(3).forEachIndexed { index, swatch ->
            StaggerIn(2 + index) {
                val bg = Color(PaletteColorMath.parseColorInt(swatch.hex))
                val ink = Color(PaletteColorMath.contrastInkArgb(swatch.hex))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(bg)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(26.dp))
                        .clickable {
                            clipboard.setText(AnnotatedString(swatch.hex))
                            toastMsg = "Copied ${swatch.hex}"
                        }
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "0${index + 1}",
                            fontFamily = BricolageFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ink
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = roles.getOrElse(index) { swatch.name },
                                fontFamily = BricolageFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ink
                            )
                            Text(
                                text = swatch.hex.uppercase(),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = ink.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }

        StaggerIn(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DomeMono(text = "DESIGN — RAW_EDIT", size = 10)
                DomeMono(text = "@raw_edit", size = 10)
            }
        }

        StaggerIn(6) {
            DomePillButton(
                text = if (generating) "Generating…" else "Next Palette",
                kind = DomeButtonKind.Primary,
                enabled = !generating,
                onClick = { regeneratePalette() }
            )
            Spacer(Modifier.height(8.dp))
        }

        StaggerIn(7) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    DomePillButton(
                        text = if (exporting) "Exporting…" else "Export PNG",
                        kind = DomeButtonKind.Ghost,
                        enabled = !exporting,
                        onClick = { exportPng(share = false) }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    DomePillButton(
                        text = "Share",
                        kind = DomeButtonKind.Ghost,
                        enabled = !exporting,
                        onClick = { exportPng(share = true) }
                    )
                }
            }
        }

        StaggerIn(8) {
            Spacer(Modifier.height(10.dp))
            DomeMuted(currentPalette.pairing)
        }

        errorMsg?.let { err ->
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(RawColors.ErrorSoft)
                    .padding(14.dp)
            ) {
                Text(
                    text = err,
                    fontFamily = BricolageFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RawColors.ErrorCoral
                )
            }
        }
    }

    toastMsg?.let { message ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(RawColors.Cream)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = message,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RawColors.Ink
                )
            }
        }
    }
}
