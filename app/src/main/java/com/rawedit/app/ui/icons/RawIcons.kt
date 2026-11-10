package com.rawedit.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rawedit.app.ui.theme.RawColors

enum class RawIconType {
    HOME,
    PLUS,
    FILM,
    PERSON,
    PLAY,
    PAUSE,
    COPY,
    PASTE,
    CHECK,
    ARROW_RIGHT,
    BACK,
    IMAGE,
    LAYERS,
    TRASH,
    EYE,
    EYE_OFF,
    LOCK,
    UNLOCK,
    SHARE,
    DOWNLOAD,
    EDIT,
    MIC,
    CLOCK,
    BOLT,
    CAMERA,
    CLOSE,
    UNDO,
    REDO,
    FORWARD,
    BACKWARD,
    TO_FRONT,
    TO_BACK,
    DUPLICATE
}

@Composable
fun RawIcon(
    type: RawIconType,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = RawColors.TextPrimary,
    strokeWidth: Dp = 2.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = strokeWidth.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (type) {
            RawIconType.HOME -> {
                val p = Path().apply {
                    moveTo(w * 0.14f, h * 0.45f)
                    lineTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.86f, h * 0.45f)
                    lineTo(w * 0.86f, h * 0.84f)
                    quadraticTo(w * 0.86f, h * 0.88f, w * 0.81f, h * 0.88f)
                    lineTo(w * 0.19f, h * 0.88f)
                    quadraticTo(w * 0.14f, h * 0.88f, w * 0.14f, h * 0.84f)
                    close()
                }
                drawPath(p, tint, style = stroke)
                drawLine(tint, Offset(w * 0.41f, h * 0.88f), Offset(w * 0.41f, h * 0.58f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.59f, h * 0.88f), Offset(w * 0.59f, h * 0.58f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.41f, h * 0.58f), Offset(w * 0.59f, h * 0.58f), sw, StrokeCap.Round)
            }

            RawIconType.PLUS -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), sw, StrokeCap.Round)
            }

            RawIconType.FILM -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.14f, h * 0.18f),
                    size = Size(w * 0.72f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.12f),
                    style = stroke
                )
                drawLine(tint, Offset(w * 0.32f, h * 0.18f), Offset(w * 0.32f, h * 0.82f), sw)
                drawLine(tint, Offset(w * 0.68f, h * 0.18f), Offset(w * 0.68f, h * 0.82f), sw)
                drawLine(tint, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.32f, h * 0.50f), sw)
                drawLine(tint, Offset(w * 0.68f, h * 0.50f), Offset(w * 0.86f, h * 0.50f), sw)
            }

            RawIconType.PERSON -> {
                drawCircle(tint, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.33f), style = stroke)
                val arc = Path().apply {
                    moveTo(w * 0.20f, h * 0.84f)
                    cubicTo(w * 0.20f, h * 0.60f, w * 0.80f, h * 0.60f, w * 0.80f, h * 0.84f)
                }
                drawPath(arc, tint, style = stroke)
            }

            RawIconType.PLAY -> {
                val p = Path().apply {
                    moveTo(w * 0.30f, h * 0.20f)
                    lineTo(w * 0.80f, h * 0.50f)
                    lineTo(w * 0.30f, h * 0.80f)
                    close()
                }
                drawPath(p, tint, style = stroke)
            }

            RawIconType.PAUSE -> {
                drawLine(tint, Offset(w * 0.35f, h * 0.22f), Offset(w * 0.35f, h * 0.78f), sw * 1.3f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.65f, h * 0.22f), Offset(w * 0.65f, h * 0.78f), sw * 1.3f, StrokeCap.Round)
            }

            RawIconType.COPY -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.34f, h * 0.34f),
                    size = Size(w * 0.50f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.10f),
                    style = stroke
                )
                val back = Path().apply {
                    moveTo(w * 0.64f, h * 0.26f)
                    lineTo(w * 0.64f, h * 0.20f)
                    quadraticTo(w * 0.64f, h * 0.15f, w * 0.59f, h * 0.15f)
                    lineTo(w * 0.22f, h * 0.15f)
                    quadraticTo(w * 0.17f, h * 0.15f, w * 0.17f, h * 0.20f)
                    lineTo(w * 0.17f, h * 0.62f)
                    quadraticTo(w * 0.17f, h * 0.67f, w * 0.22f, h * 0.67f)
                    lineTo(w * 0.28f, h * 0.67f)
                }
                drawPath(back, tint, style = stroke)
            }

            RawIconType.PASTE -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.22f, h * 0.22f),
                    size = Size(w * 0.56f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.10f),
                    style = stroke
                )
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.36f, h * 0.13f),
                    size = Size(w * 0.28f, h * 0.14f),
                    cornerRadius = CornerRadius(w * 0.05f),
                    style = stroke
                )
                drawLine(tint, Offset(w * 0.34f, h * 0.48f), Offset(w * 0.66f, h * 0.48f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.64f), Offset(w * 0.56f, h * 0.64f), sw, StrokeCap.Round)
            }

            RawIconType.CHECK -> {
                val p = Path().apply {
                    moveTo(w * 0.20f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.74f)
                    lineTo(w * 0.80f, h * 0.28f)
                }
                drawPath(p, tint, style = stroke)
            }

            RawIconType.ARROW_RIGHT -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), sw, StrokeCap.Round)
                val head = Path().apply {
                    moveTo(w * 0.54f, h * 0.26f)
                    lineTo(w * 0.80f, h * 0.50f)
                    lineTo(w * 0.54f, h * 0.74f)
                }
                drawPath(head, tint, style = stroke)
            }

            RawIconType.BACK -> {
                drawLine(tint, Offset(w * 0.78f, h * 0.50f), Offset(w * 0.22f, h * 0.50f), sw, StrokeCap.Round)
                val head = Path().apply {
                    moveTo(w * 0.46f, h * 0.26f)
                    lineTo(w * 0.22f, h * 0.50f)
                    lineTo(w * 0.46f, h * 0.74f)
                }
                drawPath(head, tint, style = stroke)
            }

            RawIconType.IMAGE -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.14f, h * 0.18f),
                    size = Size(w * 0.72f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.10f),
                    style = stroke
                )
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.34f, h * 0.38f), style = stroke)
                val mtn = Path().apply {
                    moveTo(w * 0.16f, h * 0.74f)
                    lineTo(w * 0.44f, h * 0.48f)
                    lineTo(w * 0.62f, h * 0.64f)
                    lineTo(w * 0.72f, h * 0.55f)
                    lineTo(w * 0.84f, h * 0.74f)
                }
                drawPath(mtn, tint, style = stroke)
            }

            RawIconType.LAYERS -> {
                val top = Path().apply {
                    moveTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.84f, h * 0.34f)
                    lineTo(w * 0.50f, h * 0.52f)
                    lineTo(w * 0.16f, h * 0.34f)
                    close()
                }
                val mid = Path().apply {
                    moveTo(w * 0.16f, h * 0.52f)
                    lineTo(w * 0.50f, h * 0.70f)
                    lineTo(w * 0.84f, h * 0.52f)
                }
                val bot = Path().apply {
                    moveTo(w * 0.16f, h * 0.68f)
                    lineTo(w * 0.50f, h * 0.86f)
                    lineTo(w * 0.84f, h * 0.68f)
                }
                drawPath(top, tint, style = stroke)
                drawPath(mid, tint, style = stroke)
                drawPath(bot, tint, style = stroke)
            }

            RawIconType.TRASH -> {
                drawLine(tint, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), sw, StrokeCap.Round)
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.38f, h * 0.15f),
                    size = Size(w * 0.24f, h * 0.13f),
                    cornerRadius = CornerRadius(w * 0.04f),
                    style = stroke
                )
                val body = Path().apply {
                    moveTo(w * 0.26f, h * 0.28f)
                    lineTo(w * 0.31f, h * 0.82f)
                    quadraticTo(w * 0.32f, h * 0.86f, w * 0.36f, h * 0.86f)
                    lineTo(w * 0.64f, h * 0.86f)
                    quadraticTo(w * 0.68f, h * 0.86f, w * 0.69f, h * 0.82f)
                    lineTo(w * 0.74f, h * 0.28f)
                }
                drawPath(body, tint, style = stroke)
                drawLine(tint, Offset(w * 0.43f, h * 0.42f), Offset(w * 0.44f, h * 0.72f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.57f, h * 0.42f), Offset(w * 0.56f, h * 0.72f), sw, StrokeCap.Round)
            }

            RawIconType.EYE -> {
                val eye = Path().apply {
                    moveTo(w * 0.12f, h * 0.50f)
                    quadraticTo(w * 0.50f, h * 0.16f, w * 0.88f, h * 0.50f)
                    quadraticTo(w * 0.50f, h * 0.84f, w * 0.12f, h * 0.50f)
                }
                drawPath(eye, tint, style = stroke)
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            }

            RawIconType.EYE_OFF -> {
                val eye = Path().apply {
                    moveTo(w * 0.14f, h * 0.50f)
                    quadraticTo(w * 0.50f, h * 0.20f, w * 0.86f, h * 0.50f)
                    quadraticTo(w * 0.50f, h * 0.80f, w * 0.14f, h * 0.50f)
                }
                drawPath(eye, tint, style = stroke)
                drawLine(tint, Offset(w * 0.18f, h * 0.82f), Offset(w * 0.82f, h * 0.18f), sw, StrokeCap.Round)
            }

            RawIconType.LOCK -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.22f, h * 0.44f),
                    size = Size(w * 0.56f, h * 0.42f),
                    cornerRadius = CornerRadius(w * 0.10f),
                    style = stroke
                )
                val shackle = Path().apply {
                    moveTo(w * 0.34f, h * 0.44f)
                    lineTo(w * 0.34f, h * 0.30f)
                    cubicTo(w * 0.34f, h * 0.14f, w * 0.66f, h * 0.14f, w * 0.66f, h * 0.30f)
                    lineTo(w * 0.66f, h * 0.44f)
                }
                drawPath(shackle, tint, style = stroke)
            }

            RawIconType.UNLOCK -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.22f, h * 0.44f),
                    size = Size(w * 0.56f, h * 0.42f),
                    cornerRadius = CornerRadius(w * 0.10f),
                    style = stroke
                )
                val shackle = Path().apply {
                    moveTo(w * 0.34f, h * 0.44f)
                    lineTo(w * 0.34f, h * 0.30f)
                    cubicTo(w * 0.34f, h * 0.14f, w * 0.64f, h * 0.14f, w * 0.66f, h * 0.26f)
                }
                drawPath(shackle, tint, style = stroke)
            }

            RawIconType.SHARE -> {
                drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.25f, h * 0.50f), style = stroke)
                drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.72f, h * 0.24f), style = stroke)
                drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.72f, h * 0.76f), style = stroke)
                drawLine(tint, Offset(w * 0.34f, h * 0.45f), Offset(w * 0.63f, h * 0.29f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.55f), Offset(w * 0.63f, h * 0.71f), sw, StrokeCap.Round)
            }

            RawIconType.DOWNLOAD -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.68f), sw, StrokeCap.Round)
                val arrow = Path().apply {
                    moveTo(w * 0.30f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.68f)
                    lineTo(w * 0.70f, h * 0.48f)
                }
                drawPath(arrow, tint, style = stroke)
                drawLine(tint, Offset(w * 0.20f, h * 0.84f), Offset(w * 0.80f, h * 0.84f), sw, StrokeCap.Round)
            }

            RawIconType.EDIT -> {
                val pencil = Path().apply {
                    moveTo(w * 0.18f, h * 0.82f)
                    lineTo(w * 0.24f, h * 0.58f)
                    lineTo(w * 0.66f, h * 0.16f)
                    lineTo(w * 0.84f, h * 0.34f)
                    lineTo(w * 0.42f, h * 0.76f)
                    close()
                }
                drawPath(pencil, tint, style = stroke)
            }

            RawIconType.MIC -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.37f, h * 0.14f),
                    size = Size(w * 0.26f, h * 0.44f),
                    cornerRadius = CornerRadius(w * 0.13f),
                    style = stroke
                )
                val arc = Path().apply {
                    moveTo(w * 0.24f, h * 0.48f)
                    cubicTo(w * 0.24f, h * 0.76f, w * 0.76f, h * 0.76f, w * 0.76f, h * 0.48f)
                }
                drawPath(arc, tint, style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.70f), Offset(w * 0.50f, h * 0.88f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.88f), Offset(w * 0.66f, h * 0.88f), sw, StrokeCap.Round)
            }

            RawIconType.CLOCK -> {
                drawCircle(tint, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.28f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.66f, h * 0.58f), sw, StrokeCap.Round)
            }

            RawIconType.BOLT -> {
                val p = Path().apply {
                    moveTo(w * 0.56f, h * 0.12f)
                    lineTo(w * 0.24f, h * 0.54f)
                    lineTo(w * 0.50f, h * 0.54f)
                    lineTo(w * 0.42f, h * 0.88f)
                    lineTo(w * 0.76f, h * 0.44f)
                    lineTo(w * 0.50f, h * 0.44f)
                    close()
                }
                drawPath(p, tint, style = stroke)
            }

            RawIconType.CAMERA -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.14f, h * 0.28f),
                    size = Size(w * 0.72f, h * 0.54f),
                    cornerRadius = CornerRadius(w * 0.10f),
                    style = stroke
                )
                drawCircle(tint, radius = w * 0.14f, center = Offset(w * 0.50f, h * 0.55f), style = stroke)
                val top = Path().apply {
                    moveTo(w * 0.34f, h * 0.28f)
                    lineTo(w * 0.40f, h * 0.18f)
                    lineTo(w * 0.60f, h * 0.18f)
                    lineTo(w * 0.66f, h * 0.28f)
                }
                drawPath(top, tint, style = stroke)
            }

            RawIconType.CLOSE -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), sw, StrokeCap.Round)
            }

            RawIconType.UNDO -> {
                val arc = Path().apply {
                    moveTo(w * 0.24f, h * 0.38f)
                    lineTo(w * 0.62f, h * 0.38f)
                    cubicTo(w * 0.84f, h * 0.38f, w * 0.84f, h * 0.74f, w * 0.62f, h * 0.74f)
                    lineTo(w * 0.34f, h * 0.74f)
                }
                drawPath(arc, tint, style = stroke)
                val head = Path().apply {
                    moveTo(w * 0.38f, h * 0.22f)
                    lineTo(w * 0.22f, h * 0.38f)
                    lineTo(w * 0.38f, h * 0.54f)
                }
                drawPath(head, tint, style = stroke)
            }

            RawIconType.REDO -> {
                val arc = Path().apply {
                    moveTo(w * 0.76f, h * 0.38f)
                    lineTo(w * 0.38f, h * 0.38f)
                    cubicTo(w * 0.16f, h * 0.38f, w * 0.16f, h * 0.74f, w * 0.38f, h * 0.74f)
                    lineTo(w * 0.66f, h * 0.74f)
                }
                drawPath(arc, tint, style = stroke)
                val head = Path().apply {
                    moveTo(w * 0.62f, h * 0.22f)
                    lineTo(w * 0.78f, h * 0.38f)
                    lineTo(w * 0.62f, h * 0.54f)
                }
                drawPath(head, tint, style = stroke)
            }

            RawIconType.FORWARD -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.24f), Offset(w * 0.50f, h * 0.78f), sw, StrokeCap.Round)
                val head = Path().apply {
                    moveTo(w * 0.28f, h * 0.46f)
                    lineTo(w * 0.50f, h * 0.24f)
                    lineTo(w * 0.72f, h * 0.46f)
                }
                drawPath(head, tint, style = stroke)
            }

            RawIconType.BACKWARD -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.22f), Offset(w * 0.50f, h * 0.76f), sw, StrokeCap.Round)
                val head = Path().apply {
                    moveTo(w * 0.28f, h * 0.54f)
                    lineTo(w * 0.50f, h * 0.76f)
                    lineTo(w * 0.72f, h * 0.54f)
                }
                drawPath(head, tint, style = stroke)
            }

            RawIconType.TO_FRONT -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.18f), Offset(w * 0.78f, h * 0.18f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.32f), Offset(w * 0.50f, h * 0.84f), sw, StrokeCap.Round)
                val head = Path().apply {
                    moveTo(w * 0.30f, h * 0.52f)
                    lineTo(w * 0.50f, h * 0.32f)
                    lineTo(w * 0.70f, h * 0.52f)
                }
                drawPath(head, tint, style = stroke)
            }

            RawIconType.TO_BACK -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.82f), Offset(w * 0.78f, h * 0.82f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.68f), sw, StrokeCap.Round)
                val head = Path().apply {
                    moveTo(w * 0.30f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.68f)
                    lineTo(w * 0.70f, h * 0.48f)
                }
                drawPath(head, tint, style = stroke)
            }

            RawIconType.DUPLICATE -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.34f, h * 0.34f),
                    size = Size(w * 0.50f, h * 0.50f),
                    cornerRadius = CornerRadius(w * 0.08f),
                    style = stroke
                )
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = Size(w * 0.42f, h * 0.42f),
                    cornerRadius = CornerRadius(w * 0.08f),
                    style = stroke
                )
            }
        }
    }
}
