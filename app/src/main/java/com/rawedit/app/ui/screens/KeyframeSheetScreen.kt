package com.rawedit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.domain.IssueCircle
import com.rawedit.app.domain.KeyframeSample
import com.rawedit.app.domain.KeyframeSheetGenerator
import com.rawedit.app.domain.KeyframeSheetResult
import com.rawedit.app.domain.PointNorm
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
import kotlin.math.hypot

@Composable
fun KeyframeSheetScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    apiKey: String,
    modelPref: String,
    quotaExhausted: Boolean,
    quotaBadgeText: String,
    userProfile: UserProfile? = null,
    onBack: () -> Unit,
    onEditCode: () -> Unit,
    onApplyFixedHtml: (String, Double) -> Unit,
    onOpenGroqKey: () -> Unit,
    onSwitchGroqKey: () -> Unit,
    onRefreshGroqQuota: () -> Unit,
    onRenderNow: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()
    val mainScroll = rememberScrollState()

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

    var capturing by remember { mutableStateOf(true) }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var progressTimeSec by remember { mutableStateOf(0.0) }
    var livePreviewPath by remember { mutableStateOf<String?>(null) }
    var sheetResult by remember { mutableStateOf<KeyframeSheetResult?>(null) }
    var editableSamples by remember { mutableStateOf<List<KeyframeSample>>(emptyList()) }
    var captureError by remember { mutableStateOf<String?>(null) }

    var circleDrawEnabled by remember { mutableStateOf(false) }
    val activeDragPoints = remember { mutableStateListOf<PointNorm>() }

    var exportBusy by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var promptCopied by remember { mutableStateOf(false) }
    var pendingPdfFile by remember { mutableStateOf<File?>(null) }
    var pendingZipFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(2000)
            toastMsg = null
        }
    }

    fun updateSampleInList(sampleIndex: Int, transform: (KeyframeSample) -> KeyframeSample) {
        val next = editableSamples.map { s ->
            if (s.index == sampleIndex) {
                val updated = transform(s)
                if (updated.circles.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        KeyframeSheetGenerator.saveAnnotatedThumbnail(updated, cacheManager.sheetTempDir(project.id))
                    }
                }
                updated
            } else s
        }
        editableSamples = next
        sheetResult = sheetResult?.copy(
            samples = next,
            warningCount = next.count { it.isFlagged }
        )
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
                    toastMsg = "PDF saved"
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
                    toastMsg = "ZIP saved"
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
        editableSamples = emptyList()
        progressDone = 0
        progressTotal = 0
        progressTimeSec = 0.0
        livePreviewPath = null
        circleDrawEnabled = false

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
            editableSamples = result.samples
            capturing = false
        } catch (_: CancellationException) {
        } catch (t: Throwable) {
            if (!isScreenDisposed) {
                capturing = false
                captureError = t.message ?: "Could not capture keyframe contact sheet."
            }
        }
    }

    fun downloadPdf() {
        if (editableSamples.isEmpty() || exportBusy) return
        exportBusy = true
        scope.launch {
            runCatching {
                val outName = "${safeFileBase}_contact_sheet.pdf"
                val outFile = File(cacheManager.sharedPacksDir, outName)
                KeyframeSheetGenerator.buildPdfContactSheet(
                    samples = editableSamples,
                    title = project.title.ifBlank { "Untitled Reel" },
                    outPdfFile = outFile
                )
                outFile to outName
            }.onSuccess { (pdfFile, fileName) ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dlRes = cacheManager.saveFileToDownloads(pdfFile, fileName, "application/pdf")
                    exportBusy = false
                    dlRes.onSuccess {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        toastMsg = "PDF saved to Downloads"
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

    fun downloadZip() {
        val result = sheetResult ?: return
        if (editableSamples.isEmpty() || exportBusy) return
        exportBusy = true
        scope.launch {
            runCatching {
                val outName = "${safeFileBase}_contact_sheet.zip"
                val outFile = File(cacheManager.sharedPacksDir, outName)
                KeyframeSheetGenerator.buildContactSheetZip(
                    samples = editableSamples,
                    title = project.title.ifBlank { "Untitled Reel" },
                    durationSec = result.durationSec,
                    outZipFile = outFile
                )
                outFile to outName
            }.onSuccess { (zipFile, fileName) ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dlRes = cacheManager.saveFileToDownloads(zipFile, fileName, "application/zip")
                    exportBusy = false
                    dlRes.onSuccess {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        toastMsg = "ZIP saved to Downloads"
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
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Minimal Top Bar (Contact Sheet only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 44.dp)
                Text(
                    text = "Contact Sheet",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = RawColors.TextPrimary
                )
                RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 36.dp)
            }

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
                        } else null
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
                                    .clip(RoundedCornerShape(24.dp))
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
                                            "Capturing $progressDone / $progressTotal"
                                        } else {
                                            "Preparing frames..."
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
                                fontSize = 28.sp,
                                color = RawColors.TextPrimary
                            )
                            Text(
                                text = captureError.orEmpty(),
                                style = RawTypography.bodyLarge.copy(fontSize = 15.sp),
                                color = RawColors.TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            BigPrimaryButton(
                                text = "Retry capture",
                                height = 56.dp,
                                leadingIcon = RawIconType.REFRESH,
                                onClick = { captureGen++ },
                                modifier = Modifier.width(210.dp)
                            )
                        }
                    }

                    sheetResult != null -> {
                        val res = sheetResult!!
                        val pagerState = rememberPagerState(
                            initialPage = 0,
                            pageCount = { editableSamples.size.coerceAtLeast(1) }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(mainScroll)
                                .padding(bottom = 104.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (editableSamples.isNotEmpty()) {
                                // Clean Instagram-Style Carousel Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(RawColors.CardSurface)
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    HorizontalPager(
                                        state = pagerState,
                                        userScrollEnabled = !circleDrawEnabled,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { pageIdx ->
                                        val sample = editableSamples.getOrNull(pageIdx) ?: return@HorizontalPager
                                        MinimalCarouselFrameSlide(
                                            sample = sample,
                                            pageIdx = pageIdx,
                                            totalPages = editableSamples.size,
                                            cacheKeyTag = "${captureGen}_${selectedMode.name}",
                                            circleDrawEnabled = circleDrawEnabled,
                                            activeDragPoints = activeDragPoints,
                                            onToggleFlagged = { targetFlagged ->
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                updateSampleInList(sample.index) { old ->
                                                    old.copy(isFlagged = targetFlagged)
                                                }
                                                if (!targetFlagged) circleDrawEnabled = false
                                            },
                                            onToggleCircleDrawMode = {
                                                if (!sample.isFlagged) {
                                                    updateSampleInList(sample.index) { it.copy(isFlagged = true) }
                                                }
                                                circleDrawEnabled = !circleDrawEnabled
                                            },
                                            onAddCircle = { newCircle ->
                                                updateSampleInList(sample.index) { old ->
                                                    old.copy(
                                                        isFlagged = true,
                                                        circles = old.circles + newCircle
                                                    )
                                                }
                                                toastMsg = "Issue circled"
                                            },
                                            onUndoCircle = {
                                                updateSampleInList(sample.index) { old ->
                                                    old.copy(circles = old.circles.dropLast(1))
                                                }
                                            },
                                            onClearCircles = {
                                                updateSampleInList(sample.index) { old ->
                                                    old.copy(circles = emptyList())
                                                }
                                            }
                                        )
                                    }

                                    // Minimal Prev / Dots / Next
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircleActionButton(
                                            icon = RawIconType.CHEVRON_LEFT,
                                            size = 36.dp,
                                            enabled = pagerState.currentPage > 0,
                                            onClick = {
                                                scope.launch {
                                                    pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                                }
                                            }
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val total = editableSamples.size
                                            val cur = pagerState.currentPage
                                            val windowStart = (cur - 4).coerceAtLeast(0)
                                            val windowEnd = (windowStart + 8).coerceAtMost(total - 1)
                                            for (idx in windowStart..windowEnd) {
                                                val s = editableSamples[idx]
                                                val isActiveDot = idx == cur
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (isActiveDot) 8.dp else 6.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                isActiveDot -> RawColors.Tan
                                                                s.isFlagged -> RawColors.ErrorCoral
                                                                else -> RawColors.TextMuted.copy(alpha = 0.35f)
                                                            }
                                                        )
                                                )
                                            }
                                        }

                                        CircleActionButton(
                                            icon = RawIconType.CHEVRON_RIGHT,
                                            size = 36.dp,
                                            enabled = pagerState.currentPage < editableSamples.lastIndex,
                                            onClick = {
                                                scope.launch {
                                                    pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(editableSamples.lastIndex))
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // ONLY 3 Export Actions: Copy Prompt, Download ZIP, Download PDF
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(RawColors.CardSurface)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BigPrimaryButton(
                                    text = if (promptCopied) "Prompt copied!" else "Copy prompt",
                                    height = 50.dp,
                                    leadingIcon = if (promptCopied) RawIconType.CHECK else RawIconType.COPY,
                                    onClick = {
                                        val fixPrompt = KeyframeSheetGenerator.buildFixPrompt(
                                            samples = editableSamples,
                                            title = project.title.ifBlank { "Untitled Reel" },
                                            durationSec = res.durationSec,
                                            htmlCode = resolvedHtml
                                        )
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("raw_edit fix prompt", fixPrompt))
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        promptCopied = true
                                        toastMsg = "Prompt copied"
                                        scope.launch {
                                            delay(2000)
                                            promptCopied = false
                                        }
                                    }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ExportTileButton(
                                        text = "Download ZIP",
                                        enabled = !exportBusy,
                                        icon = RawIconType.DOWNLOAD,
                                        onClick = { downloadZip() },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ExportTileButton(
                                        text = "Download PDF",
                                        enabled = !exportBusy,
                                        icon = RawIconType.DOWNLOAD,
                                        onClick = { downloadPdf() },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
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
                    height = 56.dp,
                    onClick = onEditCode,
                    modifier = Modifier.weight(1f)
                )
                val hasAudio = project.audioUri.isNotBlank() && project.audioDurationSec > 0.0
                BigPrimaryButton(
                    text = if (hasAudio) "Render video" else "Add audio first",
                    height = 56.dp,
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
}

@Composable
private fun MinimalCarouselFrameSlide(
    sample: KeyframeSample,
    pageIdx: Int,
    totalPages: Int,
    cacheKeyTag: String,
    circleDrawEnabled: Boolean,
    activeDragPoints: MutableList<PointNorm>,
    onToggleFlagged: (Boolean) -> Unit,
    onToggleCircleDrawMode: () -> Unit,
    onAddCircle: (IssueCircle) -> Unit,
    onUndoCircle: () -> Unit,
    onClearCircles: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Minimal Header: Frame # + Timestamp | OK / FLAGGED Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.US, "#%02d \u2022 %.2fs", sample.index + 1, sample.timeSec),
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                color = RawColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(RawColors.RaisedCard)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (!sample.isFlagged) Color(0xFF2E9E5B) else Color.Transparent)
                        .clickable { onToggleFlagged(false) }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "\u2713 OK",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (!sample.isFlagged) Color.White else RawColors.TextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (sample.isFlagged) RawColors.ErrorCoral else Color.Transparent)
                        .clickable { onToggleFlagged(true) }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "\u2691 FLAGGED",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (sample.isFlagged) Color.Black else RawColors.TextMuted
                    )
                }
            }
        }

        // 16:9 Carousel Image + Issue Circle Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
                .border(
                    width = if (sample.isFlagged) 2.dp else 1.dp,
                    color = if (sample.isFlagged) RawColors.ErrorCoral else RawColors.Divider,
                    shape = RoundedCornerShape(18.dp)
                )
                .then(
                    if (circleDrawEnabled) {
                        Modifier
                            .pointerInput(sample.index, circleDrawEnabled) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        activeDragPoints.clear()
                                        val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                        activeDragPoints.add(PointNorm(nx, ny))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val nx = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val ny = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                        activeDragPoints.add(PointNorm(nx, ny))
                                    },
                                    onDragEnd = {
                                        if (activeDragPoints.isNotEmpty()) {
                                            val pts = activeDragPoints.toList()
                                            val minX = pts.minOf { it.x }
                                            val maxX = pts.maxOf { it.x }
                                            val minY = pts.minOf { it.y }
                                            val maxY = pts.maxOf { it.y }
                                            val cx = (minX + maxX) * 0.5f
                                            val cy = (minY + maxY) * 0.5f
                                            val span = hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()).toFloat() * 0.5f
                                            val rad = span.coerceIn(0.08f, 0.45f)
                                            onAddCircle(
                                                IssueCircle(
                                                    centerXNorm = cx,
                                                    centerYNorm = cy,
                                                    radiusNorm = rad,
                                                    pointsNorm = pts
                                                )
                                            )
                                            activeDragPoints.clear()
                                        }
                                    },
                                    onDragCancel = {
                                        activeDragPoints.clear()
                                    }
                                )
                            }
                            .pointerInput(sample.index, circleDrawEnabled) {
                                detectTapGestures { offset ->
                                    val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    onAddCircle(
                                        IssueCircle(
                                            centerXNorm = nx,
                                            centerYNorm = ny,
                                            radiusNorm = 0.14f
                                        )
                                    )
                                }
                            }
                    } else Modifier
                )
        ) {
            val tileReq = remember(sample.thumbPath, cacheKeyTag) {
                ImageRequest.Builder(context)
                    .data(File(sample.thumbPath))
                    .size(960, 540)
                    .memoryCacheKey("${sample.thumbPath}_$cacheKeyTag")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
            AsyncImage(
                model = tileReq,
                contentDescription = "Frame ${sample.index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                for (c in sample.circles) {
                    if (c.pointsNorm.size >= 3) {
                        val p = Path()
                        c.pointsNorm.forEachIndexed { idx, pt ->
                            val px = pt.x * w
                            val py = pt.y * h
                            if (idx == 0) p.moveTo(px, py) else p.lineTo(px, py)
                        }
                        drawPath(
                            path = p,
                            color = Color.Black.copy(alpha = 0.65f),
                            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = p,
                            color = Color(0xFFFF3B30),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    } else {
                        val cx = c.centerXNorm * w
                        val cy = c.centerYNorm * h
                        val r = c.radiusNorm * minOf(w, h)
                        drawCircle(
                            color = Color(0x28FF3B30),
                            radius = r,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.65f),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 6.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFFFF3B30),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 3.5.dp.toPx())
                        )
                    }
                }

                if (activeDragPoints.size >= 2) {
                    val livePath = Path()
                    activeDragPoints.forEachIndexed { idx, pt ->
                        val px = pt.x * w
                        val py = pt.y * h
                        if (idx == 0) livePath.moveTo(px, py) else livePath.lineTo(px, py)
                    }
                    drawPath(
                        path = livePath,
                        color = Color(0xFFFF3B30),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${pageIdx + 1} / $totalPages",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White
                )
            }

            if (circleDrawEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xDDFF3B30))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Draw circle",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Single clean Circle Issue row — no text walls or bloated fields when flagged
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RawPressButton(onClick = onToggleCircleDrawMode) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (circleDrawEnabled) RawColors.ErrorCoral else RawColors.RaisedCard)
                        .border(
                            width = 1.dp,
                            color = if (sample.isFlagged) RawColors.ErrorCoral else RawColors.Divider,
                            shape = CircleShape
                        )
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RawIcon(
                        type = RawIconType.EDIT,
                        size = 13.dp,
                        tint = if (circleDrawEnabled) Color.Black else RawColors.ErrorCoral
                    )
                    Text(
                        text = if (circleDrawEnabled) "Done" else if (sample.circles.isEmpty()) "Circle issue" else "Circle (${sample.circles.size})",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = if (circleDrawEnabled) Color.Black else RawColors.TextPrimary
                    )
                }
            }

            if (sample.circles.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RawColors.RaisedCard)
                        .clickable { onUndoCircle() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Undo",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = RawColors.TextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RawColors.ErrorSoft)
                        .clickable { onClearCircles() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Clear",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = RawColors.ErrorCoral
                    )
                }
            }
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
