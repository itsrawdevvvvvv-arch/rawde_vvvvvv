package com.rawedit.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CircleAvatarCropperDialog(
    sourceBitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirmCrop: (Bitmap) -> Unit
) {
    val imgBmp = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    var userScale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RawColors.BgPrimary)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Adjust photo", style = RawTypography.headlineMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Pinch to zoom and drag to center inside the circle.", style = RawTypography.bodyMedium)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(RawColors.CardSurface)
                    .onSizeChanged { boxSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            userScale = (userScale * zoom).coerceIn(1f, 4f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                ) {
                    val cropDiameter = min(size.width, size.height) * 0.78f
                    val baseScale = maxOf(
                        cropDiameter / sourceBitmap.width.toFloat(),
                        cropDiameter / sourceBitmap.height.toFloat()
                    )
                    val totalScale = baseScale * userScale
                    val drawW = sourceBitmap.width * totalScale
                    val drawH = sourceBitmap.height * totalScale
                    val maxDx = ((drawW - cropDiameter) * 0.5f).coerceAtLeast(0f)
                    val maxDy = ((drawH - cropDiameter) * 0.5f).coerceAtLeast(0f)
                    val cx = size.width * 0.5f + offsetX.coerceIn(-maxDx, maxDx)
                    val cy = size.height * 0.5f + offsetY.coerceIn(-maxDy, maxDy)

                    drawImage(
                        image = imgBmp,
                        dstOffset = IntOffset((cx - drawW * 0.5f).roundToInt(), (cy - drawH * 0.5f).roundToInt()),
                        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
                    )

                    // Dark scrim outside circle
                    drawRect(Color.Black.copy(alpha = 0.62f))
                    drawCircle(
                        color = Color.Transparent,
                        radius = cropDiameter * 0.5f,
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        blendMode = BlendMode.Clear
                    )
                    drawCircle(
                        color = RawColors.TextPrimary,
                        radius = cropDiameter * 0.5f,
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RawPressButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(RawColors.RaisedCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", style = RawTypography.labelLarge)
                    }
                }
                RawPressButton(
                    onClick = {
                        val cropped = cropBitmapTo512(
                            source = sourceBitmap,
                            boxSize = boxSize,
                            userScale = userScale,
                            offsetX = offsetX,
                            offsetY = offsetY
                        )
                        onConfirmCrop(cropped)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(RawColors.PrimaryBtnBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Use photo", style = RawTypography.labelLarge, color = RawColors.PrimaryBtnText)
                    }
                }
            }
        }
    }
}

private fun cropBitmapTo512(
    source: Bitmap,
    boxSize: IntSize,
    userScale: Float,
    offsetX: Float,
    offsetY: Float
): Bitmap {
    val outSize = 512
    val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)

    val viewW = boxSize.width.toFloat().coerceAtLeast(1f)
    val viewH = boxSize.height.toFloat().coerceAtLeast(1f)
    val cropDiameter = min(viewW, viewH) * 0.78f
    val baseScale = maxOf(
        cropDiameter / source.width.toFloat(),
        cropDiameter / source.height.toFloat()
    )
    val totalScale = baseScale * userScale
    val drawW = source.width * totalScale
    val drawH = source.height * totalScale
    val maxDx = ((drawW - cropDiameter) * 0.5f).coerceAtLeast(0f)
    val maxDy = ((drawH - cropDiameter) * 0.5f).coerceAtLeast(0f)
    val clampedDx = offsetX.coerceIn(-maxDx, maxDx)
    val clampedDy = offsetY.coerceIn(-maxDy, maxDy)

    val cropLeftInDraw = (drawW - cropDiameter) * 0.5f - clampedDx
    val cropTopInDraw = (drawH - cropDiameter) * 0.5f - clampedDy

    val srcLeft = (cropLeftInDraw / totalScale).roundToInt().coerceIn(0, source.width - 1)
    val srcTop = (cropTopInDraw / totalScale).roundToInt().coerceIn(0, source.height - 1)
    val srcSize = (cropDiameter / totalScale).roundToInt().coerceAtLeast(1)
    val srcRight = (srcLeft + srcSize).coerceAtMost(source.width)
    val srcBottom = (srcTop + srcSize).coerceAtMost(source.height)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawCircle(outSize * 0.5f, outSize * 0.5f, outSize * 0.5f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(
        source,
        Rect(srcLeft, srcTop, srcRight, srcBottom),
        Rect(0, 0, outSize, outSize),
        paint
    )
    return out
}
