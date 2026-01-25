package com.aiu.nfcforge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * NfcForgeTheme
 *
 * Requirements:
 * - Material Design 3
 * - Dark theme by default
 * - Tech-style accent colors (cyan / blue / purple)
 *
 * We intentionally force darkTheme=true to satisfy "Dark theme by default",
 * regardless of system theme. If you want to respect the system theme later,
 * you can add a user toggle and wire it into this function.
 */
@Composable
fun NfcForgeTheme(
    content: @Composable () -> Unit,
) {
    // Dark-by-default palette with bright, neon-like accents.
    val colors = darkColorScheme(
        primary = NeonCyan,
        onPrimary = Ink,
        secondary = NeonPurple,
        onSecondary = Ink,
        tertiary = NeonBlue,
        onTertiary = Ink,
        background = Bg,
        onBackground = OnBg,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceVariant,
        outline = Outline,
        error = Error,
        onError = Ink,
    )

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}



