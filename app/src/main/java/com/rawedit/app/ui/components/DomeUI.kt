package com.rawedit.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors

/**
 * Dome UI — the design vocabulary of the raw_edit v2 prototype:
 * radial-glow backdrop, mono eyebrows, oversized display headings with an
 * inline tan "tag", pill cards, shimmering pill buttons, chip rows and the
 * animated cream-and-tan dome mascot that sits above the floating nav.
 */

// ---------------------------------------------------------------- backdrop

@Composable
fun DomeBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF171512), Color(0xFF050506)),
                    center = Offset(0.5f * 1080f, 0.2f * 1920f),
                    radius = 1400f
                )
            )
            .drawBehind {
                // faint dot grid, 6% cream
                val step = 26.dp.toPx()
                val dot = 1.4.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    while (x < size.width) {
                        drawCircle(
                            color = Color(0x0FF3EFE6),
                            radius = dot,
                            center = Offset(x, y)
                        )
                        x += step
                    }
                    y += step
                }
            }
    ) {
        content()
    }
}

// ---------------------------------------------------------------- type

@Composable
fun DomeEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontFamily = JetBrainsMonoFamily,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.7.sp,
        color = RawColors.TextMuted,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

/** A display heading line: plain text, or text wrapped in the tan "tag" pill. */
sealed interface DomeHeadPart {
    data class Plain(val text: String) : DomeHeadPart
    data class Tag(val text: String) : DomeHeadPart
}

@Composable
fun DomeHeadline(
    parts: List<DomeHeadPart>,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    lineHeightFactor: Float = 1.02f,
    color: Color = RawColors.Cream,
    align: TextAlign = TextAlign.Start
) {
    val annotated = buildAnnotatedString {
        parts.forEach { part ->
            when (part) {
                is DomeHeadPart.Plain -> append(part.text)
                is DomeHeadPart.Tag -> {
                    withStyle(
                        SpanStyle(
                            color = RawColors.Ink,
                            background = RawColors.Tan,
                            fontWeight = FontWeight.Bold
                        )
                    ) { append(" ${part.text} ") }
                }
            }
        }
    }
    Text(
        text = annotated,
        fontFamily = BricolageFamily,
        fontSize = size.value.sp,
        lineHeight = (size.value * lineHeightFactor).sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.03f * size.value).sp,
        color = color,
        textAlign = align,
        modifier = modifier.padding(bottom = 14.dp)
    )
}

@Composable
fun DomeMuted(text: String, modifier: Modifier = Modifier, size: Int = 12) {
    Text(
        text = text,
        fontFamily = BricolageFamily,
        fontSize = size.sp,
        lineHeight = (size * 1.45f).sp,
        color = RawColors.TextMuted,
        modifier = modifier
    )
}

@Composable
fun DomeMono(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 11,
    color: Color = RawColors.TextMuted,
    weight: FontWeight = FontWeight.SemiBold
) {
    Text(
        text = text,
        fontFamily = JetBrainsMonoFamily,
        fontSize = size.sp,
        fontWeight = weight,
        color = color,
        modifier = modifier
    )
}

// ---------------------------------------------------------------- buttons

enum class DomeButtonKind { Primary, Light, Ghost, Danger }

