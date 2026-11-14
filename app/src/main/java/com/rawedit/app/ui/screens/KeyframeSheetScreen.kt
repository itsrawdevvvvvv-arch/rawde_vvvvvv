package com.rawedit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.GeminiRouteMode
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.GroqCodec
import com.rawedit.app.domain.KeyframeSample
import com.rawedit.app.domain.KeyframeSheetGenerator
import com.rawedit.app.domain.KeyframeSheetResult
import com.rawedit.app.domain.SheetSamplingMode
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.MorphingFilterChips
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class SheetDisplayMode(val label: String) {
    CONTACT_SHEET("Contact Sheet"),
    SCENES("Scenes & AI Fix")
}

data class SceneGroupInfo(
    val index: Int,
    val label: String,
    val startSec: Double,
    val endSec: Double,
    val sample: KeyframeSample?
)

@Composable
fun KeyframeSheetScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    onBack: () -> Unit,
    onEditCode: () -> Unit,
    onRenderNow: () -> Unit,
    onApplyFixedHtml: (newHtml: String, newDurationSec: Double) -> Unit,
    userProfile: UserProfile? = null,
    apiKey: String = userProfile?.activeAiKey().orEmpty(),
    modelPref: String = "",
    quotaExhausted: Boolean = false,
    quotaBadgeText: String = "",
    onOpenGroqKey: () -> Unit = {},
    onSwitchGroqKey: () -> Unit = onOpenGroqKey,
    onRefreshGroqQuota: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val animEnabled = rememberAnimationsEnabled()

    val geminiRouter = remember { GeminiWaterfallRouter(context) }

    var resolvedHtml by remember(project.id) { mutableStateOf<String>(project.code) }
    var durationSec by remember(project.id) { mutableStateOf(project.audioDurationSec.coerceAtLeast(0.5)) }
    var htmlReady by remember(project.id) { mutableStateOf(false) }

    LaunchedEffect(project.id, project.code) {
        val loaded = cacheManager.resolveHtmlCode(project.code)
        resolvedHtml = loaded
        val meta = CodeValidator.extractMetadata(loaded)
        if (meta.durationSec > 0.0) {
            durationSec = meta.durationSec
        }
        htmlReady = true
    }

    var selectedModeIndex by remember { mutableIntStateOf(0) }
    val selectedMode = SheetSamplingMode.entries[selectedModeIndex]
    var selectedDisplayMode by remember { mutableStateOf(SheetDisplayMode.CONTACT_SHEET) }

    var captureGen by remember { mutableIntStateOf(1) }
    var capturing by remember { mutableStateOf(false) }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var progressTimeSec by remember { mutableStateOf(0.0) }
    var livePreviewPath by remember { mutableStateOf<String?>(null) }
    var sheetResult by remember { mutableStateOf<KeyframeSheetResult?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }

    // User OK / FLAGGED toggle overrides & annotated drawings
    val userFlaggedMap = remember { mutableStateMapOf<Int, Boolean>() }
    val annotatedMap = remember { mutableStateMapOf<Int, String>() }
    var drawingAnnotationForIndex by remember { mutableStateOf<Int?>(null) }
    var inspectingIndex by remember { mutableStateOf<Int?>(null) }
    var flaggedOnly by remember { mutableStateOf(false) }

    // Scenes & Inline AI Chatbot State
    var selectedSceneIndex by remember { mutableIntStateOf(0) }
    var sceneChatInput by remember { mutableStateOf("") }
    var sceneChatBusy by remember { mutableStateOf(false) }
    val sceneChatLog = remember { mutableStateListOf<ChatMessage>() }

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

    val safeFileBase = remember(project.title) {
        project.title.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "reel" }
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
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            captureError = e.message ?: "Capture failed"
        } finally {
            capturing = false
        }
    }

    // Helper to get samples updated with user overrides
    fun getEffectiveSamples(): List<KeyframeSample> {
        val raw = sheetResult?.samples ?: emptyList()
        return raw.map { s ->
            s.copy(
                isUserFlagged = userFlaggedMap[s.index],
                annotatedThumbPath = annotatedMap[s.index] ?: s.annotatedThumbPath
            )
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
                    samples = getEffectiveSamples(),
                    title = project.title.ifBlank { "Untitled Reel" },
                    outPdfFile = outFile
                )
                outFile to outName
            }.onSuccess { (pdfFile, fileName) ->
                if (share) {
                    exportBusy = false
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                        clipData = ClipData.newRawUri(fileName, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Contact Sheet PDF"))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dlRes = cacheManager.saveFileToDownloads(pdfFile, fileName, "application/pdf")
                    exportBusy = false
                    dlRes.onSuccess {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        toastMsg = "contact_sheet.pdf saved"
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
                    samples = getEffectiveSamples(),
                    title = project.title.ifBlank { "Untitled Reel" },
                    durationSec = result.durationSec,
                    outZipFile = outFile
                )
                outFile to outName
            }.onSuccess { (zipFile, fileName) ->
                if (share) {
                    exportBusy = false
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
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

    // Build scene groupings for Scenes mode
    val sceneGroups = remember(sheetResult, userFlaggedMap.size, annotatedMap.size) {
        val samples = getEffectiveSamples()
        val labels = samples.map { it.sceneLabel.ifBlank { "S01" } }.distinct()
        if (labels.isNotEmpty()) {
            labels.mapIndexed { idx, label ->
                val sceneSamples = samples.filter { (it.sceneLabel.ifBlank { "S01" }) == label }
                val startT = sceneSamples.minOfOrNull { it.timeSec } ?: (idx * 2.5)
                val endT = sceneSamples.maxOfOrNull { it.timeSec } ?: ((idx + 1) * 2.5)
                SceneGroupInfo(
                    index = idx,
                    label = label,
                    startSec = startT,
                    endSec = endT,
                    sample = sceneSamples.firstOrNull()
                )
            }
        } else {
            val totalSec = sheetResult?.durationSec ?: 15.0
            val count = maxOf(1, (totalSec / 3.0).toInt())
            (0 until count).map { i ->
                val s = i * 3.0
                val e = minOf(totalSec, (i + 1) * 3.0)
                SceneGroupInfo(
                    index = i,
                    label = String.format(Locale.US, "S%02d", i + 1),
                    startSec = s,
                    endSec = e,
                    sample = null
                )
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
            // Header Row
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
                words = listOf("Visual", "Inspector"),
                style = RawTypography.headlineLarge,
                highlightLastWord = true
            )

            // Segmented Tab Row: Contact Sheet (Carousel) vs Scenes (Full Scene + AI Fix)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RawColors.CardSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SheetDisplayMode.entries.forEach { mode ->
                    val isSelected = selectedDisplayMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) RawColors.Tan else Color.Transparent)
                            .clickable { selectedDisplayMode = mode }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (isSelected) RawColors.Ink else RawColors.TextMuted
                        )
                    }
                }
            }

            // Sampling Mode Chips
            MorphingFilterChips(
                options = SheetSamplingMode.entries.map { it.label },
                selectedIndex = selectedModeIndex,
                onSelect = { idx ->
                    if (idx != selectedModeIndex) {
                        selectedModeIndex = idx
                    }
                }
            )

            // Main Content Area
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
                                            "Capturing $progressDone / $progressTotal frames"
                                        } else {
                                            "Preparing stage..."
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
                        val allEffectiveSamples = getEffectiveSamples()
                        val flaggedSamples = allEffectiveSamples.filter { it.isFlagged }
                        val activeSamples = if (flaggedOnly) flaggedSamples else allEffectiveSamples

                        if (selectedDisplayMode == SheetDisplayMode.CONTACT_SHEET) {
                            // Instagram-style Carousel View
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 118.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Summary Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(RawColors.CardSurface)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${allEffectiveSamples.size} frames \u2022 ${String.format(Locale.US, "%.1fs", durationSec)}",
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = RawColors.TextPrimary
                                        )
                                        Text(
                                            text = "${flaggedSamples.size} FLAGGED \u2022 ${allEffectiveSamples.size - flaggedSamples.size} OK",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 12.sp,
                                            color = if (flaggedSamples.isNotEmpty()) RawColors.ErrorCoral else RawColors.Tan
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilterToggleChip(
                                            label = "All (${allEffectiveSamples.size})",
                                            selected = !flaggedOnly,
                                            onClick = { flaggedOnly = false }
                                        )
                                        FilterToggleChip(
                                            label = "Flagged (${flaggedSamples.size})",
                                            selected = flaggedOnly,
                                            onClick = { flaggedOnly = true }
                                        )
                                    }
                                }

                                if (activeSamples.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(RawColors.CardSurface)
                                            .padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 48.dp)
                                        Text(
                                            text = "No flagged frames",
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = RawColors.Tan
                                        )
                                        Text(
                                            text = "All frames are marked OK. Tap 'Show all' to view all frames.",
                                            style = RawTypography.bodyMedium
                                        )
                                        RawSecondaryButton(
                                            text = "Show all frames",
                                            height = 44.dp,
                                            fillWidth = false,
                                            onClick = { flaggedOnly = false }
                                        )
                                    }
                                } else {
                                    val pagerState = rememberPagerState(pageCount = { activeSamples.size })

                                    // Instagram-style Carousel Pager
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(440.dp),
                                        pageSpacing = 12.dp
                                    ) { page ->
                                        val sample = activeSamples[page]
                                        val isFlagged = sample.isFlagged
                                        val hasAnnotation = sample.annotatedThumbPath != null

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(26.dp))
                                                .background(RawColors.CardSurface)
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (isFlagged) RawColors.ErrorCoral.copy(alpha = 0.6f) else RawColors.Divider,
                                                    shape = RoundedCornerShape(26.dp)
                                                )
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Frame Header & OK/FLAGGED Toggle Chip
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = String.format(
                                                            Locale.US,
                                                            "Frame #%02d \u2022 t=%.2fs",
                                                            sample.index + 1,
                                                            sample.timeSec
                                                        ),
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.5.sp,
                                                        color = RawColors.TextPrimary
                                                    )
                                                    Text(
                                                        text = sample.sceneLabel.ifBlank { "Scene" },
                                                        fontFamily = BricolageFamily,
                                                        fontSize = 12.sp,
                                                        color = RawColors.TextMuted
                                                    )
                                                }

                                                // Full user control OK / FLAGGED toggle button
                                                Row(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(if (isFlagged) RawColors.ErrorSoft else RawColors.AccentSoft)
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isFlagged) RawColors.ErrorBorder else Color.Transparent,
                                                            shape = CircleShape
                                                        )
                                                        .clickable {
                                                            userFlaggedMap[sample.index] = !isFlagged
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        }
                                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isFlagged) RawColors.ErrorCoral else RawColors.Tan)
                                                    )
                                                    Text(
                                                        text = if (isFlagged) "FLAGGED" else "OK",
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = if (isFlagged) RawColors.ErrorCoral else RawColors.Tan
                                                    )
                                                }
                                            }

                                            // 16:9 Image Box
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .background(Color.Black)
                                                    .clickable { inspectingIndex = sample.index }
                                            ) {
                                                val imageFile = File(sample.effectiveImagePath)
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(imageFile)
                                                        .size(960, 540)
                                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                                        .diskCachePolicy(CachePolicy.DISABLED)
                                                        .build(),
                                                    contentDescription = "Frame ${sample.index + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                if (hasAnnotation) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(10.dp)
                                                            .clip(CircleShape)
                                                            .background(RawColors.ErrorCoral)
                                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = "Circled Defect",
                                                            fontFamily = JetBrainsMonoFamily,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.5.sp,
                                                            color = RawColors.Ink
                                                        )
                                                    }
                                                }
                                            }

                                            // Circle defect button & details
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isFlagged) {
                                                    RawSecondaryButton(
                                                        text = if (hasAnnotation) "Edit Circled Defect" else "Circle Defect Area",
                                                        height = 42.dp,
                                                        leadingIcon = RawIconType.EDIT,
                                                        fillWidth = false,
                                                        onClick = {
                                                            drawingAnnotationForIndex = sample.index
                                                        }
                                                    )
                                                } else {
                                                    Text(
                                                        text = "Frame is marked clean (OK)",
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontSize = 11.5.sp,
                                                        color = RawColors.TextMuted
                                                    )
                                                }

                                                Text(
                                                    text = "${page + 1} of ${activeSamples.size}",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 12.sp,
                                                    color = RawColors.TextMuted
                                                )
                                            }

                                            if (sample.warnings.isNotEmpty() || sample.autoFitCount > 0) {
                                                Text(
                                                    text = (sample.warnings + sample.autoFitNotes).joinToString("; ").take(90),
                                                    fontFamily = BricolageFamily,
                                                    fontSize = 12.sp,
                                                    color = RawColors.TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // Quick Slider to Jump to any Frame
                                    if (activeSamples.size > 1) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(RawColors.CardSurface)
                                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Jump to Frame",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 11.5.sp,
                                                    color = RawColors.TextMuted
                                                )
                                                Text(
                                                    text = "Frame #${activeSamples[pagerState.currentPage].index + 1}",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.5.sp,
                                                    color = RawColors.Tan
                                                )
                                            }
                                            Slider(
                                                value = pagerState.currentPage.toFloat(),
                                                onValueChange = { targetPage ->
                                                    scope.launch {
                                                        pagerState.scrollToPage(targetPage.toInt().coerceIn(0, activeSamples.lastIndex))
                                                    }
                                                },
                                                valueRange = 0f..activeSamples.lastIndex.toFloat(),
                                                steps = (activeSamples.size - 2).coerceAtLeast(0),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = RawColors.Tan,
                                                    activeTrackColor = RawColors.Tan,
                                                    inactiveTrackColor = RawColors.RaisedCard
                                                )
                                            )
                                        }
                                    }
                                }

                                // Offline Export Card (PDF / ZIP with circled images / Prompt)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(RawColors.CardSurface)
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "Export for Cloud AI Tools",
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 19.sp,
                                            color = RawColors.TextPrimary
                                        )
                                        Text(
                                            text = "ZIP & PDF bundles contain all circled defect frames \u2022 0 API cost",
                                            style = RawTypography.labelMedium
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ExportPillButton(
                                            text = "Download PDF",
                                            icon = RawIconType.DOWNLOAD,
                                            enabled = !exportBusy,
                                            onClick = { exportPdf(share = false) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ExportPillButton(
                                            text = "Share PDF",
                                            icon = RawIconType.SHARE,
                                            enabled = !exportBusy,
                                            onClick = { exportPdf(share = true) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ExportPillButton(
                                            text = "Download ZIP",
                                            icon = RawIconType.DOWNLOAD,
                                            enabled = !exportBusy,
                                            onClick = { exportZip(share = false) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ExportPillButton(
                                            text = "Share ZIP",
                                            icon = RawIconType.SHARE,
                                            enabled = !exportBusy,
                                            onClick = { exportZip(share = true) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    BigPrimaryButton(
                                        text = if (promptCopied) "Copied targeted fix prompt!" else "Copy targeted AI fix prompt",
                                        height = 48.dp,
                                        leadingIcon = if (promptCopied) RawIconType.CHECK else RawIconType.COPY,
                                        onClick = {
                                            val fixPrompt = KeyframeSheetGenerator.buildFixPrompt(
                                                samples = getEffectiveSamples(),
                                                title = project.title.ifBlank { "Untitled Reel" },
                                                durationSec = durationSec,
                                                htmlCode = resolvedHtml
                                            )
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("raw_edit fix prompt", fixPrompt))
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            promptCopied = true
                                            toastMsg = "Targeted prompt & HTML copied!"
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
                            // Scenes Mode with Inline Scene AI Chatbot
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 118.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Scene Selector Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    sceneGroups.forEachIndexed { idx, sc ->
                                        val isSel = idx == selectedSceneIndex
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isSel) RawColors.Tan else RawColors.CardSurface)
                                                .border(1.dp, if (isSel) RawColors.Tan else RawColors.Divider, RoundedCornerShape(16.dp))
                                                .clickable { selectedSceneIndex = idx }
                                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                        ) {
                                            Text(
                                                text = "${sc.label} (${String.format(Locale.US, "%.1f", sc.startSec)}s)",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isSel) RawColors.Ink else RawColors.TextPrimary
                                            )
                                        }
                                    }
                                }

                                val currentScene = sceneGroups.getOrNull(selectedSceneIndex) ?: sceneGroups.firstOrNull()

                                // Full Scene Preview Card
                                if (currentScene != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(26.dp))
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
                                                text = "Scene: ${currentScene.label} (${String.format(Locale.US, "%.2fs - %.2fs", currentScene.startSec, currentScene.endSec)})",
                                                fontFamily = BricolageFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp,
                                                color = RawColors.TextPrimary
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(RawColors.AccentSoft)
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "FULL SCENE",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 10.5.sp,
                                                    color = RawColors.Tan
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(Color.Black)
                                        ) {
                                            val scSample = currentScene.sample
                                            if (scSample != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(File(scSample.effectiveImagePath))
                                                        .size(960, 540)
                                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                                        .diskCachePolicy(CachePolicy.DISABLED)
                                                        .build(),
                                                    contentDescription = currentScene.label,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Scene preview at t=${String.format(Locale.US, "%.2fs", currentScene.startSec)}",
                                                        fontFamily = JetBrainsMonoFamily,
                                                        color = RawColors.TextMuted,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Inline Scene AI Chatbot
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(26.dp))
                                            .background(RawColors.CardSurface)
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            RawPixelMascot(kind = PixelMascotKind.CODE_BOT, size = 26.dp)
                                            Column {
                                                Text(
                                                    text = "SCENE AI FIX CHATBOT",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    letterSpacing = 0.8.sp,
                                                    color = RawColors.Tan
                                                )
                                                Text(
                                                    text = "Tell the chatbot what to fix in ${currentScene.label}",
                                                    fontFamily = BricolageFamily,
                                                    fontSize = 13.sp,
                                                    color = RawColors.TextMuted
                                                )
                                            }
                                        }

                                        // Quick suggestions
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ScenePromptPill("Fix overlapping text in ${currentScene.label}") {
                                                sceneChatInput = "Fix overlapping text in ${currentScene.label} and ensure clear spacing."
                                            }
                                            ScenePromptPill("Make headline bigger in ${currentScene.label}") {
                                                sceneChatInput = "Increase headline font size in ${currentScene.label} by 20% and make it punchier."
                                            }
                                            ScenePromptPill("Fix clipped edges in ${currentScene.label}") {
                                                sceneChatInput = "Ensure no text or cards clip outside the 1920x1080 stage in ${currentScene.label}."
                                            }
                                        }

                                        // Chat log messages
                                        sceneChatLog.takeLast(4).forEach { msg ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(if (msg.isUser) RawColors.AccentSoft else RawColors.RaisedCard)
                                                    .padding(10.dp)
                                            ) {
                                                Text(
                                                    text = (if (msg.isUser) "You: " else "AI: ") + msg.text,
                                                    fontFamily = BricolageFamily,
                                                    fontSize = 13.sp,
                                                    color = if (msg.isUser) RawColors.TextPrimary else RawColors.Tan
                                                )
                                            }
                                        }

                                        // Chat Input Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = sceneChatInput,
                                                onValueChange = { sceneChatInput = it },
                                                placeholder = {
                                                    Text(
                                                        text = "E.g. Fix title overlap, make subtitle bold...",
                                                        fontSize = 12.5.sp,
                                                        color = RawColors.TextMuted
                                                    )
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(20.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = RawColors.Tan,
                                                    unfocusedBorderColor = RawColors.Divider,
                                                    focusedContainerColor = RawColors.BgPrimary,
                                                    unfocusedContainerColor = RawColors.BgPrimary,
                                                    cursorColor = RawColors.Tan
                                                ),
                                                textStyle = RawTypography.bodyMedium.copy(fontSize = 13.sp)
                                            )

                                            CircleActionButton(
                                                icon = RawIconType.SPARK,
                                                size = 44.dp,
                                                backgroundColor = if (sceneChatInput.isNotBlank() && !sceneChatBusy) RawColors.Tan else RawColors.RaisedCard,
                                                iconTint = if (sceneChatInput.isNotBlank() && !sceneChatBusy) RawColors.Ink else RawColors.TextMuted,
                                                onClick = {
                                                    val promptToSend = sceneChatInput.trim()
                                                    if (promptToSend.isEmpty() || sceneChatBusy) return@CircleActionButton
                                                    val userKey = userProfile?.geminiApiKey ?: apiKey
                                                    if (userKey.isBlank()) {
                                                        toastMsg = "Add your Gemini API key in Profile first."
                                                        onOpenGroqKey()
                                                        return@CircleActionButton
                                                    }

                                                    sceneChatBusy = true
                                                    sceneChatLog.add(ChatMessage(isUser = true, text = promptToSend))
                                                    sceneChatInput = ""

                                                    scope.launch {
                                                        try {
                                                            val fixSysPrompt = """
You are an expert HTML5 motion code engineer for raw_edit.
The user wants to fix Scene ${currentScene.label} (t = ${currentScene.startSec}s to ${currentScene.endSec}s).
Instructions: ${promptToSend}

Output targeted patches in <<<SEARCH ... ===REPLACE ... >>> format, OR the full updated HTML if structural redesign is needed.
Spend tokens on fixing code, not talking.
                                                            """.trimIndent()

                                                            val res = geminiRouter.generate(
                                                                apiKey = userKey,
                                                                prompt = "Current HTML:\n$resolvedHtml\n\nFix scene ${currentScene.label}: $promptToSend",
                                                                systemInstruction = fixSysPrompt,
                                                                routeMode = GeminiRouteMode.JSON_WORKHORSE,
                                                                expectJson = false
                                                            )

                                                            sceneChatBusy = false
                                                            res.onSuccess { genRes ->
                                                                val outputText = genRes.text
                                                                val patches = GroqCodec.parseClaudeSearchReplacePatches(outputText)
                                                                if (patches.isNotEmpty()) {
                                                                    val outcome = GroqCodec.applyExactOnceEdits(resolvedHtml, patches)
                                                                    if (outcome.appliedCount > 0) {
                                                                        val validation = CodeValidator.validateFull(context, cacheManager, outcome.updatedHtml)
                                                                        if (validation.isValid) {
                                                                            resolvedHtml = validation.cleanedHtml
                                                                            onApplyFixedHtml(validation.cleanedHtml, validation.durationSec)
                                                                            captureGen++
                                                                            sceneChatLog.add(ChatMessage(isUser = false, text = "Applied fix to ${currentScene.label} (${outcome.appliedCount} patches). Recapturing frames..."))
                                                                            toastMsg = "Fixed ${currentScene.label}!"
                                                                        } else {
                                                                            sceneChatLog.add(ChatMessage(isUser = false, text = "Validation issue: ${validation.summaryMessage}"))
                                                                        }
                                                                    } else {
                                                                        sceneChatLog.add(ChatMessage(isUser = false, text = "Could not match patch target in HTML. Try a different instruction."))
                                                                    }
                                                                } else if (outputText.contains("<!DOCTYPE html", ignoreCase = true)) {
                                                                    val fullHtml = outputText.substringAfter("<!DOCTYPE html").let { "<!DOCTYPE html$it" }.substringBeforeLast("</html>") + "</html>"
                                                                    val validation = CodeValidator.validateFull(context, cacheManager, fullHtml)
                                                                    if (validation.isValid) {
                                                                        resolvedHtml = validation.cleanedHtml
                                                                        onApplyFixedHtml(validation.cleanedHtml, validation.durationSec)
                                                                        captureGen++
                                                                        sceneChatLog.add(ChatMessage(isUser = false, text = "Applied full HTML update for ${currentScene.label}!"))
                                                                        toastMsg = "Scene updated!"
                                                                    } else {
                                                                        sceneChatLog.add(ChatMessage(isUser = false, text = "Validation failed: ${validation.summaryMessage}"))
                                                                    }
                                                                } else {
                                                                    sceneChatLog.add(ChatMessage(isUser = false, text = outputText.take(200)))
                                                                }
                                                            }.onFailure { err ->
                                                                sceneChatLog.add(ChatMessage(isUser = false, text = "Gemini Error: ${err.message}"))
                                                            }
                                                        } catch (e: Exception) {
                                                            sceneChatBusy = false
                                                            sceneChatLog.add(ChatMessage(isUser = false, text = "Error: ${e.message}"))
                                                        }
                                                    }
                                                }
                                            )
                                        }

                                        if (sceneChatBusy) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = RawColors.Tan, strokeWidth = 2.dp)
                                                Text(
                                                    text = "Applying fix with Gemini...",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 11.5.sp,
                                                    color = RawColors.TextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Bar
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

    // Zoom Inspection Modal
    val activeIdx = inspectingIndex
    val samplesList = sheetResult?.samples
    if (activeIdx != null && samplesList != null) {
        val sample = samplesList.getOrNull(activeIdx)
        if (sample != null) {
            val effectivePath = annotatedMap[sample.index] ?: sample.effectiveImagePath
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
                                "Frame #%02d \u2022 t=%.2fs \u2022 %s",
                                sample.index + 1,
                                sample.timeSec,
                                sample.sceneLabel.ifBlank { "SCENE" }
                            ),
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
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
                                if (sample.isFlagged) RawColors.ErrorCoral else RawColors.Tan.copy(alpha = 0.5f),
                                RoundedCornerShape(24.dp)
                            )
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(effectivePath))
                                .size(1920, 1080)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                            contentDescription = "Zoom frame",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
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
                        RawSecondaryButton(
                            text = "Next frame",
                            height = 52.dp,
                            leadingIcon = RawIconType.CHEVRON_RIGHT,
                            onClick = {
                                inspectingIndex = (sample.index + 1) % samplesList.size
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // Circle Drawing Dialog
    val drawIdx = drawingAnnotationForIndex
    if (drawIdx != null && sheetResult != null) {
        val sampleToDraw = sheetResult!!.samples.getOrNull(drawIdx)
        if (sampleToDraw != null) {
            KeyframeCircleDrawingDialog(
                sample = sampleToDraw,
                onDismiss = { drawingAnnotationForIndex = null },
                onSaveAnnotation = { savedPath ->
                    annotatedMap[sampleToDraw.index] = savedPath
                    userFlaggedMap[sampleToDraw.index] = true
                    drawingAnnotationForIndex = null
                    toastMsg = "Saved issue circle for Frame #${sampleToDraw.index + 1}!"
                }
            )
        }
    }

    // Paste AI Patch Dialog
    if (showPatchDialog) {
        Dialog(
            onDismissRequest = { if (!patchBusy) showPatchDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(30.dp))
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Apply Search / Replace Patch",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = RawColors.TextPrimary
                            )
                            Text(
                                text = "Paste AI patch blocks (<<<SEARCH ... ===REPLACE ... >>>)",
                                style = RawTypography.labelMedium,
                                color = RawColors.TextMuted
                            )
                        }
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
                                        toastMsg = "Applied ${applyOutcome.appliedCount} patch(es) \u2014 re-capturing"
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
}

@Composable
private fun FilterToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) RawColors.Tan else RawColors.RaisedCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = if (selected) RawColors.Ink else RawColors.TextMuted
        )
    }
}

@Composable
private fun ExportPillButton(
    text: String,
    icon: RawIconType,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RawColors.RaisedCard)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RawIcon(type = icon, size = 15.dp, tint = RawColors.Tan)
            Text(
                text = text,
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = RawColors.TextPrimary
            )
        }
    }
}

@Composable
private fun ScenePromptPill(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(RawColors.RaisedCard)
            .border(1.dp, RawColors.Divider, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = BricolageFamily,
            fontSize = 11.5.sp,
            color = RawColors.TextMuted
        )
    }
}
