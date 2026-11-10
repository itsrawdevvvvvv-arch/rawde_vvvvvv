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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import com.rawedit.app.data.model.RawLayer
import com.rawedit.app.render.LocalAssetServer
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

private enum class EditorPanelMode {
    LAYER_TOOLS,
    TIMING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    onAutoSaveDraft: (ProjectEntity) -> Unit,
    onDone: (ProjectEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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
            BigPrimaryButton(text = "Back to create hub", onClick = onBack)
        }
        return
    }

    val initialLayers = remember(project.id) {
        LayerJson.decode(project.layersJson)
    }
    var layers by remember(project.id) { mutableStateOf(initialLayers) }
    val undoStack = remember { mutableStateListOf<List<RawLayer>>() }
    val redoStack = remember { mutableStateListOf<List<RawLayer>>() }

    var selectedLayerId by remember { mutableStateOf(initialLayers.maxByOrNull { it.z }?.id) }
    var panelMode by remember { mutableStateOf(EditorPanelMode.LAYER_TOOLS) }
    var showLayersSheet by remember { mutableStateOf(false) }
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

    // Playback loop
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

    // Sync layers and seek position into WebView
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
                    val imgW = 600f
                    val imgH = (600f * asset.height / asset.width.coerceAtLeast(1)).coerceIn(80f, 1000f)
                    val newLayer = RawLayer(
                        id = "img_${UUID.randomUUID().toString().take(6)}",
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
                        src = "https://${LocalAssetServer.ASSETS_HOST}/${asset.fileName}"
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 44.dp)
                CircleActionButton(
                    icon = RawIconType.UNDO,
                    onClick = ::performUndo,
                    enabled = undoStack.isNotEmpty(),
                    size = 44.dp,
                    iconTint = if (undoStack.isNotEmpty()) RawColors.TextPrimary else RawColors.TextMuted
                )
                CircleActionButton(
                    icon = RawIconType.REDO,
                    onClick = ::performRedo,
                    enabled = redoStack.isNotEmpty(),
                    size = 44.dp,
                    iconTint = if (redoStack.isNotEmpty()) RawColors.TextPrimary else RawColors.TextMuted
                )
            }

            RawPressButton(
                onClick = {
                    isPlaying = false
                    onDone(project.copy(layersJson = LayerJson.encode(layers)))
                }
            ) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.PrimaryBtnBg)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Done", style = RawTypography.labelLarge, color = RawColors.PrimaryBtnText)
                }
            }
        }

        // 16:9 Live Preview Stage + Direct Manipulation Handles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
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

            // Interactive layer selection & drag/resize handles overlay
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

        // Play / Pause + Scrub bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircleActionButton(
                icon = if (isPlaying) RawIconType.PAUSE else RawIconType.PLAY,
                onClick = { isPlaying = !isPlaying },
                size = 44.dp,
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
                    thumbColor = RawColors.TextPrimary,
                    activeTrackColor = RawColors.TextPrimary,
                    inactiveTrackColor = RawColors.PillGrey
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = FormatHelpers.formatDuration(currentTimeSec),
                style = RawTypography.labelMedium
            )
        }

        errorNotice?.let { msg ->
            Text(
                text = msg,
                style = RawTypography.bodyMedium,
                color = RawColors.ErrorCoral,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Mode bar: Tools | Layers | Add image | Timing
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorPillTab(
                label = "Edit layer",
                icon = RawIconType.EDIT,
                selected = panelMode == EditorPanelMode.LAYER_TOOLS,
                onClick = { panelMode = EditorPanelMode.LAYER_TOOLS }
            )
            EditorPillTab(
                label = "Timing",
                icon = RawIconType.CLOCK,
                selected = panelMode == EditorPanelMode.TIMING,
                onClick = { panelMode = EditorPanelMode.TIMING }
            )
            EditorPillTab(
                label = "Layers (${layers.size})",
                icon = RawIconType.LAYERS,
                selected = false,
                onClick = { showLayersSheet = true }
            )
            EditorPillTab(
                label = "Add image",
                icon = RawIconType.IMAGE,
                selected = false,
                onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }

        // Bottom tools area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            when (panelMode) {
                EditorPanelMode.LAYER_TOOLS -> {
                    if (selectedLayer == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Tap a layer in preview or open Layers to edit.", style = RawTypography.bodyMedium)
                        }
                    } else {
                        LayerToolsPanel(
                            layer = selectedLayer,
                            onOpacityPreview = { newOp ->
                                layers = layers.map {
                                    if (it.id == selectedLayer.id) it.copy(opacity = newOp) else it
                                }
                            },
                            onOpacityCommit = { newOp ->
                                pushCommit(
                                    layers.map { if (it.id == selectedLayer.id) it.copy(opacity = newOp) else it }
                                )
                            },
                            onMoveOrder = { action ->
                                val updated = reorderLayerZ(layers, selectedLayer.id, action)
                                pushCommit(updated)
                            },
                            onDuplicate = {
                                val maxZ = (layers.maxOfOrNull { it.z } ?: 0) + 1
                                val copy = selectedLayer.copy(
                                    id = "${selectedLayer.id}_${UUID.randomUUID().toString().take(4)}",
                                    name = "${selectedLayer.name.ifBlank { selectedLayer.id }} copy",
                                    x = selectedLayer.x + 30f,
                                    y = selectedLayer.y + 30f,
                                    z = maxZ
                                )
                                pushCommit(layers + copy)
                                selectedLayerId = copy.id
                            },
                            onDelete = {
                                val updated = layers.filterNot { it.id == selectedLayer.id }
                                pushCommit(updated)
                                selectedLayerId = updated.maxByOrNull { it.z }?.id
                            },
                            onEditText = {
                                editingTextLayer = selectedLayer
                            }
                        )
                    }
                }

                EditorPanelMode.TIMING -> {
                    TimingStripPanel(
                        layers = layers.sortedByDescending { it.z },
                        selectedId = selectedLayerId,
                        totalDurationSec = durationSec,
                        onSelect = { selectedLayerId = it },
                        onPreviewUpdate = { updated -> layers = updated },
                        onCommitUpdate = { before, updated ->
                            undoStack.add(before)
                            if (undoStack.size > 50) undoStack.removeAt(0)
                            redoStack.clear()
                            layers = updated
                            onAutoSaveDraft(project.copy(layersJson = LayerJson.encode(updated)))
                        }
                    )
                }
            }
        }
    }

    if (showLayersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLayersSheet = false },
            containerColor = RawColors.HeroSurface,
            contentColor = RawColors.TextPrimary
        ) {
            val sorted = layers.sortedByDescending { it.z }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("Layers (top to bottom)", style = RawTypography.headlineMedium)
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(sorted, key = { _, l -> l.id }) { idx, layer ->
                        val isSelected = layer.id == selectedLayerId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) RawColors.RaisedCard else RawColors.CardSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) RawColors.TextPrimary else RawColors.Divider,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    selectedLayerId = layer.id
                                    showLayersSheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = layer.name.ifBlank { layer.id },
                                    style = RawTypography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${layer.type}  •  z:${layer.z}",
                                    style = RawTypography.labelSmall
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconMiniButton(
                                    icon = RawIconType.FORWARD,
                                    enabled = idx > 0,
                                    onClick = {
                                        val swapped = swapAdjacentZ(sorted, idx, idx - 1)
                                        pushCommit(swapped)
                                    }
                                )
                                IconMiniButton(
                                    icon = RawIconType.BACKWARD,
                                    enabled = idx < sorted.lastIndex,
                                    onClick = {
                                        val swapped = swapAdjacentZ(sorted, idx, idx + 1)
                                        pushCommit(swapped)
                                    }
                                )
                                IconMiniButton(
                                    icon = if (layer.hidden) RawIconType.EYE_OFF else RawIconType.EYE,
                                    tint = if (layer.hidden) RawColors.TextMuted else RawColors.TextPrimary,
                                    onClick = {
                                        pushCommit(
                                            layers.map { if (it.id == layer.id) it.copy(hidden = !it.hidden) else it }
                                        )
                                    }
                                )
                                IconMiniButton(
                                    icon = if (layer.locked) RawIconType.LOCK else RawIconType.UNLOCK,
                                    tint = if (layer.locked) RawColors.ErrorCoral else RawColors.TextMuted,
                                    onClick = {
                                        pushCommit(
                                            layers.map { if (it.id == layer.id) it.copy(locked = !it.locked) else it }
                                        )
                                    }
                                )
                                IconMiniButton(
                                    icon = RawIconType.TRASH,
                                    tint = RawColors.ErrorCoral,
                                    onClick = {
                                        val next = layers.filterNot { it.id == layer.id }
                                        pushCommit(next)
                                        if (selectedLayerId == layer.id) {
                                            selectedLayerId = next.maxByOrNull { it.z }?.id
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
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
    var activeCorner by remember { mutableIntStateOf(-1) } // -1=move, 0=TL,1=TR,2=BL,3=BR

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
                        val hitRadius = 48f / scaleX
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

private enum class ZOrderAction { FORWARD, BACKWARD, TO_FRONT, TO_BACK }

private fun reorderLayerZ(layers: List<RawLayer>, id: String, action: ZOrderAction): List<RawLayer> {
    val sorted = layers.sortedBy { it.z }.toMutableList()
    val idx = sorted.indexOfFirst { it.id == id }
    if (idx < 0) return layers
    when (action) {
        ZOrderAction.FORWARD -> if (idx < sorted.lastIndex) {
            val item = sorted.removeAt(idx)
            sorted.add(idx + 1, item)
        }
        ZOrderAction.BACKWARD -> if (idx > 0) {
            val item = sorted.removeAt(idx)
            sorted.add(idx - 1, item)
        }
        ZOrderAction.TO_FRONT -> {
            val item = sorted.removeAt(idx)
            sorted.add(item)
        }
        ZOrderAction.TO_BACK -> {
            val item = sorted.removeAt(idx)
            sorted.add(0, item)
        }
    }
    return sorted.mapIndexed { newIdx, layer -> layer.copy(z = newIdx + 1) }
}

private fun swapAdjacentZ(sortedDesc: List<RawLayer>, a: Int, b: Int): List<RawLayer> {
    val list = sortedDesc.toMutableList()
    val tmp = list[a]
    list[a] = list[b]
    list[b] = tmp
    val count = list.size
    return list.mapIndexed { idx, l -> l.copy(z = count - idx) }
}

@Composable
private fun LayerToolsPanel(
    layer: RawLayer,
    onOpacityPreview: (Float) -> Unit,
    onOpacityCommit: (Float) -> Unit,
    onMoveOrder: (ZOrderAction) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onEditText: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = layer.name.ifBlank { layer.id },
                style = RawTypography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(layer.opacity * 100).roundToInt()}%",
                style = RawTypography.labelMedium
            )
        }

        // Opacity slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opacity", style = RawTypography.bodyMedium, modifier = Modifier.width(64.dp))
            Slider(
                value = layer.opacity.coerceIn(0f, 1f),
                onValueChange = onOpacityPreview,
                onValueChangeFinished = { onOpacityCommit(layer.opacity) },
                colors = SliderDefaults.colors(
                    thumbColor = RawColors.TextPrimary,
                    activeTrackColor = RawColors.TextPrimary,
                    inactiveTrackColor = RawColors.PillGrey
                ),
                modifier = Modifier.weight(1f)
            )
        }

        // Actions row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (layer.type == "text") {
                ToolChipButton("Edit text", RawIconType.EDIT, onClick = onEditText)
            }
            ToolChipButton("Forward", RawIconType.FORWARD) { onMoveOrder(ZOrderAction.FORWARD) }
            ToolChipButton("Back", RawIconType.BACKWARD) { onMoveOrder(ZOrderAction.BACKWARD) }
            ToolChipButton("To front", RawIconType.TO_FRONT) { onMoveOrder(ZOrderAction.TO_FRONT) }
            ToolChipButton("To back", RawIconType.TO_BACK) { onMoveOrder(ZOrderAction.TO_BACK) }
            ToolChipButton("Duplicate", RawIconType.DUPLICATE, onClick = onDuplicate)
            ToolChipButton("Delete", RawIconType.TRASH, tint = RawColors.ErrorCoral, onClick = onDelete)
        }
    }
}

