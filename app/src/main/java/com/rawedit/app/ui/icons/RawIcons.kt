package com.rawedit.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
    CHEVRON_RIGHT,
    CHEVRON_LEFT,
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
    ZIP,
    CODE,
    LENS,
    FILE,
    SRT,
    MORE_VERT,
    INFO,
    REFRESH,
    WARN,
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

// Pre-allocated immutable 24x24 paths so Canvas draws trigger ZERO per-frame Path allocations.
private val PATH_HOME = Path().apply {
    moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f)
    moveTo(6f, 9f); lineTo(6f, 21f); lineTo(18f, 21f); lineTo(18f, 9f)
    moveTo(10f, 21f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f)
}

private val PATH_FILM = Path().apply {
    addRect(Rect(4f, 4f, 20f, 20f))
    moveTo(8f, 4f); lineTo(8f, 20f)
    moveTo(16f, 4f); lineTo(16f, 20f)
    moveTo(4f, 9f); lineTo(8f, 9f)
    moveTo(4f, 15f); lineTo(8f, 15f)
    moveTo(16f, 9f); lineTo(20f, 9f)
    moveTo(16f, 15f); lineTo(20f, 15f)
}

private val PATH_PERSON = Path().apply {
    addRect(Rect(9f, 4f, 15f, 10f))
    moveTo(4f, 21f); lineTo(4f, 16f); lineTo(20f, 16f); lineTo(20f, 21f)
}

private val PATH_PLAY_FILL = Path().apply {
    moveTo(7f, 4f); lineTo(7f, 20f); lineTo(20f, 12f); close()
}

private val PATH_PAUSE_FILL = Path().apply {
    addRect(Rect(6.5f, 4f, 10.2f, 20f))
    addRect(Rect(13.8f, 4f, 17.5f, 20f))
}

private val PATH_CHECK = Path().apply {
    moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f)
}

private val PATH_COPY = Path().apply {
    addRect(Rect(8f, 8f, 20f, 20f))
    moveTo(4f, 16f); lineTo(4f, 4f); lineTo(16f, 4f)
}

private val PATH_PASTE = Path().apply {
    addRect(Rect(8f, 3.5f, 16f, 7f))
    moveTo(6f, 5f); lineTo(4f, 5f); lineTo(4f, 21f); lineTo(20f, 21f); lineTo(20f, 5f); lineTo(18f, 5f)
    moveTo(8f, 12f); lineTo(16f, 12f)
    moveTo(8f, 16f); lineTo(13f, 16f)
}

private val PATH_ARROW_RIGHT = Path().apply {
    moveTo(4f, 12f); lineTo(20f, 12f)
    moveTo(13f, 5f); lineTo(20f, 12f); lineTo(13f, 19f)
}

private val PATH_BACK = Path().apply {
    moveTo(20f, 12f); lineTo(5f, 12f)
    moveTo(11f, 5f); lineTo(4f, 12f); lineTo(11f, 19f)
}

private val PATH_CHEVRON_RIGHT = Path().apply {
    moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
}

private val PATH_CHEVRON_LEFT = Path().apply {
    moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
}

private val PATH_IMAGE = Path().apply {
    addRect(Rect(3f, 4f, 21f, 20f))
    moveTo(3f, 16f); lineTo(8f, 11f); lineTo(12f, 15f); lineTo(15f, 12f); lineTo(21f, 18f)
    addRect(Rect(15f, 8f, 17f, 10f))
}

private val PATH_LAYERS = Path().apply {
    moveTo(12f, 3.5f); lineTo(20.5f, 8f); lineTo(12f, 12.5f); lineTo(3.5f, 8f); close()
    moveTo(3.5f, 12.5f); lineTo(12f, 17f); lineTo(20.5f, 12.5f)
    moveTo(3.5f, 16.5f); lineTo(12f, 21f); lineTo(20.5f, 16.5f)
}

private val PATH_TRASH = Path().apply {
    moveTo(4f, 6f); lineTo(20f, 6f)
    addRect(Rect(9f, 3f, 15f, 6f))
    moveTo(6f, 6f); lineTo(6f, 21f); lineTo(18f, 21f); lineTo(18f, 6f)
    moveTo(10f, 10f); lineTo(10f, 17f)
    moveTo(14f, 10f); lineTo(14f, 17f)
}

