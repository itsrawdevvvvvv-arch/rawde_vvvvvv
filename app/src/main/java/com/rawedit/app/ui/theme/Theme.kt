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
    val BgPrimary = Color(0xFF0B0C0B)
    val CardSurface = Color(0xFF141614)
    val HeroSurface = Color(0xFF141614)
    val RaisedCard = Color(0xFF1B1E1B)
    val CodeSurface = Color(0xFF0E100E)
    val PillGrey = Color(0xFF242824)
    val DockBg = Color(0xE6161916)
    val DotGrid = Color(0xFF1F231F)

    val TextPrimary = Color(0xFFF7FAF6)
    val TextMuted = Color(0xFFB3BBB2)

    val Divider = Color(0x13FFFFFF)       // rgba(255,255,255,0.075)
    val DividerStrong = Color(0x24FFFFFF) // rgba(255,255,255,0.14)

    val Mint = Color(0xFF8CE3A8)
    val SuccessMint = Color(0xFF8CE3A8)
    val MintSoft = Color(0x1A8CE3A8)      // rgba(140,227,168,0.10)
    val MintBorder = Color(0x388CE3A8)    // rgba(140,227,168,0.22)
    val MintFocus = Color(0x808CE3A8)

    val PrimaryBtnBg = Color(0xFF8CE3A8)
    val PrimaryBtnText = Color(0xFF0B0C0B)

    val ErrorCoral = Color(0xFFFF8F80)
    val ErrorSoft = Color(0x14FF8F80)     // rgba(255,143,128,0.08)
    val ErrorBorder = Color(0x38FF8F80)   // rgba(255,143,128,0.22)
}

val InstrumentSerifFamily = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal)
)

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
        fontFamily = InstrumentSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 46.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.9).sp,
        color = RawColors.TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = InstrumentSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 50.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.0).sp,
        color = RawColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = InstrumentSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 29.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
        color = RawColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.5.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        color = RawColors.TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
        fontFeatureSettings = "tnum",
        color = RawColors.TextMuted
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.15.sp,
        fontFeatureSettings = "tnum",
        color = RawColors.TextMuted
    )
)

private val DarkScheme = darkColorScheme(
    primary = RawColors.Mint,
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

/**
 * Clean zero-overdraw surface wrapper matching the minimal Obsidian Forest card system.
 * (Removes heavy per-dot Canvas loops so Android 9+ GPUs remain completely lag-free.)
 */
@Composable
fun DotGridSurface(
    modifier: Modifier = Modifier,
    backgroundColor: Color = RawColors.BgPrimary,
    @Suppress("UNUSED_PARAMETER") dotColor: Color = RawColors.DotGrid,
    @Suppress("UNUSED_PARAMETER") spacing: Dp = 18.dp,
    @Suppress("UNUSED_PARAMETER") dotRadius: Dp = 1.4.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.background(backgroundColor)) {
        content()
    }
}
