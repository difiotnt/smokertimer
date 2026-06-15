package com.difiotnt.smokertimer

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SmokyDark = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    secondary = Color(0xFFF59E0B),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF06101B),
    surface = Color(0xFF0B1726),
    surfaceVariant = Color(0xFF152235),
    onPrimary = Color(0xFF04111A),
    onSecondary = Color(0xFF1A1200),
    onTertiary = Color(0xFF00120B),
    onBackground = Color(0xFFEAF2FF),
    onSurface = Color(0xFFEAF2FF),
    onSurfaceVariant = Color(0xFFB7C3D7),
)

private val SmokyLight = lightColorScheme(
    primary = Color(0xFF0369A1),
    secondary = Color(0xFFB45309),
    tertiary = Color(0xFF047857),
    background = Color(0xFFF4F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4ECF4),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0C1A29),
    onSurface = Color(0xFF0C1A29),
    onSurfaceVariant = Color(0xFF334155),
)

@Composable
fun SmokerTimerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context is Activity ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> SmokyDark
        else -> SmokyLight
    }

    androidx.compose.material3.MaterialTheme(
        colorScheme = scheme,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
