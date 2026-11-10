package com.rawedit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.domain.KeyframeSample
import com.rawedit.app.domain.KeyframeSheetGenerator
import com.rawedit.app.domain.KeyframeSheetResult
import com.rawedit.app.domain.SheetSamplingMode
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.MorphingFilterChips
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun KeyframeSheetScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    onBack: () -> Unit,
    onEditCode: () -> Unit,
    onRenderNow: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()

    val resolvedHtml = remember(project.id, project.code) {
        cacheManager.resolveHtmlCode(project.code)
    }
    val durationSec = remember(project.codeDurationSec, project.audioDurationSec) {
        when {
            project.codeDurationSec > 0.2 -> project.codeDurationSec
            project.audioDurationSec > 0.2 -> project.audioDurationSec
            else -> 10.0
        }
    }
    val safeFileBase = remember(project.title, project.id) {
        project.title.ifBlank { "raw_edit_reel" }
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            .trim('_')
            .ifBlank { "raw_edit_${project.id}" }
    }

    var selectedModeIndex by remember { mutableIntStateOf(0) }
    val selectedMode = SheetSamplingMode.entries[selectedModeIndex]
    var captureGen by remember { mutableIntStateOf(1) }
    var flaggedOnly by remember { mutableStateOf(false) }

    var capturing by remember { mutableStateOf(true) }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var progressTimeSec by remember { mutableStateOf(0.0) }
    var livePreviewPath by remember { mutableStateOf<String?>(null) }
    var sheetResult by remember { mutableStateOf<KeyframeSheetResult?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }

    var inspectingIndex by remember { mutableStateOf<Int?>(null) }

    var exportBusy by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var promptCopied by remember { mutableStateOf(false) }
    var pendingPdfFile by remember { mutableStateOf<File?>(null) }
    var pendingZipFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(1900)
            toastMsg = null
        }
    }

    val pdfSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val src = pendingPdfFile
        pendingPdfFile = null
        if (uri != null && src != null && src.exists()) {
            exportBusy = true
            scope.launch {
                val res = cacheManager.saveFileToUri(src, uri)
                exportBusy = false
                res.onSuccess {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    toastMsg = "PDF saved: ${safeFileBase}_contact_sheet.pdf"
                }.onFailure { err ->
                    toastMsg = err.message ?: "Could not save PDF"
                }
            }
        }
    }

    val zipSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val src = pendingZipFile
        pendingZipFile = null
        if (uri != null && src != null && src.exists()) {
            exportBusy = true
            scope.launch {
                val res = cacheManager.saveFileToUri(src, uri)
                exportBusy = false
                res.onSuccess {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    toastMsg = "ZIP saved: ${safeFileBase}_contact_sheet.zip"
                }.onFailure { err ->
                    toastMsg = err.message ?: "Could not save ZIP"
                }
            }
        }
    }

    var isScreenDisposed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { isScreenDisposed = true }
    }

    LaunchedEffect(project.id, resolvedHtml, selectedMode, captureGen) {
        if (resolvedHtml.isBlank()) {
            capturing = false
            captureError = "No validated HTML code found for this draft."
            return@LaunchedEffect
        }
        capturing = true
        captureError = null
        sheetResult = null
        progressDone = 0
        progressTotal = 0
        progressTimeSec = 0.0
        livePreviewPath = null
        flaggedOnly = false
        inspectingIndex = null

        try {
            val result = KeyframeSheetGenerator.captureKeyframes(
                context = context,
                cacheManager = cacheManager,
                projectId = project.id,
                rawHtml = resolvedHtml,
                durationSec = durationSec,
                mode = selectedMode,
                onProgress = { done, total, tSec, latestThumb ->
                    progressDone = done
                    progressTotal = total
                    progressTimeSec = tSec
                    livePreviewPath = latestThumb
                },
                cancelled = { isScreenDisposed || !this@LaunchedEffect.isActive }
            )
            sheetResult = result
            capturing = false
        } catch (_: CancellationException) {
        } catch (t: Throwable) {
            if (!isScreenDisposed) {
                capturing = false
                captureError = t.message ?: "Could not capture keyframe contact sheet."
            }
        }
    }

    fun exportPdf(share: Boolean) {
        val result = sheetResult ?: return
        if (exportBusy) return
        exportBusy = true
        scope.launch {
            runCatching {
                val outName = "${safeFileBase}_contact_sheet.pdf"
                val outFile = File(cacheManager.sharedPacksDir, outName)
                KeyframeSheetGenerator.buildPdfContactSheet(
                    samples = result.samples,
                    title = project.title.ifBlank { "Untitled Reel" },
                    outPdfFile = outFile
                )
                outFile to outName
            }.onSuccess { (pdfFile, fileName) ->
                if (share) {
                    exportBusy = false
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        pdfFile
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                        clipData = ClipData.newRawUri(fileName, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share PDF Contact Sheet"))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dlRes = cacheManager.saveFileToDownloads(pdfFile, fileName, "application/pdf")
                    exportBusy = false
                    dlRes.onSuccess {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        toastMsg = "contact-sheet.pdf saved"
                    }.onFailure {
                        pendingPdfFile = pdfFile
                        pdfSaveLauncher.launch(fileName)
                    }
                } else {
                    exportBusy = false
                    pendingPdfFile = pdfFile
                    pdfSaveLauncher.launch(fileName)
                }
            }.onFailure { err ->
                exportBusy = false
                toastMsg = err.message ?: "Could not build PDF"
            }
        }
    }

    fun exportZip(share: Boolean) {
        val result = sheetResult ?: return
        if (exportBusy) return
        exportBusy = true
        scope.launch {
            runCatching {
                val outName = "${safeFileBase}_contact_sheet.zip"
                val outFile = File(cacheManager.sharedPacksDir, outName)
                KeyframeSheetGenerator.buildContactSheetZip(
                    samples = result.samples,
                    title = project.title.ifBlank { "Untitled Reel" },
                    durationSec = result.durationSec,
                    outZipFile = outFile
                )
                outFile to outName
            }.onSuccess { (zipFile, fileName) ->
                if (share) {
                    exportBusy = false
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        zipFile
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                        clipData = ClipData.newRawUri(fileName, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Contact Sheet ZIP"))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dlRes = cacheManager.saveFileToDownloads(zipFile, fileName, "application/zip")
                    exportBusy = false
                    dlRes.onSuccess {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        toastMsg = "frames.zip saved"
                    }.onFailure {
                        pendingZipFile = zipFile
                        zipSaveLauncher.launch(fileName)
                    }
                } else {
                    exportBusy = false
                    pendingZipFile = zipFile
                    zipSaveLauncher.launch(fileName)
                }
            }.onFailure { err ->
                exportBusy = false
                toastMsg = err.message ?: "Could not build ZIP"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 42.dp)
                RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 40.dp)
            }

            KineticHeading(
                words = listOf("Contact", "sheet"),
                style = RawTypography.headlineLarge
            )

            Text(
                text = "Live DOM auto-fit ran on every frame, fully on-device. Zero API cost.",
                style = RawTypography.bodyMedium
            )

            MorphingFilterChips(
                options = SheetSamplingMode.entries.map { it.label },
                selectedIndex = selectedModeIndex,
                onSelect = { idx ->
                    if (idx != selectedModeIndex) {
                        selectedModeIndex = idx
                    }
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    capturing -> {
                        val frac = if (progressTotal > 0) {
                            (progressDone.toFloat() / progressTotal.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val smoothFrac by animateFloatAsState(
                            targetValue = frac,
                            animationSpec = tween(180),
                            label = "sheet_frac"
                        )
                        val scanY = if (animEnabled) {
                            val inf = rememberInfiniteTransition(label = "sheet_scan")
                            val sy by inf.animateFloat(
                                initialValue = -0.15f,
                                targetValue = 1.15f,
                                animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
                                label = "scan_pos"
                            )
                            sy
                        } else {
                            0.5f
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .border(1.dp, RawColors.Divider, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val thumb = livePreviewPath
                                if (thumb != null && File(thumb).exists()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(File(thumb))
                                            .size(640, 360)
                                            .memoryCachePolicy(CachePolicy.DISABLED)
                                            .diskCachePolicy(CachePolicy.DISABLED)
                                            .build(),
                                        contentDescription = "Capturing frame",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        color = RawColors.Mint,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                // Sweeping Mint scanline (.scn) drawn inside Canvas (zero relayout)
                                if (animEnabled) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val cy = size.height * scanY
                                        val bandH = size.height * 0.14f
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    RawColors.Mint.copy(alpha = 0.28f),
                                                    Color.Transparent
                                                ),
                                                startY = cy - bandH * 0.5f,
                                                endY = cy + bandH * 0.5f
                                            ),
                                            topLeft = Offset(0f, cy - bandH * 0.5f),
                                            size = Size(size.width, bandH)
                                        )
                                    }
                                }

                                Text(
                                    text = if (progressTotal > 0) {
                                        String.format(Locale.US, "t=%.2fs (%d/%d)", progressTimeSec, progressDone, progressTotal)
                                    } else {
                                        "Warming up offline stage..."
                                    },
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(RawColors.CardSurface)
                                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (progressTotal > 0) {
                                            "Capturing & auto-fitting $progressDone / $progressTotal"
                                        } else {
                                            "Preparing bundled fonts & auto-fit..."
                                        },
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = RawColors.TextMuted
                                    )
                                    Text(
                                        text = "${(smoothFrac * 100).toInt()}%",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = RawColors.TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(RawColors.PillGrey)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(smoothFrac)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(RawColors.Mint)
                                    )
                                }
                            }
                        }
                    }

                    captureError != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RawPixelMascot(kind = PixelMascotKind.CODE_BOT, size = 76.dp)
                            Text(
                                text = "Capture stopped",
                                style = RawTypography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = captureError.orEmpty(),
                                style = RawTypography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            BigPrimaryButton(
                                text = "Retry capture",
                                leadingIcon = RawIconType.REFRESH,
                                onClick = { captureGen++ },
                                modifier = Modifier.width(200.dp)
                            )
                        }
                    }

                    sheetResult != null -> {
                        val res = sheetResult!!
                        val flaggedSamples = remember(res.samples) {
                            res.samples.filter { it.warnings.isNotEmpty() || it.autoFitCount > 0 }
                        }
                        val visibleSamples = if (flaggedOnly) flaggedSamples else res.samples

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Summary & Flagged Filter Header
                            item(span = { GridItemSpan(2) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(RawColors.CardSurface)
                                        .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${res.samples.size} frames  •  ${String.format(Locale.US, "%.1fs", res.durationSec)}",
                                            style = RawTypography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        )
                                        val isClean = res.warningCount == 0 && res.totalAutoFits == 0
                                        val badgeLabel = when {
                                            res.warningCount > 0 -> "${res.warningCount} WARN"
                                            res.totalAutoFits > 0 -> "${res.totalAutoFits} AUTO-FIT"
                                            else -> "CLEAN"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(if (isClean) RawColors.MintSoft else RawColors.RaisedCard)
                                                .padding(horizontal = 11.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp,
                                                letterSpacing = 0.6.sp,
                                                color = if (isClean) RawColors.Mint else RawColors.TextMuted
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SheetTogglePill(
                                            label = "All (${res.samples.size})",
                                            selected = !flaggedOnly,
                                            onClick = { flaggedOnly = false }
                                        )
                                        SheetTogglePill(
                                            label = "Flagged (${flaggedSamples.size})",
                                            selected = flaggedOnly,
                                            onClick = { flaggedOnly = true }
                                        )
                                    }
                                }
                            }

                            // Cloud AI Export Card (.tiles + Mint fix prompt CTA)
                            item(span = { GridItemSpan(2) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(RawColors.CardSurface)
                                        .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "Export sheet for Cloud AI",
                                            style = RawTypography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "0 API cost",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = RawColors.TextMuted
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RawSecondaryButton(
                                            text = "Download PDF",
                                            height = 42.dp,
                                            enabled = !exportBusy,
                                            leadingIcon = RawIconType.DOWNLOAD,
                                            onClick = { exportPdf(share = false) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        RawSecondaryButton(
                                            text = "Share PDF",
                                            height = 42.dp,
                                            enabled = !exportBusy,
                                            leadingIcon = RawIconType.SHARE,
                                            onClick = { exportPdf(share = true) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RawSecondaryButton(
                                            text = "Download ZIP",
                                            height = 42.dp,
                                            enabled = !exportBusy,
                                            leadingIcon = RawIconType.DOWNLOAD,
                                            onClick = { exportZip(share = false) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        RawSecondaryButton(
                                            text = "Share ZIP",
                                            height = 42.dp,
                                            enabled = !exportBusy,
                                            leadingIcon = RawIconType.SHARE,
                                            onClick = { exportZip(share = true) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    RawPressButton(
                                        onClick = {
                                            val fixPrompt = KeyframeSheetGenerator.buildFixPrompt(
                                                samples = res.samples,
                                                title = project.title.ifBlank { "Untitled Reel" },
                                                durationSec = res.durationSec
                                            )
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("raw_edit fix prompt", fixPrompt))
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            promptCopied = true
                                            toastMsg = "Fix prompt copied"
                                            scope.launch {
                                                delay(2000)
                                                promptCopied = false
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(RawColors.Mint),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RawIcon(
                                                type = if (promptCopied) RawIconType.CHECK else RawIconType.COPY,
                                                size = 16.dp,
                                                tint = RawColors.PrimaryBtnText
                                            )
                                            Spacer(modifier = Modifier.width(7.dp))
                                            Text(
                                                text = if (promptCopied) "Copied fix prompt!" else "Copy targeted AI fix prompt",
                                                style = RawTypography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                                color = RawColors.PrimaryBtnText
                                            )
                                        }
                                    }
                                }
                            }

                            if (flaggedOnly && flaggedSamples.isEmpty()) {
                                item(span = { GridItemSpan(2) }) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(RawColors.CardSurface)
                                            .border(1.dp, RawColors.MintBorder, RoundedCornerShape(22.dp))
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 48.dp)
                                        Text(
                                            text = "All ${res.samples.size} frames look clean",
                                            style = RawTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = RawColors.Mint
                                        )
                                        Text(
                                            text = "No edge clips or auto-fit resizes were flagged.",
                                            style = RawTypography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        RawSecondaryButton(
                                            text = "Show all frames",
                                            height = 40.dp,
                                            fillWidth = false,
                                            onClick = { flaggedOnly = false }
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = visibleSamples,
                                    key = { _, item -> "${captureGen}_${item.index}" }
                                ) { _, sample ->
                                    ContactSheetFrameTile(
                                        sample = sample,
                                        cacheKeyTag = "${captureGen}_${selectedMode.name}",
                                        onClick = { inspectingIndex = sample.index }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sticky gradient footer (.stk .row2)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawSecondaryButton(
                    text = "Fix code",
                    height = 52.dp,
                    onClick = onEditCode,
                    modifier = Modifier.weight(1f)
                )
                val hasAudio = project.audioUri.isNotBlank() && project.audioDurationSec > 0.0
                BigPrimaryButton(
                    text = if (hasAudio) "Render video" else "Add audio first",
                    enabled = hasAudio && !capturing,
                    onClick = onRenderNow,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 86.dp)
        )
    }

    // Dark full-screen Zoom Inspection Modal (.pn.mdl)
    val activeIdx = inspectingIndex
    val samplesList = sheetResult?.samples
    if (activeIdx != null && samplesList != null) {
        val sample = samplesList.getOrNull(activeIdx)
        if (sample != null) {
            Dialog(
                onDismissRequest = { inspectingIndex = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFA0B0C0B))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "Frame #%02d  •  t=%.2fs  •  %s",
                                sample.index + 1,
                                sample.timeSec,
                                sample.sceneLabel.ifBlank { "SCENE" }
                            ),
                            style = RawTypography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        CircleActionButton(
                            icon = RawIconType.CLOSE,
                            size = 36.dp,
                            onClick = { inspectingIndex = null }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(
                                1.dp,
                                when {
                                    sample.warnings.isNotEmpty() -> RawColors.ErrorCoral
                                    sample.autoFitCount > 0 -> RawColors.Mint.copy(alpha = 0.55f)
                                    else -> RawColors.Divider
                                },
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(sample.thumbPath))
                                .size(960, 540)
                                .memoryCacheKey("${sample.thumbPath}_zoom_${captureGen}_${selectedMode.name}")
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                            contentDescription = "Zoomed keyframe",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sample.warnings.forEach { w ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RawColors.ErrorSoft)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "WARNING: $w",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp,
                                    color = RawColors.ErrorCoral
                                )
                            }
                        }
                        sample.autoFitNotes.forEach { note ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RawColors.RaisedCard)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "AUTO-FIT: $note",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp,
                                    color = RawColors.Mint
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RawColors.RaisedCard)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "OK - all other text inside margins",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.5.sp,
                                color = RawColors.TextMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RawColors.RaisedCard)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "OK - no external resources requested",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.5.sp,
                                color = RawColors.TextMuted
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RawSecondaryButton(
                            text = "Prev frame",
                            height = 52.dp,
                            leadingIcon = RawIconType.CHEVRON_LEFT,
                            onClick = {
                                inspectingIndex = (sample.index - 1 + samplesList.size) % samplesList.size
                            },
                            modifier = Modifier.weight(1f)
                        )
                        RawPressButton(
                            onClick = {
                                inspectingIndex = (sample.index + 1) % samplesList.size
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(RawColors.RaisedCard)
                                    .border(1.dp, RawColors.Divider, RoundedCornerShape(99.dp)),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Next frame", style = RawTypography.labelLarge)
                                Spacer(modifier = Modifier.width(6.dp))
                                RawIcon(RawIconType.CHEVRON_RIGHT, size = 18.dp, tint = RawColors.TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactSheetFrameTile(
    sample: KeyframeSample,
    cacheKeyTag: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val hasWarn = sample.warnings.isNotEmpty()
    val hasFit = sample.autoFitCount > 0
    val frameBorder = when {
        hasWarn -> RawColors.ErrorCoral
        hasFit -> RawColors.Mint.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val (statusLabel, statusBg, statusFg) = when {
        hasWarn -> Triple("WARN", RawColors.ErrorSoft, RawColors.ErrorCoral)
        hasFit -> Triple("FIT", RawColors.MintSoft, RawColors.Mint)
        else -> Triple("OK", RawColors.PillGrey, RawColors.TextMuted)
    }

    RawPressButton(onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "#%02d t=%.2fs", sample.index + 1, sample.timeSec),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = RawColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                        color = statusFg
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .then(
                        if (frameBorder != Color.Transparent) {
                            Modifier.border(1.5.dp, frameBorder, RoundedCornerShape(10.dp))
                        } else Modifier
                    )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(sample.thumbPath))
                        .size(480, 270)
                        .memoryCacheKey("${sample.thumbPath}_$cacheKeyTag")
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build(),
                    contentDescription = "Frame ${sample.index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = if (sample.sceneLabel.isNotBlank()) sample.sceneLabel else String.format(Locale.US, "%.2fs", sample.timeSec),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = RawColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SheetTogglePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) RawColors.Mint else Color.Transparent,
        animationSpec = tween(200, easing = RawEasings.Morph),
        label = "sh_tog_bg"
    )
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .border(1.dp, if (selected) RawColors.Mint else RawColors.Divider, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) RawColors.PrimaryBtnText else RawColors.TextMuted
        )
    }
}
