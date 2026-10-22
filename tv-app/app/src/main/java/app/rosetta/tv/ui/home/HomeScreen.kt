package app.rosetta.tv.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.ItemKind
import app.rosetta.tv.data.Kid
import app.rosetta.tv.data.Quest
import app.rosetta.tv.data.QuestStage
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.kit.AvatarRing
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.FocusCard
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.GoalMeter
import app.rosetta.tv.ui.kit.Pill
import app.rosetta.tv.ui.kit.RailHeader
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.kit.StatChip
import app.rosetta.tv.ui.theme.Rosetta

enum class HomeTab { Codex, Passport, Shop, Stone, Growth, Raid, Quiz }

@Composable
fun HomeScreen(
    household: Household,
    onOpenQuest: (Quest) -> Unit,
    onTab: (HomeTab) -> Unit,
) {
    val active = household.quests.filter { it.stage != QuestStage.Done }
    var focusedQuest by remember { mutableStateOf<Quest?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    // Camera: house sits on the right; a focused quest glides its room to stage centre with a spotlight.
    val target = focusedQuest?.let { household.room(it.roomId).rect }
    val anim = tween<Float>(1000, easing = FastOutSlowInEasing)
    val zoomT = if (target != null) 1.12f else 0.94f
    val stageX = if (target != null) 0.70f else 0.68f
    val stageY = if (target != null) 0.52f else 0.55f
    val zoom by animateFloatAsState(zoomT, anim, label = "zoom")
    val panX by animateFloatAsState(stageX - 0.5f - ((target?.cx ?: 0.52f) - 0.5f) * zoomT, anim, label = "px")
    val panY by animateFloatAsState(stageY - 0.5f - ((target?.cy ?: 0.52f) - 0.5f) * zoomT, anim, label = "py")
    val spotX by animateFloatAsState(target?.cx ?: 0.5f, anim, label = "sx")
    val spotY by animateFloatAsState(target?.cy ?: 0.5f, anim, label = "sy")
    val dark by animateFloatAsState(if (target != null) 0.6f else 0f, anim, label = "dk")

    BoxWithConstraints(Modifier.fillMaxSize().background(Rosetta.Night)) {
        val w = maxWidth
        val h = maxHeight
        // ---------- The living house ----------
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX * size.width; translationY = panY * size.height }) {
            AssetImage(Household0.HOME_ART, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            household.shop.filter { it.kind == ItemKind.Decor && it.id in household.decor }.forEach { item ->
                val s = item.spot!!
                AssetImage(
                    Household0.item(item.asset + "_cut"),
                    Modifier.offset(w * s.l, h * s.t).size(w * (s.r - s.l), h * (s.b - s.t)),
                    contentScale = ContentScale.Fit,
                )
            }
            Residents(household, w.value, h.value)
            Canvas(Modifier.fillMaxSize()) {
                if (dark > 0f) drawRect(
                    Brush.radialGradient(
                        0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Rosetta.Night.copy(alpha = dark),
                        center = Offset(spotX * size.width, spotY * size.height), radius = size.width * 0.19f,
                    ),
                )
            }
            active.forEach { q ->
                RoomPin(household.kid(q.kidId), household.room(q.roomId).rect.let { Offset(it.cx, it.cy) }, w.value, h.value, q.id == focusedQuest?.id, q)
            }
        }
        // Reading gradients.
        Box(Modifier.fillMaxHeight().width(500.dp).background(Brush.horizontalGradient(0f to Rosetta.Night, 0.55f to Rosetta.Night.copy(alpha = 0.85f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Rosetta.Night.copy(alpha = 0.9f), Color.Transparent))))

        // ---------- Top bar ----------
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 32.dp, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("ROSETTA", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 4.sp), color = Rosetta.Gold)
                Text("Tonight at the Raos'", style = MaterialTheme.typography.headlineSmall, color = Rosetta.Cream)
            }
            Spacer(Modifier.weight(1f))
            household.kids.forEach { KidChip(it, Modifier.padding(start = 12.dp)) }
            Spacer(Modifier.width(12.dp))
            Pill("JOIN ${household.pairingCode}", Rosetta.Gold)
        }

        // ---------- Left column: jackpot + quests ----------
        Column(Modifier.padding(start = 40.dp, top = 92.dp, bottom = 84.dp).width(372.dp).fillMaxHeight()) {
            QuizCard(household, onClick = { onTab(HomeTab.Quiz) }, onFocus = { focusedQuest = null })
            Spacer(Modifier.height(10.dp))
            JackpotCard(household)
            Spacer(Modifier.height(12.dp))
            RailHeader("Tonight's quests", trailing = "${active.size} left")
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                itemsIndexed(active, key = { _, q -> q.id }) { i, q ->
                    QuestCard(
                        q, household,
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        onFocus = { focusedQuest = q },
                        onClick = { onOpenQuest(q) },
                    )
                }
            }
        }

        // ---------- Bottom nav ----------
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val discovered = household.codex.count { it.discovered }
            RosettaButton("Codex", { focusedQuest = null; onTab(HomeTab.Codex) }, icon = "🧦", sub = "$discovered/${household.codex.size}", tone = ButtonTone.Glass)
            RosettaButton("Passport", { onTab(HomeTab.Passport) }, icon = "🗺️", sub = "${household.stamps.count { it.earnedOn != null }} stamps", tone = ButtonTone.Glass)
            RosettaButton("Shop", { onTab(HomeTab.Shop) }, icon = "🛍️", sub = "new drops", tone = ButtonTone.Glass)
            RosettaButton("Growth", { onTab(HomeTab.Growth) }, icon = "📈", sub = "for parents", tone = ButtonTone.Glass)
            RosettaButton("Stone", { onTab(HomeTab.Stone) }, icon = "🗿", sub = "${household.glyphsEarned}/${household.glyphs.size}", tone = ButtonTone.Gold)
        }
    }
}

