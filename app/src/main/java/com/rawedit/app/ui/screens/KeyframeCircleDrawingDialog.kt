package com.rawedit.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rawedit.app.domain.KeyframeSample
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@Composable
fun KeyframeCircleDrawingDialog(
    sample: KeyframeSample,
    onDismiss: () -> Unit,
    onSaveAnnotation: (annotatedPath: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    val rawBmp = remember(sample.thumbPath) {
        runCatching { BitmapFactory.decodeFile(sample.thumbPath) }.getOrNull()
    }

    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RawColors.BgPrimary.copy(alpha = 0.98f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CIRCLE DEFECT AREA",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.8.sp,
                        color = RawColors.ErrorCoral
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "Frame #%02d \u2022 t=%.2fs",
                            sample.index + 1,
                            sample.timeSec
                        ),
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = RawColors.TextPrimary
                    )
                    Text(
                        text = "Drag your finger to circle the visual glitch or broken text",
                        fontFamily = BricolageFamily,
                        fontSize = 13.sp,
                        color = RawColors.TextMuted
                    )
                }
                CircleActionButton(
                    icon = RawIconType.CLOSE,
                    size = 38.dp,
                    onClick = onDismiss
                )
            }

            // Interactive Drawing Canvas (16:9)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(2.dp, RawColors.ErrorCoral.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke = currentStroke + change.position
                            },
                            onDragEnd = {
                                if (currentStroke.isNotEmpty()) {
                                    strokes.add(currentStroke)
                                    currentStroke = emptyList()
                                }
                            },
                            onDragCancel = {
                                currentStroke = emptyList()
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (rawBmp != null) {
                        drawImage(
                            image = rawBmp.asImageBitmap(),
                            dstSize = canvasSize
                        )
                    }

                    val coralColor = Color(0xFFFF4D4D)
                    val strokeStyle = Stroke(
                        width = 8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )

                    // Draw completed strokes
                    strokes.forEach { strokePts ->
                        if (strokePts.size > 1) {
                            val path = Path().apply {
                                moveTo(strokePts.first().x, strokePts.first().y)
                                for (i in 1 until strokePts.size) {
                                    lineTo(strokePts[i].x, strokePts[i].y)
                                }
                            }
                            drawPath(path, coralColor, style = strokeStyle)
                        } else if (strokePts.size == 1) {
                            drawCircle(coralColor, radius = 8.dp.toPx(), center = strokePts.first())
                        }
                    }

                    // Draw active stroke
                    if (currentStroke.size > 1) {
                        val path = Path().apply {
                            moveTo(currentStroke.first().x, currentStroke.first().y)
                            for (i in 1 until currentStroke.size) {
                                lineTo(currentStroke[i].x, currentStroke[i].y)
                            }
                        }
                        drawPath(path, coralColor, style = strokeStyle)
                    } else if (currentStroke.size == 1) {
                        drawCircle(coralColor, radius = 8.dp.toPx(), center = currentStroke.first())
                    }
                }

                // Help pill
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (strokes.isEmpty() && currentStroke.isEmpty()) "Touch & drag to circle issue" else "${strokes.size} circle stroke(s)",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = RawColors.ErrorCoral
                    )
                }
            }

            // Quick actions & instructions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RawPressButton(
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            strokes.removeAt(strokes.lastIndex)
                        }
                    },
                    enabled = strokes.isNotEmpty()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RawColors.RaisedCard)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        RawIcon(RawIconType.UNDO, size = 15.dp, tint = if (strokes.isNotEmpty()) RawColors.TextPrimary else RawColors.TextMuted)
                        Text(
                            text = "Undo stroke",
                            fontFamily = BricolageFamily,
                            fontSize = 13.sp,
                            color = if (strokes.isNotEmpty()) RawColors.TextPrimary else RawColors.TextMuted
                        )
                    }
                }

                RawPressButton(
                    onClick = { strokes.clear() },
                    enabled = strokes.isNotEmpty()
                ) {
                    Text(
                        text = "Clear all",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        color = if (strokes.isNotEmpty()) RawColors.ErrorCoral else RawColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawSecondaryButton(
                    text = "Cancel",
                    height = 52.dp,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )

                BigPrimaryButton(
                    text = if (saving) "Saving..." else "Apply Circle & Flag",
                    height = 52.dp,
                    enabled = !saving && rawBmp != null && strokes.isNotEmpty(),
                    leadingIcon = RawIconType.CHECK,
                    containerColor = RawColors.ErrorCoral,
                    contentColor = RawColors.Ink,
                    onClick = {
                        val bmp = rawBmp ?: return@BigPrimaryButton
                        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@BigPrimaryButton
                        saving = true
                        scope.launch {
                            val savedPath = withContext(Dispatchers.IO) {
                                val annotatedBmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
                                val aCanvas = android.graphics.Canvas(annotatedBmp)
                                val scaleX = bmp.width.toFloat() / canvasSize.width.toFloat()
                                val scaleY = bmp.height.toFloat() / canvasSize.height.toFloat()

                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.parseColor("#FF4D4D")
                                    style = Paint.Style.STROKE
                                    strokeWidth = (8.dp.value * scaleX).coerceAtLeast(6f)
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                }

                                strokes.forEach { strokePts ->
                                    if (strokePts.size > 1) {
                                        val aPath = android.graphics.Path().apply {
                                            moveTo(strokePts.first().x * scaleX, strokePts.first().y * scaleY)
                                            for (i in 1 until strokePts.size) {
                                                lineTo(strokePts[i].x * scaleX, strokePts[i].y * scaleY)
                                            }
                                        }
                                        aCanvas.drawPath(aPath, paint)
                                    } else if (strokePts.size == 1) {
                                        aCanvas.drawCircle(
                                            strokePts.first().x * scaleX,
                                            strokePts.first().y * scaleY,
                                            paint.strokeWidth,
                                            paint
                                        )
                                    }
                                }

                                val sourceFile = File(sample.thumbPath)
                                val parentDir = sourceFile.parentFile ?: context.cacheDir
                                val outFile = File(parentDir, "frame_annotated_${sample.index}_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(outFile).use { fos ->
                                    annotatedBmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                                }
                                annotatedBmp.recycle()
                                outFile.absolutePath
                            }
                            saving = false
                            onSaveAnnotation(savedPath)
                        }
                    },
                    modifier = Modifier.weight(1.3f)
                )
            }
        }
    }
}
