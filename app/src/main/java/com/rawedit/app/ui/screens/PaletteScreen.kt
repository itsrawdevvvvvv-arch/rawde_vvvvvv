package com.rawedit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.PaletteColorMath
import com.rawedit.app.domain.InstantPaletteEngine
import com.rawedit.app.domain.PaletteExporter
import com.rawedit.app.domain.PaletteJsonCodec
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.FeatheredScrollEdges
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

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
    var autoFetchedOnce by remember { mutableStateOf(false) }
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

    val hasGeminiKey = userProfile.hasGeminiKey

    fun regeneratePalette() {
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
    }

    LaunchedEffect(userProfile.geminiApiKey, userProfile.paletteJson) {
        if (userProfile.hasGeminiKey && userProfile.paletteJson.isBlank() && !autoFetchedOnce) {
            autoFetchedOnce = true
            regeneratePalette()
        }
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
                toastMsg = err.message ?: "Could not render PNG"
            }
        }
    }

    val swatches = currentPalette.colors
    val c0Hex = swatches.firstOrNull()?.hex ?: "#DCC9A9"
    val c0Color = Color(PaletteColorMath.parseColorInt(c0Hex))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 220.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Palette",
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                lineHeight = 44.sp,
                letterSpacing = (-1.8).sp,
                color = RawColors.Cream,
                modifier = Modifier.padding(top = 6.dp)
            )

            errorMsg?.let { err ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(RawColors.ErrorSoft)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = err,
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = RawColors.ErrorCoral
                    )
                }
            }

            // #0D0D0D Poster Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, Color(0x18F7ECE6), RoundedCornerShape(30.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = currentPalette.numFormatted,
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 44.sp,
                            lineHeight = 44.sp,
                            letterSpacing = (-1.6).sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = c0Color
                        )
                        Text(
                            text = "COLOR\nINSPIRATION",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFFE6E0D8)
                        )
                    }

                    Text(
                        text = "COLOR\nPALETTE",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.End,
                        color = Color(0xFFE6E0D8)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in 0 until 3) {
                        val swatch = swatches.getOrElse(i) { PaletteState.DEFAULT_SWATCHES[i] }
                        val normalizedHex = PaletteColorMath.normalizeHex(swatch.hex)
                        val targetBg = Color(PaletteColorMath.parseColorInt(normalizedHex))
                        val targetInk = Color(PaletteColorMath.contrastInkArgb(normalizedHex))
                        val rowBg by animateColorAsState(
                            targetValue = targetBg,
                            animationSpec = tween(520),
                            label = "pal_bg_$i"
                        )
                        val rowInk by animateColorAsState(
                            targetValue = targetInk,
                            animationSpec = tween(420),
                            label = "pal_ink_$i"
                        )

                        RawPressButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("hex color", normalizedHex))
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                toastMsg = "Copied $normalizedHex"
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(66.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(rowBg)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%02d", i + 1),
                                    fontFamily = BricolageFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    lineHeight = 28.sp,
                                    letterSpacing = (-2.0).sp,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = rowInk,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )

                                AnimatedContent(
                                    targetState = swatch.name.lowercase(Locale.US) to normalizedHex,
                                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .padding(start = 78.dp),
                                    label = "pal_txt_$i"
                                ) { (swName, swHex) ->
                                    Column(
                                        modifier = Modifier.fillMaxHeight(),
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = swName,
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.5.sp,
                                            color = rowInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = swHex,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp,
                                            color = rowInk.copy(alpha = 0.92f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DESIGN \u2014 RAW_EDIT",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                        color = Color(0xFFC9C2BA)
                    )
                    Text(
                        text = "@raw_edit",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFFC9C2BA)
                    )
                }
            }

            BigPrimaryButton(
                text = "Next Palette (<50ms)",
                height = 50.dp,
                enabled = !generating,
                containerColor = RawColors.Tan,
                contentColor = RawColors.Ink,
                onClick = { regeneratePalette() }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawSecondaryButton(
                    text = if (exporting) "Exporting..." else "Export PNG",
                    height = 46.dp,
                    enabled = !exporting,
                    leadingIcon = RawIconType.DOWNLOAD,
                    onClick = { exportPng(share = false) },
                    modifier = Modifier.weight(1f)
                )
                RawSecondaryButton(
                    text = "Share",
                    height = 46.dp,
                    enabled = !exporting,
                    onClick = { exportPng(share = true) },
                    modifier = Modifier.weight(1f)
                )
            }

            RawSecondaryButton(
                text = "Copy palette prompt",
                height = 42.dp,
                leadingIcon = RawIconType.COPY,
                onClick = {
                    val colorsSummary = currentPalette.colors.mapIndexed { idx, s ->
                        "0${idx + 1} (${s.name}): ${s.hex}"
                    }.joinToString(", ")
                    val promptSnippet = "COLOR PALETTE (#${currentPalette.numFormatted} - ${currentPalette.mood}):\n${currentPalette.pairing}\nColors: $colorsSummary"
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("palette prompt", promptSnippet))
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    toastMsg = "Copied palette prompt"
                }
            )
        }

        FeatheredScrollEdges(
            scrollState = scrollState,
            topHeight = 46.dp,
            bottomHeight = 120.dp,
            alwaysShowBottom = true
        )

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 104.dp)
        )
    }
}
