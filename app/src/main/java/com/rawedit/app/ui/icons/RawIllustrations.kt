package com.rawedit.app.ui.icons

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.RawColors
import kotlin.math.sin

enum class IllustrationKind {
    ONBOARDING_VOICE,
    ONBOARDING_PROMPT,
    ONBOARDING_RENDER,
    HERO_TYPOGRAPHY,
    EMPTY_REELS
}

@Composable
fun rememberDeviceTilt(): Pair<Float, Float> {
    val context = LocalContext.current
    val animationsEnabled = rememberAnimationsEnabled()
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context, animationsEnabled) {
        if (!animationsEnabled) {
            return@DisposableEffect onDispose {}
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || accel == null) {
            return@DisposableEffect onDispose {}
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val nx = (event.values[0] / 9.81f).coerceIn(-0.6f, 0.6f)
                val ny = (event.values[1] / 9.81f).coerceIn(-0.6f, 0.6f)
                tiltX = tiltX * 0.85f + nx * 0.15f
                tiltY = tiltY * 0.85f + ny * 0.15f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sm.unregisterListener(listener)
        }
    }

    return tiltX to tiltY
}

/**
 * Original white line-art with #2A2A2A / #3A3A3A flat fills, stroke-by-stroke draw-in,
 * idle float, scroll parallax, and light device-tilt parallax.
 */
