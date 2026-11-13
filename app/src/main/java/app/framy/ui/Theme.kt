package app.framy.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.framy.Prefs

/** (container, onContainer) colour for a course id. */
val LocalCourseColor = staticCompositionLocalOf<(Long) -> Pair<Color, Color>> { { Palettes.course("slate", it, false) } }

@Composable
fun FramyTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val seed = Prefs.themeSeed
    val wallpaper = seed == "wallpaper" && Build.VERSION.SDK_INT >= 31
    val scheme = if (wallpaper) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        val pair = Palettes.presets[seed] ?: Palettes.presets.getValue("slate")
        if (dark) pair.second else pair.first
    }
    // shortcut: wallpaper mode uses slate's course hues (not harmonised to the wallpaper); harmonise at runtime if it clashes.
    val coursePreset = if (wallpaper) "slate" else seed
    CompositionLocalProvider(LocalCourseColor provides { id -> Palettes.course(coursePreset, id, dark) }) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
