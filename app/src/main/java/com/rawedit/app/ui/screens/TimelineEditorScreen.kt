package com.rawedit.app.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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

    val initialLayers = remember(project.id, project.layersJson, hasBaseHtml) {
        val decoded = LayerTimelineCodec.decode(project.layersJson)
        if (decoded.isNotEmpty() || hasBaseHtml) decoded else listOf(
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
    var selectedLayerId by remember { mutableStateOf<String?>(null) }
    var currentTimeSec by remember { mutableDoubleStateOf(0.0) }
    var isPlaying by remember { mutableStateOf(false) }
    var showMediaPicker by remember { mutableStateOf(false) }
    var editingTextLayer by remember { mutableStateOf<TimelineLayer?>(null) }
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

    // Reload WebView when layer list structure changes, and seek smoothly when time or transform changes
    val structuralKey = remember(layers.map { "${it.id}:${it.visible}:${it.text}:${it.fontSize}:${it.startSec}:${it.endSec}" }, baseHtml) {
        layers.joinToString("|") { "${it.id}:${it.visible}:${it.text}:${it.fontSize}:${it.startSec}:${it.endSec}" } + baseHtml.length
    }

    LaunchedEffect(structuralKey) {
        previewWebView?.let { wv ->
            webPageReady = false
            wv.loadUrl(LocalAssetServer.ENTRY_URL)
        }
    }

    LaunchedEffect(currentTimeSec, layers, webPageReady) {
        if (!webPageReady) return@LaunchedEffect
        val wv = previewWebView ?: return@LaunchedEffect
        val tStr = String.format(Locale.US, "%.3f", currentTimeSec)
        val jsonArg = LayerTimelineCodec.encode(layers.filter { it.visible })
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Minimal Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                CircleActionButton(icon = RawIconType.BACK, size = 36.dp, onClick = onBack)
                Column {
                    Text(
                        text = project.title.ifBlank { "Timeline" },
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
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
                    onClick = onOpenChatbot,
                    shape = CircleShape,
                    color = RawColors.AccentSoft
                ) {
                    Text(
                        text = "Fix w/ AI",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = RawColors.Tan,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
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
                        fontSize = 12.5.sp,
                        color = RawColors.Ink,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 16:9 Live Preview Stage (renders actual HTML code + timeline layers)
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

        // Minimal Transport + Scrubbing Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
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
            Slider(
                value = currentTimeSec.toFloat().coerceIn(0f, totalDurationSec.toFloat()),
                onValueChange = {
                    isPlaying = false
                    currentTimeSec = it.toDouble()
                },
                valueRange = 0f..totalDurationSec.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = RawColors.Tan,
                    activeTrackColor = RawColors.Tan,
                    inactiveTrackColor = RawColors.RaisedCard
                ),
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

        // Minimal Multi-Track Layer Timeline
        val timelineWidthDp = (totalDurationSec * pxPerSec.value).toFloat().coerceAtLeast(360f).dp
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0A0A0C))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Eye Visibility Column
                Column(
                    modifier = Modifier
                        .width(38.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF101013))
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (hasBaseHtml) {
                        Box(modifier = Modifier.height(38.dp), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(RawColors.Cream.copy(alpha = 0.7f))
                            )
                        }
                    }
                    layers.forEach { layer ->
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .fillMaxWidth()
                                .clickable { updateLayer(layer.id) { it.copy(visible = !it.visible) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (layer.visible) RawColors.Tan else RawColors.RaisedCard)
                                    .border(1.dp, RawColors.DividerStrong, CircleShape)
                            )
                        }
                    }
                }

                // Scrollable Time Ruler + Tracks
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(timelineScroll)
                ) {
                    Column(modifier = Modifier.width(timelineWidthDp).fillMaxHeight()) {
                        // Tap-to-jump Time Ruler
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .background(Color(0xFF141418))
                                .pointerInput(totalDurationSec, pxPerSecFloat) {
                                    detectTapGestures { offset ->
                                        isPlaying = false
                                        currentTimeSec = (offset.x / pxPerSecFloat).toDouble().coerceIn(0.0, totalDurationSec)
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
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            if (hasBaseHtml) {
                                item(key = "__base_html_track__") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width((totalDurationSec * pxPerSec.value).toFloat().dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF23232A))
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = "HTML Reel Code (Base)",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = RawColors.Cream
                                            )
                                        }
                                    }
                                }
                            }

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
                                        .height(38.dp)
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
                                                            cur.copy(startSec = ns, endSec = ns + dur)
                                                        }
                                                    }
                                                )
                                            }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = layer.name,
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Playhead Line
                    Box(
                        modifier = Modifier
                            .offset(x = (currentTimeSec * pxPerSec.value).toFloat().dp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(RawColors.Tan)
                    )
                }
            }
        }

        // Minimal Bottom Inspector / Add-Layer Bar
        if (selectedLayer != null) {
            val tr = selectedLayer.sampleAt(currentTimeSec)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedLayer.name,
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
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
                                Text("Edit", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = RawColors.Cream, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        Surface(
                            onClick = {
                                layers = layers.filterNot { it.id == selectedLayer.id }
                                selectedLayerId = null
                            },
                            shape = CircleShape,
                            color = RawColors.ErrorSoft
                        ) {
                            Text("Del", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = RawColors.ErrorCoral, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
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
                Text(
                    text = "Edit Text",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
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
                    height = 44.dp,
                    onClick = {
                        val fs = draftSize.toFloatOrNull()?.coerceIn(18f, 240f) ?: target.fontSize
                        updateLayer(target.id) {
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
        Box(modifier = Modifier.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
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
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontFamily = BricolageFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = RawColors.TextPrimary,
            modifier = Modifier.width(54.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = RawColors.Tan,
                activeTrackColor = RawColors.Tan,
                inactiveTrackColor = RawColors.RaisedCard
            ),
            modifier = Modifier.weight(1f)
        )
        Surface(
            onClick = onReset,
            shape = RoundedCornerShape(8.dp),
            color = RawColors.RaisedCard
        ) {
            Text(
                text = "${value.roundToInt()}$unit",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = RawColors.Tan,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(52.dp)
                    .padding(vertical = 4.dp)
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