private val PATH_EYE = Path().apply {
    moveTo(3f, 12f); lineTo(8f, 7f); lineTo(16f, 7f); lineTo(21f, 12f)
    lineTo(16f, 17f); lineTo(8f, 17f); close()
    addRect(Rect(10f, 10f, 14f, 14f))
}

private val PATH_EYE_OFF = Path().apply {
    moveTo(3f, 12f); lineTo(8f, 7f); lineTo(16f, 7f); lineTo(21f, 12f)
    lineTo(16f, 17f); lineTo(8f, 17f); close()
    moveTo(4f, 20f); lineTo(20f, 4f)
}

private val PATH_LOCK = Path().apply {
    addRect(Rect(5f, 11f, 19f, 21f))
    moveTo(8f, 11f); lineTo(8f, 7f); cubicTo(8f, 3.8f, 16f, 3.8f, 16f, 7f); lineTo(16f, 11f)
}

private val PATH_UNLOCK = Path().apply {
    addRect(Rect(5f, 11f, 19f, 21f))
    moveTo(8f, 11f); lineTo(8f, 7f); cubicTo(8f, 3.8f, 15.5f, 3.8f, 16f, 6.2f)
}

private val PATH_SHARE = Path().apply {
    moveTo(12f, 3f); lineTo(12f, 15f)
    moveTo(6f, 9f); lineTo(12f, 3f); lineTo(18f, 9f)
    moveTo(5f, 14f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 14f)
}

private val PATH_DOWNLOAD = Path().apply {
    moveTo(12f, 3f); lineTo(12f, 15f)
    moveTo(6f, 10f); lineTo(12f, 16f); lineTo(18f, 10f)
    moveTo(4f, 20f); lineTo(20f, 20f)
}

private val PATH_EDIT = Path().apply {
    moveTo(4f, 20f); lineTo(5f, 15f); lineTo(16f, 4f); lineTo(20f, 8f); lineTo(9f, 19f); close()
    moveTo(13f, 7f); lineTo(17f, 11f)
}

private val PATH_MIC = Path().apply {
    addRect(Rect(9f, 3f, 15f, 13f))
    moveTo(5f, 11f); lineTo(5f, 15f); lineTo(19f, 15f); lineTo(19f, 11f)
    moveTo(12f, 15f); lineTo(12f, 21f)
    moveTo(8f, 21f); lineTo(16f, 21f)
}

private val PATH_ZIP = Path().apply {
    addRect(Rect(5f, 3f, 19f, 21f))
    moveTo(12f, 3f); lineTo(12f, 7f)
    moveTo(12f, 9.5f); lineTo(12f, 11.5f)
    moveTo(12f, 14f); lineTo(12f, 16f)
}

private val PATH_CODE = Path().apply {
    moveTo(8f, 6f); lineTo(2f, 12f); lineTo(8f, 18f)
    moveTo(16f, 6f); lineTo(22f, 12f); lineTo(16f, 18f)
    moveTo(14f, 4f); lineTo(10f, 20f)
}

private val PATH_LENS = Path().apply {
    moveTo(3f, 9f); lineTo(3f, 4f); lineTo(8f, 4f)
    moveTo(16f, 4f); lineTo(21f, 4f); lineTo(21f, 9f)
    moveTo(21f, 15f); lineTo(21f, 20f); lineTo(16f, 20f)
    moveTo(8f, 20f); lineTo(3f, 20f); lineTo(3f, 15f)
    addRect(Rect(9f, 9f, 15f, 15f))
}

private val PATH_FILE = Path().apply {
    moveTo(6f, 3f); lineTo(14f, 3f); lineTo(18f, 7f); lineTo(18f, 21f); lineTo(6f, 21f); close()
    moveTo(14f, 3f); lineTo(14f, 7f); lineTo(18f, 7f)
    moveTo(9f, 13f); lineTo(15f, 13f)
    moveTo(9f, 17f); lineTo(15f, 17f)
}

private val PATH_SRT = Path().apply {
    addRect(Rect(3f, 5f, 21f, 19f))
    moveTo(7f, 13f); lineTo(11f, 13f)
    moveTo(13f, 13f); lineTo(17f, 13f)
    moveTo(7f, 16f); lineTo(10f, 16f)
    moveTo(12f, 16f); lineTo(17f, 16f)
}

private val PATH_INFO = Path().apply {
    addRect(Rect(5f, 4f, 19f, 20f))
    moveTo(12f, 11f); lineTo(12f, 17f)
}