@Composable
private fun KidChip(kid: Kid, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0xCC0E162E)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarRing(Household0.avatar(kid.avatar), kid.levelProgress, 44.dp, level = kid.level)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(kid.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Rosetta.Cream)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetImage(Household0.COIN, Modifier.size(15.dp), contentScale = ContentScale.Fit)
                Text(" ${kid.coins}", style = MaterialTheme.typography.labelLarge, color = Rosetta.Gold)
                Text("   🔥 ${kid.streak}", style = MaterialTheme.typography.labelLarge, color = Rosetta.Ember)
            }
        }
    }
}

@Composable
private fun JackpotCard(h: Household) {
    val glow by rememberInfiniteTransition(label = "jp").animateFloat(0.5f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "g")
    Glass(Modifier.fillMaxWidth(), glow = Rosetta.Gold.copy(alpha = 0.6f * glow)) {
        AssetImage(Household0.MOVIE_ART, Modifier.matchParentSize().graphicsLayer { alpha = 0.35f }, contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xF00E162E), Color(0x880E162E)))))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FAMILY GOAL", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Gold)
                Spacer(Modifier.weight(1f))
                Pill(if (h.stoneComplete) "UNLOCKED" else "${h.glyphs.size - h.glyphsEarned} TO GO", Rosetta.Gold, filled = true)
            }
            Text("Movie Night 🍿", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Rosetta.Cream)
            Spacer(Modifier.height(6.dp))
            GoalMeter(h.stoneProgress, Modifier.fillMaxWidth(), segments = h.glyphs.size)
        }
    }
}


@Composable
private fun QuestCard(q: Quest, h: Household, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val kid = h.kid(q.kidId)
    FocusCard(onClick = onClick, onFocus = onFocus, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarRing(Household0.avatar(kid.avatar), kid.levelProgress, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (q.isStudy) "${kid.name.uppercase()} · HOMEWORK ✏️" else "${kid.name.uppercase()} · ${h.room(q.roomId).name.uppercase()}", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = Rosetta.Mist, maxLines = 1)
                Text(q.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("+${q.xpReward} XP", Rosetta.Lime)
                    Pill("+${q.coinReward} ●", Rosetta.Gold)
                    if (q.stage != QuestStage.Ready) Pill(status(q), Rosetta.Sky)
                }
            }
        }
    }
}

private fun status(q: Quest) = when (q.stage) {
    QuestStage.AwaitingBefore -> "WAITING FOR PHOTO"
    QuestStage.Scanning, QuestStage.Verifying -> "CREW WORKING"
    QuestStage.Found, QuestStage.AwaitingAfter -> "${q.found} LOST"
    QuestStage.Partial -> "${q.home}/${q.found} HOME"
    QuestStage.Checked -> "MARKED ${q.homework?.right ?: 0}/${q.homework?.items?.size ?: 0}"
    QuestStage.Practising -> "PRACTICE ${q.answers.size}/${q.practice.size}"
    else -> ""
}

