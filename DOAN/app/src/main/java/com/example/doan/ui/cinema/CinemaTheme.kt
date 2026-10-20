package com.example.doan.ui.cinema

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CinemaColors = darkColorScheme(
    primary = Color(0xFFFF5364),
    onPrimary = Color.White,
    secondary = Color(0xFFFFB36B),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFF6F6F7),
    surface = Color(0xFF151821),
    onSurface = Color(0xFFF6F6F7),
    surfaceVariant = Color(0xFF222631),
    onSurfaceVariant = Color(0xFFB9BDC8),
    error = Color(0xFFFF6874),
)

@Composable
fun CinemaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CinemaColors, content = content)
}
