package com.lastcalled.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5F4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F2D9),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4B635B),
    tertiary = Color(0xFF8A4F2A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD6BD),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFFA7F2D9),
    secondary = Color(0xFFB2CCC2),
    tertiary = Color(0xFFFFB68E),
)

@Composable
fun LastCalledTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
