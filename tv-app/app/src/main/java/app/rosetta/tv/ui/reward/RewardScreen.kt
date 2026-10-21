package app.rosetta.tv.ui.reward

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.QuestReward
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.Rarity
import app.rosetta.tv.ui.kit.RarityFrame
import app.rosetta.tv.ui.kit.RarityRibbon
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.kit.Sfx
import app.rosetta.tv.ui.theme.Rosetta
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import app.rosetta.tv.data.Rarity as DataRarity

fun DataRarity.ui() = when (this) {
    DataRarity.Common -> Rarity.Common
    DataRarity.Rare -> Rarity.Rare
    DataRarity.Epic -> Rarity.Epic
    DataRarity.Legendary -> Rarity.Legendary
}

/** The celebration after a quest: each reward lands in sequence, then the glyph is carried to the Stone. */
@Composable
fun RewardScreen(household: Household, reward: QuestReward, onCarve: () -> Unit) {
    val kid = household.kid(reward.kidId)
    var step by remember { mutableIntStateOf(0) }
    var xpShown by remember { mutableIntStateOf(0) }
    var coinsShown by remember { mutableIntStateOf(0) }
    val carve = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(400); step = 1 // avatar + xp
        repeat(20) { xpShown = reward.xp * (it + 1) / 20; delay(35) }
        if (reward.leveledUp) { Sfx.play(Sfx.LevelUp); step = 2; delay(1200) } else delay(300)
        step = 3; Sfx.play(Sfx.Reward)
        repeat(15) { coinsShown = reward.coins * (it + 1) / 15; delay(30) }
        delay(500)
        if (reward.stamp != null) { step = 4; Sfx.play(Sfx.Found); delay(1000) }
        if (reward.codexUpgrades.isNotEmpty()) { step = 5; Sfx.play(Sfx.LevelUp); delay(1100) }
        step = 6
    }
    LaunchedEffect(step) { if (step == 6) { delay(150); runCatching { carve.requestFocus() } } }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2A2350), Rosetta.Night), radius = 1300f))) {
        Rays()
        Confetti()
        Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            // ---------- Left: the kid ----------
            Column(Modifier.width(360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val pop = remember { Animatable(0.6f) }
                LaunchedEffect(step >= 1) { if (step >= 1) pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(240.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Rosetta.Gold.copy(alpha = 0.45f), Color.Transparent))))
                    AssetImage(
                        Household0.avatar(kid.avatar),
                        Modifier.size(200.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).border(4.dp, Rosetta.Gold, CircleShape),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Well done, ${kid.name}!", style = MaterialTheme.typography.displaySmall, color = Rosetta.Cream)
                Text(kid.title, style = MaterialTheme.typography.titleMedium, color = Rosetta.Mist)
                Spacer(Modifier.height(14.dp))
                XpBar(kid.level, kid.levelProgress, xpShown)
                if (step >= 2 && reward.leveledUp) LevelBanner(reward.newLevel)
            }
            Spacer(Modifier.width(44.dp))
            // ---------- Right: loot ----------
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("QUEST COMPLETE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 4.sp), color = Rosetta.Gold)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LootTile(visible = step >= 1, title = "+$xpShown", label = "EXPERIENCE", accent = Rosetta.Lime) { Text("⚡", fontSize = 34.sp) }
                    LootTile(visible = step >= 3, title = "+$coinsShown", label = "COINS", accent = Rosetta.Gold) {
                        AssetImage(Household0.COIN, Modifier.size(52.dp), contentScale = ContentScale.Fit)
                    }
                    reward.glyph?.let { g ->
                        LootTile(visible = step >= 3, title = g.name, label = "NEW GLYPH", accent = Rosetta.Ember) {
                            AssetImage(Household0.glyph(g.asset), Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    reward.stamp?.let { s -> StampSlam(visible = step >= 4, asset = s.asset, name = s.name) }
                    reward.codexUpgrades.take(2).forEach { e ->
                        CodexUpgrade(visible = step >= 5, character = e.character, name = e.name, rarity = e.rarity.ui())
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (step >= 6) RosettaButton(
                    "Carve it into the Family Stone", onCarve, Modifier.focusRequester(carve),
                    icon = "🗿", sub = "${household.glyphsEarned + 1} of ${household.glyphs.size} glyphs", tone = ButtonTone.Gold, big = true,
                )
            }
        }
    }
}

@Composable
private fun XpBar(level: Int, progress: Float, gained: Int) {
    Column(Modifier.width(300.dp)) {
        Row {
            Text("LEVEL $level", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black), color = Rosetta.Lime)
            Spacer(Modifier.weight(1f))
            Text("+$gained XP", style = MaterialTheme.typography.labelLarge, color = Rosetta.Cream)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
            Box(
                Modifier.fillMaxWidth(progress.coerceIn(0.03f, 1f)).height(14.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF8CC21A), Rosetta.Lime, Color(0xFFE6FF8A)))),
            )
        }
    }
}

