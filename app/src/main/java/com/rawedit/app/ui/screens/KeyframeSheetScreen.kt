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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
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
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.GeminiClient
import com.rawedit.app.domain.KeyframeSample
import com.rawedit.app.domain.KeyframeSheetGenerator
import com.rawedit.app.domain.KeyframeSheetResult
import com.rawedit.app.domain.SheetReviewResult
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
import com.rawedit.app.ui.motion.FeatheredScrollEdges
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun KeyframeSheetScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    geminiApiKey: String,
    onBack: () -> Unit,
    onEditCode: () -> Unit,
    onApplyFixedHtml: (String, Double) -> Unit,
    onOpenApiKey: () -> Unit,
    onRenderNow: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()
    val gridState = rememberLazyGridState()

    var resolvedHtml by remember(project.id) {
        mutableStateOf(if (project.code.startsWith(CacheManager.CODE_FILE_PREFIX)) "" else project.code)
    }
    var htmlReady by remember(project.id) {
        mutableStateOf(!project.code.startsWith(CacheManager.CODE_FILE_PREFIX))
    }
    LaunchedEffect(project.id, project.code) {
        if (project.code.startsWith(CacheManager.CODE_FILE_PREFIX)) {
            resolvedHtml = withContext(Dispatchers.IO) { cacheManager.resolveHtmlCode(project.code) }
            htmlReady = true
        } else {
            resolvedHtml = project.code
            htmlReady = true
        }
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

    var aiBusy by remember { mutableStateOf(false) }
    var aiAction by remember { mutableStateOf("") }
    var reviewResult by remember { mutableStateOf<SheetReviewResult?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(project.id, htmlReady, resolvedHtml, selectedMode, captureGen) {
        if (!htmlReady) return@LaunchedEffect
        if (resolvedHtml.isBlank()) {
            capturing = false
            captureError = "No validated HTML code found for this draft."
            return@LaunchedEffect
        }
        capturing = true
        captureError = null
        sheetResult = null
        reviewResult = null
        aiError = null
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
                    .height(62.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 48.dp)
                RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 40.dp)
            }

            KineticHeading(
                words = listOf("Contact", "sheet"),
                style = RawTypography.headlineLarge,
                highlightLastWord = true
            )

            Text(
                text = "Live DOM auto-fit ran on every frame, fully on-device. Zero API cost.",
                style = RawTypography.bodyLarge.copy(fontSize = 15.5.sp),
                color = RawColors.TextMuted
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
                        val smoothFracState: State<Float> = animateFloatAsState(
                            targetValue = frac,
                            animationSpec = tween(180),
                            label = "sheet_frac"
                        )
                        val percentInt by remember(smoothFracState) {
                            derivedStateOf { (smoothFracState.value * 100f).toInt().coerceIn(0, 100) }
                        }
                        val scanYState: State<Float>? = if (animEnabled) {
                            val inf = rememberInfiniteTransition(label = "sheet_scan")
                            inf.animateFloat(
                                initialValue = -0.15f,
                                targetValue = 1.15f,
                                animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
                                label = "scan_pos"
                            )
                        } else {
                            null
                        }
                        val thumb = livePreviewPath
                        val thumbExists = remember(thumb) { thumb != null && File(thumb).exists() }

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
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbExists && thumb != null) {
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
                                        color = RawColors.Tan,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                if (scanYState != null) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val cy = size.height * scanYState.value
                                        val bandH = size.height * 0.14f
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    RawColors.Tan.copy(alpha = 0.28f),
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
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(RawColors.CardSurface)
                                    .padding(18.dp)
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
                                        style = RawTypography.labelMedium
                                    )
                                    Text(
                                        text = "$percentInt%",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        style = TextStyle(fontFeatureSettings = "tnum"),
                                        color = RawColors.TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(RawColors.PillGrey)
                                ) {
                                    val w = size.width * smoothFracState.value.coerceIn(0f, 1f)
                                    if (w > 0f) {
                                        drawRoundRect(
                                            color = RawColors.Tan,
                                            topLeft = Offset.Zero,
                                            size = Size(w, size.height),
                                            cornerRadius = CornerRadius(size.height * 0.5f, size.height * 0.5f)
                                        )
                                    }
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
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                lineHeight = 32.sp,
                                letterSpacing = (-0.8).sp,
                                color = RawColors.TextPrimary
                            )
                            Text(
                                text = captureError.orEmpty(),
                                style = RawTypography.bodyLarge.copy(fontSize = 15.5.sp),
                                color = RawColors.TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            BigPrimaryButton(
                                text = "Retry capture",
                                height = 58.dp,
                                leadingIcon = RawIconType.REFRESH,
                                onClick = { captureGen++ },
                                modifier = Modifier.width(210.dp)
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
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 118.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Summary & Flagged Filter Header
                            item(span = { GridItemSpan(2) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(RawColors.CardSurface)
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${res.samples.size} frames  \u2022  ${String.format(Locale.US, "%.1fs", res.durationSec)}",
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = RawColors.TextPrimary
                                        )
                                        val isClean = res.warningCount == 0 && res.totalAutoFits == 0
                                        val badgeLabel = when {
                                            res.warningCount > 0 -> "${res.warningCount} WARN"
                                            res.totalAutoFits > 0 -> "${res.totalAutoFits} AUTO-FIT"
                                            else -> "CLEAN"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isClean) RawColors.AccentSoft else RawColors.RaisedCard)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.5.sp,
                                                style = TextStyle(fontFeatureSettings = "tnum"),
                                                color = if (isClean) RawColors.Tan else RawColors.TextMuted
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

                            // Gemini Frame Inspector Card (optional, uses user's own API key)
                            item(span = { GridItemSpan(2) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(RawColors.CardSurface)
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            RawIcon(RawIconType.SPARK, size = 18.dp, tint = RawColors.Tan)
                                            Column {
                                                Text(
                                                    text = "Gemini Frame Inspector",
                                                    fontFamily = BricolageFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.5.sp,
                                                    color = RawColors.TextPrimary
                                                )
                                                Text(
                                                    text = if (geminiApiKey.isBlank()) "Optional \u2022 Add API key to enable" else "Visual inspection + 1-tap HTML auto-fix",
                                                    style = RawTypography.labelMedium
                                                )
                                            }
                                        }
                                        CircleActionButton(
                                            icon = RawIconType.KEY,
                                            size = 38.dp,
                                            backgroundColor = if (geminiApiKey.isNotBlank()) RawColors.AccentSoft else RawColors.RaisedCard,
                                            iconTint = if (geminiApiKey.isNotBlank()) RawColors.Tan else RawColors.TextMuted,
                                            onClick = onOpenApiKey
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RawSecondaryButton(
                                            text = if (aiBusy && aiAction == "review") "Reviewing..." else "AI review",
                                            height = 48.dp,
                                            enabled = !aiBusy,
                                            leadingIcon = RawIconType.LENS,
                                            onClick = {
                                                if (geminiApiKey.isBlank()) {
                                                    onOpenApiKey()
                                                    return@RawSecondaryButton
                                                }
                                                aiBusy = true
                                                aiAction = "review"
                                                aiError = null
                                                scope.launch {
                                                    val imgs = KeyframeSheetGenerator.buildGeminiSheetImages(
                                                        samples = res.samples,
                                                        title = project.title.ifBlank { "Untitled Reel" },
                                                        outDir = cacheManager.sheetTempDir(project.id),
                                                        maxPages = 2
                                                    )
                                                    val review = GeminiClient.reviewContactSheet(
                                                        apiKey = geminiApiKey,
                                                        sheetImages = imgs,
                                                        htmlCode = resolvedHtml,
                                                        frameCount = res.samples.size
                                                    )
                                                    aiBusy = false
                                                    aiAction = ""
                                                    review.onSuccess { r ->
                                                        reviewResult = r
                                                        toastMsg = if (r.issues.isEmpty()) "No visual glitches spotted" else "Found ${r.issues.size} frame notes"
                                                    }.onFailure { err ->
                                                        aiError = err.message ?: "Gemini inspection failed."
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )

                                        BigPrimaryButton(
                                            text = if (aiBusy && aiAction == "fix") "Fixing..." else "Auto-fix",
                                            height = 48.dp,
                                            enabled = !aiBusy,
                                            leadingIcon = RawIconType.SPARK,
                                            containerColor = RawColors.Tan,
                                            contentColor = RawColors.Ink,
                                            onClick = {
                                                if (geminiApiKey.isBlank()) {
                                                    onOpenApiKey()
                                                    return@BigPrimaryButton
                                                }
                                                aiBusy = true
                                                aiAction = "fix"
                                                aiError = null
                                                scope.launch {
                                                    val imgs = KeyframeSheetGenerator.buildGeminiSheetImages(
                                                        samples = res.samples,
                                                        title = project.title.ifBlank { "Untitled Reel" },
                                                        outDir = cacheManager.sheetTempDir(project.id),
                                                        maxPages = 2
                                                    )
                                                    val issuesText = buildString {
                                                        reviewResult?.let { rev ->
                                                            if (rev.summary.isNotBlank()) appendLine(rev.summary)
                                                            rev.issues.forEach { iss ->
                                                                appendLine("${iss.frame} (${iss.time}): ${iss.problem} -> ${iss.fix}")
                                                            }
                                                        }
                                                        flaggedSamples.forEach { s ->
                                                            appendLine(
                                                                String.format(
                                                                    Locale.US,
                                                                    "Local DOM #${s.index + 1} (t=%.2fs): ${s.warnings.joinToString("; ")} ${s.autoFitNotes.joinToString("; ")}",
                                                                    s.timeSec
                                                                )
                                                            )
                                                        }
                                                    }.ifBlank { "Inspect all frames for text overflow, overlap, margin clipping, or contrast issues." }

                                                    val fixRes = GeminiClient.autoFixHtmlWithSheet(
                                                        apiKey = geminiApiKey,
                                                        sheetImages = imgs,
                                                        htmlCode = resolvedHtml,
                                                        issuesSummary = issuesText,
                                                        durationSec = res.durationSec
                                                    )
                                                    fixRes.onSuccess { fixedHtml ->
                                                        val validation = CodeValidator.validateFull(context, cacheManager, fixedHtml)
                                                        aiBusy = false
                                                        aiAction = ""
                                                        if (validation.isValid) {
                                                            resolvedHtml = validation.cleanedHtml
                                                            onApplyFixedHtml(validation.cleanedHtml, validation.durationSec)
                                                            reviewResult = null
                                                            aiError = null
                                                            toastMsg = "Gemini fix applied — re-capturing sheet"
                                                            captureGen++
                                                        } else {
                                                            aiError = "Auto-fix failed offline check: ${validation.summaryMessage}"
                                                        }
                                                    }.onFailure { err ->
                                                        aiBusy = false
                                                        aiAction = ""
                                                        aiError = err.message ?: "Gemini auto-fix failed."
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    aiError?.let { msg ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(RawColors.ErrorSoft)
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = msg,
                                                fontFamily = BricolageFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.5.sp,
                                                color = RawColors.ErrorCoral
                                            )
                                        }
                                    }

                                    reviewResult?.let { rev ->
                                        Text(
                                            text = rev.summary,
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = RawColors.TextPrimary
                                        )
                                        rev.issues.forEach { issue ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .background(RawColors.RaisedCard)
                                                    .clickable {
                                                        val frameNum = Regex("\\d+").find(issue.frame)?.value?.toIntOrNull()
                                                        if (frameNum != null && res.samples.isNotEmpty()) {
                                                            inspectingIndex = (frameNum - 1).coerceIn(0, res.samples.lastIndex)
                                                        }
                                                    }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = "${issue.frame}  \u2022  ${issue.time}",
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = RawColors.Tan
                                                    )
                                                    if (issue.problem.isNotBlank()) {
                                                        Text(
                                                            text = issue.problem,
                                                            fontFamily = BricolageFamily,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 13.5.sp,
                                                            color = RawColors.TextPrimary
                                                        )
                                                    }
                                                    if (issue.fix.isNotBlank()) {
                                                        Text(
                                                            text = "Fix: ${issue.fix}",
                                                            fontFamily = BricolageFamily,
                                                            fontWeight = FontWeight.Medium,
                                                            fontSize = 12.5.sp,
                                                            color = RawColors.TextMuted
                                                        )
                                                    }
                                                }
                                                RawIcon(RawIconType.CHEVRON_RIGHT, size = 16.dp, tint = RawColors.TextMuted)
                                            }
                                        }
                                    }
                                }
                            }

                            // Offline Cloud AI Export Card (.tiles)
                            item(span = { GridItemSpan(2) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(RawColors.CardSurface)
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "Export sheet for Cloud AI",
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 19.sp,
                                            color = RawColors.TextPrimary
                                        )
                                        Text(
                                            text = "0 API cost",
                                            style = RawTypography.labelMedium
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ExportTileButton(
                                            text = "Download PDF",
                                            enabled = !exportBusy,
                                            icon = RawIconType.DOWNLOAD,
                                            onClick = { exportPdf(share = false) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ExportTileButton(
                                            text = "Share PDF",
                                            enabled = !exportBusy,
                                            icon = RawIconType.SHARE,
                                            onClick = { exportPdf(share = true) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ExportTileButton(
                                            text = "Download ZIP",
                                            enabled = !exportBusy,
                                            icon = RawIconType.DOWNLOAD,
                                            onClick = { exportZip(share = false) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ExportTileButton(
                                            text = "Share ZIP",
                                            enabled = !exportBusy,
                                            icon = RawIconType.SHARE,
                                            onClick = { exportZip(share = true) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    BigPrimaryButton(
                                        text = if (promptCopied) "Copied fix prompt!" else "Copy targeted AI fix prompt",
                                        height = 50.dp,
                                        leadingIcon = if (promptCopied) RawIconType.CHECK else RawIconType.COPY,
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
                                    )
                                }
                            }

                            if (flaggedOnly && flaggedSamples.isEmpty()) {
                                item(span = { GridItemSpan(2) }) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(RawColors.CardSurface)
                                            .border(1.dp, RawColors.AccentBorder, RoundedCornerShape(28.dp))
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 48.dp)
                                        Text(
                                            text = "All ${res.samples.size} frames look clean",
                                            style = RawTypography.titleMedium,
                                            color = RawColors.Tan
                                        )
                                        Text(
                                            text = "No edge clips or auto-fit resizes were flagged.",
                                            style = RawTypography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        RawSecondaryButton(
                                            text = "Show all frames",
                                            height = 46.dp,
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

                        FeatheredScrollEdges(
                            gridState = gridState,
                            topHeight = 34.dp,
                            bottomHeight = 112.dp,
                            alwaysShowBottom = true
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawSecondaryButton(
                    text = "Fix code",
                    height = 58.dp,
                    onClick = onEditCode,
                    modifier = Modifier.weight(1f)
                )
                val hasAudio = project.audioUri.isNotBlank() && project.audioDurationSec > 0.0
                BigPrimaryButton(
                    text = if (hasAudio) "Render video" else "Add audio first",
                    height = 58.dp,
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
                .padding(bottom = 90.dp)
        )
    }

    // Zoom Inspection Modal (.pn.mdl)
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
                        .background(RawColors.BgPrimary.copy(alpha = 0.98f))
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
                                "Frame #%02d  \u2022  t=%.2fs  \u2022  %s",
                                sample.index + 1,
                                sample.timeSec,
                                sample.sceneLabel.ifBlank { "SCENE" }
                            ),
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            lineHeight = 28.sp,
                            letterSpacing = (-0.6).sp,
                            color = RawColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        CircleActionButton(
                            icon = RawIconType.CLOSE,
                            size = 38.dp,
                            onClick = { inspectingIndex = null }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black)
                            .border(
                                1.5.dp,
                                when {
                                    sample.warnings.isNotEmpty() -> RawColors.ErrorCoral
                                    sample.autoFitCount > 0 -> RawColors.Tan.copy(alpha = 0.65f)
                                    else -> RawColors.Divider
                                },
                                RoundedCornerShape(24.dp)
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
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(RawColors.ErrorSoft)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "WARNING: $w",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = RawColors.ErrorCoral
                                )
                            }
                        }
                        sample.autoFitNotes.forEach { note ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(RawColors.RaisedCard)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "AUTO-FIT: $note",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = RawColors.Tan
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RawColors.RaisedCard)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "OK - all other text inside margins",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                color = RawColors.TextMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RawColors.RaisedCard)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "OK - no external resources requested",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
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
                            height = 56.dp,
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
                                    .height(56.dp)
                                    .clip(CircleShape)
                                    .background(RawColors.RaisedCard),
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
        hasFit -> RawColors.Tan.copy(alpha = 0.65f)
        else -> Color.Transparent
    }
    val (statusLabel, statusBg, statusFg) = when {
        hasWarn -> Triple("WARN", RawColors.ErrorSoft, RawColors.ErrorCoral)
        hasFit -> Triple("FIT", RawColors.AccentSoft, RawColors.Tan)
        else -> Triple("OK", RawColors.PillGrey, RawColors.TextMuted)
    }

    RawPressButton(onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(RawColors.CardSurface)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "#%02d t=%.2fs", sample.index + 1, sample.timeSec),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    color = RawColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        color = statusFg
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .then(
                        if (frameBorder != Color.Transparent) {
                            Modifier.border(1.5.dp, frameBorder, RoundedCornerShape(16.dp))
                        } else Modifier
                    )
            ) {
                val tileReq = remember(sample.thumbPath, cacheKeyTag) {
                    ImageRequest.Builder(context)
                        .data(File(sample.thumbPath))
                        .size(480, 270)
                        .memoryCacheKey("${sample.thumbPath}_$cacheKeyTag")
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                }
                AsyncImage(
                    model = tileReq,
                    contentDescription = "Frame ${sample.index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = if (sample.sceneLabel.isNotBlank()) sample.sceneLabel else String.format(Locale.US, "%.2fs", sample.timeSec),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
                color = RawColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExportTileButton(
    text: String,
    icon: RawIconType,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RawPressButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(CircleShape)
                .background(RawColors.RaisedCard)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RawIcon(type = icon, size = 15.dp, tint = RawColors.TextPrimary)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = RawColors.TextPrimary,
                maxLines = 1,
                softWrap = false
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
        targetValue = if (selected) RawColors.Tan else RawColors.RaisedCard,
        animationSpec = tween(200, easing = RawEasings.Morph),
        label = "sh_tog_bg"
    )
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = BricolageFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (selected) RawColors.Ink else RawColors.TextMuted
        )
    }
}
