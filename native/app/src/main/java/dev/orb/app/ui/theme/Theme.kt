package dev.orb.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OrbBg = Color(0xFF05060A)
val OrbPanel = Color(0xFF0E1017)
val OrbPanelHigh = Color(0xFF151824)
val OrbRed = Color(0xFFFF2D55)
val OrbRedDeep = Color(0xFF8A0020)
val OrbText = Color(0xFFF5F5F7)
val OrbMuted = Color(0xFF8A8F9C)
val OrbLine = Color(0xFF232735)

private val OrbColors = darkColorScheme(
    primary = OrbRed,
    onPrimary = Color.White,
    secondary = OrbRed,
    onSecondary = Color.White,
    background = OrbBg,
    onBackground = OrbText,
    surface = OrbPanel,
    onSurface = OrbText,
    surfaceVariant = OrbPanelHigh,
    onSurfaceVariant = OrbMuted,
    outline = OrbLine,
    error = Color(0xFFFF6B6B),
)

@Composable
fun OrbTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OrbColors, content = content)
}