private val PATH_REFRESH = Path().apply {
    moveTo(20f, 12f)
    cubicTo(20f, 7.6f, 16.4f, 4f, 12f, 4f)
    cubicTo(7.6f, 4f, 4f, 7.6f, 4f, 12f)
    cubicTo(4f, 16.4f, 7.6f, 20f, 12f, 20f)
    cubicTo(14.5f, 20f, 16.7f, 18.8f, 18f, 17f)
    moveTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f)
}

private val PATH_WARN = Path().apply {
    moveTo(12f, 3f); lineTo(22f, 20f); lineTo(2f, 20f); close()
    moveTo(12f, 9.5f); lineTo(12f, 14.5f)
}

private val PATH_BOLT = Path().apply {
    moveTo(13.5f, 3f); lineTo(6f, 13f); lineTo(12f, 13f); lineTo(10f, 21f)
    lineTo(18f, 10.5f); lineTo(12f, 10.5f); close()
}

private val PATH_CAMERA = Path().apply {
    moveTo(3f, 7f); lineTo(8f, 7f); lineTo(10f, 4f); lineTo(14f, 4f)
    lineTo(16f, 7f); lineTo(21f, 7f); lineTo(21f, 20f); lineTo(3f, 20f); close()
    addRect(Rect(9f, 11f, 15f, 17f))
}

private val PATH_CLOSE = Path().apply {
    moveTo(5f, 5f); lineTo(19f, 19f)
    moveTo(19f, 5f); lineTo(5f, 19f)
}

private val PATH_PLUS = Path().apply {
    moveTo(12f, 4f); lineTo(12f, 20f)
    moveTo(4f, 12f); lineTo(20f, 12f)
}

private val PATH_CLOCK = Path().apply {
    addRect(Rect(4f, 4f, 20f, 20f))
    moveTo(12f, 8f); lineTo(12f, 12f); lineTo(16f, 14f)
}

private val PATH_UNDO = Path().apply {
    moveTo(5.5f, 9f); lineTo(15f, 9f); cubicTo(20f, 9f, 20f, 17.5f, 15f, 17.5f); lineTo(8f, 17.5f)
    moveTo(9f, 5.5f); lineTo(5f, 9f); lineTo(9f, 12.5f)
}

private val PATH_REDO = Path().apply {
    moveTo(18.5f, 9f); lineTo(9f, 9f); cubicTo(4f, 9f, 4f, 17.5f, 9f, 17.5f); lineTo(16f, 17.5f)
    moveTo(15f, 5.5f); lineTo(19f, 9f); lineTo(15f, 12.5f)
}

private val PATH_FORWARD = Path().apply {
    moveTo(12f, 5.5f); lineTo(12f, 18.5f)
    moveTo(6.5f, 11f); lineTo(12f, 5.5f); lineTo(17.5f, 11f)
}

private val PATH_BACKWARD = Path().apply {
    moveTo(12f, 5.5f); lineTo(12f, 18.5f)
    moveTo(6.5f, 13f); lineTo(12f, 18.5f); lineTo(17.5f, 13f)
}

private val PATH_TO_FRONT = Path().apply {
    moveTo(5f, 4.5f); lineTo(19f, 4.5f)
    moveTo(12f, 8f); lineTo(12f, 20f)
    moveTo(7f, 12.5f); lineTo(12f, 8f); lineTo(17f, 12.5f)
}