@Composable
fun DomePillButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: DomeButtonKind = DomeButtonKind.Ghost,
    enabled: Boolean = true,
    icon: RawIconType? = null,
    trailingArrow: Boolean = false,
    onClick: () -> Unit
) {
    val animEnabled = rememberAnimationsEnabled()
    val brush: Brush? = when (kind) {
        DomeButtonKind.Primary -> RawColors.TanButtonBrush
        DomeButtonKind.Light -> RawColors.LightButtonBrush
        DomeButtonKind.Danger -> RawColors.DangerButtonBrush
        DomeButtonKind.Ghost -> null
    }
    val contentColor = when (kind) {
        DomeButtonKind.Primary, DomeButtonKind.Light, DomeButtonKind.Danger -> RawColors.Ink
        DomeButtonKind.Ghost -> RawColors.Cream
    }
    // shimmer sweep across filled buttons
    val shimmer = if (animEnabled && brush != null) {
        val transition = rememberInfiniteTransition(label = "pill_shimmer")
        val phase by transition.animateFloat(
            initialValue = -1.2f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pill_shimmer_phase"
        )
        phase
    } else {
        -2f
    }

    RawPressButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(CircleShape)
                .then(
                    if (brush != null) Modifier.background(brush)
                    else Modifier.background(RawColors.RaisedCard)
                )
                .border(
                    width = 1.dp,
                    color = when (kind) {
                        DomeButtonKind.Primary -> Color(0x47F3EFE6)
                        DomeButtonKind.Light -> Color(0x73FFFFFF)
                        DomeButtonKind.Ghost -> RawColors.Divider
                        DomeButtonKind.Danger -> Color(0x40FFFFFF)
                    },
                    shape = CircleShape
                )
                .drawBehind {
                    if (brush != null && shimmer > -1.2f) {
                        val bandW = size.width * 0.55f
                        val x = (shimmer * size.width) - bandW / 2f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x59FFFFFF),
                                    Color.Transparent
                                ),
                                startX = x,
                                endX = x + bandW
                            ),
                            topLeft = Offset(x, 0f),
                            size = Size(bandW, size.height)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    RawIcon(type = icon, size = 17.dp, tint = contentColor)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    fontFamily = BricolageFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.45f)
                )
                if (trailingArrow) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "→",
                        fontFamily = BricolageFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (enabled) contentColor else contentColor.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- chips

@Composable
fun DomeChip(
    text: String,
    active: Boolean = false,
    icon: RawIconType? = null,
    onClick: (() -> Unit)? = null
) {
    val bg = if (active) RawColors.TanButtonBrush else null
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .then(if (bg != null) Modifier.background(bg) else Modifier.background(RawColors.CardSurface))
            .border(1.dp, if (active) Color(0x40F3EFE6) else RawColors.Divider, CircleShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                RawIcon(
                    type = icon,
                    size = 13.dp,
                    tint = if (active) RawColors.Ink else RawColors.TextMuted,
                    strokeWidth = 1.8.dp
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) RawColors.Ink else RawColors.TextMuted
            )
        }
    }
}

@Composable
fun DomeChipRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}

// ---------------------------------------------------------------- cards

@Composable
fun DomePillCard(
    modifier: Modifier = Modifier,
    tan: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    trailingIcon: RawIconType? = null,
    onClick: (() -> Unit)? = null
) {
    val bg = if (tan) RawColors.TanButtonBrush else null
    val titleColor = if (tan) RawColors.Ink else RawColors.Cream
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(CircleShape)
            .then(if (bg != null) Modifier.background(bg) else Modifier.background(RawColors.CardSurface))
            .border(1.dp, if (tan) Color(0x40F3EFE6) else RawColors.Divider, CircleShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = BricolageFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.5.sp,
                        color = if (tan) RawColors.Ink.copy(alpha = 0.7f) else RawColors.TextMuted
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = trailing,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tan) RawColors.Ink.copy(alpha = 0.75f) else RawColors.TextMuted
                )
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                RawIcon(
                    type = trailingIcon,
                    size = 16.dp,
                    tint = if (tan) RawColors.Ink else RawColors.TextMuted
                )
            }
        }
    }
}

@Composable
fun DomeCard(
    modifier: Modifier = Modifier,
    tan: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (tan) {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(Color(0x33C89F72), Color(0xF5171718))
                        )
                    )
                } else {
                    Modifier.background(RawColors.CardSurface)
                }
            )
            .border(
                1.dp,
                if (tan) Color(0x66C89F72) else RawColors.Divider,
                RoundedCornerShape(24.dp)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) { content() }
}

// ---------------------------------------------------------------- dome mascot

/**
 * The dome: a cream-topped tan hemisphere with blinking eyes, blush cheeks and
 * a mouth that opens when the screen is happy. Idle bob + orbiting sparks.
 */
