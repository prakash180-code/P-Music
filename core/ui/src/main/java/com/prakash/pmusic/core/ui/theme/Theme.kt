package com.prakash.pmusic.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.prakash.pmusic.domain.model.ThemeMode

/**
 * Root theme composable for P-Music.
 *
 * Decisions:
 * - Material You (dynamic colour) is enabled by default on Android 12+, with
 *   the brand violet as the fallback palette on older devices. It can be
 *   disabled from Settings.
 * - [ThemeMode.SYSTEM] follows the system dark setting; LIGHT/DARK force a
 *   palette. [ThemeMode.AMOLED] behaves like DARK but requests true-black
 *   surfaces to save power on OLED displays, and takes priority over dynamic
 *   colour since pure black is its defining trait.
 *
 * @param themeMode the user's selected theme mode (defaults to system).
 * @param dynamicColor whether to use Material You wallpaper colours.
 * @param content the screen to wrap with the theme.
 */
@Composable
fun PmusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    val colorScheme = when {
        themeMode == ThemeMode.AMOLED -> AmoledColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
