package app.rosetta.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/**
 * Night-museum palette: the deep blue of the Home Base backdrop, candle-gold glyph light,
 * and warm cream text. Taken from the generated key art so UI and imagery read as one piece.
 */
object Rosetta {
    val Night = Color(0xFF0A1020)
    val Deep = Color(0xFF0F1830)
    val Panel = Color(0xFF131E3A)
    val Line = Color(0x33F6EAD7)
    val Gold = Color(0xFFF4C56A)
    val Ember = Color(0xFFFF9A4D)
    val Cream = Color(0xFFF6EAD7)
    val Mist = Color(0xFFA9B4CC)
    val Moss = Color(0xFF9BE37A)
    val Waypoint = Color(0xFFFFD27A)
    val Lime = Color(0xFFC6F432)
    val Sky = Color(0xFF6BC6FF)
    val Violet = Color(0xFFC77DFF)
    val Rose = Color(0xFFFF6FA3)
    val Paper = Color(0xFFF7EEDC)
    val Ink = Color(0xFF1B2440)

    /** Per-kid data series, validated (dataviz validator, dark surface #0F1830): band, chroma, CVD, contrast all pass. */
    val KidSeries = listOf(Color(0xFF749A10), Color(0xFF3D97D1), Color(0xFFE0477F), Color(0xFFA257DD))
}

private val scheme = darkColorScheme(
    primary = Rosetta.Gold,
    onPrimary = Rosetta.Night,
    secondary = Rosetta.Ember,
    background = Rosetta.Night,
    onBackground = Rosetta.Cream,
    surface = Rosetta.Panel,
    onSurface = Rosetta.Cream,
    onSurfaceVariant = Rosetta.Mist,
)

/** Serif display (museum / carved-stone feel) with the system sans for UI text. */
private val typography = Typography(
    displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 44.sp, lineHeight = 50.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.4.sp),
)

@Composable
fun RosettaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
