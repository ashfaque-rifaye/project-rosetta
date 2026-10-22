package app.rosetta.tv.ui.stone

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.Household
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.common.Eyebrow
import app.rosetta.tv.ui.theme.Rosetta

/** The reward the whole family earned together. Picking a film hands off to an installed streaming app. */
@Composable
fun MovieNightScreen(household: Household, onBack: () -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    // The kid with fewer stars this round picks the film: fair, and it's real data.
    val picker = household.kids.minByOrNull { k -> household.glyphs.count { it.earnedBy == k.id } } ?: household.kids.first()
    Box(Modifier.fillMaxSize()) {
        AssetImage(Household0.MOVIE_ART, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Rosetta.Night.copy(alpha = 0.92f), Rosetta.Night.copy(alpha = 0.1f)))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 56.dp).width(440.dp)) {
            Eyebrow("THE FAMILY GOAL IS DONE")
            Text("Movie Night is unlocked", style = MaterialTheme.typography.displayMedium, color = Rosetta.Cream, modifier = Modifier.padding(top = 8.dp))
            Text(
                "${household.glyphs.size} stars, earned together: ${household.kids.joinToString(" and ") { k -> "${k.name} ${household.glyphs.count { it.earnedBy == k.id }}" }}. Tonight ${picker.name} picks the film, and everyone gets the popcorn.",
                style = MaterialTheme.typography.bodyLarge,
                color = Rosetta.Mist,
                modifier = Modifier.padding(top = 12.dp, bottom = 26.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                app.rosetta.tv.ui.kit.RosettaButton("Pick a film", {
                    val launch = context.packageManager.getLaunchIntentForPackage("com.amazon.avod")
                        ?: Intent(android.provider.Settings.ACTION_SETTINGS)
                    runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, Modifier.focusRequester(first), icon = "🍿", sub = "${picker.name} chooses tonight", tone = app.rosetta.tv.ui.kit.ButtonTone.Gold, big = true)
                app.rosetta.tv.ui.kit.RosettaButton("Start a new goal", onBack, icon = "⭐", sub = "Back to the house", tone = app.rosetta.tv.ui.kit.ButtonTone.Glass, big = true)
            }
        }
    }
}
