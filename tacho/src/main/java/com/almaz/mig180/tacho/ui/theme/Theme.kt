package com.almaz.mig180.tacho.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Тёмная палитра — приложение работает в мастерской, светлой темы нет намеренно. */
val Amber = Color(0xFFFFB300)
val Mint = Color(0xFF7FD1B9)
val Danger = Color(0xFFFF6B6B)
val Ink = Color(0xFF101214)
val Surface1 = Color(0xFF181B1E)
val Surface2 = Color(0xFF23282C)
val TextPrimary = Color(0xFFECEFF1)
val TextSecondary = Color(0xFFA8B0B8)

private val TachoColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241A00),
    primaryContainer = Color(0xFF3A2C00),
    onPrimaryContainer = Amber,
    secondary = Mint,
    onSecondary = Color(0xFF00201A),
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF4A5158),
    error = Danger,
    onError = Color(0xFF3A0000),
)

@Composable
fun TachoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TachoColors, content = content)
}
