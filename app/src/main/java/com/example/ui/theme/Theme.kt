package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CopyEditColorScheme = lightColorScheme(
    primary = PurpleAccent,
    onPrimary = White,
    primaryContainer = PurpleAccentLight,
    onPrimaryContainer = PurpleAccent,
    secondary = BlueAccent,
    onSecondary = White,
    secondaryContainer = BlueAccentLight,
    onSecondaryContainer = BlueAccent,
    tertiary = PurpleAccent,
    onTertiary = White,
    background = White,
    onBackground = PrimaryText,
    surface = White,
    onSurface = PrimaryText,
    surfaceVariant = SecondaryBackground,
    onSurfaceVariant = SecondaryText,
    outline = BorderColor,
    outlineVariant = BorderColor.copy(alpha = 0.5f),
    error = ErrorRed,
    onError = White
)

@Composable
fun CopyEditTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CopyEditColorScheme,
        typography = Typography,
        content = content
    )
}

// Retain alias for template compatibility
@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    CopyEditTheme(content = content)
}