private val PATH_TO_BACK = Path().apply {
    moveTo(5f, 19.5f); lineTo(19f, 19.5f)
    moveTo(12f, 4f); lineTo(12f, 16f)
    moveTo(7f, 11.5f); lineTo(12f, 16f); lineTo(17f, 11.5f)
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
        val scale = this.size.minDimension / 24f
        if (scale <= 0f) return@Canvas
        val sw = strokeWidth.toPx() / scale
        val stroke = Stroke(width = sw, cap = StrokeCap.Square, join = StrokeJoin.Miter)

        withTransform({ scale(scale, scale) }) {
            when (type) {
                RawIconType.HOME -> drawPath(PATH_HOME, tint, style = stroke)
                RawIconType.PLUS -> drawPath(PATH_PLUS, tint, style = stroke)
                RawIconType.FILM -> drawPath(PATH_FILM, tint, style = stroke)
                RawIconType.PERSON -> drawPath(PATH_PERSON, tint, style = stroke)
                RawIconType.PLAY -> drawPath(PATH_PLAY_FILL, tint, style = Fill)
                RawIconType.PAUSE -> drawPath(PATH_PAUSE_FILL, tint, style = Fill)
                RawIconType.COPY -> drawPath(PATH_COPY, tint, style = stroke)
                RawIconType.PASTE -> drawPath(PATH_PASTE, tint, style = stroke)
                RawIconType.CHECK -> drawPath(PATH_CHECK, tint, style = stroke)
                RawIconType.ARROW_RIGHT -> drawPath(PATH_ARROW_RIGHT, tint, style = stroke)
                RawIconType.BACK -> drawPath(PATH_BACK, tint, style = stroke)
                RawIconType.CHEVRON_RIGHT -> drawPath(PATH_CHEVRON_RIGHT, tint, style = stroke)
                RawIconType.CHEVRON_LEFT -> drawPath(PATH_CHEVRON_LEFT, tint, style = stroke)
                RawIconType.IMAGE -> drawPath(PATH_IMAGE, tint, style = stroke)
                RawIconType.LAYERS -> drawPath(PATH_LAYERS, tint, style = stroke)
                RawIconType.TRASH -> drawPath(PATH_TRASH, tint, style = stroke)
                RawIconType.EYE -> drawPath(PATH_EYE, tint, style = stroke)
                RawIconType.EYE_OFF -> drawPath(PATH_EYE_OFF, tint, style = stroke)
                RawIconType.LOCK -> drawPath(PATH_LOCK, tint, style = stroke)
                RawIconType.UNLOCK -> drawPath(PATH_UNLOCK, tint, style = stroke)
                RawIconType.SHARE -> drawPath(PATH_SHARE, tint, style = stroke)
                RawIconType.DOWNLOAD -> drawPath(PATH_DOWNLOAD, tint, style = stroke)
                RawIconType.EDIT -> drawPath(PATH_EDIT, tint, style = stroke)
                RawIconType.MIC -> drawPath(PATH_MIC, tint, style = stroke)
                RawIconType.ZIP -> drawPath(PATH_ZIP, tint, style = stroke)
                RawIconType.CODE -> drawPath(PATH_CODE, tint, style = stroke)
                RawIconType.LENS -> drawPath(PATH_LENS, tint, style = stroke)
                RawIconType.FILE -> drawPath(PATH_FILE, tint, style = stroke)
                RawIconType.SRT -> drawPath(PATH_SRT, tint, style = stroke)
                RawIconType.MORE_VERT -> {
                    val r = 1.6f
                    drawRect(tint, Offset(12f - r, 5f - r), Size(r * 2f, r * 2f))
                    drawRect(tint, Offset(12f - r, 12f - r), Size(r * 2f, r * 2f))
                    drawRect(tint, Offset(12f - r, 19f - r), Size(r * 2f, r * 2f))
                }
                RawIconType.INFO -> {
                    drawPath(PATH_INFO, tint, style = stroke)
                    drawRect(tint, Offset(10.8f, 7f), Size(2.4f, 2.4f))
                }
                RawIconType.REFRESH -> drawPath(PATH_REFRESH, tint, style = stroke)
                RawIconType.WARN -> {
                    drawPath(PATH_WARN, tint, style = stroke)
                    drawRect(tint, Offset(10.8f, 16.5f), Size(2.4f, 2.4f))
                }
                RawIconType.CLOCK -> drawPath(PATH_CLOCK, tint, style = stroke)
                RawIconType.BOLT -> drawPath(PATH_BOLT, tint, style = stroke)
                RawIconType.CAMERA -> drawPath(PATH_CAMERA, tint, style = stroke)
                RawIconType.CLOSE -> drawPath(PATH_CLOSE, tint, style = stroke)
                RawIconType.UNDO -> drawPath(PATH_UNDO, tint, style = stroke)
                RawIconType.REDO -> drawPath(PATH_REDO, tint, style = stroke)
                RawIconType.FORWARD -> drawPath(PATH_FORWARD, tint, style = stroke)
                RawIconType.BACKWARD -> drawPath(PATH_BACKWARD, tint, style = stroke)
                RawIconType.TO_FRONT -> drawPath(PATH_TO_FRONT, tint, style = stroke)
                RawIconType.TO_BACK -> drawPath(PATH_TO_BACK, tint, style = stroke)
                RawIconType.DUPLICATE -> drawPath(PATH_COPY, tint, style = stroke)
            }
        }
    }
}
