package com.rawedit.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val CardSurface = Color(0xFF17171A)
    val HeroSurface = Color(0xFF17171A)
    val RaisedCard = Color(0xFF202023)
    val CodeSurface = Color(0xFF121215)
    val PillGrey = Color(0xFF2C2B2D)
    val DockBg = Color(0xDB202023)

    val Cream = Color(0xFFF7ECE6)
    val Tan = Color(0xFFCAA07D)
    val Ink = Color(0xFF0D0D0F)

    val TextPrimary = Color(0xFFF7ECE6)
    val TextMuted = Color(0xFFA99D95)

    val Divider = Color(0x14F7ECE6)       // rgba(247,236,230,0.08)
    val DividerStrong = Color(0x26F7ECE6) // rgba(247,236,230,0.15)

    val AccentSoft = Color(0x24CAA07D)    // rgba(202,160,125,0.14)
    val AccentBorder = Color(0x61CAA07D)  // rgba(202,160,125,0.38)
    val AccentFocus = Color(0x80CAA07D)

    val PrimaryBtnBg = Color(0xFFF7ECE6)
    val PrimaryBtnText = Color(0xFF0D0D0F)

    val ErrorCoral = Color(0xFFFF8F80)
    val ErrorSoft = Color(0x1FFF8F80)     // rgba(255,143,128,0.12)
    val ErrorBorder = Color(0x40FF8F80)   // rgba(255,143,128,0.25)
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
        fontSize = 44.sp,
        lineHeight = 45.sp,
        letterSpacing = (-1.4).sp,
        color = RawColors.TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.3).sp,
        color = RawColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.8).sp,
        color = RawColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
        color = RawColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
        color = RawColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 22.5.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = "tnum",
        color = RawColors.TextMuted
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.1.sp,
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

/**
 * Clean zero-overdraw surface wrapper matching the Wallet palette system.
 */
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
