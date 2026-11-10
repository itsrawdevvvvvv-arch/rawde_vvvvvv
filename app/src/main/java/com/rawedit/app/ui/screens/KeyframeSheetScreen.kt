package com.rawedit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
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
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.PixelSparkle
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
    var exportNotice by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var promptCopied by remember { mutableStateOf(false) }
    var pendingPdfFile by remember { mutableStateOf<File?>(null) }
    var pendingZipFile by remember { mutableStateOf<File?>(null) }

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
                    exportNotice = "PDF saved: ${safeFileBase}_contact_sheet.pdf"
                }.onFailure { err ->
                    exportError = err.message ?: "Could not save PDF."
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
                    exportNotice = "ZIP saved: ${safeFileBase}_contact_sheet.zip"
                }.onFailure { err ->
                    exportError = err.message ?: "Could not save ZIP."
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
        exportNotice = null
        exportError = null

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
            // Coroutine restarted or left screen cleanly
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
        exportError = null
        exportNotice = null
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
                    dlRes.onSuccess { msg ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        exportNotice = msg
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
                exportError = err.message ?: "Could not build PDF contact sheet."
            }
        }
    }

    fun exportZip(share: Boolean) {
        val result = sheetResult ?: return
        if (exportBusy) return
        exportBusy = true
        exportError = null
        exportNotice = null
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
                    dlRes.onSuccess { msg ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        exportNotice = msg
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
                exportError = err.message ?: "Could not build contact sheet ZIP."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Top bar with Back + playful retro Lens Bot mascot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack)
                Box(contentAlignment = Alignment.TopEnd) {
                    RawPixelMascot(
                        kind = PixelMascotKind.LENS_BOT,
                        size = 44.dp
                    )
                    PixelSparkle(
                        size = 11.dp,
                        phaseOffsetMs = 150,
                        modifier = Modifier.offset(x = 4.dp, y = (-4).dp)
                    )
                }
            }

            KineticHeading(
                words = listOf("Contact", "sheet"),
                style = RawTypography.headlineLarge
            )

            Text(
                text = "Live DOM auto-fit ran on every frame. Inspect timestamps below or export PDF/ZIP to Cloud AI to fix specific scenes.",
                style = RawTypography.bodyMedium
            )

            // Density selector pills
            MorphingFilterChips(
                options = SheetSamplingMode.entries.map { it.label },
                selectedIndex = selectedModeIndex,
                onSelect = { idx ->
                    if (idx != selectedModeIndex) {
                        selectedModeIndex = idx
                    }
                }
            )

            // Main content body: Capturing / Error / Contact Sheet Grid
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    capturing -> {
                        val frac = if (progressTotal > 0) {
                            (progressDone.toFloat() / progressTotal.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val smoothFrac by animateFloatAsState(
                            targetValue = frac,
                            animationSpec = tween(180),
                            label = "sheet_capture_frac"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(RawColors.HeroSurface)
                                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val thumb = livePreviewPath
                                if (thumb != null && File(thumb).exists()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(File(thumb))
                                            .memoryCachePolicy(CachePolicy.DISABLED)
                                            .diskCachePolicy(CachePolicy.DISABLED)
                                            .build(),
                                        contentDescription = "Capturing frame",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        color = RawColors.TextPrimary,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RawColors.BgPrimary.copy(alpha = 0.85f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (progressTotal > 0) {
                                            String.format(Locale.US, "t=%.2fs  (%d/%d)", progressTimeSec, progressDone, progressTotal)
                                        } else {
                                            "Warming up offline stage..."
                                        },
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = RawColors.TextPrimary
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(RawColors.CardSurface)
                                    .border(1.dp, RawColors.Divider, RoundedCornerShape(18.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                            "Preparing bundled fonts & DOM auto-fit..."
                                        },
                                        style = RawTypography.labelMedium,
                                        color = RawColors.TextPrimary
                                    )
                                    Text(
                                        text = "${(smoothFrac * 100).toInt()}%",
                                        style = RawTypography.labelMedium,
                                        color = RawColors.SuccessMint
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(RawColors.RaisedCard)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(smoothFrac)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(RawColors.TextPrimary)
                                    )
                                }
                            }
                        }
                    }

                    captureError != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            RawPixelMascot(kind = PixelMascotKind.CODE_BOT, size = 66.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(RawColors.ErrorCoral.copy(alpha = 0.14f))
                                    .border(1.dp, RawColors.ErrorCoral.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = captureError.orEmpty(),
                                    style = RawTypography.bodyMedium,
                                    color = RawColors.ErrorCoral
                                )
                            }
                            BigPrimaryButton(
                                text = "Retry capture",
                                leadingIcon = RawIconType.BOLT,
                                onClick = { captureGen++ }
                            )
                        }
                    }

                    sheetResult != null -> {
                        val res = sheetResult!!
                        val flaggedSamples = remember(res.samples) {
                            res.samples.filter { it.warnings.isNotEmpty() || it.autoFitCount > 0 }
                        }
                        val visibleSamples = if (flaggedOnly && flaggedSamples.isNotEmpty()) {
                            flaggedSamples
                        } else {
                            res.samples
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Summary & Filter Toggle Header
                            item(span = { GridItemSpan(2) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(RawColors.CardSurface)
                                        .border(
                                            1.dp,
                                            if (res.warningCount > 0) RawColors.ErrorCoral.copy(alpha = 0.55f)
                                            else if (res.totalAutoFits > 0) RawColors.SuccessMint.copy(alpha = 0.45f)
                                            else RawColors.Divider,
                                            RoundedCornerShape(20.dp)
                                        )
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${res.samples.size} frames  •  ${String.format(Locale.US, "%.1fs", res.durationSec)}",
                                            style = RawTypography.titleMedium
                                        )
                                        val badgeColor = when {
                                            res.warningCount > 0 -> RawColors.ErrorCoral
                                            res.totalAutoFits > 0 -> RawColors.SuccessMint
                                            else -> RawColors.SuccessMint
                                        }
                                        val badgeLabel = when {
                                            res.warningCount > 0 -> "${res.warningCount} WARN"
                                            res.totalAutoFits > 0 -> "${res.totalAutoFits} AUTO-FIT"
                                            else -> "CLEAN"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(badgeColor.copy(alpha = 0.16f))
                                                .padding(horizontal = 9.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeColor
                                            )
                                        }
                                    }

                                    Text(
                                        text = when {
                                            res.warningCount > 0 -> "${res.totalAutoFits} DOM size fixes applied automatically; ${res.warningCount} frames still flagged. Tap a tile to inspect or share PDF to Cloud AI."
                                            res.totalAutoFits > 0 -> "${res.totalAutoFits} oversized text/margin elements were auto-fitted live so they match your final MP4 cleanly."
                                            else -> "Zero overflow or overlap warnings detected across sampled frames."
                                        },
                                        style = RawTypography.bodyMedium
                                    )

                                    if (flaggedSamples.isNotEmpty()) {
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
                            }

                            // Cloud AI Export Card (PDF + ZIP + Smart Fix Prompt)
                            item(span = { GridItemSpan(2) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(RawColors.HeroSurface)
                                        .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Export sheet for Cloud AI (0 API cost)",
                                        style = RawTypography.titleMedium
                                    )

                                    exportNotice?.let { msg ->
                                        Text(text = msg, style = RawTypography.labelSmall, color = RawColors.SuccessMint)
                                    }
                                    exportError?.let { msg ->
                                        Text(text = msg, style = RawTypography.labelSmall, color = RawColors.ErrorCoral)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CompactExportButton(
                                            icon = RawIconType.DOWNLOAD,
                                            label = if (exportBusy) "Working..." else "Download PDF",
                                            primary = true,
                                            enabled = !exportBusy,
                                            onClick = { exportPdf(share = false) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CompactExportButton(
                                            icon = RawIconType.SHARE,
                                            label = "Share PDF",
                                            primary = false,
                                            enabled = !exportBusy,
                                            onClick = { exportPdf(share = true) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CompactExportButton(
                                            icon = RawIconType.DOWNLOAD,
                                            label = "Download ZIP",
                                            primary = false,
                                            enabled = !exportBusy,
                                            onClick = { exportZip(share = false) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CompactExportButton(
                                            icon = RawIconType.SHARE,
                                            label = "Share ZIP",
                                            primary = false,
                                            enabled = !exportBusy,
                                            onClick = { exportZip(share = true) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    val promptBg by animateColorAsState(
                                        targetValue = if (promptCopied) RawColors.SuccessMint else RawColors.RaisedCard,
                                        animationSpec = tween(if (animEnabled) 220 else 0, easing = RawEasings.Morph),
                                        label = "fix_prompt_bg"
                                    )
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
                                                .clip(RoundedCornerShape(21.dp))
                                                .background(promptBg),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RawIcon(
                                                type = if (promptCopied) RawIconType.CHECK else RawIconType.COPY,
                                                size = 16.dp,
                                                tint = if (promptCopied) RawColors.PrimaryBtnText else RawColors.TextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (promptCopied) "Copied fix prompt!" else "Copy targeted AI fix prompt",
                                                style = RawTypography.labelLarge.copy(fontSize = 13.sp),
                                                color = if (promptCopied) RawColors.PrimaryBtnText else RawColors.TextPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            // 2-Column Contact Sheet Tiles
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

            // Bottom sticky action buttons: [ Fix code ]  [ Render video ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawPressButton(
                    onClick = onEditCode,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(27.dp))
                            .background(RawColors.RaisedCard),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RawIcon(RawIconType.EDIT, size = 17.dp)
                        Spacer(modifier = Modifier.width(7.dp))
                        Text("Fix code", style = RawTypography.labelLarge)
                    }
                }

                val hasAudio = project.audioUri.isNotBlank() && project.audioDurationSec > 0.0
                RawPressButton(
                    enabled = hasAudio && !capturing,
                    onClick = onRenderNow,
                    modifier = Modifier.weight(1.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(27.dp))
                            .background(if (hasAudio && !capturing) RawColors.PrimaryBtnBg else RawColors.PillGrey),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RawIcon(
                            type = RawIconType.PLAY,
                            size = 17.dp,
                            tint = if (hasAudio && !capturing) RawColors.PrimaryBtnText else RawColors.TextMuted
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = if (hasAudio) "Render video" else "Add audio first",
                            style = RawTypography.labelLarge,
                            color = if (hasAudio && !capturing) RawColors.PrimaryBtnText else RawColors.TextMuted
                        )
                    }
                }
            }
        }
    }

    // Enlarged Frame Inspection Dialog
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
                        .fillMaxWidth()
                        .padding(18.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(RawColors.HeroSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "Frame #%02d  •  t=%.2fs  %s",
                                sample.index + 1,
                                sample.timeSec,
                                sample.sceneLabel
                            ).trim(),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = RawColors.TextPrimary
                        )
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
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(sample.thumbPath))
                                .memoryCacheKey("${sample.thumbPath}_${captureGen}_${selectedMode.name}")
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
                            .clip(RoundedCornerShape(14.dp))
                            .background(RawColors.CardSurface)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (sample.warnings.isEmpty() && sample.autoFitNotes.isEmpty()) {
                            Text(
                                text = "Status: OK — no overflow or auto-fit changes needed.",
                                style = RawTypography.bodyMedium,
                                color = RawColors.SuccessMint
                            )
                        }
                        sample.warnings.forEach { w ->
                            Text(
                                text = "WARNING: $w",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = RawColors.ErrorCoral
                            )
                        }
                        sample.autoFitNotes.forEach { note ->
                            Text(
                                text = "AUTO-FIT: $note",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = RawColors.SuccessMint
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RawPressButton(
                            enabled = sample.index > 0,
                            onClick = { inspectingIndex = (sample.index - 1).coerceAtLeast(0) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (sample.index > 0) RawColors.RaisedCard else RawColors.CardSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Prev frame",
                                    style = RawTypography.labelLarge.copy(fontSize = 13.sp),
                                    color = if (sample.index > 0) RawColors.TextPrimary else RawColors.TextMuted
                                )
                            }
                        }
                        RawPressButton(
                            enabled = sample.index < samplesList.lastIndex,
                            onClick = { inspectingIndex = (sample.index + 1).coerceAtMost(samplesList.lastIndex) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (sample.index < samplesList.lastIndex) RawColors.RaisedCard else RawColors.CardSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Next frame",
                                    style = RawTypography.labelLarge.copy(fontSize = 13.sp),
                                    color = if (sample.index < samplesList.lastIndex) RawColors.TextPrimary else RawColors.TextMuted
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
private fun ContactSheetFrameTile(
    sample: KeyframeSample,
    cacheKeyTag: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val hasWarn = sample.warnings.isNotEmpty()
    val hasFit = sample.autoFitCount > 0
    val borderColor = when {
        hasWarn -> RawColors.ErrorCoral.copy(alpha = 0.72f)
        hasFit -> RawColors.SuccessMint.copy(alpha = 0.55f)
        else -> RawColors.Divider.copy(alpha = 0.65f)
    }
    val (statusLabel, statusColor) = when {
        hasWarn -> "WARN" to RawColors.ErrorCoral
        hasFit -> "FIT" to RawColors.SuccessMint
        else -> "OK" to RawColors.TextMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ts = String.format(Locale.US, "t=%.2fs", sample.timeSec)
            Text(
                text = "#${sample.index + 1}  $ts",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = RawColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = statusLabel,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(sample.thumbPath))
                    .memoryCacheKey("${sample.thumbPath}_$cacheKeyTag")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = "Frame ${sample.index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(RawColors.BgPrimary.copy(alpha = 0.86f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (sample.sceneLabel.isNotBlank()) sample.sceneLabel else String.format(Locale.US, "%.2fs", sample.timeSec),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = RawColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun CompactExportButton(
    icon: RawIconType,
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RawPressButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(if (primary) RawColors.PrimaryBtnBg else RawColors.RaisedCard),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RawIcon(
                type = icon,
                size = 15.dp,
                tint = if (primary) RawColors.PrimaryBtnText else RawColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = RawTypography.labelLarge.copy(fontSize = 12.5.sp),
                color = if (primary) RawColors.PrimaryBtnText else RawColors.TextPrimary,
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) RawColors.PrimaryBtnBg else RawColors.RaisedCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) RawColors.PrimaryBtnText else RawColors.TextPrimary
        )
    }
}
