package app.rosetta.tv.ui.raid

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.Hit
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.kit.Sfx
import app.rosetta.tv.ui.theme.Rosetta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat

private val kidColors = Rosetta.KidSeries

@Composable
fun RaidScreen(household: Household, onDevHit: () -> Unit, onDone: () -> Unit) {
    val raid = household.raid ?: return
    val boss = raid.boss
    val root = remember { FocusRequester() }
    val done = remember { FocusRequester() }

    // Hit reactions: shake, squash and a red flash on every new hit.
    val shake = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    val floaters = remember { mutableStateListOf<Hit>() }
    LaunchedEffect(raid.hits.size) {
        val hit = raid.hits.lastOrNull() ?: return@LaunchedEffect
        floaters += hit
        Sfx.play(if (hit.crit) Sfx.LevelUp else Sfx.Home)
        launch { flash.snapTo(if (hit.crit) 0.6f else 0.35f); flash.animateTo(0f, tween(450)) }
        launch { squash.snapTo(0.86f); squash.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)) }
        repeat(6) { i -> shake.animateTo(if (i % 2 == 0) 14f else -14f, tween(35)) }
        shake.animateTo(0f, tween(60))
        delay(1600)
        floaters.remove(hit)
    }
    LaunchedEffect(raid.defeated) {
        if (raid.defeated) { Sfx.play(Sfx.Reward); delay(900); runCatching { done.requestFocus() } } else runCatching { root.requestFocus() }
    }
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(-8f, 8f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "b")
    val defeatFade by animateFloatAsState(if (raid.defeated) 0f else 1f, tween(900), label = "df")

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Rosetta.Night)
            .focusRequester(root).focusable()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_MENU) { onDevHit(); true } else false
            },
    ) {
        AssetImage(Household0.ARENA, Modifier.fillMaxSize().graphicsLayer { translationX = shake.value * 0.4f }, contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Rosetta.Night.copy(alpha = 0.75f), Color.Transparent, Rosetta.Night.copy(alpha = 0.9f)))))

        // ---------- The boss ----------
        Box(Modifier.align(Alignment.Center).offset(y = 30.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(460.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFF3B5C).copy(alpha = 0.25f + flash.value), Color.Transparent))))
            AssetImage(
                Household0.boss(boss.asset),
                Modifier.size(400.dp).graphicsLayer {
                    translationX = shake.value; translationY = bob
                    scaleX = (2f - squash.value) * defeatFade; scaleY = squash.value * defeatFade
                    alpha = defeatFade
                },
            )
            floaters.forEachIndexed { i, h -> key(h.atMs) { DamageNumber(h, household, i) } }
        }

        // ---------- Header: boss name + HP ----------
        Column(Modifier.align(Alignment.TopCenter).padding(top = 22.dp).width(720.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MESS BOSS · ${boss.roomName.uppercase()}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 3.sp), color = Rosetta.Rose)
            Text(boss.name, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = Rosetta.Cream)
            Text(boss.tagline, style = MaterialTheme.typography.bodyLarge, color = Rosetta.Mist)
            Spacer(Modifier.height(10.dp))
            HpBar(raid.hp, boss.maxHp)
        }

        // ---------- Combo ----------
        if (raid.combo >= 2 && !raid.defeated) ComboBadge(raid.combo, Modifier.align(Alignment.CenterEnd).padding(end = 60.dp))

        // ---------- Co-op team bar ----------
        Glass(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).width(820.dp), radius = 24.dp) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TEAM RAO", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Gold)
                    Spacer(Modifier.width(10.dp))
                    Text("${raid.hits.size} things put away · ends ${raid.endsAt}", style = MaterialTheme.typography.labelLarge, color = Rosetta.Mist)
                    Spacer(Modifier.weight(1f))
                    Text("put stuff away to hit it 💥", style = MaterialTheme.typography.labelLarge, color = Rosetta.Cream)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    household.kids.forEachIndexed { i, k ->
                        Row(Modifier.padding(end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            AssetImage(Household0.avatar(k.avatar), Modifier.size(42.dp).clip(CircleShape).border(2.dp, kidColors[i % 4], CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(k.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream)
                                Text("${raid.dealtBy(k.id)} dmg", fontSize = 13.sp, fontWeight = FontWeight.Black, color = kidColors[i % 4])
                            }
                        }
                    }
                    // Stacked contribution bar: who did how much of the team's damage.
                    Row(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                        val total = boss.maxHp.toFloat()
                        household.kids.forEachIndexed { i, k ->
                            val f = raid.dealtBy(k.id) / total
                            if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(kidColors[i % 4]))
                        }
                        if (raid.hp > 0) Box(Modifier.weight(raid.hp / total).fillMaxHeight())
                    }
                }
            }
        }

        // ---------- Victory ----------
        AnimatedVisibility(raid.defeated, enter = fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.7f), modifier = Modifier.align(Alignment.Center)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BOSS DEFEATED", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 6.sp), color = Rosetta.Gold)
                Text("Team Rao cleared the ${boss.roomName.lowercase()} together.", style = MaterialTheme.typography.headlineSmall, color = Rosetta.Cream)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Loot("+200", "COINS EACH", Rosetta.Gold)
                    Loot("+150", "XP EACH", Rosetta.Lime)
                    Loot("1", "LEGENDARY TROPHY", Rosetta.Violet)
                }
                Spacer(Modifier.height(18.dp))
                RosettaButton("Back to the house", onDone, Modifier.focusRequester(done), icon = "🏠", tone = ButtonTone.Gold, big = true)
            }
        }
    }
}

