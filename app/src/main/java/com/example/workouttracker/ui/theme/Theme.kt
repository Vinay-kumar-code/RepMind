package com.example.workouttracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorSchemeFallback = darkColorScheme(
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF10B981),
    tertiary = Color(0xFFEF4444),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
)

private val LightColorSchemeFallback = lightColorScheme(
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF10B981),
    tertiary = Color(0xFFEF4444),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

@Composable
@ReadOnlyComposable
private fun dynamicOrFallbackColorScheme(darkTheme: Boolean, dynamic: Boolean): ColorScheme {
    val context = LocalContext.current
    return if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColorSchemeFallback else LightColorSchemeFallback
    }
}

@Composable
fun WorkoutTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = dynamicOrFallbackColorScheme(darkTheme, dynamicColor)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
