package com.mutinymedia.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.mutinymedia.android.domain.ThemeFont
import com.mutinymedia.android.domain.ThemePreset

val DeepPurple = Color(0xFF100923)
val RaisedPurple = Color(0xFF221535)
val NeonPurple = Color(0xFFAD57F5)
val Mint = Color(0xFF40F4B9)
val Ink = Color(0xFFF3EDFF)
val MutedInk = Color(0xFFB5A9C8)

private val colors = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    secondary = Mint,
    onSecondary = DeepPurple,
    background = DeepPurple,
    onBackground = Ink,
    surface = RaisedPurple,
    onSurface = Ink,
    surfaceVariant = Color(0xFF312343),
    onSurfaceVariant = MutedInk,
    outline = Color(0xFF624973),
    error = Color(0xFFFF7F8F),
)

private fun alternatePalette(accent: Long, background: Long, surface: Long, variant: Long) =
    colors.copy(
        primary = Color(accent),
        onPrimary = Color(background),
        secondary = Color(accent),
        onSecondary = Color(background),
        primaryContainer = Color(variant),
        onPrimaryContainer = Ink,
        secondaryContainer = Color(variant),
        onSecondaryContainer = Ink,
        background = Color(background),
        surface = Color(surface),
        surfaceVariant = Color(variant),
        surfaceTint = Color(accent),
    )

private val ocean = alternatePalette(0xFF8EDCFF, 0xFF081827, 0xFF102539, 0xFF1C3D55)
private val forest = alternatePalette(0xFF91E7AD, 0xFF0B1A14, 0xFF142A20, 0xFF244438)
private val ember = alternatePalette(0xFFFFBF8C, 0xFF24120E, 0xFF381E18, 0xFF54332A)

private fun typographyWith(family: FontFamily): Typography {
    val defaults = Typography()
    return defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = family),
        displayMedium = defaults.displayMedium.copy(fontFamily = family),
        displaySmall = defaults.displaySmall.copy(fontFamily = family),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = family),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = family),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = family),
        titleLarge = defaults.titleLarge.copy(fontFamily = family),
        titleMedium = defaults.titleMedium.copy(fontFamily = family),
        titleSmall = defaults.titleSmall.copy(fontFamily = family),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = family),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = family),
        bodySmall = defaults.bodySmall.copy(fontFamily = family),
        labelLarge = defaults.labelLarge.copy(fontFamily = family),
        labelMedium = defaults.labelMedium.copy(fontFamily = family),
        labelSmall = defaults.labelSmall.copy(fontFamily = family),
    )
}

private val systemTypography = Typography()
private val serifTypography = typographyWith(FontFamily.Serif)
private val monospaceTypography = typographyWith(FontFamily.Monospace)

@Composable
fun MutinyTheme(
    preset: ThemePreset = ThemePreset.MUTINY,
    font: ThemeFont = ThemeFont.DEFAULT,
    content: @Composable () -> Unit,
) {
    val scheme = when (preset) {
        ThemePreset.MUTINY -> colors
        ThemePreset.OCEAN -> ocean
        ThemePreset.FOREST -> forest
        ThemePreset.EMBER -> ember
    }
    val typography = when (font) {
        ThemeFont.DEFAULT -> systemTypography
        ThemeFont.SERIF -> serifTypography
        ThemeFont.MONOSPACE -> monospaceTypography
    }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