@Composable
private fun LevelBanner(level: Int) {
    val s = remember { Animatable(2.2f) }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    Text(
        "LEVEL UP · $level", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 3.sp), color = Rosetta.Night,
        modifier = Modifier.padding(top = 14.dp).graphicsLayer { scaleX = s.value; scaleY = s.value }
            .clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Rosetta.Lime, Color(0xFFE6FF8A)))).padding(horizontal = 22.dp, vertical = 8.dp),
    )
}

@Composable
private fun LootTile(visible: Boolean, title: String, label: String, accent: Color, icon: @Composable () -> Unit) {
    val s = remember { Animatable(0f) }
    LaunchedEffect(visible) { if (visible) s.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
    Glass(Modifier.width(150.dp).height(150.dp).graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = s.value }, glow = accent.copy(alpha = 0.5f)) {
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            icon()
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, fontSize = 19.sp), color = accent, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp), color = Rosetta.Mist)
        }
    }
}

@Composable
private fun StampSlam(visible: Boolean, asset: String, name: String) {
    val s = remember { Animatable(0f) }
    LaunchedEffect(visible) { if (visible) { s.snapTo(2.4f); s.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) } }
    if (!visible) return
    Box(Modifier.width(150.dp).height(170.dp).clip(RoundedCornerShape(16.dp)).background(Rosetta.Paper), contentAlignment = Alignment.Center) {
        AssetImage(Household0.stamp(asset), Modifier.size(104.dp).graphicsLayer { scaleX = s.value; scaleY = s.value; rotationZ = -12f; alpha = (2.4f - s.value) / 1.4f }, contentScale = ContentScale.Fit)
        Text(name.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Rosetta.Ink, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        Text("NEW STAMP", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Rosetta.Ember, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
    }
}

@Composable
private fun CodexUpgrade(visible: Boolean, character: String, name: String, rarity: Rarity) {
    val flip = remember { Animatable(90f) }
    LaunchedEffect(visible) { if (visible) flip.animateTo(0f, tween(600, easing = FastOutSlowInEasing)) }
    if (!visible) return
    RarityFrame(rarity, Modifier.width(150.dp).height(170.dp).graphicsLayer { rotationY = flip.value; cameraDistance = 12f * density }) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RarityRibbon(rarity)
            AssetImage(Household0.thing(character), Modifier.padding(top = 6.dp).size(92.dp).clip(RoundedCornerShape(12.dp)))
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            Text("CODEX UPGRADE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = rarity.color, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
private fun Rays() {
    val r by rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "r")
    Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = r; scaleX = 1.8f; scaleY = 1.8f; alpha = 0.18f }) {
        val c = Offset(size.width * 0.22f, size.height * 0.5f)
        repeat(16) { i ->
            val a = Math.toRadians(i * 22.5).toFloat()
            drawLine(Rosetta.Gold, c, Offset(c.x + cos(a) * size.width, c.y + sin(a) * size.width), strokeWidth = 40f)
        }
    }
}

@Composable
private fun Confetti() {
    val t by rememberInfiniteTransition(label = "cf").animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "t")
    val colors = listOf(Rosetta.Gold, Rosetta.Lime, Rosetta.Sky, Rosetta.Rose, Rosetta.Violet)
    Canvas(Modifier.fillMaxSize()) {
        repeat(46) { i ->
            val seed = (i * 7919) % 1000 / 1000f
            val x = (seed * 1.37f + i * 0.021f) % 1f * size.width
            val y = ((t + seed) % 1f) * (size.height + 40f) - 20f
            val wob = sin((t * 6.28f * 2) + i) * 14f
            drawRect(colors[i % colors.size].copy(alpha = 0.8f), topLeft = Offset(x + wob, y), size = androidx.compose.ui.geometry.Size(8f + (i % 3) * 3f, 14f))
        }
    }
}
