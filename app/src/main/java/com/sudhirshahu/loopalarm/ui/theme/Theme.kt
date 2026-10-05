package com.sudhirshahu.loopalarm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.sudhirshahu.loopalarm.data.Accent
import com.sudhirshahu.loopalarm.data.ThemeMode

private fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    return if (dark) {
        val primary = lerp(seed, Color.White, 0.35f)
        darkColorScheme(
            primary = primary,
            onPrimary = lerp(seed, Color.Black, 0.7f),
            primaryContainer = lerp(seed, Color.Black, 0.45f),
            onPrimaryContainer = lerp(seed, Color.White, 0.8f),
            secondary = lerp(primary, Color.Gray, 0.35f),
            secondaryContainer = lerp(seed, Color(0xFF1C1B1F), 0.75f),
            onSecondaryContainer = lerp(seed, Color.White, 0.85f),
            tertiary = lerp(primary, Color(0xFFFFB4AB), 0.3f),
        )
    } else {
        lightColorScheme(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = lerp(seed, Color.White, 0.8f),
            onPrimaryContainer = lerp(seed, Color.Black, 0.65f),
            secondary = lerp(seed, Color.Gray, 0.4f),
            secondaryContainer = lerp(seed, Color.White, 0.88f),
            onSecondaryContainer = lerp(seed, Color.Black, 0.7f),
            tertiary = lerp(seed, Color(0xFF7D5260), 0.5f),
        )
    }
}

/** White accent: white highlights on dark backgrounds; near-black on light ones, where white would not show. */
private fun monochromeScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF3A3A3A),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFBDBDBD),
        secondaryContainer = Color(0xFF2C2C2C),
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFFE0E0E0),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF212121),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6E6E6),
        onPrimaryContainer = Color(0xFF111111),
        secondary = Color(0xFF616161),
        secondaryContainer = Color(0xFFEEEEEE),
        onSecondaryContainer = Color(0xFF1A1A1A),
        tertiary = Color(0xFF424242),
    )
}

@Composable
fun LoopAlarmTheme(mode: ThemeMode, accent: Accent, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val context = LocalContext.current
    var scheme = if (accent == Accent.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (accent == Accent.WHITE) {
        monochromeScheme(dark)
    } else {
        schemeFromSeed(Color(accent.argb), dark)
    }
    if (mode == ThemeMode.BLACK) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF0E0E0E),
            surfaceContainerLow = Color(0xFF080808),
            surfaceContainerHigh = Color(0xFF161616),
            surfaceContainerHighest = Color(0xFF1E1E1E),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
