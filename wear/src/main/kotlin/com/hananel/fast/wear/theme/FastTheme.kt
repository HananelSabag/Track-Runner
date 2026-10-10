package com.hananel.fast.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** Brand colours. Kept in sync by hand with docs/brand/brand.md and the phone theme. */
object FastColors {
    val Volt = Color(0xFFC8FF2E)
    val VoltDim = Color(0xFF9BC81F)
    val Ink = Color(0xFF0B0D0F)
    val Redline = Color(0xFFFF5A36)
    val Ice = Color(0xFFF4F7F5)
    val Mist = Color(0xFF9AA3AD)
    val Graphite = Color(0xFF1B1E22)
    val GraphiteHigh = Color(0xFF262A30)
}

// The watch is OLED: a true-black background costs no power and hides the bezel.
private val FastColorScheme = ColorScheme(
    primary = FastColors.Volt,
    primaryDim = FastColors.VoltDim,
    primaryContainer = Color(0xFF2F3D0A),
    onPrimary = FastColors.Ink,
    onPrimaryContainer = FastColors.Volt,
    secondary = FastColors.Ice,
    onSecondary = FastColors.Ink,
    tertiary = FastColors.Redline,
    onTertiary = FastColors.Ink,
    surfaceContainerLow = Color(0xFF131518),
    surfaceContainer = FastColors.Graphite,
    surfaceContainerHigh = FastColors.GraphiteHigh,
    onSurface = FastColors.Ice,
    onSurfaceVariant = FastColors.Mist,
    background = Color.Black,
    onBackground = FastColors.Ice,
    error = FastColors.Redline,
    onError = FastColors.Ink,
)

@Composable
fun FastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FastColorScheme, content = content)
}
