package com.mutinymedia.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun MutinyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