@Composable
private fun HpBar(hp: Int, max: Int) {
    val frac = hp.toFloat() / max
    val fast by animateFloatAsState(frac, tween(180), label = "hp")
    val ghost by animateFloatAsState(frac, tween(900, delayMillis = 350, easing = FastOutSlowInEasing), label = "ghost")
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(50)).background(Color(0xCC12182E)).border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(ghost).fillMaxHeight().background(Color(0xFFFFE6A8)))
            Box(Modifier.fillMaxWidth(fast).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFFFF3B5C), Color(0xFFFF7A59), Color(0xFFFFB23F)))))
            Text(
                "${NumberFormat.getIntegerInstance().format(hp)} / ${NumberFormat.getIntegerInstance().format(max)} HP",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black), color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun DamageNumber(hit: Hit, h: Household, index: Int) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(1500, easing = LinearEasing)) }
    val kidIdx = h.kids.indexOfFirst { it.id == hit.kidId }.coerceAtLeast(0)
    val x = (if (index % 2 == 0) -1 else 1) * (90 + (hit.atMs % 70).toInt())
    Row(
        Modifier.offset(x = x.dp, y = (-60 - rise.value * 170).dp).graphicsLayer {
            alpha = 1f - rise.value * rise.value
            val pop = if (rise.value < 0.12f) 0.6f + rise.value * 4f else 1.08f - rise.value * 0.08f
            scaleX = pop; scaleY = pop
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetImage(Household0.thing(hit.thing), Modifier.size(34.dp).clip(CircleShape).border(2.dp, kidColors[kidIdx % 4], CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            (if (hit.crit) "CRIT! " else "") + "-${hit.damage}",
            fontSize = if (hit.crit) 40.sp else 32.sp, fontWeight = FontWeight.Black,
            color = if (hit.crit) Rosetta.Gold else Color.White,
        )
    }
}

@Composable
private fun ComboBadge(combo: Int, modifier: Modifier) {
    val pop = remember(combo) { Animatable(1.5f) }
    LaunchedEffect(combo) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Column(modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔥", fontSize = 54.sp)
        Text("x$combo", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Rosetta.Ember)
        Text("COMBO", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Rosetta.Cream)
        Text("+${(combo - 1).coerceAtMost(5) * 10}% dmg", fontSize = 13.sp, color = Rosetta.Mist, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Loot(value: String, label: String, color: Color) {
    Glass(Modifier.width(170.dp), glow = color.copy(alpha = 0.5f)) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = Rosetta.Mist)
        }
    }
}
