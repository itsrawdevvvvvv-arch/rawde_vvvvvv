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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.domain.ChatMessageItem
import com.rawedit.app.domain.ChatbotEngine
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.GroqCodec
import com.rawedit.app.domain.HtmlVersionStore
import com.rawedit.app.domain.IssueCircle
import com.rawedit.app.domain.KeyframeSample
import com.rawedit.app.domain.KeyframeSheetGenerator
import com.rawedit.app.domain.KeyframeSheetResult
import com.rawedit.app.domain.PointNorm
import com.rawedit.app.domain.SceneSpan
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

private enum class SheetTopTab(val label: String) {
    CONTACT_SHEET("Contact Sheet"),
    SCENES("Scenes")
}

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
    val geminiRouter = remember { GeminiWaterfallRouter(context) }
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

    var activeTopTab by remember { mutableStateOf(SheetTopTab.CONTACT_SHEET) }
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
    var editableSamples by remember { mutableStateOf<List<KeyframeSample>>(emptyList()) }
    var captureError by remember { mutableStateOf<String?>(null) }

    // Circle drawing state
    var circleDrawEnabled by remember { mutableStateOf(false) }
    val activeDragPoints = remember { mutableStateListOf<PointNorm>() }

    // Scene tab state + Scene Chatbot state
    var selectedSceneIndex by remember { mutableIntStateOf(0) }
    var selectedSceneFrameIdx by remember { mutableIntStateOf(0) }
    var sceneChatMessages by remember(project.id) {
        mutableStateOf(
            ChatbotEngine.loadChatHistory(cacheManager, "scene_${project.id}").ifEmpty {
                listOf(
                    ChatMessageItem(
                        role = "assistant",
                        text = "Select any scene above. If a frame is **FLAGGED** (or you circled an issue area), tell me what to fix below and I'll patch the scene's HTML code directly!"
                    )
                )
            }
        )
    }
    var sceneChatInput by remember { mutableStateOf("") }
    var sceneChatBusy by remember { mutableStateOf(false) }
    var undoTick by remember { mutableIntStateOf(0) }
    val versions = remember(resolvedHtml.hashCode(), undoTick) { HtmlVersionStore.list(context, project.id) }

    var exportBusy by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var promptCopied by remember { mutableStateOf(false) }
    var showPatchDialog by remember { mutableStateOf(false) }
    var patchDraft by remember { mutableStateOf("") }
    var patchBusy by remember { mutableStateOf(false) }
    var patchError by remember { mutableStateOf<String?>(null) }
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
        editableSamples = emptyList()
        progressDone = 0
        progressTotal = 0
        progressTimeSec = 0.0
        livePreviewPath = null
        flaggedOnly = false

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

    fun exportPdf(share: Boolean) {
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
                    context.startActivity(Intent.createChooser(intent, "Share PDF Contact Sheet (with Circled Issues)"))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dlRes = cacheManager.saveFileToDownloads(pdfFile, fileName, "application/pdf")
                    exportBusy = false
                    dlRes.onSuccess {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        toastMsg = "contact-sheet.pdf saved (with circled issues)"
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
                    context.startActivity(Intent.createChooser(intent, "Share Contact Sheet ZIP (with Circled Images)"))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dlRes = cacheManager.saveFileToDownloads(zipFile, fileName, "application/zip")
                    exportBusy = false
                    dlRes.onSuccess {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        toastMsg = "frames.zip saved (with circled images)"
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 46.dp)

                // Top Mode Switcher: Contact Sheet vs Scenes
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RawColors.CardSurface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SheetTopTab.entries.forEach { tab ->
                        val selected = activeTopTab == tab
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) RawColors.Tan else Color.Transparent)
                                .clickable { activeTopTab = tab }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = if (selected) RawColors.Ink else RawColors.TextMuted
                            )
                        }
                    }
                }

                RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 38.dp)
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
                        val flaggedList = remember(editableSamples) {
                            editableSamples.filter { it.isFlagged }
                        }
                        val okCount = remember(editableSamples) {
                            editableSamples.count { !it.isFlagged }
                        }

                        if (activeTopTab == SheetTopTab.CONTACT_SHEET) {
                            val visibleSamples = if (flaggedOnly) flaggedList else editableSamples
                            val pagerState = rememberPagerState(
                                initialPage = 0,
                                pageCount = { visibleSamples.size.coerceAtLeast(1) }
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(mainScroll)
                                    .padding(bottom = 112.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Summary + Filter + Quick Select Image Strip
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(RawColors.CardSurface)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${editableSamples.size} frames • $okCount OK • ${flaggedList.size} Flagged",
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = RawColors.TextPrimary
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            SheetTogglePill(
                                                label = "All (${editableSamples.size})",
                                                selected = !flaggedOnly,
                                                onClick = { flaggedOnly = false }
                                            )
                                            SheetTogglePill(
                                                label = "Flagged (${flaggedList.size})",
                                                selected = flaggedOnly,
                                                onClick = { flaggedOnly = true }
                                            )
                                        }
                                    }

                                    // Select Particular Image Strip
                                    if (visibleSamples.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "SELECT IMAGE:",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = RawColors.TextMuted
                                            )
                                            visibleSamples.forEachIndexed { pageIdx, s ->
                                                val isCurr = pagerState.currentPage == pageIdx
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                isCurr -> RawColors.Tan
                                                                s.isFlagged -> RawColors.ErrorSoft
                                                                else -> RawColors.RaisedCard
                                                            }
                                                        )
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (s.isFlagged && !isCurr) RawColors.ErrorCoral else Color.Transparent,
                                                            shape = CircleShape
                                                        )
                                                        .clickable {
                                                            scope.launch { pagerState.animateScrollToPage(pageIdx) }
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = String.format(Locale.US, "#%02d%s", s.index + 1, if (s.circles.isNotEmpty()) " \u25CE" else ""),
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = when {
                                                            isCurr -> RawColors.Ink
                                                            s.isFlagged -> RawColors.ErrorCoral
                                                            else -> RawColors.TextPrimary
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (visibleSamples.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(RawColors.CardSurface)
                                            .padding(22.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 48.dp)
                                        Text(
                                            text = "No flagged frames!",
                                            style = RawTypography.titleMedium,
                                            color = RawColors.Tan
                                        )
                                        RawSecondaryButton(
                                            text = "Show all frames",
                                            height = 44.dp,
                                            fillWidth = false,
                                            onClick = { flaggedOnly = false }
                                        )
                                    }
                                } else {
                                    // Instagram-Style Carousel View
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(RawColors.CardSurface)
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        HorizontalPager(
                                            state = pagerState,
                                            userScrollEnabled = !circleDrawEnabled,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { pageIdx ->
                                            val sample = visibleSamples.getOrNull(pageIdx) ?: return@HorizontalPager
                                            InstagramCarouselFrameSlide(
                                                sample = sample,
                                                pageIdx = pageIdx,
                                                totalPages = visibleSamples.size,
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
                                                    toastMsg = "Circled issue area saved for AI export"
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
                                                },
                                                onUpdateUserNote = { note ->
                                                    updateSampleInList(sample.index) { old ->
                                                        old.copy(userNote = note)
                                                    }
                                                }
                                            )
                                        }

                                        // Instagram Carousel Prev / Dots / Next Bar
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircleActionButton(
                                                icon = RawIconType.CHEVRON_LEFT,
                                                size = 38.dp,
                                                enabled = pagerState.currentPage > 0,
                                                onClick = {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                                    }
                                                }
                                            )

                                            // Instagram Pagination Dots
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val total = visibleSamples.size
                                                val cur = pagerState.currentPage
                                                val windowStart = (cur - 4).coerceAtLeast(0)
                                                val windowEnd = (windowStart + 8).coerceAtMost(total - 1)
                                                for (idx in windowStart..windowEnd) {
                                                    val s = visibleSamples[idx]
                                                    val isActiveDot = idx == cur
                                                    Box(
                                                        modifier = Modifier
                                                            .size(if (isActiveDot) 9.dp else 6.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                when {
                                                                    isActiveDot -> RawColors.Tan
                                                                    s.isFlagged -> RawColors.ErrorCoral
                                                                    else -> RawColors.TextMuted.copy(alpha = 0.4f)
                                                                }
                                                            )
                                                    )
                                                }
                                            }

                                            CircleActionButton(
                                                icon = RawIconType.CHEVRON_RIGHT,
                                                size = 38.dp,
                                                enabled = pagerState.currentPage < visibleSamples.lastIndex,
                                                onClick = {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(visibleSamples.lastIndex))
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                // Export Sheet for Cloud AI Card
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
                                            text = "Includes all OK/FLAGGED states & red-circled issue images",
                                            style = RawTypography.labelMedium,
                                            color = RawColors.Tan
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
                                                samples = editableSamples,
                                                title = project.title.ifBlank { "Untitled Reel" },
                                                durationSec = res.durationSec,
                                                htmlCode = resolvedHtml
                                            )
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("raw_edit fix prompt", fixPrompt))
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            promptCopied = true
                                            toastMsg = "Fix prompt + circled coords copied"
                                            scope.launch {
                                                delay(2000)
                                                promptCopied = false
                                            }
                                        }
                                    )

                                    RawSecondaryButton(
                                        text = "Paste AI patch (<<<SEARCH)",
                                        height = 46.dp,
                                        leadingIcon = RawIconType.CODE,
                                        onClick = {
                                            patchError = null
                                            showPatchDialog = true
                                        }
                                    )
                                }
                            }
                        } else {
                            // SCENES VIEW: Select Scene -> Full Scene Preview + Toggle/Circle + Scene Chatbot Below
                            val scenes = remember(res.scenes, editableSamples, res.durationSec) {
                                res.scenes.ifEmpty {
                                    KeyframeSheetGenerator.buildFallbackScenes(res.durationSec, editableSamples)
                                }
                            }
                            val safeSceneIdx = selectedSceneIndex.coerceIn(0, (scenes.size - 1).coerceAtLeast(0))
                            val currentScene = scenes.getOrElse(safeSceneIdx) {
                                SceneSpan(1, 0.0, res.durationSec, "SCENE")
                            }
                            val sceneSamples = remember(currentScene, editableSamples, scenes.size) {
                                editableSamples.filter { s ->
                                    if (currentScene.index == scenes.size) {
                                        s.timeSec >= currentScene.start - 0.05 && s.timeSec <= currentScene.end + 0.1
                                    } else {
                                        s.timeSec >= currentScene.start - 0.05 && s.timeSec < currentScene.end + 0.02
                                    }
                                }.ifEmpty { editableSamples.take(3) }
                            }
                            val safeFrameIdxInScene = selectedSceneFrameIdx.coerceIn(0, (sceneSamples.size - 1).coerceAtLeast(0))
                            val currentSceneSample = sceneSamples.getOrNull(safeFrameIdxInScene)

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(mainScroll)
                                    .padding(bottom = 112.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Scene Selector Bar
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(RawColors.CardSurface)
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "SELECT SCENE (${scenes.size} SCENES)",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = RawColors.Tan
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        scenes.forEachIndexed { idx, sc ->
                                            val isSelected = idx == safeSceneIdx
                                            val hasFlaggedInScene = editableSamples.any { s ->
                                                s.isFlagged && s.timeSec >= sc.start - 0.02 && s.timeSec <= sc.end + 0.02
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isSelected -> RawColors.Tan
                                                            hasFlaggedInScene -> RawColors.ErrorSoft
                                                            else -> RawColors.RaisedCard
                                                        }
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (hasFlaggedInScene && !isSelected) RawColors.ErrorCoral else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        selectedSceneIndex = idx
                                                        selectedSceneFrameIdx = 0
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = String.format(
                                                        Locale.US,
                                                        "Scene %d (%.1fs–%.1fs)%s",
                                                        sc.index,
                                                        sc.start,
                                                        sc.end,
                                                        if (hasFlaggedInScene) " \u2691" else ""
                                                    ),
                                                    fontFamily = BricolageFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = when {
                                                        isSelected -> RawColors.Ink
                                                        hasFlaggedInScene -> RawColors.ErrorCoral
                                                        else -> RawColors.TextPrimary
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Full Scene Preview & Frame Selector within Scene
                                if (currentSceneSample != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(RawColors.CardSurface)
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = String.format(
                                                    Locale.US,
                                                    "Scene %d • %.2fs – %.2fs (%d keyframes)",
                                                    currentScene.index,
                                                    currentScene.start,
                                                    currentScene.end,
                                                    sceneSamples.size
                                                ),
                                                fontFamily = BricolageFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.5.sp,
                                                color = RawColors.TextPrimary
                                            )
                                            if (versions.isNotEmpty()) {
                                                RawPressButton(
                                                    onClick = {
                                                        val v = versions.firstOrNull() ?: return@RawPressButton
                                                        scope.launch {
                                                            val prevHtml = HtmlVersionStore.readHtml(v)
                                                            val vr = CodeValidator.validateFull(context, cacheManager, prevHtml)
                                                            if (vr.isValid) {
                                                                HtmlVersionStore.remove(v)
                                                                resolvedHtml = vr.cleanedHtml
                                                                onApplyFixedHtml(vr.cleanedHtml, vr.durationSec)
                                                                undoTick++
                                                                captureGen++
                                                                toastMsg = "Restored previous HTML version"
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(RawColors.RaisedCard)
                                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                                    ) {
                                                        Text(
                                                            text = "Undo Fix",
                                                            fontFamily = JetBrainsMonoFamily,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = RawColors.Tan
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Sub-frame beats inside the selected Scene
                                        if (sceneSamples.size > 1) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                sceneSamples.forEachIndexed { fIdx, s ->
                                                    val isSel = fIdx == safeFrameIdxInScene
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(if (isSel) RawColors.Tan else RawColors.RaisedCard)
                                                            .clickable { selectedSceneFrameIdx = fIdx }
                                                            .padding(horizontal = 11.dp, vertical = 5.dp)
                                                    ) {
                                                        Text(
                                                            text = String.format(
                                                                Locale.US,
                                                                "Beat %d (t=%.2fs) %s",
                                                                fIdx + 1,
                                                                s.timeSec,
                                                                if (s.isFlagged) "[FLAGGED]" else "[OK]"
                                                            ),
                                                            fontFamily = JetBrainsMonoFamily,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = if (isSel) RawColors.Ink else if (s.isFlagged) RawColors.ErrorCoral else RawColors.TextPrimary
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        InstagramCarouselFrameSlide(
                                            sample = currentSceneSample,
                                            pageIdx = safeFrameIdxInScene,
                                            totalPages = sceneSamples.size,
                                            cacheKeyTag = "${captureGen}_scene_${selectedMode.name}",
                                            circleDrawEnabled = circleDrawEnabled,
                                            activeDragPoints = activeDragPoints,
                                            onToggleFlagged = { targetFlagged ->
                                                updateSampleInList(currentSceneSample.index) {
                                                    it.copy(isFlagged = targetFlagged)
                                                }
                                            },
                                            onToggleCircleDrawMode = {
                                                if (!currentSceneSample.isFlagged) {
                                                    updateSampleInList(currentSceneSample.index) { it.copy(isFlagged = true) }
                                                }
                                                circleDrawEnabled = !circleDrawEnabled
                                            },
                                            onAddCircle = { newCircle ->
                                                updateSampleInList(currentSceneSample.index) { old ->
                                                    old.copy(isFlagged = true, circles = old.circles + newCircle)
                                                }
                                                toastMsg = "Circled issue area saved"
                                            },
                                            onUndoCircle = {
                                                updateSampleInList(currentSceneSample.index) { old ->
                                                    old.copy(circles = old.circles.dropLast(1))
                                                }
                                            },
                                            onClearCircles = {
                                                updateSampleInList(currentSceneSample.index) { old ->
                                                    old.copy(circles = emptyList())
                                                }
                                            },
                                            onUpdateUserNote = { note ->
                                                updateSampleInList(currentSceneSample.index) { old ->
                                                    old.copy(userNote = note)
                                                }
                                            }
                                        )
                                    }
                                }

                                // Scene Chatbot Directly Below the Selected Scene
                                SceneFixChatbotCard(
                                    scene = currentScene,
                                    messages = sceneChatMessages,
                                    inputText = sceneChatInput,
                                    busy = sceneChatBusy,
                                    onInputChange = { sceneChatInput = it },
                                    onSendInstruction = { textToSend ->
                                        val cleanInstruction = textToSend.trim()
                                        if (cleanInstruction.isEmpty() || sceneChatBusy) return@SceneFixChatbotCard
                                        val profile = userProfile ?: UserProfile(geminiApiKey = apiKey)
                                        if (!profile.hasGeminiKey) {
                                            onOpenGroqKey()
                                            return@SceneFixChatbotCard
                                        }
                                        val userMsg = ChatMessageItem(
                                            role = "user",
                                            text = "[Scene S${currentScene.index}] $cleanInstruction"
                                        )
                                        val nextHistory = sceneChatMessages + userMsg
                                        sceneChatMessages = nextHistory
                                        ChatbotEngine.saveChatHistory(cacheManager, nextHistory, "scene_${project.id}")
                                        sceneChatInput = ""
                                        sceneChatBusy = true

                                        scope.launch {
                                            val htmlBefore = resolvedHtml
                                            val resFix = ChatbotEngine.sendSceneFixMessage(
                                                context = context,
                                                geminiRouter = geminiRouter,
                                                cacheManager = cacheManager,
                                                userProfile = profile,
                                                project = project,
                                                currentHtml = htmlBefore,
                                                scene = currentScene,
                                                sceneSamples = sceneSamples,
                                                allSamples = editableSamples,
                                                userInstruction = cleanInstruction
                                            )
                                            sceneChatBusy = false
                                            resFix.onSuccess { (botMsg, validated) ->
                                                val updatedChat = sceneChatMessages + botMsg
                                                sceneChatMessages = updatedChat
                                                ChatbotEngine.saveChatHistory(cacheManager, updatedChat, "scene_${project.id}")
                                                if (validated != null && validated.isValid) {
                                                    HtmlVersionStore.save(context, project.id, htmlBefore, "Before Scene S${currentScene.index} fix")
                                                    resolvedHtml = validated.cleanedHtml
                                                    onApplyFixedHtml(validated.cleanedHtml, validated.durationSec)
                                                    undoTick++
                                                    captureGen++
                                                    toastMsg = "Scene S${currentScene.index} fixed — re-capturing!"
                                                }
                                            }.onFailure { err ->
                                                val errMsg = ChatMessageItem(
                                                    role = "assistant",
                                                    text = "Could not apply scene fix: ${err.message ?: "Check your Gemini API key."}"
                                                )
                                                val updatedChat = sceneChatMessages + errMsg
                                                sceneChatMessages = updatedChat
                                                ChatbotEngine.saveChatHistory(cacheManager, updatedChat, "scene_${project.id}")
                                            }
                                        }
                                    }
                                )
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

    if (showPatchDialog) {
        Dialog(
            onDismissRequest = { if (!patchBusy) showPatchDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(RawColors.CardSurface)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Paste AI Patch",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = RawColors.TextPrimary
                    )
                    CircleActionButton(
                        icon = RawIconType.CLOSE,
                        size = 36.dp,
                        onClick = { if (!patchBusy) showPatchDialog = false }
                    )
                }

                OutlinedTextField(
                    value = patchDraft,
                    onValueChange = {
                        patchDraft = it
                        patchError = null
                    },
                    placeholder = {
                        Text(
                            text = "<<<SEARCH\nexact text from HTML\n===REPLACE\nupdated text\n>>>",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            color = RawColors.TextMuted
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.5.sp,
                        color = RawColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.Tan,
                        unfocusedBorderColor = RawColors.Divider,
                        focusedContainerColor = RawColors.BgPrimary,
                        unfocusedContainerColor = RawColors.BgPrimary,
                        cursorColor = RawColors.Tan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 260.dp)
                )

                patchError?.let { errTxt ->
                    Text(
                        text = errTxt,
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = RawColors.ErrorCoral
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawSecondaryButton(
                        text = "Cancel",
                        height = 48.dp,
                        enabled = !patchBusy,
                        onClick = { showPatchDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    BigPrimaryButton(
                        text = if (patchBusy) "Applying..." else "Apply patch",
                        height = 48.dp,
                        enabled = !patchBusy,
                        leadingIcon = RawIconType.CHECK,
                        containerColor = RawColors.Tan,
                        contentColor = RawColors.Ink,
                        onClick = {
                            val patches = GroqCodec.parseClaudeSearchReplacePatches(patchDraft)
                            if (patches.isEmpty()) {
                                patchError = "No valid <<<SEARCH ... ===REPLACE ... >>> blocks found."
                                return@BigPrimaryButton
                            }
                            val applyOutcome = GroqCodec.applyExactOnceEdits(resolvedHtml, patches)
                            if (applyOutcome.failedEdits.isNotEmpty()) {
                                val firstFail = applyOutcome.failedEdits.first()
                                patchError = "Patch failed (${firstFail.reason}): ${firstFail.edit.find.take(90)}"
                                return@BigPrimaryButton
                            }
                            if (applyOutcome.appliedCount == 0) {
                                patchError = "No changes matched in current HTML."
                                return@BigPrimaryButton
                            }
                            patchBusy = true
                            patchError = null
                            scope.launch {
                                val validation = CodeValidator.validateFull(context, cacheManager, applyOutcome.updatedHtml)
                                patchBusy = false
                                if (validation.isValid) {
                                    resolvedHtml = validation.cleanedHtml
                                    onApplyFixedHtml(validation.cleanedHtml, validation.durationSec)
                                    showPatchDialog = false
                                    patchDraft = ""
                                    toastMsg = "Applied ${applyOutcome.appliedCount} patch(es) — re-capturing"
                                    captureGen++
                                } else {
                                    patchError = "Patched HTML failed check (rolled back): ${validation.summaryMessage}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun InstagramCarouselFrameSlide(
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
    onClearCircles: () -> Unit,
    onUpdateUserNote: (String) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Row: Frame # + Timestamp + Complete OK / FLAGGED Toggle Control
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format(
                        Locale.US,
                        "Frame #%02d  \u2022  t=%.2fs  %s",
                        sample.index + 1,
                        sample.timeSec,
                        sample.sceneLabel
                    ),
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = RawColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Two-State Complete User Control Toggle: [ OK ] | [ FLAGGED ]
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(RawColors.RaisedCard)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (!sample.isFlagged) Color(0xFF2E9E5B) else Color.Transparent)
                        .clickable { onToggleFlagged(false) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "\u2713 OK",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = if (!sample.isFlagged) Color.White else RawColors.TextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (sample.isFlagged) RawColors.ErrorCoral else Color.Transparent)
                        .clickable { onToggleFlagged(true) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "\u2691 FLAGGED",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = if (sample.isFlagged) Color.Black else RawColors.TextMuted
                    )
                }
            }
        }

        // 16:9 Carousel Image + Interactive Issue Circle Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
                .border(
                    width = if (sample.isFlagged) 2.dp else 1.dp,
                    color = if (sample.isFlagged) RawColors.ErrorCoral else RawColors.Divider,
                    shape = RoundedCornerShape(20.dp)
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

            // Draw saved circles + live active drag stroke
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

            // Instagram-style Counter Pill (top-right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${pageIdx + 1} / $totalPages",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = Color.White
                )
            }

            if (circleDrawEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xDDFF3B30))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "DRAW MODE: Drag or tap to circle issue",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Circle Issue Tool Row when FLAGGED (or to quickly flag & circle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
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
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RawIcon(
                        type = RawIconType.EDIT,
                        size = 14.dp,
                        tint = if (circleDrawEnabled) Color.Black else RawColors.ErrorCoral
                    )
                    Text(
                        text = if (circleDrawEnabled) "Done Circling" else "Circle Issue Area (${sample.circles.size})",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (circleDrawEnabled) Color.Black else RawColors.TextPrimary
                    )
                }
            }

            if (sample.circles.isNotEmpty()) {
                RawSecondaryButton(
                    text = "Undo circle",
                    height = 36.dp,
                    leadingIcon = RawIconType.UNDO,
                    fillWidth = false,
                    onClick = onUndoCircle
                )
                RawSecondaryButton(
                    text = "Clear circles",
                    height = 36.dp,
                    isDanger = true,
                    fillWidth = false,
                    onClick = onClearCircles
                )
            }
        }

        if (sample.isFlagged) {
            OutlinedTextField(
                value = sample.userNote,
                onValueChange = onUpdateUserNote,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Optional note for circled/flagged issue (sent to AI)...",
                        fontSize = 12.5.sp,
                        color = RawColors.TextMuted
                    )
                },
                textStyle = TextStyle(
                    fontFamily = BricolageFamily,
                    fontSize = 13.5.sp,
                    color = RawColors.TextPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RawColors.ErrorCoral,
                    unfocusedBorderColor = RawColors.Divider,
                    focusedContainerColor = RawColors.RaisedCard,
                    unfocusedContainerColor = RawColors.RaisedCard,
                    cursorColor = RawColors.ErrorCoral
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Diagnostics notes if any
        if (sample.warnings.isNotEmpty() || sample.autoFitNotes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sample.warnings.take(2).forEach { w ->
                    Text(
                        text = "\u26A0 $w",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.5.sp,
                        color = RawColors.ErrorCoral
                    )
                }
                sample.autoFitNotes.take(2).forEach { n ->
                    Text(
                        text = "\u2728 $n",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.5.sp,
                        color = RawColors.Tan
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneFixChatbotCard(
    scene: SceneSpan,
    messages: List<ChatMessageItem>,
    inputText: String,
    busy: Boolean,
    onInputChange: (String) -> Unit,
    onSendInstruction: (String) -> Unit
) {
    val quickFixes = listOf(
        "Fix the flagged & circled issue in Scene ${scene.index}",
        "Fix text overlap or edge clipping in Scene ${scene.index}",
        "Improve text contrast & layout spacing in Scene ${scene.index}"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.AccentBorder, RoundedCornerShape(28.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RawIcon(RawIconType.SPARK, size = 18.dp, tint = RawColors.Tan)
                Column {
                    Text(
                        text = "Scene ${scene.index} AI Fix Chatbot",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.5.sp,
                        color = RawColors.TextPrimary
                    )
                    Text(
                        text = "Tell the chatbot what to change or fix in Scene ${scene.index} (${String.format(Locale.US, "%.1fs–%.1fs", scene.start, scene.end)})",
                        fontFamily = BricolageFamily,
                        fontSize = 12.sp,
                        color = RawColors.TextMuted
                    )
                }
            }
        }

        // Quick Fix Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickFixes.forEach { q ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RawColors.RaisedCard)
                        .clickable(enabled = !busy) { onSendInstruction(q) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = q,
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = RawColors.Tan
                    )
                }
            }
        }

        // Recent Scene Chat Messages
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            messages.takeLast(5).forEach { m ->
                val isUser = m.role == "user"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isUser) RawColors.AccentSoft else RawColors.RaisedCard)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isUser) "YOU" else "SCENE AI",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = RawColors.Tan
                    )
                    Text(
                        text = m.text,
                        fontFamily = BricolageFamily,
                        fontSize = 13.5.sp,
                        lineHeight = 18.5.sp,
                        color = RawColors.TextPrimary
                    )
                    m.appliedActions.forEach { badge ->
                        Text(
                            text = "\u2713 $badge",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = RawColors.Tan
                        )
                    }
                }
            }
            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(4.dp)
                ) {
                    CircularProgressIndicator(
                        color = RawColors.Tan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Patching Scene ${scene.index} HTML & verifying...",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = RawColors.Tan
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        text = "Describe the fix for Scene ${scene.index} (e.g., fix circled area)...",
                        fontSize = 13.sp,
                        color = RawColors.TextMuted
                    )
                },
                textStyle = TextStyle(
                    fontFamily = BricolageFamily,
                    fontSize = 14.sp,
                    color = RawColors.TextPrimary
                ),
                maxLines = 3,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RawColors.Tan,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = RawColors.RaisedCard,
                    unfocusedContainerColor = RawColors.RaisedCard,
                    cursorColor = RawColors.Tan
                ),
                modifier = Modifier.weight(1f)
            )
            CircleActionButton(
                icon = RawIconType.ARROW_RIGHT,
                size = 44.dp,
                backgroundColor = RawColors.Tan,
                iconTint = RawColors.Ink,
                enabled = !busy && inputText.isNotBlank(),
                onClick = { onSendInstruction(inputText) }
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
            .height(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = BricolageFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (selected) RawColors.Ink else RawColors.TextMuted
        )
    }
}
