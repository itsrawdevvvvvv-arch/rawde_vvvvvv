package com.rawedit.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.domain.CutoutProcessor
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.IssueCircle
import com.rawedit.app.domain.PointNorm
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.FeatheredScrollEdges
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

data class BgChatEntry(
    val isUser: Boolean,
    val text: String
)

data class BgRemoverItem(
    val id: Int,
    val name: String,
    val sourceBitmap: Bitmap,
    val cutoutBitmap: Bitmap,
    val tolerance: Int = 34,
    val circles: List<IssueCircle> = emptyList(),
    val chatLog: List<BgChatEntry> = emptyList()
)

@Composable
fun BackgroundRemoverScreen(
    apiKey: String,
    geminiRouter: GeminiWaterfallRouter,
    onBack: () -> Unit,
    onOpenGeminiKey: () -> Unit,
    onSaveCutout: (bitmap: Bitmap, name: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var items by remember { mutableStateOf<List<BgRemoverItem>>(emptyList()) }
    var processingBatch by remember { mutableStateOf(false) }
    var refiningAi by remember { mutableStateOf(false) }
    var circleDrawMode by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    val activeDragPoints = remember { mutableStateListOf<PointNorm>() }
    var nextId by remember { mutableIntStateOf(1) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(2000)
            toastMsg = null
        }
    }

    val batchPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        processingBatch = true
        scope.launch {
            val created = mutableListOf<BgRemoverItem>()
            for ((idx, uri) in uris.withIndex()) {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }.getOrNull()
                }
                if (bmp != null) {
                    val out = CutoutProcessor.segmentSubjectWithMlKit(bmp)
                    val rawName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                        ?.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                        ?.ifBlank { "cutout_${nextId + idx}" }
                        ?: "cutout_${nextId + idx}"
                    created.add(
                        BgRemoverItem(
                            id = nextId + idx,
                            name = "${rawName}_cutout",
                            sourceBitmap = bmp,
                            cutoutBitmap = out.resultBitmap,
                            tolerance = out.toleranceUsed
                        )
                    )
                }
            }
            nextId += uris.size
            items = items + created
            processingBatch = false
            toastMsg = if (created.isNotEmpty()) "Processed ${created.size} cutout(s)" else "Could not load images"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 44.dp)
                Text(
                    text = "Background Remover",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = RawColors.TextPrimary
                )
                RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 34.dp)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (items.isEmpty() && !processingBatch) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 64.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "SMART CUTOUT\nSTUDIO",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            lineHeight = 31.sp,
                            textAlign = TextAlign.Center,
                            color = RawColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Batch upload images, then circle or chat with AI to refine edges.",
                            fontFamily = BricolageFamily,
                            fontSize = 14.5.sp,
                            textAlign = TextAlign.Center,
                            color = RawColors.TextMuted
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        BigPrimaryButton(
                            text = "Batch upload images",
                            height = 56.dp,
                            leadingIcon = RawIconType.IMAGE,
                            onClick = { batchPicker.launch(arrayOf("image/*")) }
                        )
                    }
                } else {
                    val pagerState = rememberPagerState(
                        initialPage = 0,
                        pageCount = { items.size.coerceAtLeast(1) }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 18.dp)
                            .padding(top = 6.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (processingBatch) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(RawColors.CardSurface)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = RawColors.Tan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Segmenting subject...",
                                    fontFamily = BricolageFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = RawColors.TextPrimary
                                )
                            }
                        }

                        if (items.isNotEmpty()) {
                            val currentItem = items.getOrNull(pagerState.currentPage)

                            // Cutout Preview Card
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(RawColors.CardSurface)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    userScrollEnabled = !circleDrawMode,
                                    modifier = Modifier.fillMaxWidth()
                                ) { pageIdx ->
                                    val item = items.getOrNull(pageIdx) ?: return@HorizontalPager
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${pageIdx + 1} / ${items.size}",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = RawColors.Tan
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                RawPressButton(onClick = { circleDrawMode = !circleDrawMode }) {
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(if (circleDrawMode) RawColors.ErrorCoral else RawColors.RaisedCard)
                                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        RawIcon(
                                                            type = RawIconType.EDIT,
                                                            size = 12.dp,
                                                            tint = if (circleDrawMode) Color.Black else RawColors.ErrorCoral
                                                        )
                                                        Text(
                                                            text = if (circleDrawMode) "Done" else "Circle area",
                                                            fontFamily = BricolageFamily,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = if (circleDrawMode) Color.Black else RawColors.TextPrimary
                                                        )
                                                    }
                                                }
                                                if (item.circles.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(RawColors.RaisedCard)
                                                            .clickable {
                                                                items = items.map {
                                                                    if (it.id == item.id) it.copy(circles = emptyList()) else it
                                                                }
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Clear",
                                                            fontFamily = BricolageFamily,
                                                            fontSize = 12.sp,
                                                            color = RawColors.TextMuted
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(RawColors.BgPrimary)
                                                .border(
                                                    width = if (circleDrawMode) 2.dp else 1.dp,
                                                    color = if (circleDrawMode) RawColors.ErrorCoral else RawColors.Divider,
                                                    shape = RoundedCornerShape(18.dp)
                                                )
                                                .then(
                                                    if (circleDrawMode) {
                                                        Modifier
                                                            .pointerInput(item.id, circleDrawMode) {
                                                                detectDragGestures(
                                                                    onDragStart = { offset ->
                                                                        activeDragPoints.clear()
                                                                        activeDragPoints.add(
                                                                            PointNorm(
                                                                                (offset.x / size.width).coerceIn(0f, 1f),
                                                                                (offset.y / size.height).coerceIn(0f, 1f)
                                                                            )
                                                                        )
                                                                    },
                                                                    onDrag = { change, _ ->
                                                                        change.consume()
                                                                        activeDragPoints.add(
                                                                            PointNorm(
                                                                                (change.position.x / size.width).coerceIn(0f, 1f),
                                                                                (change.position.y / size.height).coerceIn(0f, 1f)
                                                                            )
                                                                        )
                                                                    },
                                                                    onDragEnd = {
                                                                        if (activeDragPoints.isNotEmpty()) {
                                                                            val pts = activeDragPoints.toList()
                                                                            val minX = pts.minOf { it.x }
                                                                            val maxX = pts.maxOf { it.x }
                                                                            val minY = pts.minOf { it.y }
                                                                            val maxY = pts.maxOf { it.y }
                                                                            val span = hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()).toFloat() * 0.5f
                                                                            val c = IssueCircle(
                                                                                centerXNorm = (minX + maxX) * 0.5f,
                                                                                centerYNorm = (minY + maxY) * 0.5f,
                                                                                radiusNorm = span.coerceIn(0.06f, 0.5f),
                                                                                pointsNorm = pts
                                                                            )
                                                                            items = items.map {
                                                                                if (it.id == item.id) it.copy(circles = it.circles + c) else it
                                                                            }
                                                                            activeDragPoints.clear()
                                                                        }
                                                                    },
                                                                    onDragCancel = { activeDragPoints.clear() }
                                                                )
                                                            }
                                                            .pointerInput(item.id, circleDrawMode) {
                                                                detectTapGestures { offset ->
                                                                    val c = IssueCircle(
                                                                        centerXNorm = (offset.x / size.width).coerceIn(0f, 1f),
                                                                        centerYNorm = (offset.y / size.height).coerceIn(0f, 1f),
                                                                        radiusNorm = 0.14f
                                                                    )
                                                                    items = items.map {
                                                                        if (it.id == item.id) it.copy(circles = it.circles + c) else it
                                                                    }
                                                                }
                                                            }
                                                    } else Modifier
                                                )
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val cell = 18.dp.toPx()
                                                val cols = (size.width / cell).toInt() + 1
                                                val rows = (size.height / cell).toInt() + 1
                                                for (r in 0..rows) {
                                                    for (c in 0..cols) {
                                                        if ((r + c) % 2 == 0) {
                                                            drawRect(
                                                                color = Color(0xFF1A1A1E),
                                                                topLeft = Offset(c * cell, r * cell),
                                                                size = Size(cell, cell)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Image(
                                                bitmap = item.cutoutBitmap.asImageBitmap(),
                                                contentDescription = item.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp)
                                            )

                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val w = size.width
                                                val h = size.height
                                                for (c in item.circles) {
                                                    if (c.pointsNorm.size >= 3) {
                                                        val p = Path()
                                                        c.pointsNorm.forEachIndexed { idx, pt ->
                                                            if (idx == 0) p.moveTo(pt.x * w, pt.y * h) else p.lineTo(pt.x * w, pt.y * h)
                                                        }
                                                        drawPath(
                                                            path = p,
                                                            color = Color(0xFFFF3B30),
                                                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                        )
                                                    } else {
                                                        drawCircle(
                                                            color = Color(0x28FF3B30),
                                                            radius = c.radiusNorm * minOf(w, h),
                                                            center = Offset(c.centerXNorm * w, c.centerYNorm * h)
                                                        )
                                                        drawCircle(
                                                            color = Color(0xFFFF3B30),
                                                            radius = c.radiusNorm * minOf(w, h),
                                                            center = Offset(c.centerXNorm * w, c.centerYNorm * h),
                                                            style = Stroke(width = 3.5.dp.toPx())
                                                        )
                                                    }
                                                }
                                                if (activeDragPoints.size >= 2) {
                                                    val lp = Path()
                                                    activeDragPoints.forEachIndexed { idx, pt ->
                                                        if (idx == 0) lp.moveTo(pt.x * w, pt.y * h) else lp.lineTo(pt.x * w, pt.y * h)
                                                    }
                                                    drawPath(
                                                        path = lp,
                                                        color = Color(0xFFFF3B30),
                                                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // AI Cutout Chat Box Below Image
                            if (currentItem != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(RawColors.CardSurface)
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    currentItem.chatLog.takeLast(3).forEach { entry ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (entry.isUser) RawColors.AccentSoft else RawColors.RaisedCard)
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = entry.text,
                                                fontFamily = BricolageFamily,
                                                fontSize = 13.sp,
                                                color = if (entry.isUser) RawColors.Tan else RawColors.TextPrimary
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = chatInput,
                                            onValueChange = { chatInput = it },
                                            placeholder = {
                                                Text(
                                                    text = "Describe fix (e.g. remove circled area, soften edges)...",
                                                    color = RawColors.TextMuted,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            textStyle = TextStyle(
                                                fontFamily = BricolageFamily,
                                                fontSize = 14.sp,
                                                color = RawColors.TextPrimary
                                            ),
                                            maxLines = 2,
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
                                            icon = if (refiningAi) RawIconType.CLOCK else RawIconType.ARROW_RIGHT,
                                            size = 44.dp,
                                            backgroundColor = RawColors.Tan,
                                            iconTint = RawColors.Ink,
                                            enabled = !refiningAi && (chatInput.isNotBlank() || currentItem.circles.isNotEmpty()),
                                            onClick = {
                                                if (apiKey.isBlank()) {
                                                    onOpenGeminiKey()
                                                    return@CircleActionButton
                                                }
                                                val msg = chatInput.trim().ifBlank { "Fix the circled area cleanly." }
                                                chatInput = ""
                                                circleDrawMode = false
                                                refiningAi = true
                                                items = items.map {
                                                    if (it.id == currentItem.id) {
                                                        it.copy(chatLog = it.chatLog + BgChatEntry(true, msg))
                                                    } else it
                                                }
                                                scope.launch {
                                                    val agentRes = geminiRouter.runCutoutToolAgent(
                                                        apiKey = apiKey,
                                                        cutoutBitmap = currentItem.cutoutBitmap,
                                                        circles = currentItem.circles,
                                                        userInstruction = msg,
                                                        currentTolerance = currentItem.tolerance
                                                    )
                                                    agentRes.onSuccess { outcome ->
                                                        var nextBmp = currentItem.cutoutBitmap
                                                        var nextTol = currentItem.tolerance
                                                        for (call in outcome.toolCalls) {
                                                            when (call.tool) {
                                                                "erase_circle" -> {
                                                                    nextBmp = withContext(Dispatchers.Default) {
                                                                        CutoutProcessor.applyCutoutToolEraseCircle(
                                                                            nextBmp, call.cx, call.cy, call.radius
                                                                        )
                                                                    }
                                                                }
                                                                "restore_circle" -> {
                                                                    nextBmp = withContext(Dispatchers.Default) {
                                                                        CutoutProcessor.applyCutoutToolRestoreCircle(
                                                                            nextBmp, currentItem.sourceBitmap, call.cx, call.cy, call.radius
                                                                        )
                                                                    }
                                                                }
                                                                "soften_edges" -> {
                                                                    nextBmp = withContext(Dispatchers.Default) {
                                                                        CutoutProcessor.applyCutoutToolSoftenEdges(nextBmp, call.intParam)
                                                                    }
                                                                }
                                                                "resegment_flood" -> {
                                                                    nextTol = call.intParam.coerceIn(8, 90)
                                                                    nextBmp = withContext(Dispatchers.Default) {
                                                                        CutoutProcessor.processBitmapToCutout(
                                                                            currentItem.sourceBitmap, tolerance = nextTol
                                                                        ).resultBitmap
                                                                    }
                                                                }
                                                                "reset_ml_cutout" -> {
                                                                    nextBmp = CutoutProcessor.segmentSubjectWithMlKit(
                                                                        currentItem.sourceBitmap
                                                                    ).resultBitmap
                                                                }
                                                            }
                                                        }
                                                        items = items.map {
                                                            if (it.id == currentItem.id) {
                                                                it.copy(
                                                                    cutoutBitmap = nextBmp,
                                                                    tolerance = nextTol,
                                                                    circles = emptyList(),
                                                                    chatLog = it.chatLog + BgChatEntry(false, outcome.reply)
                                                                )
                                                            } else it
                                                        }
                                                    }.onFailure { err ->
                                                        items = items.map {
                                                            if (it.id == currentItem.id) {
                                                                it.copy(
                                                                    chatLog = it.chatLog + BgChatEntry(false, err.message ?: "Could not run AI tool")
                                                                )
                                                            } else it
                                                        }
                                                    }
                                                    refiningAi = false
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RawSecondaryButton(
                                    text = "Add more",
                                    height = 50.dp,
                                    leadingIcon = RawIconType.PLUS,
                                    onClick = { batchPicker.launch(arrayOf("image/*")) },
                                    modifier = Modifier.weight(1f)
                                )
                                BigPrimaryButton(
                                    text = if (items.size == 1) "Save cutout" else "Save all (${items.size})",
                                    height = 50.dp,
                                    leadingIcon = RawIconType.CHECK,
                                    onClick = {
                                        items.forEach { it ->
                                            onSaveCutout(it.cutoutBitmap, it.name)
                                        }
                                        toastMsg = "Saved ${items.size} cutout(s)"
                                        onBack()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                FeatheredScrollEdges(
                    scrollState = scrollState,
                    topHeight = 32.dp,
                    bottomHeight = 90.dp,
                    alwaysShowBottom = true
                )
            }
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        )
    }
}
