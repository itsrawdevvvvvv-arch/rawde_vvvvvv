package com.rawedit.app.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.rawedit.app.ui.components.FormatHelpers
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
import kotlin.math.roundToInt

private enum class SimpleToolTab(val label: String, val icon: RawIconType) {
    POSITION_SCALE("Pos & Scale", RawIconType.EDIT),
    OPACITY("Opacity", RawIconType.EYE),
    MASK("Mask", RawIconType.LAYERS)
}

/**
 * After Effects / CapCut / Alight Motion style multi-track timeline editor
 * with simple controls: Position, Scale, Opacity, Add Images, Masking, and Track Ordering (Above/Below).
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
                    text = "This code has no editable layers. You can still create the video.",
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

    var selectedLayerId by remember { mutableStateOf(initialLayers.maxByOrNull { it.z }?.id) }
    var activeTool by remember { mutableStateOf(SimpleToolTab.POSITION_SCALE) }
    var editingTextLayer by remember { mutableStateOf<RawLayer?>(null) }
    var errorNotice by remember { mutableStateOf<String?>(null) }

    val durationSec = remember(project.audioDurationSec, project.codeDurationSec) {
        maxOf(project.audioDurationSec, project.codeDurationSec, 1.0)
    }
    var currentTimeSec by remember { mutableDoubleStateOf(0.0) }
    var isPlaying by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var previewBoxSize by remember { mutableStateOf(IntSize(1, 1)) }

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

    LaunchedEffect(layers, currentTimeSec, pageReady, previewBoxSize) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        val stageJson = LayerJson.encodeForStage(layers)
        val js = """
            (function() {
              document.documentElement.style.overflow = 'hidden';
              document.body.style.margin = '0';
              document.body.style.overflow = 'hidden';
              var s = document.getElementById('stage');
              if (s) {
                var cssScale = window.innerWidth / 1920;
                s.style.transformOrigin = 'top left';
                s.style.transform = 'scale(' + cssScale + ')';
              }
              if (window.RAW && typeof window.RAW.seek === 'function') {
                window.RAW.layers = $stageJson;
                window.RAW.seek($currentTimeSec);
                if (typeof window.__applyLayerMasks === 'function') {
                  window.__applyLayerMasks();
                }
              }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
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
                    val imgW = 560f
                    val imgH = (560f * asset.height / asset.width.coerceAtLeast(1)).coerceIn(80f, 960f)
                    val newId = "img_${UUID.randomUUID().toString().take(6)}"
                    baseDimensions[newId] = imgW to imgH
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
                        start = 0.0,
                        end = durationSec,
                        text = null,
                        src = "https://${LocalAssetServer.ASSETS_HOST}/${asset.fileName}",
                        mask = LayerMask.NONE.key
                    )
                    pushCommit(layers + newLayer)
                    selectedLayerId = newLayer.id
                }.onFailure { err ->
                    errorNotice = err.message ?: "Could not add image."
                }
            }
        }
    }

    val selectedLayer = layers.firstOrNull { it.id == selectedLayerId }
    val sortedTracks = remember(layers) { layers.sortedByDescending { it.z } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 42.dp)
                CircleActionButton(
                    icon = RawIconType.UNDO,
                    onClick = ::performUndo,
                    enabled = undoStack.isNotEmpty(),
                    size = 42.dp,
                    iconTint = if (undoStack.isNotEmpty()) RawColors.TextPrimary else RawColors.TextMuted
                )
                CircleActionButton(
                    icon = RawIconType.REDO,
                    onClick = ::performRedo,
                    enabled = redoStack.isNotEmpty(),
                    size = 42.dp,
                    iconTint = if (redoStack.isNotEmpty()) RawColors.TextPrimary else RawColors.TextMuted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RawPressButton(
                    onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(RawColors.RaisedCard)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RawIcon(RawIconType.IMAGE, size = 16.dp)
                        Text("+ Image", style = RawTypography.labelLarge.copy(fontSize = 13.sp))
                    }
                }

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
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(RawColors.PrimaryBtnBg)
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (openedAfterRender) "Re-render" else "Done",
                            style = RawTypography.labelLarge.copy(fontSize = 13.sp),
                            color = RawColors.PrimaryBtnText
                        )
                    }
                }
            }
        }

        // 16:9 Live Preview Canvas with direct Position drag & Corner Scale handles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp))
                .onSizeChanged { previewBoxSize = it }
        ) {
            EditorStageWebView(
                rawHtml = project.code,
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
                onSelect = { selectedLayerId = it },
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

        // Transport Playhead Bar (CapCut / Alight Motion style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircleActionButton(
                icon = if (isPlaying) RawIconType.PAUSE else RawIconType.PLAY,
                onClick = { isPlaying = !isPlaying },
                size = 40.dp,
                backgroundColor = RawColors.RaisedCard
            )
            Text(
                text = String.format(Locale.US, "%05.2fs", currentTimeSec),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                color = RawColors.TextPrimary
            )
            Slider(
                value = currentTimeSec.toFloat().coerceIn(0f, durationSec.toFloat()),
                onValueChange = {
                    isPlaying = false
                    currentTimeSec = it.toDouble()
                },
                valueRange = 0f..durationSec.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = RawColors.TextPrimary,
                    activeTrackColor = RawColors.TextPrimary,
                    inactiveTrackColor = RawColors.PillGrey
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format(Locale.US, "%.1fs", durationSec),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                color = RawColors.TextMuted
            )
        }

        errorNotice?.let { msg ->
            Text(
                text = msg,
                style = RawTypography.bodyMedium,
                color = RawColors.ErrorCoral,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
        }

        // Simple Property Strip for Selected Layer (Position/Scale, Opacity, Mask, Edit Text)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SimpleToolTab.entries.forEach { tab ->
                    val active = activeTool == tab
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (active) RawColors.PrimaryBtnBg else RawColors.RaisedCard)
                            .clickable { activeTool = tab }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        RawIcon(
                            type = tab.icon,
                            size = 14.dp,
                            tint = if (active) RawColors.PrimaryBtnText else RawColors.TextPrimary
                        )
                        Text(
                            text = tab.label,
                            style = RawTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (active) RawColors.PrimaryBtnText else RawColors.TextPrimary
                        )
                    }
                }
                if (selectedLayer?.type == "text") {
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(RawColors.RaisedCard)
                            .clickable { editingTextLayer = selectedLayer }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        RawIcon(RawIconType.EDIT, size = 14.dp)
                        Text(
                            text = "Edit text",
                            style = RawTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = RawColors.TextPrimary
                        )
                    }
                }
            }

            if (selectedLayer == null) {
                Text(
                    text = "Tap any track below or element on screen to adjust it.",
                    style = RawTypography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                when (activeTool) {
                    SimpleToolTab.POSITION_SCALE -> {
                        val base = baseDimensions.getOrPut(selectedLayer.id) {
                            selectedLayer.w.coerceAtLeast(40f) to selectedLayer.h.coerceAtLeast(40f)
                        }
                        val currentScale = (selectedLayer.w / base.first).coerceIn(0.25f, 2.5f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Scale ${(currentScale * 100).roundToInt()}%",
                                style = RawTypography.labelSmall,
                                modifier = Modifier.width(74.dp)
                            )
                            Slider(
                                value = currentScale,
                                onValueChange = { sc ->
                                    val nw = base.first * sc
                                    val nh = base.second * sc
                                    val cx = selectedLayer.x + selectedLayer.w * 0.5f
                                    val cy = selectedLayer.y + selectedLayer.h * 0.5f
                                    layers = layers.map {
                                        if (it.id == selectedLayer.id) {
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
                            // Quick center X button
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RawColors.RaisedCard)
                                    .clickable {
                                        pushCommit(
                                            layers.map {
                                                if (it.id == selectedLayer.id) it.copy(x = (1920f - it.w) * 0.5f) else it
                                            }
                                        )
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Center X", style = RawTypography.labelSmall, color = RawColors.TextPrimary)
                            }
                        }
                    }

                    SimpleToolTab.OPACITY -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Opacity ${(selectedLayer.opacity * 100).roundToInt()}%",
                                style = RawTypography.labelSmall,
                                modifier = Modifier.width(86.dp)
                            )
                            Slider(
                                value = selectedLayer.opacity.coerceIn(0f, 1f),
                                onValueChange = { op ->
                                    layers = layers.map {
                                        if (it.id == selectedLayer.id) it.copy(opacity = op) else it
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

                    SimpleToolTab.MASK -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LayerMask.entries.forEach { maskOption ->
                                val isCur = selectedLayer.mask.equals(maskOption.key, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isCur) RawColors.SuccessMint else RawColors.RaisedCard)
                                        .clickable {
                                            pushCommit(
                                                layers.map {
                                                    if (it.id == selectedLayer.id) it.copy(mask = maskOption.key) else it
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
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Multi-Track Stacked Timeline (After Effects / CapCut / Alight Motion style: Above & Below tracks)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RawColors.HeroSurface)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIMELINE TRACKS (TOP = ABOVE)",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = RawColors.TextMuted
                )
                Text(
                    text = "${sortedTracks.size} layers",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = RawColors.TextPrimary
                )
            }

            MultiTrackTimelineList(
                tracksTopFirst = sortedTracks,
                selectedId = selectedLayerId,
                currentTimeSec = currentTimeSec,
                totalDurationSec = durationSec,
                onSelect = { selectedLayerId = it },
                onMoveAbove = { idx ->
                    if (idx > 0) pushCommit(swapAdjacentZ(sortedTracks, idx, idx - 1))
                },
                onMoveBelow = { idx ->
                    if (idx < sortedTracks.lastIndex) pushCommit(swapAdjacentZ(sortedTracks, idx, idx + 1))
                },
                onToggleHidden = { layer ->
                    pushCommit(layers.map { if (it.id == layer.id) it.copy(hidden = !it.hidden) else it })
                },
                onDelete = { layer ->
                    val next = layers.filterNot { it.id == layer.id }
                    pushCommit(next)
                    if (selectedLayerId == layer.id) {
                        selectedLayerId = next.maxByOrNull { it.z }?.id
                    }
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
private fun MultiTrackTimelineList(
    tracksTopFirst: List<RawLayer>,
    selectedId: String?,
    currentTimeSec: Double,
    totalDurationSec: Double,
    onSelect: (String) -> Unit,
    onMoveAbove: (Int) -> Unit,
    onMoveBelow: (Int) -> Unit,
    onToggleHidden: (RawLayer) -> Unit,
    onDelete: (RawLayer) -> Unit,
    onPreviewUpdate: (List<RawLayer>) -> Unit,
    onCommitUpdate: (before: List<RawLayer>, after: List<RawLayer>) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestLayers by rememberUpdatedState(tracksTopFirst)
    val density = LocalDensity.current
    var laneWidthPx by remember { mutableFloatStateOf(1f) }
    var beforeSnap by remember { mutableStateOf<List<RawLayer>?>(null) }

    fun snap01(v: Double): Double = (v * 10.0).roundToInt() / 10.0

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(tracksTopFirst, key = { _, l -> l.id }) { index, layer ->
            val selected = layer.id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) RawColors.HeroSurface else RawColors.BgPrimary.copy(alpha = 0.65f))
                    .border(
                        1.dp,
                        if (selected) RawColors.TextPrimary else RawColors.Divider.copy(alpha = 0.6f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(layer.id) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Left track header: Above/Below ordering + Eye + Delete
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TinyTrackBtn(
                        icon = RawIconType.FORWARD,
                        enabled = index > 0,
                        onClick = { onMoveAbove(index) }
                    )
                    TinyTrackBtn(
                        icon = RawIconType.BACKWARD,
                        enabled = index < tracksTopFirst.lastIndex,
                        onClick = { onMoveBelow(index) }
                    )
                    TinyTrackBtn(
                        icon = if (layer.hidden) RawIconType.EYE_OFF else RawIconType.EYE,
                        tint = if (layer.hidden) RawColors.TextMuted else RawColors.TextPrimary,
                        onClick = { onToggleHidden(layer) }
                    )
                    TinyTrackBtn(
                        icon = RawIconType.TRASH,
                        tint = RawColors.ErrorCoral,
                        onClick = { onDelete(layer) }
                    )
                }

                // Right clip lane with Playhead line and draggable/trimmable clip bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RawColors.BgPrimary)
                        .onSizeChanged { laneWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                ) {
                    val startFrac = (layer.start / totalDurationSec).toFloat().coerceIn(0f, 1f)
                    val endFrac = (layer.end / totalDurationSec).toFloat().coerceIn(startFrac, 1f)
                    val barLeftPx = (startFrac * laneWidthPx).roundToInt()
                    val minBarPx = with(density) { 36.dp.roundToPx() }
                    val barWidthPx = ((endFrac - startFrac) * laneWidthPx).roundToInt().coerceAtLeast(minBarPx)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(barLeftPx, 0) }
                            .width(with(density) { barWidthPx.toDp() })
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                when {
                                    selected -> RawColors.TextPrimary
                                    layer.type == "image" -> RawColors.SuccessMint.copy(alpha = 0.25f)
                                    else -> RawColors.RaisedCard
                                }
                            )
                            .pointerInput(layer.id, totalDurationSec, laneWidthPx) {
                                detectDragGestures(
                                    onDragStart = { beforeSnap = latestLayers },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        val cur = latestLayers
                                        val curLayer = cur.firstOrNull { it.id == layer.id } ?: return@detectDragGestures
                                        val deltaSec = (drag.x / laneWidthPx) * totalDurationSec
                                        val span = curLayer.end - curLayer.start
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
                        Text(
                            text = layer.name.ifBlank { layer.text ?: layer.id },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) RawColors.PrimaryBtnText else RawColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Left trim handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(10.dp)
                                .fillMaxHeight()
                                .background(RawColors.PillGrey.copy(alpha = 0.7f))
                                .pointerInput(layer.id, totalDurationSec, laneWidthPx) {
                                    detectDragGestures(
                                        onDragStart = { beforeSnap = latestLayers },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val cur = latestLayers
                                            val curLayer = cur.firstOrNull { it.id == layer.id } ?: return@detectDragGestures
                                            val deltaSec = (drag.x / laneWidthPx) * totalDurationSec
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
                                .width(10.dp)
                                .fillMaxHeight()
                                .background(RawColors.PillGrey.copy(alpha = 0.7f))
                                .pointerInput(layer.id, totalDurationSec, laneWidthPx) {
                                    detectDragGestures(
                                        onDragStart = { beforeSnap = latestLayers },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val cur = latestLayers
                                            val curLayer = cur.firstOrNull { it.id == layer.id } ?: return@detectDragGestures
                                            val deltaSec = (drag.x / laneWidthPx) * totalDurationSec
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

                    // Playhead marker line
                    val playheadX = ((currentTimeSec / totalDurationSec).toFloat().coerceIn(0f, 1f) * laneWidthPx).roundToInt()
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(playheadX, 0) }
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(RawColors.ErrorCoral)
                    )
                }
            }
        }
    }
}

@Composable
private fun TinyTrackBtn(
    icon: RawIconType,
    enabled: Boolean = true,
    tint: Color = RawColors.TextPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(RawColors.HeroSurface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RawIcon(
            type = icon,
            size = 13.dp,
            tint = if (enabled) tint else RawColors.Divider
        )
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
    servedHtml = remember(rawHtml) { server.buildInjectedHtml(rawHtml) }

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

    var beforeSnapshot by remember { mutableStateOf<List<RawLayer>?>(null) }
    var activeCorner by remember { mutableIntStateOf(-1) }

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
                        if (sel == null || sel.locked || scaleX <= 0f || scaleY <= 0f) {
                            beforeSnapshot = null
                            return@detectDragGestures
                        }
                        beforeSnapshot = cur
                        val sx = start.x / scaleX
                        val sy = start.y / scaleY
                        val hitRadius = 56f / scaleX
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
                                -1 -> l.copy(x = l.x + dx, y = l.y + dy)
                                3 -> {
                                    val nw = (l.w + dx).coerceAtLeast(40f)
                                    val nh = if (l.type == "image" && l.w > 0f) nw * (l.h / l.w) else (l.h + dy).coerceAtLeast(40f)
                                    l.copy(w = nw, h = nh)
                                }
                                0 -> {
                                    val nw = (l.w - dx).coerceAtLeast(40f)
                                    val nh = if (l.type == "image" && l.w > 0f) nw * (l.h / l.w) else (l.h - dy).coerceAtLeast(40f)
                                    l.copy(x = l.x + (l.w - nw), y = l.y + (l.h - nh), w = nw, h = nh)
                                }
                                1 -> {
                                    val nw = (l.w + dx).coerceAtLeast(40f)
                                    val nh = if (l.type == "image" && l.w > 0f) nw * (l.h / l.w) else (l.h - dy).coerceAtLeast(40f)
                                    l.copy(y = l.y + (l.h - nh), w = nw, h = nh)
                                }
                                else -> {
                                    val nw = (l.w - dx).coerceAtLeast(40f)
                                    val nh = if (l.type == "image" && l.w > 0f) nw * (l.h / l.w) else (l.h + dy).coerceAtLeast(40f)
                                    l.copy(x = l.x + (l.w - nw), w = nw, h = nh)
                                }
                            }
                        }
                        onPreviewMutate(updated)
                    },
                    onDragEnd = {
                        val snap = beforeSnapshot
                        if (snap != null) {
                            onCommitMutate(snap, latestLayers)
                        }
                        beforeSnapshot = null
                    },
                    onDragCancel = {
                        beforeSnapshot = null
                    }
                )
            }
    ) {
        if (selected != null && !selected.hidden && scaleX > 0f && scaleY > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rx = selected.x * scaleX
                val ry = selected.y * scaleY
                val rw = selected.w * scaleX
                val rh = selected.h * scaleY
                val borderColor = if (selected.locked) RawColors.ErrorCoral else RawColors.TextPrimary
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(rx, ry),
                    size = Size(rw, rh),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
                if (!selected.locked) {
                    val handleR = 6.dp.toPx()
                    listOf(
                        Offset(rx, ry),
                        Offset(rx + rw, ry),
                        Offset(rx, ry + rh),
                        Offset(rx + rw, ry + rh)
                    ).forEach { corner ->
                        drawCircle(RawColors.PrimaryBtnBg, radius = handleR, center = corner)
                        drawCircle(RawColors.BgPrimary, radius = handleR * 0.45f, center = corner)
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