@Composable
fun DomeMascot(
    speech: String?,
    modifier: Modifier = Modifier,
    happy: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val animEnabled = rememberAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "dome_idle")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animEnabled) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dome_bob"
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animEnabled) 0.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dome_blink"
    )
    val spark by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animEnabled) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dome_spark"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (speech != null) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(CircleShape)
                        .background(RawColors.Cream)
                        .padding(horizontal = 13.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = speech,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = RawColors.Ink
                    )
                }
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 386.dp)
                    .fillMaxWidth()
                    .height(150.dp)
                    .scale(scaleX = 1f, scaleY = 1f + (bob * 0.02f))
                    .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
                contentAlignment = Alignment.TopCenter
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val bodyPath = Path().apply {
                        moveTo(w * 0.04f, h)
                        cubicTo(
                            w * 0.04f, h * 0.42f,
                            w * 0.26f, h * 0.02f,
                            w * 0.5f, h * 0.02f
                        )
                        cubicTo(
                            w * 0.74f, h * 0.02f,
                            w * 0.96f, h * 0.42f,
                            w * 0.96f, h
                        )
                        close()
                    }
                    drawPath(
                        path = bodyPath,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFDAB68B),
                                RawColors.Tan,
                                Color(0xFFA98055)
                            ),
                            center = Offset(w * 0.35f, h * 0.22f),
                            radius = w * 0.75f
                        )
                    )
                    drawPath(
                        path = bodyPath,
                        color = RawColors.Cream.copy(alpha = 0.9f),
                        style = Stroke(width = 2.4f)
                    )
                    // shine
                    drawOval(
                        color = Color(0x8CF3EFE6),
                        topLeft = Offset(w * 0.66f, h * 0.10f),
                        size = Size(w * 0.09f, h * 0.045f)
                    )
                    // eyes
                    val eyeW = w * 0.045f
                    val eyeH = h * 0.11f * blink.coerceAtLeast(0.12f)
                    val eyeY = h * 0.30f
                    listOf(w * 0.40f, w * 0.60f).forEach { cx ->
                        drawRoundRect(
                            color = RawColors.Ink,
                            topLeft = Offset(cx - eyeW / 2f, eyeY),
                            size = Size(eyeW, eyeH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(eyeW * 0.35f)
                        )
                        drawRoundRect(
                            color = RawColors.Cream,
                            topLeft = Offset(cx - eyeW / 2f + eyeW * 0.18f, eyeY + eyeH * 0.15f),
                            size = Size(eyeW * 0.33f, eyeH * 0.3f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(eyeW * 0.12f)
                        )
                    }
                    // cheeks
                    listOf(w * 0.33f, w * 0.67f).forEach { cx ->
                        drawOval(
                            color = Color(0xA6D97B66),
                            topLeft = Offset(cx - w * 0.022f, eyeY + eyeH + h * 0.03f),
                            size = Size(w * 0.044f, h * 0.035f)
                        )
                    }
                    // mouth
                    val mouthY = eyeY + eyeH + h * 0.09f
                    if (happy) {
                        drawRoundRect(
                            color = RawColors.Ink,
                            topLeft = Offset(w * 0.5f - w * 0.026f, mouthY),
                            size = Size(w * 0.052f, h * 0.06f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.012f)
                        )
                    } else {
                        val mouth = Path().apply {
                            moveTo(w * 0.5f - w * 0.024f, mouthY)
                            quadraticBezierTo(
                                w * 0.5f, mouthY + h * 0.05f,
                                w * 0.5f + w * 0.024f, mouthY
                            )
                        }
                        drawPath(
                            path = mouth,
                            color = RawColors.Ink,
                            style = Stroke(width = 3f, cap = StrokeCap.Round)
                        )
                    }
                    // orbiting sparks
                    val sparkAlpha = 0.35f + spark * 0.65f
                    drawRoundRect(
                        color = RawColors.Cream.copy(alpha = sparkAlpha),
                        topLeft = Offset(w * 0.12f, h * 0.18f - spark * 10f),
                        size = Size(7f, 7f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                    )
                    drawRoundRect(
                        color = RawColors.Cream.copy(alpha = 0.35f + (1f - spark) * 0.65f),
                        topLeft = Offset(w * 0.84f, h * 0.24f - (1f - spark) * 10f),
                        size = Size(5f, 5f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f)
                    )
                }
            }
        }
    }
}

/** Staggered entrance used by every screen body, mirroring the prototype's .stagger. */
@Composable
fun StaggerIn(index: Int, content: @Composable () -> Unit) {
    val animEnabled = rememberAnimationsEnabled()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(index) {
        if (animEnabled) {
            delay((index * 45).toLong())
            progress.animateTo(1f, animationSpec = tween(520))
        } else {
            progress.snapTo(1f)
        }
    }
    val v = progress.value
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = v
                translationY = (1f - v) * 20f
                scaleX = 0.95f + v * 0.05f
                scaleY = 0.95f + v * 0.05f
            }
    ) { content() }
}
