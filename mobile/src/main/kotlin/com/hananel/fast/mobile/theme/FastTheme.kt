package com.hananel.fast.mobile.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand colours. Kept in sync by hand with docs/brand/brand.md and the watch theme. */
object FastColors {
    val Volt = Color(0xFFC8FF2E)
    val Ink = Color(0xFF0B0D0F)
    val Redline = Color(0xFFFF5A36)
    val Ice = Color(0xFFF4F7F5)
    val Mist = Color(0xFF9AA3AD)
    val Graphite = Color(0xFF16191C)
    val GraphiteHigh = Color(0xFF1F2327)
    val Outline = Color(0xFF2E343A)
}

// Dark-only by design: the brand is built around volt on near-black, matching the watch.
private val FastColorScheme = darkColorScheme(
    primary = FastColors.Volt,
    onPrimary = FastColors.Ink,
    primaryContainer = Color(0xFF2F3D0A),
    onPrimaryContainer = FastColors.Volt,
    secondary = FastColors.Ice,
    onSecondary = FastColors.Ink,
    tertiary = FastColors.Redline,
    onTertiary = FastColors.Ink,
    background = FastColors.Ink,
    onBackground = FastColors.Ice,
    surface = FastColors.Ink,
    onSurface = FastColors.Ice,
    surfaceVariant = FastColors.GraphiteHigh,
    onSurfaceVariant = FastColors.Mist,
    surfaceContainer = FastColors.Graphite,
    surfaceContainerHigh = FastColors.GraphiteHigh,
    outline = FastColors.Outline,
    outlineVariant = FastColors.Outline,
    error = FastColors.Redline,
    onError = FastColors.Ink,
)

@Composable
fun FastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FastColorScheme, content = content)
}