@Composable
private fun TimingStripPanel(
    layers: List<RawLayer>,
    selectedId: String?,
    totalDurationSec: Double,
    onSelect: (String) -> Unit,
    onPreviewUpdate: (List<RawLayer>) -> Unit,
    onCommitUpdate: (before: List<RawLayer>, after: List<RawLayer>) -> Unit
) {
    val latestLayers by rememberUpdatedState(layers)
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var beforeSnap by remember { mutableStateOf<List<RawLayer>?>(null) }

    fun snap01(v: Double): Double = (v * 10.0).roundToInt() / 10.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(layers, key = { _, l -> l.id }) { _, layer ->
            val selected = layer.id == selectedId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) RawColors.HeroSurface else Color.Transparent)
                    .clickable { onSelect(layer.id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = layer.name.ifBlank { layer.id },
                        style = RawTypography.labelMedium,
                        color = if (selected) RawColors.TextPrimary else RawColors.TextMuted,
                        maxLines = 1
                    )
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", layer.start)}s - ${String.format(java.util.Locale.US, "%.1f", layer.end)}s",
                        style = RawTypography.labelSmall
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RawColors.BgPrimary)
                        .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                ) {
                    val startFrac = (layer.start / totalDurationSec).toFloat().coerceIn(0f, 1f)
                    val endFrac = (layer.end / totalDurationSec).toFloat().coerceIn(startFrac, 1f)
                    val barLeftPx = (startFrac * trackWidthPx).roundToInt()
                    val barWidthPx = ((endFrac - startFrac) * trackWidthPx).roundToInt().coerceAtLeast(with(density) { 28.dp.roundToPx() })

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(barLeftPx, 0) }
                            .width(with(density) { barWidthPx.toDp() })
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) RawColors.TextPrimary else RawColors.RaisedCard)
                            .pointerInput(layer.id, totalDurationSec, trackWidthPx) {
                                detectDragGestures(
                                    onDragStart = { beforeSnap = latestLayers },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        val cur = latestLayers
                                        val curLayer = cur.firstOrNull { it.id == layer.id } ?: return@detectDragGestures
                                        val deltaSec = (drag.x / trackWidthPx) * totalDurationSec
                                        val span = curLayer.end - curLayer.start
                                        val nextStart = snap01((curLayer.start + deltaSec).coerceIn(0.0, (totalDurationSec - span).coerceAtLeast(0.0)))
                                        val nextEnd = snap01(nextStart + span)
                                        onPreviewUpdate(
                                            cur.map {
                                                if (it.id == layer.id) it.copy(start = nextStart, end = nextEnd) else it
                                            }
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
                    ) {
                        // Left trim handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(12.dp)
                                .height(30.dp)
                                .background(RawColors.PillGrey.copy(alpha = 0.6f))
                                .pointerInput(layer.id, totalDurationSec, trackWidthPx) {
                                    detectDragGestures(
                                        onDragStart = { beforeSnap = latestLayers },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val cur = latestLayers
                                            val curLayer = cur.firstOrNull { it.id == layer.id } ?: return@detectDragGestures
                                            val deltaSec = (drag.x / trackWidthPx) * totalDurationSec
                                            val nextStart = snap01((curLayer.start + deltaSec).coerceIn(0.0, (curLayer.end - 0.1).coerceAtLeast(0.0)))
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
                                .height(30.dp)
                                .background(RawColors.PillGrey.copy(alpha = 0.6f))
                                .pointerInput(layer.id, totalDurationSec, trackWidthPx) {
                                    detectDragGestures(
                                        onDragStart = { beforeSnap = latestLayers },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val cur = latestLayers
                                            val curLayer = cur.firstOrNull { it.id == layer.id } ?: return@detectDragGestures
                                            val deltaSec = (drag.x / trackWidthPx) * totalDurationSec
                                            val nextEnd = snap01((curLayer.end + deltaSec).coerceIn((curLayer.start + 0.1).coerceAtMost(totalDurationSec), totalDurationSec))
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
            }
        }
    }
}

@Composable
private fun EditorPillTab(
    label: String,
    icon: RawIconType,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (selected) RawColors.PrimaryBtnBg else RawColors.RaisedCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RawIcon(
            type = icon,
            size = 15.dp,
            tint = if (selected) RawColors.PrimaryBtnText else RawColors.TextPrimary
        )
        Text(
            text = label,
            style = RawTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) RawColors.PrimaryBtnText else RawColors.TextPrimary
        )
    }
}

@Composable
private fun ToolChipButton(
    label: String,
    icon: RawIconType,
    tint: Color = RawColors.TextPrimary,
    onClick: () -> Unit
) {
    RawPressButton(onClick = onClick) {
        Row(
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(RawColors.RaisedCard)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RawIcon(type = icon, size = 16.dp, tint = tint)
            Text(text = label, style = RawTypography.bodyMedium, color = tint)
        }
    }
}

@Composable
private fun IconMiniButton(
    icon: RawIconType,
    enabled: Boolean = true,
    tint: Color = RawColors.TextPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(RawColors.HeroSurface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RawIcon(
            type = icon,
            size = 16.dp,
            tint = if (enabled) tint else RawColors.Divider
        )
    }
}
