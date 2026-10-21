package app.rosetta.tv.ui.raid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.rosetta.tv.data.Emote
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.theme.Rosetta
import kotlin.math.sin

/** Hype emotes from family phones float up the right edge of any screen, each tagged with the sender. */
@Composable
fun EmoteLayer(household: Household, modifier: Modifier = Modifier) {
    val now = System.currentTimeMillis()
    BoxWithConstraints(modifier.fillMaxSize()) {
        household.emotes.filter { now - it.atMs < 4000 }.forEach { e -> key(e.id) { FloatingEmote(e, household, maxHeight.value) } }
    }
}

@Composable
private fun FloatingEmote(e: Emote, h: Household, height: Float) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3200, easing = LinearEasing)) }
    val lane = (e.id % 4).toInt()
    val kid = h.kids.firstOrNull { it.id == e.kidId }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier
                .padding(end = (40 + lane * 70).dp, bottom = 40.dp)
                .offset(x = (sin(t.value * 9f + lane) * 18).dp, y = (-t.value * height * 0.7f).dp)
                .graphicsLayer { alpha = if (t.value > 0.75f) (1f - t.value) * 4f else 1f; val s = if (t.value < 0.1f) t.value * 10f else 1f; scaleX = s; scaleY = s },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(e.emoji, fontSize = 52.sp)
            if (kid != null) {
                AssetImage(Household0.avatar(kid.avatar), Modifier.size(30.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape))
                Text(
                    kid.name, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Rosetta.Night,
                    modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(Rosetta.Cream).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
