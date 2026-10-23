package app.rosetta.tv.ui.stone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.Glyph
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.NormRect
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.common.Eyebrow
import app.rosetta.tv.ui.common.rememberImage
import app.rosetta.tv.ui.theme.Rosetta
import kotlinx.coroutines.delay

/** The empty face of the tablet on the stone key art, where the 3×3 glyph grid is carved. */
private val StoneFace = NormRect(0.378f, 0.163f, 0.625f, 0.844f)

/** The tablet is centered in its art; shift it right so the caption column never covers it. */
private const val STONE_SHIFT_DP = 150f

@Composable
fun StoneScreen(
    household: Household,
    /** Glyph just earned: plays the carving animation. */
    carvedGlyphId: String?,
    onMovieNight: () -> Unit,
    onBack: () -> Unit,
) {
    val carved = carvedGlyphId?.let { id -> household.glyphs.firstOrNull { it.id == id } }
    var showUnlock by remember { mutableStateOf(false) }
    LaunchedEffect(household.stoneComplete, carvedGlyphId) {
        if (household.stoneComplete) { delay(if (carved != null) 3200 else 600); showUnlock = true }
    }
    val action = remember { FocusRequester() }
    LaunchedEffect(showUnlock) { runCatching { action.requestFocus() } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Rosetta.Night)) {
        val w = maxWidth.value
        val h = maxHeight.value
        AssetImage(Household0.STONE_ART, Modifier.offset(x = STONE_SHIFT_DP.dp).fillMaxSize(), contentScale = ContentScale.FillBounds)

        // 3×3 glyph grid on the stone face.
        val cols = 3
        val faceW = (StoneFace.r - StoneFace.l) * w
        val faceH = (StoneFace.b - StoneFace.t) * h
        val cell = minOf(faceW / cols, faceH / 3f)
        val gridW = cell * cols
        val left = STONE_SHIFT_DP + StoneFace.l * w + (faceW - gridW) / 2
        val top = StoneFace.t * h + (faceH - cell * 3) / 2
        household.glyphs.take(9).forEachIndexed { i, g ->
            GlyphSlot(
                glyph = g,
                isNew = g.id == carvedGlyphId,
                index = i,
                household = household,
                modifier = Modifier
                    .offset((left + (i % cols) * cell).dp, (top + (i / cols) * cell).dp)
                    .size(cell.dp)
                    .padding(6.dp),
            )
        }

        // Left caption.
        // Solid under the gap the shifted art leaves, then fade: no visible seam.
        Box(
            Modifier.fillMaxHeight().width(440.dp).background(
                Brush.horizontalGradient(0f to Rosetta.Night, 0.36f to Rosetta.Night.copy(alpha = 0.92f), 1f to Rosetta.Night.copy(alpha = 0f)),
            ),
        )
        Column(Modifier.padding(start = 48.dp, top = 40.dp).width(300.dp)) {
            Eyebrow("THE FAMILY GOAL")
            Text(
                "${household.glyphsEarned} of ${household.glyphs.size} stars",
                style = MaterialTheme.typography.displaySmall,
                color = Rosetta.Cream,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (carved != null) {
                Text(
                    "${household.kids.firstOrNull { it.id == carved.earnedBy }?.name ?: "Someone"} earned a star for “${carved.name}”.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Rosetta.Gold,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            Text(
                "Every task anyone in the family finishes adds a star. Fill all ${household.glyphs.size} and Movie Night is unlocked for everyone.",
                style = MaterialTheme.typography.bodyMedium,
                color = Rosetta.Mist,
                modifier = Modifier.padding(top = 14.dp),
            )
            Spacer(Modifier.height(18.dp))
            GlyphLegend(household)
            // The star in real-time 3D: it spins faster when one was just earned.
            app.rosetta.tv.ui.kit.Model3D("models/star.glb", Modifier.padding(top = 12.dp).size(190.dp), spinDegPerSec = if (carved != null) 140f else 45f)
        }

        AnimatedVisibility(
            visible = showUnlock,
            enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp),
        ) {
            app.rosetta.tv.ui.kit.RosettaButton(
                "Movie Night is unlocked", onMovieNight, Modifier.focusRequester(action),
                icon = "🍿", sub = "All ${household.glyphs.size} stars are in", tone = app.rosetta.tv.ui.kit.ButtonTone.Gold, big = true,
            )
        }
        if (!household.stoneComplete) {
            app.rosetta.tv.ui.kit.RosettaButton(
                "Back to the house", onBack, Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp).focusRequester(action),
                icon = "🏠", tone = app.rosetta.tv.ui.kit.ButtonTone.Glass,
            )
        }
    }
}

/** One star slot on the stone: a gold star with who earned it, or a faint empty star still to win. */
@Composable
private fun GlyphSlot(glyph: Glyph, isNew: Boolean, index: Int, household: Household, modifier: Modifier) {
    val carve = remember(glyph.id, isNew) { Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(glyph.id, isNew) {
        if (isNew) { delay(700); app.rosetta.tv.ui.kit.Sfx.play(app.rosetta.tv.ui.kit.Sfx.Carve); carve.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    }
    val glow by rememberInfiniteTransition(label = "g").animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (glyph.earned) Rosetta.Gold.copy(alpha = if (isNew) glow else 0.5f) else Rosetta.Line, RoundedCornerShape(14.dp))
            .background(Rosetta.Night.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph.earned) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Rosetta.Gold.copy(alpha = 0.28f * (if (isNew) glow else 0.7f)), Rosetta.Gold.copy(alpha = 0f)))))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "★", fontSize = 54.sp, color = Rosetta.Gold,
                    modifier = Modifier.graphicsLayer {
                        alpha = carve.value
                        val sc = if (isNew) 1.8f - 0.8f * carve.value else 1f
                        scaleX = sc; scaleY = sc
                    },
                )
                Text(household.kids.firstOrNull { it.id == glyph.earnedBy }?.name ?: "", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream, maxLines = 1)
            }
        } else {
            Text("☆", fontSize = 44.sp, color = Color.White.copy(alpha = 0.16f))
        }
    }
}

@Composable
private fun GlyphLegend(household: Household) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        household.kids.forEach { kid ->
            val mine = household.glyphs.filter { it.earnedBy == kid.id }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kid.name, style = MaterialTheme.typography.titleMedium, color = Rosetta.Cream, modifier = Modifier.width(90.dp))
                Text(if (mine.size == 1) "1 star" else "${mine.size} stars", style = MaterialTheme.typography.bodyMedium, color = Rosetta.Mist)
            }
        }
    }
}