/** SideQuest-style pin: the kid's avatar bubble floating over the room, with a pulsing beacon. */
@Composable
private fun RoomPin(kid: Kid, at: Offset, w: Float, h: Float, focused: Boolean, q: Quest) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "b")
    val ring by rememberInfiniteTransition(label = "ring").animateFloat(0f, 1f, infiniteRepeatable(tween(1600)), label = "r")
    val pinSize = if (focused) 50.dp else 38.dp
    Box(Modifier.offset((at.x * w).dp - 110.dp, (at.y * h).dp - 70.dp - (bob * 6).dp).size(220.dp, 96.dp), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.size(80.dp).offset(y = 34.dp)) {
            drawCircle(Rosetta.Gold.copy(alpha = (1 - ring) * 0.7f), radius = 10.dp.toPx() + ring * 26.dp.toPx(), center = Offset(size.width / 2, size.height / 2), style = Stroke(2.dp.toPx()))
            drawCircle(Rosetta.Gold, radius = 5.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(pinSize).clip(CircleShape).border(3.dp, if (focused) Rosetta.Gold else Color.White, CircleShape)) {
                AssetImage(Household0.avatar(kid.avatar), Modifier.fillMaxSize())
            }
            if (focused) Text(
                q.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rosetta.Night, maxLines = 1,
                modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(50)).background(Rosetta.Gold).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}


/** Tonight's Family Quiz Battle, built from the week's homework. Replaces the Mess Boss raid card (22 Oct). */
@Composable
private fun QuizCard(h: Household, onClick: () -> Unit, onFocus: () -> Unit) {
    val quiz = h.quiz
    val pulse by rememberInfiniteTransition(label = "qz").animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    FocusCard(onClick = onClick, onFocus = onFocus, accent = Rosetta.Gold, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2116)), contentAlignment = Alignment.Center) {
                AssetImage(Household0.glyph("scholar"), Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Rosetta.Gold.copy(alpha = pulse)))
                    Text(if (quiz != null && !quiz.done) "  LIVE · QUIZ BATTLE" else "  TONIGHT · QUIZ BATTLE", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, color = Rosetta.Gold)
                }
                Text("The Stone Trial", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                if (quiz != null) {
                    Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxWidth(quiz.lit.toFloat() / quiz.goal.coerceAtLeast(1)).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFFFFB23F), Rosetta.Gold))))
                    }
                    Text("${quiz.lit}/${quiz.goal} glyphs lit · family co-op", fontSize = 12.sp, color = Rosetta.Mist)
                } else Text("Family quiz from this week's homework", fontSize = 12.sp, color = Rosetta.Mist)
            }
        }
    }
}

@Suppress("unused") // the Mess Boss raid was retired on 22 Oct; kept for the Unity arena's legacy mode
@Composable
private fun RaidCard(raid: app.rosetta.tv.data.Raid, onClick: () -> Unit, onFocus: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    FocusCard(onClick = onClick, onFocus = onFocus, accent = Rosetta.Rose, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B1226))) {
                AssetImage(Household0.boss(raid.boss.asset + "_card"), Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Rosetta.Rose.copy(alpha = pulse)))
                    Text("  LIVE · MESS BOSS", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, color = Rosetta.Rose)
                }
                Text(raid.boss.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(raid.hp.toFloat() / raid.boss.maxHp).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFFFF3B5C), Color(0xFFFFB23F)))))
                }
                Text("${raid.hp} HP left · team raid ⚔️", fontSize = 12.sp, color = Rosetta.Mist)
            }
        }
    }
}

/** Rescued Lost Things live in the house: each bobs in its room, and one at a time says something in a bubble. */
@Composable
private fun Residents(h: Household, w: Float, hh: Float) {
    val residents = h.codex.filter { it.discovered && Household0.residence.containsKey(it.character) }
    val bob by rememberInfiniteTransition(label = "res").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "b")
    var speaker by remember { mutableStateOf(0) }
    LaunchedEffect(residents.size) { while (true) { kotlinx.coroutines.delay(3200); speaker = (speaker + 1) % residents.size.coerceAtLeast(1) } }
    residents.forEachIndexed { i, e ->
        val r = Household0.residence.getValue(e.character)
        val phase = if (i % 2 == 0) bob else 1f - bob
        Box(Modifier.offset((r.l * w).dp, (r.t * hh).dp - (phase * 4).dp)) {
            AssetImage(Household0.thing(e.character), Modifier.size(((r.r - r.l) * w).dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape))
            if (i == speaker) Text(
                Household0.residentLines[e.character] ?: "",
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Rosetta.Night, maxLines = 1,
                modifier = Modifier.offset(x = (-30).dp, y = (-26).dp).clip(RoundedCornerShape(10.dp)).background(Rosetta.Cream).padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}
