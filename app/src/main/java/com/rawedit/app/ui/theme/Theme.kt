package com.rawedit.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
    val BgPrimary = Color(0xFF0D0D0F)
    val CardSurface = Color(0xFF171718)
    val HeroSurface = Color(0xFF171718)
    val RaisedCard = Color(0xFF202024)
    val CodeSurface = Color(0xFF121215)
    val PillGrey = Color(0xFF202024)
    val DockBg = Color(0xEB0D0D0F)

    val Cream = Color(0xFFF3EFE6)
    val Tan = Color(0xFFC89F72)
    val TanLight = Color(0xFFE2BC91)
    val TanDeep = Color(0xFFAD8255)
    val Ink = Color(0xFF0D0D0F)

    val TextPrimary = Color(0xFFF3EFE6)
    val TextMuted = Color(0xFF8E8A84)

    val Divider = Color(0x1AF3EFE6)       // rgba(243,239,230,0.10)
    val DividerStrong = Color(0x33F3EFE6) // rgba(243,239,230,0.20)

    val AccentSoft = Color(0x24C89F72)    // rgba(200,159,114,0.14)
    val AccentBorder = Color(0x66C89F72)  // rgba(200,159,114,0.40)
    val AccentFocus = Color(0x8CC89F72)

    val PrimaryBtnBg = Color(0xFFF3EFE6)
    val PrimaryBtnText = Color(0xFF0D0D0F)

    val Success = Color(0xFF4E8B63)
    val Danger = Color(0xFFD9534F)
    val Blush = Color(0xFFD97B66)

    val ErrorCoral = Color(0xFFD9534F)
    val ErrorSoft = Color(0x1FD9534F)     // rgba(217,83,79,0.12)
    val ErrorBorder = Color(0x4DD9534F)   // rgba(217,83,79,0.30)

    val TanButtonBrush: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFFE2BC91), Color(0xFFC89F72), Color(0xFFAD8255))
    )
    val LightButtonBrush: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF3EFE6), Color(0xFFDED7C8))
    )
    val SuccessButtonBrush: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFF5BA876), Color(0xFF3E7B53), Color(0xFF2D5C3D))
    )
    val DangerButtonBrush: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFFE86B67), Color(0xFFC94440), Color(0xFF9C2F2B))
    )
    val TopInnerHighlight = Color(0x61FFFFFF) // rgba(255,255,255,0.38)
}

val BricolageFamily = FontFamily(
    Font(R.font.bricolage_grotesque_regular, FontWeight.Normal),
    Font(R.font.bricolage_grotesque_medium, FontWeight.Medium),
    Font(R.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold)
)

val BricolageCondensedFamily = FontFamily(
    Font(R.font.bricolage_grotesque_condensed_medium, FontWeight.Medium),
    Font(R.font.bricolage_grotesque_condensed_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_condensed_extrabold, FontWeight.ExtraBold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

val RawTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.8).sp,
        color = RawColors.TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1.5).sp,
        color = RawColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.9).sp,
        color = RawColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp,
        color = RawColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.15).sp,
        color = RawColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = "tnum",
        color = RawColors.TextMuted
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.15.sp,
        fontFeatureSettings = "tnum",
        color = RawColors.TextMuted
    )
)

private val DarkScheme = darkColorScheme(
    primary = RawColors.Cream,
    onPrimary = RawColors.PrimaryBtnText,
    secondary = RawColors.Tan,
    onSecondary = RawColors.Ink,
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
    backgroundColor: Color = RawColors.BgPrimary,
    @Suppress("UNUSED_PARAMETER") dotColor: Color = RawColors.RaisedCard,
    @Suppress("UNUSED_PARAMETER") spacing: Dp = 18.dp,
    @Suppress("UNUSED_PARAMETER") dotRadius: Dp = 1.4.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.background(backgroundColor)) {
        content()
    }
}
