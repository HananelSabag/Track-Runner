package com.hananel.fast.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** Brand colours. Kept in sync by hand with docs/brand/brand.md and the phone theme. */
object FastColors {
    // Brand
    val Electric = Color(0xFF3B82F6)
    val Sky = Color(0xFF60A5FA)
    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFF8B5CF6)
    val Lilac = Color(0xFFA78BFA)

    // Meaning
    val Mint = Color(0xFF34D399)
    val Rose = Color(0xFFFB7185)
    val RoseDeep = Color(0xFFF43F5E)

    // Neutrals
    val Navy = Color(0xFF0B1120)
    val Ice = Color(0xFFF1F5F9)
    val Mist = Color(0xFF94A3B8)
    val Slate = Color(0xFF64748B)
    val SurfaceLow = Color(0xFF0F172A)
    val Surface = Color(0xFF141E33)
    val SurfaceHigh = Color(0xFF1E293B)

    /**
     * Movement intensity, cool to hot: rest, walk, jog, run, high speed, sprint.
     * One hue sweep from cyan to rose, so a zone is readable without a legend.
     */
    val Zones = listOf(Slate, Cyan, Electric, Violet, Color(0xFFD946EF), Rose)
}

// The watch is OLED: a true-black background costs no power and hides the bezel.
private val FastColorScheme = ColorScheme(
    primary = FastColors.Sky,
    primaryDim = FastColors.Electric,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimary = FastColors.Navy,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = FastColors.Cyan,
    secondaryDim = Color(0xFF06B6D4),
    secondaryContainer = Color(0xFF164E63),
    onSecondary = FastColors.Navy,
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = FastColors.Lilac,
    tertiaryDim = FastColors.Violet,
    tertiaryContainer = Color(0xFF4C1D95),
    onTertiary = FastColors.Navy,
    onTertiaryContainer = Color(0xFFEDE9FE),
    surfaceContainerLow = FastColors.SurfaceLow,
    surfaceContainer = FastColors.Surface,
    surfaceContainerHigh = FastColors.SurfaceHigh,
    onSurface = FastColors.Ice,
    onSurfaceVariant = FastColors.Mist,
    outline = FastColors.Slate,
    outlineVariant = FastColors.SurfaceHigh,
    background = Color.Black,
    onBackground = FastColors.Ice,
    error = FastColors.Rose,
    onError = FastColors.Navy,
)

@Composable
fun FastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FastColorScheme, content = content)
}
