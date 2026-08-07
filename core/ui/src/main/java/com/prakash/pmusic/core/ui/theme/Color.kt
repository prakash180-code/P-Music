package com.prakash.pmusic.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand colours for P-Music.
 *
 * The brand is built around a vivid violet seed (`MusicViolet`). This seed is
 * also what will drive Material You dynamic theming where available, keeping
 * the palette recognisable across devices.
 */

// Brand seed colour.
val MusicViolet = Color(0xFF5F3DC4)

// --- Light colour scheme (Material 3 tonal palette around MusicViolet) ---
val LightColorScheme = lightColorScheme(
    primary = MusicViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF1B005B),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF31101D),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454F)
)

// --- Dark colour scheme (deep, contrast-friendly palette) ---
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFCCBFFF),
    onPrimary = Color(0xFF2E0085),
    primaryContainer = Color(0xFF4712B2),
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E2),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

/**
 * AMOLED colour scheme: the [DarkColorScheme] tonal palette with true-black
 * surfaces so OLED pixels stay off, saving power on dark UI.
 */
val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFFCCBFFF),
    onPrimary = Color(0xFF2E0085),
    primaryContainer = Color(0xFF4712B2),
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E2),
    background = Color.Black,
    onBackground = Color(0xFFE6E0E9),
    surface = Color.Black,
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF1E1B22),
    onSurfaceVariant = Color(0xFFCAC4D0)
)
