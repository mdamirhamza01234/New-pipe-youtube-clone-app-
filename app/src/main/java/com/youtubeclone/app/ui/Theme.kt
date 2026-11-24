package com.youtubeclone.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val YtRed = Color(0xFFFF0000)

private val DarkColors = darkColorScheme(
    primary = YtRed,
    onPrimary = Color.White,
    background = Color(0xFF0F0F0F),
    onBackground = Color.White,
    surface = Color(0xFF0F0F0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF3F3F3F),
)

private val LightColors = lightColorScheme(
    primary = YtRed,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF0F0F0F),
    surface = Color.White,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060),
    outline = Color(0xFFE5E5E5),
)

@Composable
fun YtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