@Composable
fun RawLineArtIllustration(
    kind: IllustrationKind,
    modifier: Modifier = Modifier,
    scrollOffsetPx: Float = 0f,
    restartKey: Any = kind
) {
    val animEnabled = rememberAnimationsEnabled()
    val drawProgress = remember(restartKey) { Animatable(if (animEnabled) 0f else 1f) }
    val (tiltX, tiltY) = rememberDeviceTilt()

    LaunchedEffect(restartKey, animEnabled) {
        if (animEnabled) {
            drawProgress.snapTo(0f)
            drawProgress.animateTo(1f, tween(durationMillis = 720))
        } else {
            drawProgress.snapTo(1f)
        }
    }

    val infinite = rememberInfiniteTransition(label = "illust_idle")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "float_phase"
    )

    val floatY = if (animEnabled) sin(phase) * 6f else 0f
    val floatX = if (animEnabled) sin(phase * 0.5f) * 4f else 0f
    val pxOffsetX = tiltX * 14f + floatX
    val pxOffsetY = tiltY * 10f + floatY - (scrollOffsetPx * 0.08f).coerceIn(-20f, 20f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val prog = drawProgress.value
        val fillDark = RawColors.RaisedCard
        val fillMid = RawColors.PillGrey
        val strokeColor = RawColors.TextPrimary
        val mutedColor = RawColors.TextMuted
        val sw = (w * 0.009f).coerceIn(3.5f, 6f)

        when (kind) {
            IllustrationKind.ONBOARDING_VOICE -> {
                drawFloatingBezierAndNodes(w, h, prog, pxOffsetX * 0.5f, pxOffsetY * 0.5f, sw, strokeColor)
                // Waveform card
                withTransform({ translate(pxOffsetX, pxOffsetY) }) {
                    val cardLeft = w * 0.14f
                    val cardTop = h * 0.24f
                    val cardW = w * 0.52f
                    val cardH = h * 0.40f
                    if (prog > 0.2f) {
                        drawRoundRect(
                            color = fillDark,
                            topLeft = Offset(cardLeft, cardTop),
                            size = Size(cardW, cardH),
                            cornerRadius = CornerRadius(w * 0.06f)
                        )
                    }
                    val cardPath = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                cardLeft, cardTop, cardLeft + cardW, cardTop + cardH,
                                CornerRadius(w * 0.06f)
                            )
                        )
                    }
                    drawProgressivePath(cardPath, strokeColor, sw, prog)

                    val bars = listOf(0.28f, 0.58f, 0.85f, 0.45f, 0.72f, 0.90f, 0.38f, 0.62f)
                    bars.forEachIndexed { idx, amp ->
                        val bx = cardLeft + w * 0.08f + idx * (w * 0.048f)
                        val barH = cardH * 0.48f * amp * prog
                        val cy = cardTop + cardH * 0.50f
                        drawLine(
                            color = if (idx % 2 == 0) strokeColor else mutedColor,
                            start = Offset(bx, cy - barH * 0.5f),
                            end = Offset(bx, cy + barH * 0.5f),
                            strokeWidth = sw * 1.1f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                // Floating mic pill top right
                withTransform({ translate(-pxOffsetX * 0.8f, pxOffsetY * 1.2f) }) {
                    val mx = w * 0.62f
                    val my = h * 0.12f
                    val ms = w * 0.24f
                    if (prog > 0.25f) {
                        drawRoundRect(fillMid, Offset(mx, my), Size(ms, ms), CornerRadius(w * 0.06f))
                    }
                    val box = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                mx, my, mx + ms, my + ms,
                                CornerRadius(w * 0.06f)
                            )
                        )
                    }
                    drawProgressivePath(box, strokeColor, sw, prog)
                    // Mic inside
                    val micCap = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                mx + ms * 0.38f, my + ms * 0.22f,
                                mx + ms * 0.62f, my + ms * 0.58f,
                                CornerRadius(ms * 0.12f)
                            )
                        )
                    }
                    drawProgressivePath(micCap, strokeColor, sw * 0.9f, prog)
                    drawLine(
                        strokeColor,
                        Offset(mx + ms * 0.5f, my + ms * 0.62f),
                        Offset(mx + ms * 0.5f, my + ms * 0.78f),
                        sw * 0.9f,
                        StrokeCap.Round
                    )
                }
            }

            IllustrationKind.ONBOARDING_PROMPT -> {
                drawFloatingBezierAndNodes(w, h, prog, -pxOffsetX * 0.5f, pxOffsetY * 0.6f, sw, strokeColor)
                // Window card with text lines
                withTransform({ translate(pxOffsetX, pxOffsetY) }) {
                    val wx = w * 0.18f
                    val wy = h * 0.16f
                    val ww = w * 0.64f
                    val wh = h * 0.52f
                    if (prog > 0.2f) {
                        drawRoundRect(fillDark, Offset(wx, wy), Size(ww, wh), CornerRadius(w * 0.05f))
                        drawRoundRect(fillMid, Offset(wx, wy), Size(ww, wh * 0.22f), CornerRadius(w * 0.05f))
                    }
                    val frame = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                wx, wy, wx + ww, wy + wh,
                                CornerRadius(w * 0.05f)
                            )
                        )
                    }
                    drawProgressivePath(frame, strokeColor, sw, prog)
                    drawCircle(strokeColor, sw * 0.8f, Offset(wx + w * 0.06f, wy + wh * 0.11f))
                    drawCircle(mutedColor, sw * 0.8f, Offset(wx + w * 0.11f, wy + wh * 0.11f))
                    // Mono prompt lines
                    listOf(0.75f, 0.55f, 0.82f, 0.42f).forEachIndexed { i, len ->
                        val ly = wy + wh * (0.36f + i * 0.14f)
                        drawLine(
                            if (i == 0) strokeColor else mutedColor,
                            Offset(wx + w * 0.07f, ly),
                            Offset(wx + w * 0.07f + (ww - w * 0.14f) * len * prog, ly),
                            sw,
                            StrokeCap.Round
                        )
                    }
                }
                // Copy badge bottom right
                withTransform({ translate(-pxOffsetX * 0.7f, -pxOffsetY * 0.8f) }) {
                    val cx = w * 0.64f
                    val cy = h * 0.58f
                    val cw = w * 0.24f
                    val ch = h * 0.22f
                    if (prog > 0.3f) {
                        drawRoundRect(fillMid, Offset(cx, cy), Size(cw, ch), CornerRadius(w * 0.05f))
                    }
                    val badge = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                cx, cy, cx + cw, cy + ch,
                                CornerRadius(w * 0.05f)
                            )
                        )
                    }
                    drawProgressivePath(badge, strokeColor, sw, prog)
                    val tick = Path().apply {
                        moveTo(cx + cw * 0.30f, cy + ch * 0.52f)
                        lineTo(cx + cw * 0.46f, cy + ch * 0.68f)
                        lineTo(cx + cw * 0.72f, cy + ch * 0.34f)
                    }
                    drawProgressivePath(tick, RawColors.SuccessMint, sw, prog)
                }
            }

            IllustrationKind.ONBOARDING_RENDER -> {
                drawFloatingBezierAndNodes(w, h, prog, pxOffsetX * 0.4f, -pxOffsetY * 0.5f, sw, strokeColor)
                // Video stage frame with corner brackets and big play button
                withTransform({ translate(pxOffsetX, pxOffsetY) }) {
                    val sx = w * 0.16f
                    val sy = h * 0.18f
                    val swd = w * 0.68f
                    val sh = h * 0.46f
                    if (prog > 0.2f) {
                        drawRoundRect(fillDark, Offset(sx, sy), Size(swd, sh), CornerRadius(w * 0.05f))
                    }
                    val stagePath = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                sx, sy, sx + swd, sy + sh,
                                CornerRadius(w * 0.05f)
                            )
                        )
                    }
                    drawProgressivePath(stagePath, strokeColor, sw, prog)

                    // Play circle
                    val cx = sx + swd * 0.50f
                    val cy = sy + sh * 0.48f
                    val r = sh * 0.24f
                    if (prog > 0.35f) {
                        drawCircle(fillMid, r, Offset(cx, cy))
                    }
                    drawCircle(strokeColor, r, Offset(cx, cy), style = Stroke(sw))
                    val tri = Path().apply {
                        moveTo(cx - r * 0.28f, cy - r * 0.38f)
                        lineTo(cx + r * 0.45f, cy)
                        lineTo(cx - r * 0.28f, cy + r * 0.38f)
                        close()
                    }
                    drawProgressivePath(tri, strokeColor, sw * 0.9f, prog)

                    // Timeline strip below
                    val ty = sy + sh + h * 0.08f
                    drawRoundRect(fillMid, Offset(sx + w * 0.06f, ty), Size(swd - w * 0.12f, h * 0.06f), CornerRadius(h * 0.03f))
                    drawRoundRect(
                        strokeColor,
                        Offset(sx + w * 0.10f, ty),
                        Size((swd - w * 0.22f) * 0.62f * prog, h * 0.06f),
                        CornerRadius(h * 0.03f)
                    )
                }
            }

            IllustrationKind.HERO_TYPOGRAPHY -> {
                // Hero illustration echoing floating cards + pen curve + caption bar + play circle
                withTransform({ translate(pxOffsetX * 0.6f, pxOffsetY * 0.6f) }) {
                    // Tilted layered card
                    val p1 = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                w * 0.14f, h * 0.18f, w * 0.56f, h * 0.64f,
                                CornerRadius(w * 0.05f)
                            )
                        )
                    }
                    if (prog > 0.2f) {
                        drawRoundRect(fillMid, Offset(w * 0.14f, h * 0.18f), Size(w * 0.42f, h * 0.46f), CornerRadius(w * 0.05f))
                    }
                    drawProgressivePath(p1, strokeColor, sw, prog)

                    // Kinetic A / T glyph bars inside card
                    drawLine(strokeColor, Offset(w * 0.22f, h * 0.32f), Offset(w * 0.48f, h * 0.32f), sw * 1.4f, StrokeCap.Round)
                    drawLine(mutedColor, Offset(w * 0.22f, h * 0.44f), Offset(w * 0.42f, h * 0.44f), sw, StrokeCap.Round)
                    drawLine(mutedColor, Offset(w * 0.22f, h * 0.54f), Offset(w * 0.35f, h * 0.54f), sw, StrokeCap.Round)
                }

                withTransform({ translate(-pxOffsetX * 0.8f, pxOffsetY) }) {
                    // Floating waveform + caption pills on right
                    val rx = w * 0.46f
                    val ry = h * 0.38f
                    if (prog > 0.25f) {
                        drawRoundRect(fillDark, Offset(rx, ry), Size(w * 0.40f, h * 0.36f), CornerRadius(w * 0.05f))
                    }
                    val rPath = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                rx, ry, rx + w * 0.40f, ry + h * 0.36f,
                                CornerRadius(w * 0.05f)
                            )
                        )
                    }
                    drawProgressivePath(rPath, strokeColor, sw, prog)

                    // Caption bars
                    drawRoundRect(strokeColor, Offset(rx + w * 0.05f, ry + h * 0.09f), Size(w * 0.18f * prog, h * 0.055f), CornerRadius(h * 0.03f))
                    drawRoundRect(fillMid, Offset(rx + w * 0.05f, ry + h * 0.18f), Size(w * 0.28f * prog, h * 0.055f), CornerRadius(h * 0.03f))
                }
                drawFloatingBezierAndNodes(w, h, prog, pxOffsetX, -pxOffsetY * 0.6f, sw, strokeColor)
            }

            IllustrationKind.EMPTY_REELS -> {
                withTransform({ translate(pxOffsetX * 0.5f, pxOffsetY * 0.6f) }) {
                    val fx = w * 0.24f
                    val fy = h * 0.20f
                    val fw = w * 0.52f
                    val fh = h * 0.56f
                    drawRoundRect(fillDark, Offset(fx, fy), Size(fw, fh), CornerRadius(w * 0.05f))
                    val box = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                fx, fy, fx + fw, fy + fh,
                                CornerRadius(w * 0.05f)
                            )
                        )
                    }
                    drawProgressivePath(box, strokeColor, sw, prog)
                    // Corner brackets
                    val pad = w * 0.05f
                    drawCornerBracket(fx + pad, fy + pad, w * 0.05f, 1f, 1f, strokeColor, sw)
                    drawCornerBracket(fx + fw - pad, fy + pad, w * 0.05f, -1f, 1f, strokeColor, sw)
                    drawCornerBracket(fx + pad, fy + fh - pad, w * 0.05f, 1f, -1f, strokeColor, sw)
                    drawCornerBracket(fx + fw - pad, fy + fh - pad, w * 0.05f, -1f, -1f, strokeColor, sw)
                    // Small play triangle center
                    val cx = fx + fw * 0.5f
                    val cy = fy + fh * 0.5f
                    val r = fh * 0.16f
                    val tri = Path().apply {
                        moveTo(cx - r * 0.6f, cy - r * 0.8f)
                        lineTo(cx + r * 0.9f, cy)
                        lineTo(cx - r * 0.6f, cy + r * 0.8f)
                        close()
                    }
                    drawProgressivePath(tri, mutedColor, sw, prog)
                }
            }
        }
    }
}

