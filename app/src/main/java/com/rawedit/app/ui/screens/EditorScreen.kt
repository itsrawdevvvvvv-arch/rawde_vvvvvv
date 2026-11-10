package com.rawedit.app.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.model.LayerJson
import com.rawedit.app.data.model.LayerMask
import com.rawedit.app.data.model.RawLayer
import com.rawedit.app.render.LocalAssetServer
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class EditorAdjuster(val label: String) {
    NONE("None"),
    POS_SCALE("Pos & Scale"),
    OPACITY("Opacity"),
    MASK("Mask")
}

private enum class TrackFilter(val label: String) {
    ALL("All"),
    ACTIVE_NOW("On Screen"),
    TEXT("Text"),
    IMAGE("Images")
}

private val ClipBlue = Color(0xFF1F7FE8)
private val ClipTeal = Color(0xFF0E947A)
private val ClipSlate = Color(0xFF52567A)
private val PlayheadRed = Color(0xFFFF4D5A)

/**
 * CapCut / Alight Motion / After Effects style mobile timeline editor.
 * Supports synchronized zoomable time ruler, live playhead scrubbing, color-coded readable tracks,
 * timing trimming (0.1s snap), above/below z-ordering, position/scale, opacity, images, and masking.
 */
@Composable
fun EditorScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    openedAfterRender: Boolean = false,
    onAutoSaveDraft: (ProjectEntity) -> Unit,
    onDone: (ProjectEntity) -> Unit,
    onRenderUpdated: (ProjectEntity) -> Unit = onDone,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val resolvedHtml = remember(project.code) { cacheManager.resolveHtmlCode(project.code) }

    if (!project.hasEditableLayers) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RawColors.BgPrimary)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CircleActionButton(icon = RawIconType.BACK, onClick = onBack)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RawIcon(RawIconType.LAYERS, size = 44.dp, tint = RawColors.TextMuted)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This code has no editable layers. You can still render the video.",
                    style = RawTypography.titleLarge
                )
            }
            BigPrimaryButton(text = "Back", onClick = onBack)
        }
        return
    }

    val initialLayers = remember(project.id) { LayerJson.decode(project.layersJson) }
    var layers by remember(project.id) { mutableStateOf(initialLayers) }
    val baseDimensions = remember(project.id) {
        initialLayers.associate { it.id to (it.w.coerceAtLeast(40f) to it.h.coerceAtLeast(40f)) }.toMutableMap()
    }
    val undoStack = remember { mutableStateListOf<List<RawLayer>>() }
    val redoStack = remember { mutableStateListOf<List<RawLayer>>() }

    val durationSec = remember(project.audioDurationSec, project.codeDurationSec) {
        maxOf(project.audioDurationSec, project.codeDurationSec, 1.0)
    }
    var currentTimeSec by remember { mutableDoubleStateOf(0.0) }
    var isPlaying by remember { mutableStateOf(false) }

    // Default select an element visible at playhead (0s) or top-most z
    var selectedLayerId by remember(project.id) {
        mutableStateOf(
            initialLayers.filter { 0.0 >= it.start && 0.0 <= it.end }.maxByOrNull { it.z }?.id
                ?: initialLayers.maxByOrNull { it.z }?.id
        )
    }
    var activeAdjuster by remember { mutableStateOf(EditorAdjuster.POS_SCALE) }
    var trackFilter by remember(project.id) {
        mutableStateOf(if (initialLayers.size > 60) TrackFilter.ACTIVE_NOW else TrackFilter.ALL)
    }
    var dpPerSec by remember { mutableFloatStateOf(78f) }
    var timelineViewportWidthPx by remember { mutableFloatStateOf(300f) }
    val timelineScroll = rememberScrollState()

    var editingTextLayer by remember { mutableStateOf<RawLayer?>(null) }
    var errorNotice by remember { mutableStateOf<String?>(null) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var previewBoxSize by remember { mutableStateOf(IntSize(1, 1)) }

    fun snap01(v: Double): Double = (v * 10.0).roundToInt() / 10.0

    fun pushCommit(newLayers: List<RawLayer>, recordHistory: Boolean = true) {
        if (recordHistory) {
            undoStack.add(layers)
            if (undoStack.size > 50) undoStack.removeAt(0)
            redoStack.clear()
        }
        layers = newLayers
        onAutoSaveDraft(project.copy(layersJson = LayerJson.encode(newLayers)))
    }

    fun performUndo() {
        if (undoStack.isEmpty()) return
        redoStack.add(layers)
        val prev = undoStack.removeAt(undoStack.lastIndex)
        layers = prev
        onAutoSaveDraft(project.copy(layersJson = LayerJson.encode(prev)))
    }

    fun performRedo() {
        if (redoStack.isEmpty()) return
        undoStack.add(layers)
        val next = redoStack.removeAt(redoStack.lastIndex)
        layers = next
        onAutoSaveDraft(project.copy(layersJson = LayerJson.encode(next)))
    }

    // Playback loop (~30fps)
    LaunchedEffect(isPlaying, durationSec) {
        if (isPlaying) {
            var lastNs = System.nanoTime()
            while (isPlaying) {
                delay(33L)
                val now = System.nanoTime()
                val dt = (now - lastNs) / 1_000_000_000.0
                lastNs = now
                val next = currentTimeSec + dt
                currentTimeSec = if (next >= durationSec) 0.0 else next
            }
        }
    }

    // Push layer JSON to WebView ONLY when layers list changes (avoids 30fps JSON serialization lag on Android 9)
    val latestStageJson = remember(layers) { LayerJson.encodeForStage(layers) }
    LaunchedEffect(latestStageJson, pageReady, previewBoxSize) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        val fallbackVw = (previewBoxSize.width / density.density).roundToInt().coerceAtLeast(1)
        val fallbackVh = (previewBoxSize.height / density.density).roundToInt().coerceAtLeast(1)
        val js = """
            (function() {
              window.__RAW_EDITED_LAYERS = $latestStageJson;
              if (typeof window.__editorSeek === 'function') {
                window.__editorSeek(window.__RAW_EDITED_LAYERS, $currentTimeSec, $fallbackVw, $fallbackVh);
              }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    // Fast playhead seek (<1ms) whenever currentTimeSec changes
    LaunchedEffect(currentTimeSec, pageReady, previewBoxSize) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        val fallbackVw = (previewBoxSize.width / density.density).roundToInt().coerceAtLeast(1)
        val fallbackVh = (previewBoxSize.height / density.density).roundToInt().coerceAtLeast(1)
        val js = "if(typeof window.__editorSeek==='function'){window.__editorSeek(window.__RAW_EDITED_LAYERS,$currentTimeSec,$fallbackVw,$fallbackVh);}"
        wv.evaluateJavascript(js, null)
    }

    // Keep playhead in view when scrubbing/playing across wide zoomed timelines
    LaunchedEffect(currentTimeSec, dpPerSec, timelineViewportWidthPx) {
        if (timelineViewportWidthPx <= 10f) return@LaunchedEffect
        val playheadPx = currentTimeSec.toFloat() * with(density) { dpPerSec.dp.toPx() }
        val curScroll = timelineScroll.value.toFloat()
        val pad = timelineViewportWidthPx * 0.08f
        if (playheadPx < curScroll + pad || playheadPx > curScroll + timelineViewportWidthPx - pad) {
            val target = (playheadPx - timelineViewportWidthPx * 0.35f)
                .roundToInt()
                .coerceIn(0, timelineScroll.maxValue)
            timelineScroll.scrollTo(target)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (cacheManager.countStoredAssets() >= 20) {
                errorNotice = "Maximum of 20 added images reached."
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val res = cacheManager.importEditorImage(uri)
                res.onSuccess { asset ->
                    errorNotice = null
                    val maxZ = (layers.maxOfOrNull { it.z } ?: 0) + 1
                    val imgW = 540f
                    val imgH = (540f * asset.height / asset.width.coerceAtLeast(1)).coerceIn(80f, 920f)
                    val newId = "img_${UUID.randomUUID().toString().take(6)}"
                    baseDimensions[newId] = imgW to imgH
                    val clipStart = snap01(currentTimeSec.coerceIn(0.0, (durationSec - 0.5).coerceAtLeast(0.0)))
                    val clipEnd = snap01(minOf(durationSec, clipStart + 4.0).let {
                        if (it - clipStart < 0.8) durationSec else it
                    })
                    val newLayer = RawLayer(
                        id = newId,
                        name = "Image ${layers.count { it.type == "image" } + 1}",
                        type = "image",
                        x = (1920f - imgW) * 0.5f,
                        y = (1080f - imgH) * 0.5f,
                        w = imgW,
                        h = imgH,
                        opacity = 1f,
                        z = maxZ,
                        start = clipStart,
                        end = clipEnd,
                        text = null,
                        src = "https://${LocalAssetServer.ASSETS_HOST}/${asset.fileName}",
                        mask = LayerMask.NONE.key
                    )
                    pushCommit(layers + newLayer)
                    selectedLayerId = newLayer.id
                    activeAdjuster = EditorAdjuster.POS_SCALE
                }.onFailure { err ->
                    errorNotice = err.message ?: "Could not add image."
                }
            }
        }
    }

    val selectedLayer = layers.firstOrNull { it.id == selectedLayerId }
    val allSortedDesc = remember(layers) { layers.sortedByDescending { it.z } }
    val filteredTracks = remember(allSortedDesc, trackFilter, currentTimeSec) {
        when (trackFilter) {
            TrackFilter.ALL -> allSortedDesc
            TrackFilter.ACTIVE_NOW -> allSortedDesc.filter {
                !it.hidden && currentTimeSec >= (it.start - 0.05) && currentTimeSec <= (it.end + 0.05)
            }
            TrackFilter.TEXT -> allSortedDesc.filter { it.type == "text" }
            TrackFilter.IMAGE -> allSortedDesc.filter { it.type == "image" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Clean Top Bar (Reference Video Style: Back + Title + Undo/Redo + Export/Re-render)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 38.dp)
                Column {
                    Text(
                        text = project.title.ifBlank { "Untitled Reel" },
                        style = RawTypography.titleMedium.copy(fontSize = 15.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(Locale.US, "%05.2fs / %.1fs", currentTimeSec, durationSec),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = RawColors.TextMuted
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(
                    icon = RawIconType.UNDO,
                    onClick = ::performUndo,
                    enabled = undoStack.isNotEmpty(),
                    size = 38.dp,
                    iconTint = if (undoStack.isNotEmpty()) RawColors.TextPrimary else RawColors.TextMuted
                )
                CircleActionButton(
                    icon = RawIconType.REDO,
                    onClick = ::performRedo,
                    enabled = redoStack.isNotEmpty(),
                    size = 38.dp,
                    iconTint = if (redoStack.isNotEmpty()) RawColors.TextPrimary else RawColors.TextMuted
                )
                RawPressButton(
                    onClick = {
                        isPlaying = false
                        val updated = project.copy(layersJson = LayerJson.encode(layers))
                        if (openedAfterRender) {
                            onRenderUpdated(updated)
                        } else {
                            onDone(updated)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(RawColors.PrimaryBtnBg)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (openedAfterRender) "Re-render" else "Export",
                            style = RawTypography.labelLarge.copy(fontSize = 13.sp),
                            color = RawColors.PrimaryBtnText
                        )
                    }
                }
            }
        }

        // 2. 16:9 Live Preview Canvas (Always Dark Background + Guaranteed Fit + Direct Drag/Scale)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(RawColors.BgPrimary)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp))
                .onSizeChanged { previewBoxSize = it }
        ) {
            EditorStageWebView(
                rawHtml = resolvedHtml,
                cacheManager = cacheManager,
                onWebViewReady = { wv ->
                    webViewRef = wv
                    pageReady = true
                }
            )

            StageGestureOverlay(
                layers = layers,
                selectedId = selectedLayerId,
                currentTimeSec = currentTimeSec,
                boxSize = previewBoxSize,
                onSelect = { id -> selectedLayerId = id },
                onPreviewMutate = { updated -> layers = updated },
                onCommitMutate = { snapshotBefore, updated ->
                    undoStack.add(snapshotBefore)
                    if (undoStack.size > 50) undoStack.removeAt(0)
                    redoStack.clear()
                    layers = updated
                    onAutoSaveDraft(project.copy(layersJson = LayerJson.encode(updated)))
                }
            )
        }

        // 3. Slim Transport & Filter Strip (Play/Pause + Time Slider + Filter + Zoom)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircleActionButton(
                icon = if (isPlaying) RawIconType.PAUSE else RawIconType.PLAY,
                onClick = { isPlaying = !isPlaying },
                size = 36.dp,
                backgroundColor = RawColors.RaisedCard
            )

            Slider(
                value = currentTimeSec.toFloat().coerceIn(0f, durationSec.toFloat()),
                onValueChange = {
                    isPlaying = false
                    currentTimeSec = it.toDouble()
                },
                valueRange = 0f..durationSec.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = PlayheadRed,
                    activeTrackColor = RawColors.TextPrimary,
                    inactiveTrackColor = RawColors.PillGrey
                ),
                modifier = Modifier.weight(1f)
            )

            // Timeline zoom controls (- / Fit / +)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ZoomChip("-") {
                    dpPerSec = (dpPerSec * 0.72f).coerceIn(14f, 180f)
                }
                ZoomChip("Fit") {
                    val availDp = (timelineViewportWidthPx / density.density).coerceAtLeast(180f)
                    dpPerSec = (availDp / durationSec.toFloat()).coerceIn(10f, 180f)
                }
                ZoomChip("+") {
                    dpPerSec = (dpPerSec * 1.38f).coerceIn(14f, 180f)
                }
            }
        }

        errorNotice?.let { msg ->
            Text(
                text = msg,
                style = RawTypography.labelSmall,
                color = RawColors.ErrorCoral,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
        }

        // 4. CapCut / Alight Motion Multi-Track Timeline Section (Takes Main Focus!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
        ) {
            // Filter bar (All / On Screen / Text / Images)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RawColors.HeroSurface)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackFilter.entries.forEach { f ->
                    val active = trackFilter == f
                    val count = when (f) {
                        TrackFilter.ALL -> allSortedDesc.size
                        TrackFilter.ACTIVE_NOW -> allSortedDesc.count {
                            !it.hidden && currentTimeSec >= (it.start - 0.05) && currentTimeSec <= (it.end + 0.05)
                        }
                        TrackFilter.TEXT -> allSortedDesc.count { it.type == "text" }
                        TrackFilter.IMAGE -> allSortedDesc.count { it.type == "image" }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) RawColors.TextPrimary else RawColors.RaisedCard)
                            .clickable { trackFilter = f }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${f.label} ($count)",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) RawColors.PrimaryBtnText else RawColors.TextMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Synchronized Time Ruler + Multi-Track Stacked Rows
            ReferenceStyleTimeline(
                tracksTopFirst = filteredTracks,
                allLayers = layers,
                selectedId = selectedLayerId,
                currentTimeSec = currentTimeSec,
                totalDurationSec = durationSec,
                dpPerSec = dpPerSec,
                scrollState = timelineScroll,
                onViewportMeasured = { wPx -> timelineViewportWidthPx = wPx },
                onSeekTime = { t ->
                    isPlaying = false
                    currentTimeSec = t.coerceIn(0.0, durationSec)
                },
                onSelectTrack = { layer ->
                    selectedLayerId = layer.id
                    if (currentTimeSec < layer.start || currentTimeSec > layer.end) {
                        val span = (layer.end - layer.start).coerceAtLeast(0.0)
                        isPlaying = false
                        currentTimeSec = (layer.start + minOf(0.25, span * 0.4)).coerceIn(0.0, durationSec)
                    }
                },
                onToggleHidden = { layer ->
                    pushCommit(layers.map { if (it.id == layer.id) it.copy(hidden = !it.hidden) else it })
                },
                onPreviewUpdate = { updated -> layers = updated },
                onCommitUpdate = { before, updated ->
                    undoStack.add(before)
                    if (undoStack.size > 50) undoStack.removeAt(0)
                    redoStack.clear()
                    layers = updated
                    onAutoSaveDraft(project.copy(layersJson = LayerJson.encode(updated)))
                },
                modifier = Modifier.weight(1f)
            )
        }

        // 5. Contextual Quick-Adjust Bar (Only appears for the selected property!)
        AnimatedVisibility(visible = selectedLayer != null && activeAdjuster != EditorAdjuster.NONE) {
            selectedLayer?.let { sel ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RawColors.HeroSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    when (activeAdjuster) {
                        EditorAdjuster.POS_SCALE -> {
                            val base = baseDimensions.getOrPut(sel.id) {
                                sel.w.coerceAtLeast(40f) to sel.h.coerceAtLeast(40f)
                            }
                            val currentScale = (sel.w / base.first).coerceIn(0.25f, 2.5f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Scale ${(currentScale * 100).roundToInt()}%",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = RawColors.TextPrimary,
                                    modifier = Modifier.width(76.dp)
                                )
                                Slider(
                                    value = currentScale,
                                    onValueChange = { sc ->
                                        val nw = base.first * sc
                                        val nh = base.second * sc
                                        val cx = sel.x + sel.w * 0.5f
                                        val cy = sel.y + sel.h * 0.5f
                                        layers = layers.map {
                                            if (it.id == sel.id) {
                                                it.copy(x = cx - nw * 0.5f, y = cy - nh * 0.5f, w = nw, h = nh)
                                            } else it
                                        }
                                    },
                                    onValueChangeFinished = { pushCommit(layers) },
                                    valueRange = 0.25f..2.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = RawColors.TextPrimary,
                                        activeTrackColor = RawColors.TextPrimary,
                                        inactiveTrackColor = RawColors.PillGrey
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                SmallPillAction("Center X") {
                                    pushCommit(
                                        layers.map {
                                            if (it.id == sel.id) it.copy(x = (1920f - it.w) * 0.5f) else it
                                        }
                                    )
                                }
                            }
                        }

                        EditorAdjuster.OPACITY -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Opacity ${(sel.opacity * 100).roundToInt()}%",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = RawColors.TextPrimary,
                                    modifier = Modifier.width(86.dp)
                                )
                                Slider(
                                    value = sel.opacity.coerceIn(0f, 1f),
                                    onValueChange = { op ->
                                        layers = layers.map {
                                            if (it.id == sel.id) it.copy(opacity = op) else it
                                        }
                                    },
                                    onValueChangeFinished = { pushCommit(layers) },
                                    colors = SliderDefaults.colors(
                                        thumbColor = RawColors.TextPrimary,
                                        activeTrackColor = RawColors.TextPrimary,
                                        inactiveTrackColor = RawColors.PillGrey
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        EditorAdjuster.MASK -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LayerMask.entries.forEach { maskOption ->
                                    val isCur = sel.mask.equals(maskOption.key, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isCur) RawColors.SuccessMint else RawColors.RaisedCard)
                                            .clickable {
                                                pushCommit(
                                                    layers.map {
                                                        if (it.id == sel.id) it.copy(mask = maskOption.key) else it
                                                    }
                                                )
                                            }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = maskOption.label,
                                            style = RawTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isCur) RawColors.PrimaryBtnText else RawColors.TextPrimary
                                        )
                                    }
                                }
                            }
                        }

                        EditorAdjuster.NONE -> Unit
                    }
                }
            }
        }

        // 6. CapCut / Alight Motion Bottom Dock (Clean Icon-Above-Label Tool Strip)
        val selIdxInAll = allSortedDesc.indexOfFirst { it.id == selectedLayerId }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RawColors.BgPrimary)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockToolButton(
                icon = RawIconType.IMAGE,
                label = "+ Image",
                highlighted = false,
                onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
            DockToolButton(
                icon = RawIconType.EDIT,
                label = "Pos/Scale",
                enabled = selectedLayer != null,
                highlighted = activeAdjuster == EditorAdjuster.POS_SCALE,
                onClick = {
                    activeAdjuster = if (activeAdjuster == EditorAdjuster.POS_SCALE) EditorAdjuster.NONE else EditorAdjuster.POS_SCALE
                }
            )
            DockToolButton(
                icon = RawIconType.EYE,
                label = "Opacity",
                enabled = selectedLayer != null,
                highlighted = activeAdjuster == EditorAdjuster.OPACITY,
                onClick = {
                    activeAdjuster = if (activeAdjuster == EditorAdjuster.OPACITY) EditorAdjuster.NONE else EditorAdjuster.OPACITY
                }
            )
            DockToolButton(
                icon = RawIconType.LAYERS,
                label = "Mask",
                enabled = selectedLayer != null,
                highlighted = activeAdjuster == EditorAdjuster.MASK,
                onClick = {
                    activeAdjuster = if (activeAdjuster == EditorAdjuster.MASK) EditorAdjuster.NONE else EditorAdjuster.MASK
                }
            )
            if (selectedLayer?.type == "text") {
                DockToolButton(
                    icon = RawIconType.CODE,
                    label = "Edit Text",
                    enabled = true,
                    highlighted = false,
                    onClick = { editingTextLayer = selectedLayer }
                )
            }
            DockToolButton(
                icon = RawIconType.FORWARD,
                label = "Above",
                enabled = selIdxInAll > 0,
                highlighted = false,
                onClick = {
                    if (selIdxInAll > 0) pushCommit(swapAdjacentZ(allSortedDesc, selIdxInAll, selIdxInAll - 1))
                }
            )
            DockToolButton(
                icon = RawIconType.BACKWARD,
                label = "Below",
                enabled = selIdxInAll in 0 until allSortedDesc.lastIndex,
                highlighted = false,
                onClick = {
                    if (selIdxInAll in 0 until allSortedDesc.lastIndex) {
                        pushCommit(swapAdjacentZ(allSortedDesc, selIdxInAll, selIdxInAll + 1))
                    }
                }
            )
            DockToolButton(
                icon = RawIconType.TRASH,
                label = "Delete",
                enabled = selectedLayer != null,
                tint = RawColors.ErrorCoral,
                highlighted = false,
                onClick = {
                    selectedLayer?.let { target ->
                        val next = layers.filterNot { it.id == target.id }
                        pushCommit(next)
                        selectedLayerId = next.filter { currentTimeSec >= it.start && currentTimeSec <= it.end }
                            .maxByOrNull { it.z }?.id ?: next.maxByOrNull { it.z }?.id
                    }
                }
            )
        }
    }

    editingTextLayer?.let { target ->
        var textDraft by remember(target.id) { mutableStateOf(target.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingTextLayer = null },
            containerColor = RawColors.HeroSurface,
            titleContentColor = RawColors.TextPrimary,
            textContentColor = RawColors.TextPrimary,
            title = { Text("Edit text") },
            text = {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.TextPrimary,
                        unfocusedBorderColor = RawColors.Divider,
                        focusedTextColor = RawColors.TextPrimary,
                        unfocusedTextColor = RawColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pushCommit(
                            layers.map { if (it.id == target.id) it.copy(text = textDraft) else it }
                        )
                        editingTextLayer = null
                    }
                ) {
                    Text("Save", color = RawColors.TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTextLayer = null }) {
                    Text("Cancel", color = RawColors.TextMuted)
                }
            }
        )
    }
}

@Composable
private fun ZoomChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = RawColors.TextPrimary
        )
    }
}

@Composable
private fun SmallPillAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(RawColors.RaisedCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = RawTypography.labelSmall, color = RawColors.TextPrimary)
    }
}

@Composable
private fun DockToolButton(
    icon: RawIconType,
    label: String,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    tint: Color = RawColors.TextPrimary,
    onClick: () -> Unit
) {
    val effectiveTint = when {
        !enabled -> RawColors.TextMuted.copy(alpha = 0.35f)
        highlighted -> RawColors.PrimaryBtnText
        else -> tint
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) RawColors.PrimaryBtnBg else RawColors.CardSurface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        RawIcon(type = icon, size = 16.dp, tint = effectiveTint)
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = effectiveTint,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Synchronized horizontal timeline canvas with top Time Ruler, color-coded readable track bars,
 * slim Eye visibility column, left/right 0.1s trim handles, and full-height Playhead marker.
 */
@Composable
private fun ReferenceStyleTimeline(
    tracksTopFirst: List<RawLayer>,
    allLayers: List<RawLayer>,
    selectedId: String?,
    currentTimeSec: Double,
    totalDurationSec: Double,
    dpPerSec: Float,
    scrollState: ScrollState,
    onViewportMeasured: (Float) -> Unit,
    onSeekTime: (Double) -> Unit,
    onSelectTrack: (RawLayer) -> Unit,
    onToggleHidden: (RawLayer) -> Unit,
    onPreviewUpdate: (List<RawLayer>) -> Unit,
    onCommitUpdate: (before: List<RawLayer>, after: List<RawLayer>) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestLayers by rememberUpdatedState(allLayers)
    val density = LocalDensity.current
    val pxPerSec = with(density) { dpPerSec.dp.toPx() }.coerceAtLeast(8f)
    val trackTotalWidthDp: Dp = ((totalDurationSec.toFloat() * dpPerSec) + 48f).dp
    val gutterWidth = 36.dp
    var beforeSnap by remember { mutableStateOf<List<RawLayer>?>(null) }

    fun snap01(v: Double): Double = (v * 10.0).roundToInt() / 10.0

    Column(modifier = modifier.fillMaxWidth()) {
        // Top Header: Fixed Eye corner + Horizontally Scrolled Time Ruler with Scrub Gesture
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(RawColors.BgPrimary)
        ) {
            Box(
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxHeight()
                    .border(0.5.dp, RawColors.Divider),
                contentAlignment = Alignment.Center
            ) {
                RawIcon(RawIconType.EYE, size = 12.dp, tint = RawColors.TextMuted)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { onViewportMeasured(it.width.toFloat()) }
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(trackTotalWidthDp)
                        .fillMaxHeight()
                        .pointerInput(totalDurationSec, pxPerSec) {
                            detectTapGestures { offset ->
                                onSeekTime((offset.x / pxPerSec).toDouble())
                            }
                        }
                        .pointerInput(totalDurationSec, pxPerSec) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                onSeekTime((change.position.x / pxPerSec).toDouble())
                            }
                        }
                ) {
                    val stepSec = when {
                        dpPerSec >= 60f -> 1.0
                        dpPerSec >= 30f -> 2.0
                        else -> 5.0
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxT = ceil(totalDurationSec).toInt()
                        var sec = 0.0
                        while (sec <= maxT) {
                            val x = (sec * pxPerSec).toFloat()
                            drawLine(
                                color = RawColors.TextMuted.copy(alpha = 0.65f),
                                start = Offset(x, size.height - 9.dp.toPx()),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            val halfX = ((sec + stepSec * 0.5) * pxPerSec).toFloat()
                            if (halfX < size.width) {
                                drawLine(
                                    color = RawColors.Divider,
                                    start = Offset(halfX, size.height - 5.dp.toPx()),
                                    end = Offset(halfX, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            sec += stepSec
                        }
                        // Playhead triangle at top of ruler
                        val phX = (currentTimeSec * pxPerSec).toFloat()
                        val tri = Path().apply {
                            moveTo(phX, size.height)
                            lineTo(phX - 6.dp.toPx(), 2.dp.toPx())
                            lineTo(phX + 6.dp.toPx(), 2.dp.toPx())
                            close()
                        }
                        drawPath(tri, color = PlayheadRed)
                    }

                    // Second labels placed cleanly along ruler
                    val labelStep = when {
                        dpPerSec >= 60f -> 1
                        dpPerSec >= 30f -> 2
                        else -> 5
                    }
                    var s = 0
                    val endS = ceil(totalDurationSec).toInt()
                    while (s <= endS) {
                        val xDp = (s * dpPerSec).dp
                        Text(
                            text = "${s}s",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = RawColors.TextMuted,
                            modifier = Modifier
                                .offset(x = xDp + 3.dp, y = 2.dp)
                        )
                        s += labelStep
                    }
                }
            }
        }

        if (tracksTopFirst.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No layers at this timestamp. Scrub slider or switch filter to All.",
                    style = RawTypography.bodyMedium,
                    color = RawColors.TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(tracksTopFirst, key = { _, l -> l.id }) { _, layer ->
                    val selected = layer.id == selectedId
                    val activeNow = !layer.hidden && currentTimeSec in layer.start..layer.end
                    val barColor = when (layer.type) {
                        "image" -> ClipTeal
                        "text" -> ClipBlue
                        else -> ClipSlate
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .background(if (selected) RawColors.HeroSurface else Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Fixed Slim Left Gutter (Eye Visibility Toggle)
                        Box(
                            modifier = Modifier
                                .width(gutterWidth)
                                .fillMaxHeight()
                                .clickable { onToggleHidden(layer) },
                            contentAlignment = Alignment.Center
                        ) {
                            RawIcon(
                                type = if (layer.hidden) RawIconType.EYE_OFF else RawIconType.EYE,
                                size = 14.dp,
                                tint = if (layer.hidden) RawColors.Divider else RawColors.TextMuted
                            )
                        }

                        // Synced Horizontal Clip Lane
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(scrollState)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(trackTotalWidthDp)
                                    .fillMaxHeight()
                            ) {
                                val startPx = (layer.start * pxPerSec).roundToInt()
                                val minBarPx = with(density) { 42.dp.roundToPx() }
                                val rawWidthPx = ((layer.end - layer.start) * pxPerSec).roundToInt().coerceAtLeast(minBarPx)

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(startPx, 0) }
                                        .width(with(density) { rawWidthPx.toDp() })
                                        .fillMaxHeight()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            barColor.copy(
                                                alpha = when {
                                                    layer.hidden -> 0.25f
                                                    selected -> 1f
                                                    activeNow -> 0.92f
                                                    else -> 0.56f
                                                }
                                            )
                                        )
                                        .border(
                                            width = if (selected) 1.5.dp else 0.5.dp,
                                            color = if (selected) Color.White else Color.White.copy(alpha = 0.22f),
                                            shape = RoundedCornerShape(7.dp)
                                        )
                                        .clickable { onSelectTrack(layer) }
                                        .pointerInput(layer.id, totalDurationSec, pxPerSec) {
                                            detectDragGestures(
                                                onDragStart = { beforeSnap = latestLayers },
                                                onDrag = { change, drag ->
                                                    change.consume()
                                                    val cur = latestLayers
                                                    val curLayer = cur.firstOrNull { it.id == layer.id } ?: return@detectDragGestures
                                                    val deltaSec = (drag.x / pxPerSec).toDouble()
                                                    val span = (curLayer.end - curLayer.start).coerceAtLeast(0.1)
                                                    val nextStart = snap01(
                                                        (curLayer.start + deltaSec).coerceIn(0.0, (totalDurationSec - span).coerceAtLeast(0.0))
                                                    )
                                                    val nextEnd = snap01(nextStart + span)
                                                    onPreviewUpdate(
                                                        cur.map { if (it.id == layer.id) it.copy(start = nextStart, end = nextEnd) else it }
                                                    )
                                                },
                                                onDragEnd = {
                                                    val b = beforeSnap
                                                    if (b != null) onCommitUpdate(b, latestLayers)
                                                    beforeSnap = null
                                                },
                                                onDragCancel = { beforeSnap = null }
                                            )
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    val displayLabel = buildString {
                                        if (layer.type == "image") append("IMG: ")
                                        val txt = layer.text?.replace('\n', ' ')?.trim().orEmpty()
                                        val nm = layer.name.trim()
                                        append(
                                            when {
                                                txt.isNotBlank() && txt.length <= 42 -> txt
                                                nm.isNotBlank() -> nm
                                                txt.isNotBlank() -> txt
                                                else -> layer.id
                                            }
                                        )
                                    }
                                    Text(
                                        text = displayLabel,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )

                                    if (selected) {
                                        // Left trim handle
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .width(12.dp)
                                                .fillMaxHeight()
                                                .background(Color.White.copy(alpha = 0.85f))
                                                .pointerInput(layer.id, totalDurationSec, pxPerSec) {
                                                    detectDragGestures(
                                                        onDragStart = { beforeSnap = latestLayers },
                                                        onDrag = { change, drag ->
                                                            change.consume()
                                                            val cur = latestLayers
                                                            val curLayer = cur.firstOrNull { it.id == layer.id } ?: return@detectDragGestures
                                                            val deltaSec = (drag.x / pxPerSec).toDouble()
                                                            val nextStart = snap01(
                                                                (curLayer.start + deltaSec).coerceIn(0.0, (curLayer.end - 0.1).coerceAtLeast(0.0))
                                                            )
                                                            onPreviewUpdate(
                                                                cur.map { if (it.id == layer.id) it.copy(start = nextStart) else it }
                                                            )
                                                        },
                                                        onDragEnd = {
                                                            val b = beforeSnap
                                                            if (b != null) onCommitUpdate(b, latestLayers)
                                                            beforeSnap = null
                                                        },
                                                        onDragCancel = { beforeSnap = null }
                                                    )
                                                }
                                        )

                                        // Right trim handle
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .width(12.dp)
                                                .fillMaxHeight()
                                                .background(Color.White.copy(alpha = 0.85f))
                                                .pointerInput(layer.id, totalDurationSec, pxPerSec) {
                                                    detectDragGestures(
                                                        onDragStart = { beforeSnap = latestLayers },
                                                        onDrag = { change, drag ->
                                                            change.consume()
                                                            val cur = latestLayers
                                                            val curLayer = cur.firstOrNull { it.id == layer.id } ?: return@detectDragGestures
                                                            val deltaSec = (drag.x / pxPerSec).toDouble()
                                                            val nextEnd = snap01(
                                                                (curLayer.end + deltaSec).coerceIn(
                                                                    (curLayer.start + 0.1).coerceAtMost(totalDurationSec),
                                                                    totalDurationSec
                                                                )
                                                            )
                                                            onPreviewUpdate(
                                                                cur.map { if (it.id == layer.id) it.copy(end = nextEnd) else it }
                                                            )
                                                        },
                                                        onDragEnd = {
                                                            val b = beforeSnap
                                                            if (b != null) onCommitUpdate(b, latestLayers)
                                                            beforeSnap = null
                                                        },
                                                        onDragCancel = { beforeSnap = null }
                                                    )
                                                }
                                        )
                                    }
                                }

                                // Continuous vertical playhead line across every track
                                val playheadPx = (currentTimeSec * pxPerSec).roundToInt()
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(playheadPx, 0) }
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(PlayheadRed)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EditorStageWebView(
    rawHtml: String,
    cacheManager: CacheManager,
    onWebViewReady: (WebView) -> Unit
) {
    val context = LocalContext.current
    var servedHtml by remember { mutableStateOf("") }
    val server = remember(cacheManager) {
        LocalAssetServer(context, cacheManager) { servedHtml }
    }
    servedHtml = remember(rawHtml) {
        server.buildInjectedHtml(rawHtml = rawHtml, previewFitViewport = true)
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            server.configureSandboxedWebView(this) {
                onWebViewReady(this)
            }
            loadUrl(LocalAssetServer.ENTRY_URL)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun StageGestureOverlay(
    layers: List<RawLayer>,
    selectedId: String?,
    currentTimeSec: Double,
    boxSize: IntSize,
    onSelect: (String?) -> Unit,
    onPreviewMutate: (List<RawLayer>) -> Unit,
    onCommitMutate: (before: List<RawLayer>, after: List<RawLayer>) -> Unit
) {
    val latestLayers by rememberUpdatedState(layers)
    val latestTime by rememberUpdatedState(currentTimeSec)
    val scaleX = boxSize.width / 1920f
    val scaleY = boxSize.height / 1080f
    val selected = layers.firstOrNull { it.id == selectedId }
    val selectedVisibleNow = selected != null && !selected.hidden &&
        currentTimeSec >= selected.start && currentTimeSec <= selected.end

    var beforeSnapshot by remember { mutableStateOf<List<RawLayer>?>(null) }
    var activeCorner by remember { mutableIntStateOf(-1) }
    var snapCenterGuide by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(boxSize) {
                detectTapGestures { tap ->
                    if (scaleX <= 0f || scaleY <= 0f) return@detectTapGestures
                    val stageX = tap.x / scaleX
                    val stageY = tap.y / scaleY
                    val visible = latestLayers
                        .filter { !it.hidden && latestTime >= it.start && latestTime <= it.end }
                        .sortedByDescending { it.z }
                    val hit = visible.firstOrNull { l ->
                        stageX in l.x..(l.x + l.w) && stageY in l.y..(l.y + l.h)
                    }
                    onSelect(hit?.id)
                }
            }
            .pointerInput(selectedId, boxSize) {
                detectDragGestures(
                    onDragStart = { start ->
                        val cur = latestLayers
                        val sel = cur.firstOrNull { it.id == selectedId }
                        if (sel == null || sel.locked || sel.hidden ||
                            latestTime < sel.start || latestTime > sel.end ||
                            scaleX <= 0f || scaleY <= 0f
                        ) {
                            beforeSnapshot = null
                            snapCenterGuide = false
                            return@detectDragGestures
                        }
                        beforeSnapshot = cur
                        val sx = start.x / scaleX
                        val sy = start.y / scaleY
                        val hitRadius = 60f / scaleX
                        val corners = listOf(
                            Offset(sel.x, sel.y),
                            Offset(sel.x + sel.w, sel.y),
                            Offset(sel.x, sel.y + sel.h),
                            Offset(sel.x + sel.w, sel.y + sel.h)
                        )
                        activeCorner = corners.indexOfFirst { c ->
                            kotlin.math.hypot((sx - c.x).toDouble(), (sy - c.y).toDouble()) <= hitRadius
                        }
                    },
                    onDrag = { change, drag ->
                        if (beforeSnapshot == null) return@detectDragGestures
                        val cur = latestLayers
                        val sel = cur.firstOrNull { it.id == selectedId } ?: return@detectDragGestures
                        change.consume()
                        val dx = drag.x / scaleX
                        val dy = drag.y / scaleY
                        val updated = cur.map { l ->
                            if (l.id != sel.id) l else when (activeCorner) {
                                -1 -> {
                                    var nx = l.x + dx
                                    val centerDiff = abs((nx + l.w * 0.5f) - 960f)
                                    snapCenterGuide = centerDiff < 18f
                                    if (snapCenterGuide) {
                                        nx = (1920f - l.w) * 0.5f
                                    }
                                    l.copy(x = nx, y = l.y + dy)
                                }
                                3 -> {
                                    snapCenterGuide = false
                                    val nw = (l.w + dx).coerceAtLeast(40f)
                                    val nh = if (l.type == "image" && l.w > 0f) nw * (l.h / l.w) else (l.h + dy).coerceAtLeast(40f)
                                    l.copy(w = nw, h = nh)
                                }
                                0 -> {
                                    snapCenterGuide = false
                                    val nw = (l.w - dx).coerceAtLeast(40f)
                                    val nh = if (l.type == "image" && l.w > 0f) nw * (l.h / l.w) else (l.h - dy).coerceAtLeast(40f)
                                    l.copy(x = l.x + (l.w - nw), y = l.y + (l.h - nh), w = nw, h = nh)
                                }
                                1 -> {
                                    snapCenterGuide = false
                                    val nw = (l.w + dx).coerceAtLeast(40f)
                                    val nh = if (l.type == "image" && l.w > 0f) nw * (l.h / l.w) else (l.h - dy).coerceAtLeast(40f)
                                    l.copy(y = l.y + (l.h - nh), w = nw, h = nh)
                                }
                                else -> {
                                    snapCenterGuide = false
                                    val nw = (l.w - dx).coerceAtLeast(40f)
                                    val nh = if (l.type == "image" && l.w > 0f) nw * (l.h / l.w) else (l.h + dy).coerceAtLeast(40f)
                                    l.copy(x = l.x + (l.w - nw), w = nw, h = nh)
                                }
                            }
                        }
                        onPreviewMutate(updated)
                    },
                    onDragEnd = {
                        snapCenterGuide = false
                        val snap = beforeSnapshot
                        if (snap != null) {
                            onCommitMutate(snap, latestLayers)
                        }
                        beforeSnapshot = null
                    },
                    onDragCancel = {
                        snapCenterGuide = false
                        beforeSnapshot = null
                    }
                )
            }
    ) {
        if (selectedVisibleNow && selected != null && scaleX > 0f && scaleY > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (snapCenterGuide) {
                    val cx = size.width * 0.5f
                    drawLine(
                        color = PlayheadRed.copy(alpha = 0.85f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                val rx = selected.x * scaleX
                val ry = selected.y * scaleY
                val rw = selected.w * scaleX
                val rh = selected.h * scaleY

                // Dual-contrast outline so selection box stands out on BOTH dark AND cream/white scenes
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.65f),
                    topLeft = Offset(rx, ry),
                    size = Size(rw, rh),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 3.5.dp.toPx())
                )
                drawRoundRect(
                    color = if (selected.locked) RawColors.ErrorCoral else Color.White,
                    topLeft = Offset(rx, ry),
                    size = Size(rw, rh),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.8.dp.toPx())
                )

                if (!selected.locked) {
                    val handleR = 5.5.dp.toPx()
                    listOf(
                        Offset(rx, ry),
                        Offset(rx + rw, ry),
                        Offset(rx, ry + rh),
                        Offset(rx + rw, ry + rh)
                    ).forEach { corner ->
                        drawCircle(Color.Black, radius = handleR + 1.5.dp.toPx(), center = corner)
                        drawCircle(Color.White, radius = handleR, center = corner)
                    }
                }
            }
        }
    }
}

private fun swapAdjacentZ(sortedDesc: List<RawLayer>, a: Int, b: Int): List<RawLayer> {
    val list = sortedDesc.toMutableList()
    val tmp = list[a]
    list[a] = list[b]
    list[b] = tmp
    val count = list.size
    return list.mapIndexed { idx, l -> l.copy(z = count - idx) }
}
