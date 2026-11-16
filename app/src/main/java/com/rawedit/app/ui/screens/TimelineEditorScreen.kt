package com.rawedit.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.domain.LayerKeyframe
import com.rawedit.app.domain.LayerTimelineCodec
import com.rawedit.app.domain.LayerTimelineCompiler
import com.rawedit.app.domain.TimelineLayer
import com.rawedit.app.domain.TimelineLayerType
import com.rawedit.app.render.LocalAssetServer
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val STUDIO_SWATCHES = listOf(
    "#F3EFE6", "#111114", "#C89F72", "#FFFFFF",
    "#E53E3E", "#3182CE", "#38A169", "#D69E2E", "#805AD5"
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TimelineEditorScreen(
    project: ProjectEntity,
    allAssets: List<LocalAssetEntity>,
    cacheManager: CacheManager,
    onOpenChatbot: () -> Unit = {},
    onBack: () -> Unit,
    onSaveLayers: (layersJson: String, hasLayers: Boolean) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val baseHtml = remember(project.id, project.code) {
        cacheManager.resolveHtmlCode(project.code)
    }
    val hasBaseHtml = baseHtml.isNotBlank()

    val totalDurationSec = remember(project.audioDurationSec, project.codeDurationSec) {
        maxOf(project.audioDurationSec, project.codeDurationSec, 15.0)
    }

    val initialLayers = remember(project.id, project.layersJson) {
        LayerTimelineCodec.decode(project.layersJson)
    }

    var layers by remember { mutableStateOf(initialLayers) }
    var selectedLayerId by remember { mutableStateOf<String?>(null) }
    var currentTimeSec by remember { mutableDoubleStateOf(0.0) }
    var isPlaying by remember { mutableStateOf(false) }
    var showMediaPicker by remember { mutableStateOf(false) }
    var editingTextLayer by remember { mutableStateOf<TimelineLayer?>(null) }
    var editingHexLayer by remember { mutableStateOf<TimelineLayer?>(null) }
    var previewWebView by remember { mutableStateOf<WebView?>(null) }
    var webPageReady by remember { mutableStateOf(false) }

    val compiledHtml = remember(baseHtml, layers, totalDurationSec) {
        LayerTimelineCompiler.compileToHtml(
            baseHtml = baseHtml,
            layersJson = LayerTimelineCodec.encode(layers),
            durationSec = totalDurationSec
        )
    }
    val latestCompiledHtml by rememberUpdatedState(compiledHtml)

    val assetServer = remember(cacheManager) {
        LocalAssetServer(context, cacheManager) {
            LocalAssetServer(context, cacheManager) { "" }.buildInjectedHtml(latestCompiledHtml)
        }
    }

    fun extractHtmlLayersFromWebView(wv: WebView, forceReplace: Boolean = false) {
        if (!hasBaseHtml) return
        if (!forceReplace && layers.any { it.domEid.isNotBlank() }) return
        wv.evaluateJavascript(LayerTimelineCompiler.EXTRACT_DOM_ELEMENTS_JS) { rawJson ->
            val clean = rawJson?.trim()?.removeSurrounding("\"")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                .orEmpty()
            runCatching {
                val arr = JSONArray(clean)
                val extracted = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val eid = o.optString("eid")
                    if (eid.isBlank()) return@mapNotNull null
                    TimelineLayer(
                        id = "dom_$eid",
                        type = TimelineLayerType.fromId(o.optString("type", "shape")),
                        name = o.optString("name", "Element ${i + 1}"),
                        startSec = o.optDouble("startSec", 0.0).coerceIn(0.0, totalDurationSec),
                        endSec = o.optDouble("endSec", totalDurationSec).coerceIn(0.2, totalDurationSec),
                        text = o.optString("text", ""),
                        fontSize = o.optDouble("fontSize", 48.0).toFloat(),
                        colorHex = o.optString("colorHex", "#1A1A1E").ifBlank { "#1A1A1E" },
                        bgColorHex = o.optString("bgColorHex", "#C89F72").ifBlank { "#C89F72" },
                        domEid = eid
                    )
                }
                if (extracted.isNotEmpty()) {
                    val customOnly = layers.filter { it.domEid.isBlank() }
                    layers = extracted + customOnly
                    val tStr = String.format(Locale.US, "%.3f", currentTimeSec)
                    wv.evaluateJavascript("(function(){ var r=(window.__ensureRawContract&&window.__ensureRawContract())||window.RAW; if(r&&typeof r.seek==='function'){ r.seek($tStr); } })()", null)
                }
            }
        }
    }

    LaunchedEffect(currentTimeSec, layers, webPageReady) {
        if (!webPageReady) return@LaunchedEffect
        val wv = previewWebView ?: return@LaunchedEffect
        val tStr = String.format(Locale.US, "%.3f", currentTimeSec)
        val jsonArg = LayerTimelineCodec.encode(layers)
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        wv.evaluateJavascript(
            "(function(){ if(window.__updateLayerData){ window.__updateLayerData('$jsonArg'); } var r=(window.__ensureRawContract&&window.__ensureRawContract())||window.RAW; if(r&&typeof r.seek==='function'){ r.seek($tStr); } })()",
            null
        )
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

    fun updateLayer(layerId: String, updater: (TimelineLayer) -> TimelineLayer) {
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
        updateLayer(layerId) { l ->
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

    val selectedLayer = layers.firstOrNull { it.id == selectedLayerId }
    val pxPerSec = 52.dp
    val pxPerSecFloat = with(density) { pxPerSec.toPx() }
    val timelineScroll = rememberScrollState()
    val trackListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var programmaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(timelineScroll, pxPerSecFloat, totalDurationSec) {
        snapshotFlow { timelineScroll.value }
            .collect { scrollPx ->
                if (!programmaticScroll && !isPlaying) {
                    val t = (scrollPx / pxPerSecFloat).toDouble().coerceIn(0.0, totalDurationSec)
                    if (abs(t - currentTimeSec) > 0.005) {
                        currentTimeSec = t
                    }
                }
            }
    }

    LaunchedEffect(currentTimeSec, isPlaying) {
        if (isPlaying) {
            programmaticScroll = true
            timelineScroll.scrollTo((currentTimeSec * pxPerSecFloat).roundToInt())
            programmaticScroll = false
        }
        if (selectedLayerId == null && layers.isNotEmpty()) {
            val activeIdx = layers.indexOfFirst { currentTimeSec in it.startSec..it.endSec }
            if (activeIdx >= 0) {
                val visible = trackListState.layoutInfo.visibleItemsInfo.map { it.index }
                if (activeIdx !in visible) {
                    runCatching { trackListState.animateScrollToItem(activeIdx) }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Clean Top Bar (generous padding so timecode never clips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                CircleActionButton(icon = RawIconType.BACK, size = 36.dp, onClick = onBack)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = project.title.ifBlank { "Timeline" },
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasBaseHtml) {
                    Surface(
                        onClick = { previewWebView?.let { extractHtmlLayersFromWebView(it, forceReplace = true) } },
                        shape = CircleShape,
                        color = RawColors.RaisedCard
                    ) {
                        Text(
                            text = "Scan",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = RawColors.Cream,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
                Surface(
                    onClick = onOpenChatbot,
                    shape = CircleShape,
                    color = RawColors.AccentSoft
                ) {
                    Text(
                        text = "AI",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = RawColors.Tan,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
                Surface(
                    onClick = { onSaveLayers(LayerTimelineCodec.encode(layers), layers.isNotEmpty()) },
                    shape = CircleShape,
                    color = RawColors.Tan
                ) {
                    Text(
                        text = "Save",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = RawColors.Ink,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 16:9 Live Preview Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0D0D0F))
                .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp))
                .clipToBounds()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        assetServer.configureSandboxedWebView(this) {
                            webPageReady = true
                            extractHtmlLayersFromWebView(this, forceReplace = false)
                            val tStr = String.format(Locale.US, "%.3f", currentTimeSec)
                            evaluateJavascript(
                                "(function(){ var r=(window.__ensureRawContract&&window.__ensureRawContract())||window.RAW; if(r&&typeof r.seek==='function'){ r.seek($tStr); } })()",
                                null
                            )
                        }
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        setInitialScale(1)
                        loadUrl(LocalAssetServer.ENTRY_URL)
                        previewWebView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Sleek Transport + Custom Scrub Track
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = { isPlaying = !isPlaying },
                shape = CircleShape,
                color = RawColors.Tan
            ) {
                Text(
                    text = if (isPlaying) "Pause" else "Play",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = RawColors.Ink,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            SleekScrubBar(
                value = currentTimeSec.toFloat().coerceIn(0f, totalDurationSec.toFloat()),
                range = 0f..totalDurationSec.toFloat(),
                onChange = {
                    isPlaying = false
                    val nextT = it.toDouble()
                    currentTimeSec = nextT
                    scope.launch {
                        programmaticScroll = true
                        timelineScroll.scrollTo((nextT * pxPerSecFloat).roundToInt())
                        programmaticScroll = false
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (selectedLayer != null) {
                val hasKf = selectedLayer.keyframes.any { abs(it.timeSec - currentTimeSec) < 0.08 }
                Surface(
                    onClick = { toggleKeyframeAtPlayhead(selectedLayer.id) },
                    shape = CircleShape,
                    color = if (hasKf) RawColors.Tan else RawColors.CardSurface
                ) {
                    Text(
                        text = if (hasKf) "\u25C6 Key" else "\u25C7 Key",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (hasKf) RawColors.Ink else RawColors.Tan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Multi-Track Layer Timeline (All extracted HTML elements + added layers)
        val timelineWidthDp = (totalDurationSec * pxPerSec.value).toFloat().coerceAtLeast(360f).dp
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0A0A0C))
                .pointerInput(Unit) {
                    detectTapGestures { selectedLayerId = null }
                }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Eye Visibility Column
                Column(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF101013))
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    layers.forEach { layer ->
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .fillMaxWidth()
                                .clickable { updateLayer(layer.id) { it.copy(visible = !it.visible) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .background(if (layer.visible) RawColors.Tan else RawColors.RaisedCard)
                                    .border(1.dp, RawColors.DividerStrong, CircleShape)
                            )
                        }
                    }
                }

                // Scrollable Time Ruler + Tracks with Center-Fixed Playhead
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val halfViewportDp = maxWidth / 2f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(timelineScroll)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = halfViewportDp)
                                .width(timelineWidthDp)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(Color(0xFF141418))
                                    .pointerInput(totalDurationSec, pxPerSecFloat) {
                                        detectTapGestures { offset ->
                                            isPlaying = false
                                            val nextT = (offset.x / pxPerSecFloat).toDouble().coerceIn(0.0, totalDurationSec)
                                            currentTimeSec = nextT
                                            scope.launch {
                                                programmaticScroll = true
                                                timelineScroll.animateScrollTo((nextT * pxPerSecFloat).roundToInt())
                                                programmaticScroll = false
                                            }
                                        }
                                    }
                            ) {
                                val secCount = totalDurationSec.toInt() + 1
                                for (s in 0..secCount) {
                                    Text(
                                        text = "${s}s",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = RawColors.TextMuted,
                                        modifier = Modifier.offset(x = (s * pxPerSec.value).dp + 3.dp, y = 4.dp)
                                    )
                                }
                            }

                            LazyColumn(
                                state = trackListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                itemsIndexed(layers, key = { _, l -> l.id }) { _, layer ->
                                    val isSel = layer.id == selectedLayerId
                                    val startDp = (layer.startSec * pxPerSec.value).toFloat().dp
                                    val barWidthDp = ((layer.endSec - layer.startSec) * pxPerSec.value).toFloat().coerceAtLeast(44f).dp
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
                                            .height(36.dp)
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = startDp)
                                                .width(barWidthDp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(barColor.copy(alpha = if (layer.visible) 0.88f else 0.35f))
                                                .border(
                                                    width = if (isSel) 2.dp else 0.dp,
                                                    color = if (isSel) RawColors.Cream else Color.Transparent,
                                                    shape = RoundedCornerShape(7.dp)
                                                )
                                                .pointerInput(layer.id) {
                                                    detectTapGestures {
                                                        selectedLayerId = if (selectedLayerId == layer.id) null else layer.id
                                                    }
                                                }
                                                .pointerInput(layer.id, pxPerSecFloat) {
                                                    detectHorizontalDragGestures(
                                                        onDragStart = { selectedLayerId = layer.id },
                                                        onHorizontalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            val dt = (dragAmount / pxPerSecFloat).toDouble()
                                                            updateLayer(layer.id) { cur ->
                                                                val dur = cur.endSec - cur.startSec
                                                                val ns = (cur.startSec + dt).coerceIn(0.0, (totalDurationSec - dur).coerceAtLeast(0.0))
                                                                cur.copy(startSec = ns, endSec = ns + dur, timingEdited = true)
                                                            }
                                                        }
                                                    )
                                                }
                                                .padding(horizontal = 9.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = layer.name,
                                                fontFamily = BricolageFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Fixed Center Playhead Needle
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(RawColors.Tan)
                    )
                }
            }
        }

        // Bottom Inspector (Transforms + Color Swatches + Text Edit) OR Add-Layer Dock
        if (selectedLayer != null) {
            val tr = selectedLayer.sampleAt(currentTimeSec)
            val isText = selectedLayer.type == TimelineLayerType.TEXT
            val activeHex = if (isText) selectedLayer.colorHex else selectedLayer.bgColorHex

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedLayer.type.label}: ${selectedLayer.name}",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = RawColors.Tan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isText) {
                            Surface(
                                onClick = { editingTextLayer = selectedLayer },
                                shape = CircleShape,
                                color = RawColors.RaisedCard
                            ) {
                                Text("Edit Text", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = RawColors.Cream, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        Surface(
                            onClick = {
                                if (selectedLayer.domEid.isNotBlank()) {
                                    updateLayer(selectedLayer.id) { it.copy(visible = false) }
                                } else {
                                    layers = layers.filterNot { it.id == selectedLayer.id }
                                }
                                selectedLayerId = null
                            },
                            shape = CircleShape,
                            color = RawColors.ErrorSoft
                        ) {
                            Text("Hide/Del", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = RawColors.ErrorCoral, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                        }
                        Surface(
                            onClick = { selectedLayerId = null },
                            shape = CircleShape,
                            color = RawColors.RaisedCard
                        ) {
                            Text("Done", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = RawColors.Tan, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }

                // Color Picker Swatches Row (for Text, Shape, Solid)
                if (selectedLayer.type in setOf(TimelineLayerType.TEXT, TimelineLayerType.SHAPE, TimelineLayerType.SOLID)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isText) "Color" else "Fill",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = RawColors.TextMuted
                        )
                        STUDIO_SWATCHES.forEach { hex ->
                            val isChosen = activeHex.equals(hex, ignoreCase = true)
                            val col = runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(RawColors.Tan)
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (isChosen) 2.dp else 1.dp,
                                        color = if (isChosen) RawColors.Tan else RawColors.DividerStrong,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        updateLayer(selectedLayer.id) {
                                            if (isText) it.copy(colorHex = hex, colorEdited = true)
                                            else it.copy(bgColorHex = hex, bgColorEdited = true)
                                        }
                                    }
                            )
                        }
                        Surface(
                            onClick = { editingHexLayer = selectedLayer },
                            shape = CircleShape,
                            color = RawColors.RaisedCard
                        ) {
                            Text(
                                text = activeHex,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = RawColors.Tan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                MinimalTransformSlider("Pos X", tr.x, -960f..960f, "", { applyTransformProp(selectedLayer.id, x = 0f) }) {
                    applyTransformProp(selectedLayer.id, x = it)
                }
                MinimalTransformSlider("Pos Y", tr.y, -540f..540f, "", { applyTransformProp(selectedLayer.id, y = 0f) }) {
                    applyTransformProp(selectedLayer.id, y = it)
                }
                MinimalTransformSlider("Scale", tr.scale, 10f..400f, "%", { applyTransformProp(selectedLayer.id, scale = 100f) }) {
                    applyTransformProp(selectedLayer.id, scale = it)
                }
                MinimalTransformSlider("Rotate", tr.rotation, -360f..360f, "\u00B0", { applyTransformProp(selectedLayer.id, rotation = 0f) }) {
                    applyTransformProp(selectedLayer.id, rotation = it)
                }
                MinimalTransformSlider("Opacity", tr.opacity, 0f..100f, "%", { applyTransformProp(selectedLayer.id, opacity = 100f) }) {
                    applyTransformProp(selectedLayer.id, opacity = it)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AddLayerButton("+ Media", Modifier.weight(1f)) { showMediaPicker = true }
                AddLayerButton("+ Text", Modifier.weight(1f)) {
                    val nl = TimelineLayer(
                        type = TimelineLayerType.TEXT,
                        name = "Text ${layers.size + 1}",
                        startSec = currentTimeSec,
                        endSec = minOf(currentTimeSec + 4.0, totalDurationSec),
                        text = "New Text"
                    )
                    layers = layers + nl
                    selectedLayerId = nl.id
                    editingTextLayer = nl
                }
                AddLayerButton("+ Solid", Modifier.weight(1f)) {
                    val nl = TimelineLayer(
                        type = TimelineLayerType.SOLID,
                        name = "Solid ${layers.size + 1}",
                        startSec = currentTimeSec,
                        endSec = minOf(currentTimeSec + 4.0, totalDurationSec),
                        bgColorHex = "#1A1A1E"
                    )
                    layers = listOf(nl) + layers
                    selectedLayerId = nl.id
                }
                AddLayerButton("+ Shape", Modifier.weight(1f)) {
                    val nl = TimelineLayer(
                        type = TimelineLayerType.SHAPE,
                        name = "Shape ${layers.size + 1}",
                        startSec = currentTimeSec,
                        endSec = minOf(currentTimeSec + 4.0, totalDurationSec),
                        bgColorHex = "#C89F72",
                        shapeKind = "RECT"
                    )
                    layers = layers + nl
                    selectedLayerId = nl.id
                }
            }
        }
    }

    if (showMediaPicker) {
        Dialog(onDismissRequest = { showMediaPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add Media from Asset Locker",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = RawColors.TextPrimary
                )
                if (allAssets.isEmpty()) {
                    Text(
                        text = "No images or videos in Asset Locker yet. Add media in Step 2 first.",
                        fontFamily = BricolageFamily,
                        fontSize = 13.sp,
                        color = RawColors.TextMuted
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
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
                                    layers = layers + nl
                                    selectedLayerId = nl.id
                                    showMediaPicker = false
                                },
                                shape = RoundedCornerShape(12.dp),
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
                                        fontSize = 12.sp,
                                        color = RawColors.TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (isVid) "VIDEO" else "IMAGE",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
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
                    .clip(RoundedCornerShape(22.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Edit Text", fontFamily = BricolageFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RawColors.TextPrimary)
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    label = { Text("Text") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RawColors.Tan, unfocusedBorderColor = RawColors.Divider),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draftSize,
                    onValueChange = { draftSize = it },
                    label = { Text("Font size (px)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RawColors.Tan, unfocusedBorderColor = RawColors.Divider),
                    modifier = Modifier.fillMaxWidth()
                )
                BigPrimaryButton(
                    text = "Apply",
                    height = 44.dp,
                    onClick = {
                        val fs = draftSize.toFloatOrNull()?.coerceIn(14f, 260f) ?: target.fontSize
                        updateLayer(target.id) {
                            it.copy(
                                text = draftText,
                                name = draftText.take(24).ifBlank { "Text" },
                                fontSize = fs,
                                textEdited = true,
                                fontSizeEdited = true
                            )
                        }
                        editingTextLayer = null
                    }
                )
            }
        }
    }

    editingHexLayer?.let { target ->
        val isText = target.type == TimelineLayerType.TEXT
        var draftHex by remember(target.id) { mutableStateOf(if (isText) target.colorHex else target.bgColorHex) }
        Dialog(onDismissRequest = { editingHexLayer = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Custom Hex Color", fontFamily = BricolageFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RawColors.TextPrimary)
                OutlinedTextField(
                    value = draftHex,
                    onValueChange = { draftHex = it },
                    label = { Text("#RRGGBB") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RawColors.Tan, unfocusedBorderColor = RawColors.Divider),
                    modifier = Modifier.fillMaxWidth()
                )
                BigPrimaryButton(
                    text = "Apply Color",
                    height = 44.dp,
                    onClick = {
                        val clean = draftHex.trim().let { if (it.startsWith("#")) it else "#$it" }
                        val valid = runCatching { AndroidColor.parseColor(clean); true }.getOrDefault(false)
                        if (valid) {
                            updateLayer(target.id) {
                                if (isText) it.copy(colorHex = clean, colorEdited = true)
                                else it.copy(bgColorHex = clean, bgColorEdited = true)
                            }
                        }
                        editingHexLayer = null
                    }
                )
            }
        }
    }
}

@Composable
private fun SleekScrubBar(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnChange by rememberUpdatedState(onChange)
    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    val norm = ((value - range.start) / span).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(26.dp)
            .pointerInput(range) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    latestOnChange(range.start + frac * span)
                }
            }
            .pointerInput(range) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    latestOnChange(range.start + frac * span)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val cy = size.height / 2f
            val trackH = 4.dp.toPx()
            drawLine(
                color = RawColors.RaisedCard,
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = trackH
            )
            val activeX = norm * size.width
            drawLine(
                color = RawColors.Tan,
                start = Offset(0f, cy),
                end = Offset(activeX, cy),
                strokeWidth = trackH
            )
            drawCircle(
                color = RawColors.Cream,
                radius = 6.5.dp.toPx(),
                center = Offset(activeX, cy)
            )
        }
    }
}

@Composable
private fun AddLayerButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = RawColors.RaisedCard,
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = RawColors.Cream
            )
        }
    }
}

@Composable
private fun MinimalTransformSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onReset: () -> Unit,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontFamily = BricolageFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = RawColors.TextPrimary,
            modifier = Modifier.width(56.dp)
        )
        SleekScrubBar(
            value = value.coerceIn(range.start, range.endInclusive),
            range = range,
            onChange = onChange,
            modifier = Modifier.weight(1f)
        )
        Surface(
            onClick = onReset,
            shape = RoundedCornerShape(7.dp),
            color = RawColors.RaisedCard
        ) {
            Text(
                text = "${value.roundToInt()}$unit",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                color = RawColors.Tan,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(48.dp)
                    .padding(vertical = 3.dp)
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
