package app.rosetta.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object RosettaColors {
    val Ink = Color(0xFF0E0A1A)
    val Night = Color(0xFF1B1035)
    val Saffron = Color(0xFFE07A3F)
    val Marigold = Color(0xFFF2B33D)
    val Parchment = Color(0xFFF4ECDD)
    val Muted = Color(0xFFB9AFC9)
}

private val scheme = darkColorScheme(
    primary = RosettaColors.Saffron,
    secondary = RosettaColors.Marigold,
    background = RosettaColors.Ink,
    surface = RosettaColors.Night,
    onPrimary = RosettaColors.Ink,
    onBackground = RosettaColors.Parchment,
    onSurface = RosettaColors.Parchment,
    onSurfaceVariant = RosettaColors.Muted,
)

@Composable
fun RosettaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
