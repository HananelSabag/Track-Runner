package com.hananel.fast.mobile.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand colours. Kept in sync by hand with docs/brand/brand.md and the watch theme. */
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
    val Surface = Color(0xFF141E33)
    val SurfaceHigh = Color(0xFF1E293B)
    val Outline = Color(0xFF263247)

    /** The ground a track is drawn on: a shade under the cards, so the coloured trail carries. */
    val Pitch = Color(0xFF0A0F1C)

    /**
     * Movement intensity, cool to hot: rest, walk, jog, run, high speed, sprint.
     * One hue sweep from cyan to rose, so a zone is readable without a legend.
     */
    val Zones = listOf(Slate, Cyan, Electric, Violet, Color(0xFFD946EF), Rose)
}

// Dark-only by design: deep navy with a blue-to-violet accent, matching the watch.
private val FastColorScheme = darkColorScheme(
    primary = FastColors.Sky,
    onPrimary = FastColors.Navy,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = FastColors.Cyan,
    onSecondary = FastColors.Navy,
    // Tonal buttons and selected chips: a quiet blue, clearly a step below the primary action.
    secondaryContainer = Color(0xFF1D3157),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = FastColors.Lilac,
    onTertiary = FastColors.Navy,
    tertiaryContainer = Color(0xFF3B2A6B),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = FastColors.Navy,
    onBackground = FastColors.Ice,
    surface = FastColors.Navy,
    onSurface = FastColors.Ice,
    surfaceVariant = FastColors.SurfaceHigh,
    onSurfaceVariant = FastColors.Mist,
    // Every surface tone is set: any left to the library default would come out purple-grey.
    surfaceDim = FastColors.Navy,
    surfaceBright = FastColors.SurfaceHigh,
    surfaceContainerLowest = FastColors.Pitch,
    surfaceContainerLow = Color(0xFF0F172A),
    surfaceContainer = FastColors.Surface,
    surfaceContainerHigh = FastColors.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF273449),
    inverseSurface = FastColors.Ice,
    inverseOnSurface = FastColors.Navy,
    inversePrimary = FastColors.Electric,
    errorContainer = Color(0xFF5B1A2A),
    onErrorContainer = Color(0xFFFFE4E6),
    scrim = Color.Black,
    outline = FastColors.Slate,
    outlineVariant = FastColors.Outline,
    error = FastColors.Rose,
    onError = FastColors.Navy,
)

@Composable
fun FastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FastColorScheme, content = content)
}
