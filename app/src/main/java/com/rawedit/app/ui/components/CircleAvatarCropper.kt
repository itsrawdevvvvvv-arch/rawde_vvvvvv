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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    // Pre-allocate EvenOdd overlay Path per size change instead of allocating per frame
    var cachedSize by remember { mutableStateOf(IntSize.Zero) }
    var cachedPath by remember { mutableStateOf<Path?>(null) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RawColors.BgPrimary)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Crop photo", style = RawTypography.headlineMedium)
                Text("drag • zoom", style = RawTypography.labelMedium)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                userScale = (userScale * zoom).coerceIn(1f, 2.5f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val curIntSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                        val cropDiameter = min(size.width, size.height) * 0.85f
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

                        val r = cropDiameter * 0.5f
                        val centerX = size.width * 0.5f
                        val centerY = size.height * 0.5f
                        val scrim = if (cachedPath != null && cachedSize == curIntSize) {
                            cachedPath!!
                        } else {
                            Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(ComposeRect(0f, 0f, size.width, size.height))
                                addOval(ComposeRect(centerX - r, centerY - r, centerX + r, centerY + r))
                            }.also {
                                cachedSize = curIntSize
                                cachedPath = it
                            }
                        }
                        drawPath(scrim, Color.Black.copy(alpha = 0.65f))
                        drawCircle(
                            color = RawColors.Tan.copy(alpha = 0.6f),
                            radius = r,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Slider(
                    value = userScale,
                    onValueChange = { userScale = it },
                    valueRange = 1f..2.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = RawColors.Tan,
                        activeTrackColor = RawColors.Tan,
                        inactiveTrackColor = RawColors.PillGrey
                    ),
                    modifier = Modifier.fillMaxWidth(0.86f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawSecondaryButton(
                    text = "Cancel",
                    height = 60.dp,
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                )
                BigPrimaryButton(
                    text = "Use Photo",
                    height = 60.dp,
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
                )
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
    val cropDiameter = min(viewW, viewH) * 0.85f
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