private fun DrawScope.drawFloatingBezierAndNodes(
    w: Float,
    h: Float,
    prog: Float,
    ox: Float,
    oy: Float,
    sw: Float,
    strokeColor: Color
) {
    withTransform({ translate(ox, oy) }) {
        val curve = Path().apply {
            moveTo(w * 0.12f, h * 0.80f)
            cubicTo(w * 0.28f, h * 0.62f, w * 0.42f, h * 0.88f, w * 0.58f, h * 0.72f)
        }
        drawProgressivePath(curve, strokeColor, sw * 0.85f, prog)
        if (prog > 0.6f) {
            drawCircle(RawColors.BgPrimary, sw * 1.4f, Offset(w * 0.12f, h * 0.80f))
            drawCircle(strokeColor, sw * 1.4f, Offset(w * 0.12f, h * 0.80f), style = Stroke(sw * 0.8f))
            drawCircle(RawColors.BgPrimary, sw * 1.4f, Offset(w * 0.58f, h * 0.72f))
            drawCircle(strokeColor, sw * 1.4f, Offset(w * 0.58f, h * 0.72f), style = Stroke(sw * 0.8f))
            drawLine(RawColors.TextMuted, Offset(w * 0.20f, h * 0.72f), Offset(w * 0.36f, h * 0.58f), sw * 0.6f)
            drawRect(strokeColor, Offset(w * 0.34f, h * 0.56f), Size(sw * 1.8f, sw * 1.8f))
        }
    }
}

private fun DrawScope.drawProgressivePath(
    fullPath: Path,
    color: Color,
    strokeWidth: Float,
    progress: Float
) {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (p >= 0.999f) {
        drawPath(fullPath, color, style = stroke)
        return
    }
    val measure = PathMeasure()
    measure.setPath(fullPath, false)
    val segment = Path()
    measure.getSegment(0f, measure.length * p, segment, true)
    drawPath(segment, color, style = stroke)
}

private fun DrawScope.drawCornerBracket(
    x: Float,
    y: Float,
    len: Float,
    dirX: Float,
    dirY: Float,
    color: Color,
    sw: Float
) {
    drawLine(color, Offset(x, y), Offset(x + len * dirX, y), sw, StrokeCap.Round)
    drawLine(color, Offset(x, y), Offset(x, y + len * dirY), sw, StrokeCap.Round)
}
