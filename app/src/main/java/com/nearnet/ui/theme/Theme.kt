package com.nearnet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NetGreenMid,
    onPrimary = NetGreenDark,
    secondary = NetGreenMid,
    tertiary = NetGreenMid,
)

private val LightColorScheme = lightColorScheme(
    primary = NetGreenLight,
    onPrimary = NetGreenDark,
    secondary = NetGreenMid,
    tertiary = NetGreenLight,
    surface = NetGrayLight
)

@Composable
fun NearNetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
