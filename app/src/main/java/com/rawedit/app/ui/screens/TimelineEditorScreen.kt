package com.rawedit.app.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.domain.LayerKeyframe
import com.rawedit.app.domain.LayerTimelineCodec
import com.rawedit.app.domain.TimelineLayer
import com.rawedit.app.domain.TimelineLayerType
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TimelineEditorScreen(
    project: ProjectEntity,
    allAssets: List<LocalAssetEntity>,
    cacheManager: CacheManager,
    onBack: () -> Unit,
    onSaveLayers: (layersJson: String, hasLayers: Boolean) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val totalDurationSec = remember(project.audioDurationSec, project.codeDurationSec) {
        maxOf(project.audioDurationSec, project.codeDurationSec, 15.0)
    }

    val initialLayers = remember(project.id, project.layersJson) {
        val decoded = LayerTimelineCodec.decode(project.layersJson)
        if (decoded.isNotEmpty()) decoded else listOf(
            TimelineLayer(
                type = TimelineLayerType.TEXT,
                name = "It's Rawdev",
                startSec = 0.0,
                endSec = minOf(6.0, totalDurationSec),
                text = "It's Rawdev",
                fontSize = 84f,
                colorHex = "#F3EFE6"
            )
        )
    }

    var layers by remember { mutableStateOf(initialLayers) }
    val undoStack = remember { mutableStateListOf<List<TimelineLayer>>() }
    val redoStack = remember { mutableStateListOf<List<TimelineLayer>>() }
    var selectedLayerId by remember { mutableStateOf<String?>(initialLayers.firstOrNull()?.id) }
    var currentTimeSec by remember { mutableDoubleStateOf(0.0) }
    var isPlaying by remember { mutableStateOf(false) }
    var showMediaPicker by remember { mutableStateOf(false) }
    var editingTextLayer by remember { mutableStateOf<TimelineLayer?>(null) }
    var lightCanvasGrid by remember { mutableStateOf(true) }

    fun pushUndoSnapshot() {
        undoStack.add(layers)
        if (undoStack.size > 30) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun mutateLayers(recordUndo: Boolean = true, transform: (List<TimelineLayer>) -> List<TimelineLayer>) {
        if (recordUndo) pushUndoSnapshot()
        layers = transform(layers)
    }

    fun updateLayer(layerId: String, recordUndo: Boolean = false, updater: (TimelineLayer) -> TimelineLayer) {
        if (recordUndo) pushUndoSnapshot()
        layers = layers.map { if (it.id == layerId) updater(it) else it }
    }

    fun applyTransformProp(
        layerId: String,
        x: Float? = null,
        y: Float? = null,
        scale: Float? = null,
        rotation: Float? = null,
        opacity: Float? = null
    ) {
        updateLayer(layerId) { l ->
            val sampled = l.sampleAt(currentTimeSec)
            val nx = x ?: sampled.x
            val ny = y ?: sampled.y
            val ns = scale ?: sampled.scale
            val nr = rotation ?: sampled.rotation
            val no = opacity ?: sampled.opacity

            if (l.keyframes.isEmpty()) {
                l.copy(x = nx, y = ny, scale = ns, rotation = nr, opacity = no)
            } else {
                val nearIdx = l.keyframes.indexOfFirst { abs(it.timeSec - currentTimeSec) < 0.08 }
                val updatedKfs = l.keyframes.toMutableList()
                val kf = LayerKeyframe(currentTimeSec, nx, ny, ns, nr, no)
                if (nearIdx >= 0) updatedKfs[nearIdx] = kf else updatedKfs.add(kf)
                l.copy(x = nx, y = ny, scale = ns, rotation = nr, opacity = no, keyframes = updatedKfs.sortedBy { it.timeSec })
            }
        }
    }

    fun toggleKeyframeAtPlayhead(layerId: String) {
        mutateLayers(recordUndo = true) { list ->
            list.map { l ->
                if (l.id != layerId) l
                else {
                    val nearIdx = l.keyframes.indexOfFirst { abs(it.timeSec - currentTimeSec) < 0.08 }
                    if (nearIdx >= 0) {
                        val rem = l.keyframes.toMutableList().also { it.removeAt(nearIdx) }
                        l.copy(keyframes = rem)
                    } else {
                        val s = l.sampleAt(currentTimeSec)
                        val nextKf = LayerKeyframe(currentTimeSec, s.x, s.y, s.scale, s.rotation, s.opacity)
                        l.copy(keyframes = (l.keyframes + nextKf).sortedBy { it.timeSec })
                    }
                }
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(33L)
            val next = currentTimeSec + 0.033
            if (next >= totalDurationSec) {
                currentTimeSec = 0.0
                isPlaying = false
            } else {
                currentTimeSec = next
            }
        }
    }

    val selectedLayer = layers.firstOrNull { it.id == selectedLayerId }
    val pxPerSec = 56.dp
    val pxPerSecFloat = with(density) { pxPerSec.toPx() }
    val timelineScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                CircleActionButton(icon = RawIconType.BACK, size = 38.dp, onClick = onBack)
                Column {
                    Text(
                        text = project.title.ifBlank { "Timeline Studio" },
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = RawColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatTimecode(currentTimeSec)} / ${formatTimecode(totalDurationSec)}",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = RawColors.Tan
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { lightCanvasGrid = !lightCanvasGrid },
                    shape = CircleShape,
                    color = RawColors.RaisedCard
                ) {
                    Text(
                        text = if (lightCanvasGrid) "Light" else "Dark",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = RawColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
                Surface(
                    onClick = {
                        onSaveLayers(LayerTimelineCodec.encode(layers), layers.isNotEmpty())
                    },
                    shape = CircleShape,
                    color = RawColors.Tan
                ) {
                    Text(
                        text = "Save",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = RawColors.Ink,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // 16:9 Live Preview Stage
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (lightCanvasGrid) Color(0xFFF6F4EE) else Color(0xFF141417))
                .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
                .clipToBounds()
                .pointerInput(selectedLayerId, currentTimeSec) {
                    detectDragGestures(
                        onDragStart = { if (selectedLayerId != null) pushUndoSnapshot() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val sid = selectedLayerId ?: return@detectDragGestures
                            val scaleXFactor = 1920f / size.width.coerceAtLeast(1)
                            val scaleYFactor = 1080f / size.height.coerceAtLeast(1)
                            val layer = layers.firstOrNull { it.id == sid } ?: return@detectDragGestures
                            val st = layer.sampleAt(currentTimeSec)
                            applyTransformProp(
                                layerId = sid,
                                x = (st.x + dragAmount.x * scaleXFactor).coerceIn(-1200f, 1200f),
                                y = (st.y + dragAmount.y * scaleYFactor).coerceIn(-800f, 800f)
                            )
                        }
                    )
                }
        ) {
            val stageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val stageRatio = stageW / 1920f

            // Subtle Alight Motion style grid
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 28.dp.toPx()
                val gridColor = if (lightCanvasGrid) Color.Black.copy(alpha = 0.055f) else Color.White.copy(alpha = 0.06f)
                var gx = step
                while (gx < size.width) {
                    drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), 1f)
                    gx += step
                }
                var gy = step
                while (gy < size.height) {
                    drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 1f)
                    gy += step
                }
            }

            // Render visible layers at currentTimeSec
            layers.forEach { layer ->
                if (layer.visible && currentTimeSec in layer.startSec..layer.endSec) {
                    val tr = layer.sampleAt(currentTimeSec)
                    val isSel = layer.id == selectedLayerId
                    val itemModifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = tr.x * stageRatio
                            translationY = tr.y * stageRatio
                            scaleX = (tr.scale / 100f).coerceAtLeast(0.05f)
                            scaleY = (tr.scale / 100f).coerceAtLeast(0.05f)
                            rotationZ = tr.rotation
                            alpha = (tr.opacity / 100f).coerceIn(0f, 1f)
                        }
                        .then(
                            if (isSel) {
                                Modifier.border(
                                    width = 1.5.dp,
                                    color = RawColors.Tan,
                                    shape = RoundedCornerShape(6.dp)
                                )
                            } else Modifier
                        )
                        .pointerInput(layer.id) {
                            detectTapGestures { selectedLayerId = layer.id }
                        }

                    when (layer.type) {
                        TimelineLayerType.TEXT -> {
                            val txtColor = parseColorSafe(layer.colorHex, if (lightCanvasGrid) Color(0xFF1A1A1E) else RawColors.Cream)
                            Text(
                                text = layer.text.ifBlank { "Text" },
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = (layer.fontSize * stageRatio).coerceAtLeast(10f).sp,
                                color = txtColor,
                                textAlign = TextAlign.Center,
                                modifier = itemModifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        TimelineLayerType.IMAGE, TimelineLayerType.VIDEO -> {
                            val file = when {
                                layer.assetThumbPath.isNotBlank() && File(layer.assetThumbPath).exists() -> File(layer.assetThumbPath)
                                layer.assetRelativePath.isNotBlank() -> cacheManager.getAssetFile(layer.assetRelativePath.removePrefix("assets/"))
                                else -> null
                            }
                            Box(
                                modifier = itemModifier
                                    .size((640f * stageRatio).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RawColors.RaisedCard),
                                contentAlignment = Alignment.Center
                            ) {
                                if (file != null && file.exists()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(file).build(),
                                        contentDescription = layer.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = layer.name,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = RawColors.Cream
                                    )
                                }
                            }
                        }
                        TimelineLayerType.SOLID -> {
                            Box(
                                modifier = itemModifier
                                    .fillMaxSize()
                                    .background(parseColorSafe(layer.bgColorHex, Color(0xFF18181B)))
                            )
                        }
                        TimelineLayerType.SHAPE -> {
                            val shape = when (layer.shapeKind) {
                                "CIRCLE" -> CircleShape
                                "PILL" -> RoundedCornerShape(999.dp)
                                else -> RoundedCornerShape(12.dp)
                            }
                            Box(
                                modifier = itemModifier
                                    .size((380f * stageRatio).dp)
                                    .clip(shape)
                                    .background(parseColorSafe(layer.bgColorHex, RawColors.Tan))
                            )
                        }
                    }
                }
            }
        }

        // Transport + Keyframe + Undo/Redo Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = {
                        if (undoStack.isNotEmpty()) {
                            redoStack.add(layers)
                            layers = undoStack.removeAt(undoStack.lastIndex)
                        }
                    },
                    shape = CircleShape,
                    color = RawColors.CardSurface
                ) {
                    Text(
                        text = "Undo",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = if (undoStack.isNotEmpty()) RawColors.TextPrimary else RawColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Surface(
                    onClick = {
                        if (redoStack.isNotEmpty()) {
                            undoStack.add(layers)
                            layers = redoStack.removeAt(redoStack.lastIndex)
                        }
                    },
                    shape = CircleShape,
                    color = RawColors.CardSurface
                ) {
                    Text(
                        text = "Redo",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = if (redoStack.isNotEmpty()) RawColors.TextPrimary else RawColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleActionButton(
                    icon = RawIconType.CHEVRON_LEFT,
                    size = 34.dp,
                    onClick = { currentTimeSec = (currentTimeSec - 0.1).coerceAtLeast(0.0) }
                )
                Surface(
                    onClick = { isPlaying = !isPlaying },
                    shape = CircleShape,
                    color = RawColors.Tan
                ) {
                    Text(
                        text = if (isPlaying) "Pause" else "Play",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = RawColors.Ink,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
                CircleActionButton(
                    icon = RawIconType.CHEVRON_RIGHT,
                    size = 34.dp,
                    onClick = { currentTimeSec = (currentTimeSec + 0.1).coerceAtMost(totalDurationSec) }
                )
            }

            if (selectedLayer != null) {
                val hasKfHere = selectedLayer.keyframes.any { abs(it.timeSec - currentTimeSec) < 0.08 }
                Surface(
                    onClick = { toggleKeyframeAtPlayhead(selectedLayer.id) },
                    shape = CircleShape,
                    color = if (hasKfHere) RawColors.Tan else RawColors.CardSurface
                ) {
                    Text(
                        text = if (hasKfHere) "\u25C6 Key" else "\u25C7 Key",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (hasKfHere) RawColors.Ink else RawColors.Tan,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(58.dp))
            }
        }

        // Scrollable Time Ruler + Multi-Track Layer Stack
        val timelineWidthDp = (totalDurationSec * pxPerSec.value).toFloat().coerceAtLeast(360f).dp
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0B0B0D))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Eye Visibility Column
                Column(
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF101013))
                        .padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    layers.forEach { layer ->
                        Box(
                            modifier = Modifier
                                .height(42.dp)
                                .fillMaxWidth()
                                .clickable {
                                    updateLayer(layer.id, recordUndo = true) { it.copy(visible = !it.visible) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (layer.visible) RawColors.Tan else RawColors.RaisedCard)
                                    .border(1.dp, RawColors.DividerStrong, CircleShape)
                            )
                        }
                    }
                }

                // Horizontally Scrollable Ruler & Layer Bars
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(timelineScroll)
                ) {
                    Column(modifier = Modifier.width(timelineWidthDp).fillMaxHeight()) {
                        // Time Ruler (drag/tap to scrub playhead)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(Color(0xFF141418))
                                .pointerInput(totalDurationSec, pxPerSecFloat) {
                                    detectTapGestures { offset ->
                                        currentTimeSec = (offset.x / pxPerSecFloat).toDouble().coerceIn(0.0, totalDurationSec)
                                    }
                                }
                                .pointerInput(totalDurationSec, pxPerSecFloat) {
                                    detectHorizontalDragGestures { change, _ ->
                                        change.consume()
                                        currentTimeSec = (change.position.x / pxPerSecFloat).toDouble().coerceIn(0.0, totalDurationSec)
                                    }
                                }
                        ) {
                            val secCount = totalDurationSec.toInt() + 1
                            for (s in 0..secCount) {
                                val xDp = (s * pxPerSec.value).dp
                                Text(
                                    text = "${s}s",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = RawColors.TextMuted,
                                    modifier = Modifier
                                        .offset(x = xDp + 3.dp, y = 4.dp)
                                )
                            }
                        }

                        // Layer Track Bars
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            itemsIndexed(layers, key = { _, l -> l.id }) { _, layer ->
                                val isSel = layer.id == selectedLayerId
                                val startDp = (layer.startSec * pxPerSec.value).toFloat().dp
                                val barWidthDp = ((layer.endSec - layer.startSec) * pxPerSec.value).toFloat().coerceAtLeast(36f).dp
                                val barColor = when (layer.type) {
                                    TimelineLayerType.VIDEO -> Color(0xFF2B6CB0)
                                    TimelineLayerType.IMAGE -> Color(0xFF2F855A)
                                    TimelineLayerType.TEXT -> Color(0xFF3182CE)
                                    TimelineLayerType.SOLID -> Color(0xFF805AD5)
                                    TimelineLayerType.SHAPE -> Color(0xFFC89F72)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .padding(vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = startDp)
                                            .width(barWidthDp)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(barColor.copy(alpha = if (layer.visible) 0.88f else 0.35f))
                                            .border(
                                                width = if (isSel) 2.dp else 0.dp,
                                                color = if (isSel) RawColors.Cream else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .pointerInput(layer.id) {
                                                detectTapGestures { selectedLayerId = layer.id }
                                            }
                                            .pointerInput(layer.id, pxPerSecFloat) {
                                                detectHorizontalDragGestures(
                                                    onDragStart = { selectedLayerId = layer.id; pushUndoSnapshot() },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val dt = (dragAmount / pxPerSecFloat).toDouble()
                                                        updateLayer(layer.id) { cur ->
                                                            val dur = cur.endSec - cur.startSec
                                                            val ns = (cur.startSec + dt).coerceIn(0.0, (totalDurationSec - dur).coerceAtLeast(0.0))
                                                            cur.copy(startSec = ns, endSec = ns + dur)
                                                        }
                                                    }
                                                )
                                            }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = layer.name,
                                                fontFamily = BricolageFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (layer.keyframes.isNotEmpty()) {
                                                Text(
                                                    text = "${layer.keyframes.size}\u25C6",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 10.sp,
                                                    color = RawColors.Cream
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Vertical Playhead Line
                    val playheadX = (currentTimeSec * pxPerSec.value).toFloat().dp
                    Box(
                        modifier = Modifier
                            .offset(x = playheadX)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(RawColors.Tan)
                    )
                }
            }
        }

        // Bottom Panel: Selected Layer Attributes (Position, Scale, Rotation, Opacity) OR Add Layer Bar
        if (selectedLayer != null) {
            val tr = selectedLayer.sampleAt(currentTimeSec)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selected layer quick actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedLayer.type.label}: ${selectedLayer.name}",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = RawColors.Tan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (selectedLayer.type == TimelineLayerType.TEXT) {
                            Surface(
                                onClick = { editingTextLayer = selectedLayer },
                                shape = CircleShape,
                                color = RawColors.RaisedCard
                            ) {
                                Text("Edit Text", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = RawColors.Cream, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                        Surface(
                            onClick = {
                                mutateLayers { list ->
                                    val idx = list.indexOfFirst { it.id == selectedLayer.id }
                                    if (idx > 0) list.toMutableList().apply { add(idx - 1, removeAt(idx)) } else list
                                }
                            },
                            shape = CircleShape,
                            color = RawColors.RaisedCard
                        ) {
                            Text("Up", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = RawColors.TextPrimary, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                        Surface(
                            onClick = {
                                mutateLayers { list ->
                                    val idx = list.indexOfFirst { it.id == selectedLayer.id }
                                    if (idx in 0 until list.lastIndex) list.toMutableList().apply { add(idx + 1, removeAt(idx)) } else list
                                }
                            },
                            shape = CircleShape,
                            color = RawColors.RaisedCard
                        ) {
                            Text("Down", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = RawColors.TextPrimary, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                        Surface(
                            onClick = {
                                mutateLayers { list -> list.filterNot { it.id == selectedLayer.id } }
                                selectedLayerId = null
                            },
                            shape = CircleShape,
                            color = RawColors.ErrorSoft
                        ) {
                            Text("Del", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = RawColors.ErrorCoral, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                        Surface(
                            onClick = { selectedLayerId = null },
                            shape = CircleShape,
                            color = RawColors.RaisedCard
                        ) {
                            Text("Add+", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = RawColors.Tan, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }

                // Alight-Motion Style Scrubbable Transform Rulers
                RulerAttributeSlider(
                    label = "Position X",
                    value = tr.x,
                    range = -960f..960f,
                    unit = "",
                    onReset = { applyTransformProp(selectedLayer.id, x = 0f) },
                    onChange = { applyTransformProp(selectedLayer.id, x = it) }
                )
                RulerAttributeSlider(
                    label = "Position Y",
                    value = tr.y,
                    range = -540f..540f,
                    unit = "",
                    onReset = { applyTransformProp(selectedLayer.id, y = 0f) },
                    onChange = { applyTransformProp(selectedLayer.id, y = it) }
                )
                RulerAttributeSlider(
                    label = "Scale (%)",
                    value = tr.scale,
                    range = 10f..400f,
                    unit = "%",
                    onReset = { applyTransformProp(selectedLayer.id, scale = 100f) },
                    onChange = { applyTransformProp(selectedLayer.id, scale = it) }
                )
                RulerAttributeSlider(
                    label = "Rotation",
                    value = tr.rotation,
                    range = -360f..360f,
                    unit = "\u00B0",
                    onReset = { applyTransformProp(selectedLayer.id, rotation = 0f) },
                    onChange = { applyTransformProp(selectedLayer.id, rotation = it) }
                )
                RulerAttributeSlider(
                    label = "Opacity",
                    value = tr.opacity,
                    range = 0f..100f,
                    unit = "%",
                    onReset = { applyTransformProp(selectedLayer.id, opacity = 100f) },
                    onChange = { applyTransformProp(selectedLayer.id, opacity = it) }
                )
            }
        } else {
            // Bottom Add-Layer Dock (Media, Text, Solid, Shape)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AddLayerDockButton(
                    label = "+ Media",
                    modifier = Modifier.weight(1f),
                    onClick = { showMediaPicker = true }
                )
                AddLayerDockButton(
                    label = "+ Text",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val nl = TimelineLayer(
                            type = TimelineLayerType.TEXT,
                            name = "Text ${layers.size + 1}",
                            startSec = currentTimeSec,
                            endSec = minOf(currentTimeSec + 4.0, totalDurationSec),
                            text = "New Text"
                        )
                        mutateLayers { it + nl }
                        selectedLayerId = nl.id
                        editingTextLayer = nl
                    }
                )
                AddLayerDockButton(
                    label = "+ Solid",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val nl = TimelineLayer(
                            type = TimelineLayerType.SOLID,
                            name = "Solid ${layers.size + 1}",
                            startSec = currentTimeSec,
                            endSec = minOf(currentTimeSec + 4.0, totalDurationSec),
                            bgColorHex = "#1A1A1E"
                        )
                        mutateLayers { listOf(nl) + it }
                        selectedLayerId = nl.id
                    }
                )
                AddLayerDockButton(
                    label = "+ Shape",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val nl = TimelineLayer(
                            type = TimelineLayerType.SHAPE,
                            name = "Shape ${layers.size + 1}",
                            startSec = currentTimeSec,
                            endSec = minOf(currentTimeSec + 4.0, totalDurationSec),
                            bgColorHex = "#C89F72",
                            shapeKind = "RECT"
                        )
                        mutateLayers { it + nl }
                        selectedLayerId = nl.id
                    }
                )
            }
        }
    }

    if (showMediaPicker) {
        Dialog(onDismissRequest = { showMediaPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add Media from Asset Locker",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = RawColors.TextPrimary
                )
                if (allAssets.isEmpty()) {
                    Text(
                        text = "No images or videos in Asset Locker yet. Add media in Step 2 first.",
                        fontFamily = BricolageFamily,
                        fontSize = 13.5.sp,
                        color = RawColors.TextMuted
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allAssets.forEach { asset ->
                            val isVid = asset.kind == LocalAssetEntity.KIND_CLIP
                            Surface(
                                onClick = {
                                    val nl = TimelineLayer(
                                        type = if (isVid) TimelineLayerType.VIDEO else TimelineLayerType.IMAGE,
                                        name = asset.fileName,
                                        startSec = currentTimeSec,
                                        endSec = minOf(currentTimeSec + maxOf(asset.durationSec, 4.0), totalDurationSec),
                                        assetRelativePath = asset.relativePath,
                                        assetThumbPath = asset.thumbPath
                                    )
                                    mutateLayers { it + nl }
                                    selectedLayerId = nl.id
                                    showMediaPicker = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = RawColors.RaisedCard,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = asset.fileName,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = RawColors.TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (isVid) "VIDEO" else "IMAGE",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp,
                                        color = RawColors.Tan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingTextLayer?.let { target ->
        var draftText by remember(target.id) { mutableStateOf(target.text) }
        var draftSize by remember(target.id) { mutableStateOf(target.fontSize.roundToInt().toString()) }
        Dialog(onDismissRequest = { editingTextLayer = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Edit Text Layer",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = RawColors.TextPrimary
                )
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    label = { Text("Text") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.Tan,
                        unfocusedBorderColor = RawColors.Divider
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draftSize,
                    onValueChange = { draftSize = it },
                    label = { Text("Font size (px)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.Tan,
                        unfocusedBorderColor = RawColors.Divider
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                BigPrimaryButton(
                    text = "Apply",
                    height = 46.dp,
                    onClick = {
                        val fs = draftSize.toFloatOrNull()?.coerceIn(18f, 240f) ?: target.fontSize
                        updateLayer(target.id, recordUndo = true) {
                            it.copy(text = draftText, name = draftText.take(22).ifBlank { "Text" }, fontSize = fs)
                        }
                        editingTextLayer = null
                    }
                )
            }
        }
    }
}

@Composable
private fun AddLayerDockButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = RawColors.RaisedCard,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = RawColors.Cream
            )
        }
    }
}

@Composable
private fun RulerAttributeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onReset: () -> Unit,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            fontFamily = BricolageFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = RawColors.TextPrimary,
            modifier = Modifier.width(76.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141418))
                .pointerInput(range) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val span = range.endInclusive - range.start
                        val delta = (dragAmount / size.width.coerceAtLeast(1)) * span * 0.65f
                        onChange((value + delta).coerceIn(range.start, range.endInclusive))
                    }
                }
        ) {
            // Tick marks
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ticks = 24
                val step = size.width / ticks
                for (i in 0..ticks) {
                    val x = i * step
                    val h = if (i % 4 == 0) size.height * 0.5f else size.height * 0.28f
                    drawLine(
                        color = Color.White.copy(alpha = if (i % 4 == 0) 0.36f else 0.16f),
                        start = Offset(x, size.height - h),
                        end = Offset(x, size.height),
                        strokeWidth = 1.5f
                    )
                }
                val norm = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                val markerX = norm * size.width
                drawLine(
                    color = Color(0xFF3182CE),
                    start = Offset(markerX, 0f),
                    end = Offset(markerX, size.height),
                    strokeWidth = 3f
                )
            }
        }
        Surface(
            onClick = onReset,
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1F3A5F)
        ) {
            Text(
                text = "${value.roundToInt()}$unit",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(58.dp)
                    .padding(vertical = 5.dp)
            )
        }
    }
}

private fun formatTimecode(sec: Double): String {
    val totalMs = (sec.coerceAtLeast(0.0) * 1000.0).roundToInt()
    val mins = totalMs / 60000
    val secs = (totalMs % 60000) / 1000
    val frames = ((totalMs % 1000) * 30 / 1000).coerceIn(0, 29)
    return String.format(Locale.US, "%02d:%02d:%02d", mins, secs, frames)
}

private fun parseColorSafe(hex: String, fallback: Color): Color {
    return runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(fallback)
}
