package com.rawedit.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.R

object RawColors {
    val BgPrimary = Color(0xFF0F0F0F)
    val HeroSurface = Color(0xFF1C1C1C)
    val DotGrid = Color(0xFF2C2C2C)
    val CardSurface = Color(0xFF171717)
    val RaisedCard = Color(0xFF2A2A2A)
    val PillGrey = Color(0xFF3A3A3A)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF9B9B9B)
    val Divider = Color(0xFF333333)
    val ErrorCoral = Color(0xFFFF6B6B)
    val SuccessMint = Color(0xFF7BE0A0)
    val PrimaryBtnBg = Color(0xFFFFFFFF)
    val PrimaryBtnText = Color(0xFF111111)
}

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

val RawTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp,
        color = RawColors.TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
        color = RawColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = RawColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = RawColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = RawColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = RawColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = RawColors.TextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = RawColors.TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = RawColors.TextMuted
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = RawColors.TextMuted
    )
)

private val DarkScheme = darkColorScheme(
    primary = RawColors.PrimaryBtnBg,
    onPrimary = RawColors.PrimaryBtnText,
    secondary = RawColors.PillGrey,
    onSecondary = RawColors.TextPrimary,
    background = RawColors.BgPrimary,
    onBackground = RawColors.TextPrimary,
    surface = RawColors.CardSurface,
    onSurface = RawColors.TextPrimary,
    surfaceVariant = RawColors.RaisedCard,
    onSurfaceVariant = RawColors.TextMuted,
    outline = RawColors.Divider,
    error = RawColors.ErrorCoral,
    onError = RawColors.PrimaryBtnText
)

@Composable
fun RawEditTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = RawTypography,
        content = content
    )
}

@Composable
fun DotGridSurface(
    modifier: Modifier = Modifier,
    backgroundColor: Color = RawColors.HeroSurface,
    dotColor: Color = RawColors.DotGrid,
    spacing: Dp = 18.dp,
    dotRadius: Dp = 1.4.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.background(backgroundColor)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stepPx = spacing.toPx()
            val radiusPx = dotRadius.toPx()
            if (stepPx > 0f) {
                var y = stepPx * 0.5f
                while (y < size.height) {
                    var x = stepPx * 0.5f
                    while (x < size.width) {
                        drawCircle(
                            color = dotColor,
                            radius = radiusPx,
                            center = Offset(x, y)
                        )
                        x += stepPx
                    }
                    y += stepPx
                }
            }
        }
        content()
    }
}
