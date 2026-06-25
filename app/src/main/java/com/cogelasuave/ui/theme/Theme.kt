package com.cogelasuave.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Teal200,
    onPrimary = Teal900,
    secondary = Teal400,
    background = Charcoal,
    onBackground = Sand,
    surface = Slate,
    onSurface = Sand,
)

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = Teal400,
    background = Sand,
    onBackground = Charcoal,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Charcoal,
)

@Composable
fun CogelaSuaveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
