package app.rosetta.tv.ui.home

import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.key
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Text
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.ItemKind
import app.rosetta.tv.data.Kid
import app.rosetta.tv.data.NormRect
import app.rosetta.tv.data.Parent
import app.rosetta.tv.data.Quest
import app.rosetta.tv.data.QuestStage
import app.rosetta.tv.data.TvTime
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.kit.AvatarRing
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.FocusCard
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.theme.Rosetta

enum class HomeTab { Codex, Passport, Shop, Stone, Growth, Raid, Quiz }

/** The streaming apps TV time can open (Fire TV and Android TV package names). */
enum class WatchApp(val label: String, val icon: String, val packages: List<String>) {
    Prime("Prime Video", "🎬", listOf("com.amazon.avod", "com.amazon.amazonvideo.livingroom")),
    YouTube("YouTube", "▶️", listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.google.android.youtube.tvkids")),
    Netflix("Netflix", "🍿", listOf("com.netflix.ninja", "com.netflix.mediaclient")),
}

/**
 * Home is a game world: the family's house is alive (a Veo-animated loop of the dollhouse), every task is a quest
 * pinned to its room, the camera glides to a quest's room when it's selected, and Lost Things a child has put back
 * live in the house. The HUD around it: players (kids → their Progress, parents → the parent page), the quest board,
 * the TV-time portal, and the family's goals.
 */
@Composable
fun HomeScreen(
    household: Household,
    onOpenQuest: (Quest) -> Unit,
    onTab: (HomeTab) -> Unit,
    onRequestTv: () -> Unit = {},
    onWatch: (WatchApp) -> Unit = {},
    onKid: (String) -> Unit = {},
    onParents: () -> Unit = {},
) {
    val quests = household.quests.sortedWith(compareBy<Quest>({ it.stage == QuestStage.Done }, { !it.mustDo }, { it.isHabit }, { it.kidId }))
    var focused by remember { mutableStateOf<Quest?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    // Camera: the house sits right of the quest board; a selected quest glides its room to stage centre with a spotlight.
    val target = focused?.let { roomRect(household, it) }
    val anim = tween<Float>(900, easing = FastOutSlowInEasing)
    val zoomT = if (target != null) 1.14f else 1.0f
    val stageX = if (target != null) 0.63f else 0.64f
    val stageY = if (target != null) 0.50f else 0.52f
    val zoom by animateFloatAsState(zoomT, anim, label = "zoom")
    val panX by animateFloatAsState(stageX - 0.5f - ((target?.cx ?: 0.545f) - 0.5f) * zoomT, anim, label = "px")
    val panY by animateFloatAsState(stageY - 0.5f - ((target?.cy ?: 0.55f) - 0.5f) * zoomT, anim, label = "py")
    val spotX by animateFloatAsState(target?.cx ?: 0.5f, anim, label = "sx")
    val spotY by animateFloatAsState(target?.cy ?: 0.5f, anim, label = "sy")
    val dark by animateFloatAsState(if (target != null) 0.55f else 0f, anim, label = "dk")

    Box(Modifier.fillMaxSize().background(Rosetta.Night)) {
        // ---------- The living house ----------
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX * size.width; translationY = panY * size.height }) {
            LivingHouse()
            household.shop.filter { it.kind == ItemKind.Decor && it.id in household.decor }.forEach { item ->
                val s = item.spot!!
                AssetImage(
                    Household0.item(item.asset + "_cut"),
                    Modifier.offset((1440 * s.l).dp, (810 * s.t).dp).size((1440 * (s.r - s.l)).dp, (810 * (s.b - s.t)).dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Residents(household)
            Canvas(Modifier.fillMaxSize()) {
                if (dark > 0f) drawRect(
                    Brush.radialGradient(
                        0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Rosetta.Night.copy(alpha = dark),
                        center = Offset(spotX * size.width, spotY * size.height), radius = size.width * 0.2f,
                    ),
                )
            }
            // One pin per child per room (with a count), so a busy evening stays readable.
            quests.filter { it.stage != QuestStage.Done }.groupBy { roomRect(household, it) }.forEach { (rect, qs) ->
                val byKid = qs.groupBy { it.kidId }.entries.toList()
                byKid.forEachIndexed { i, (kidId, mine) ->
                    val sel = mine.firstOrNull { it.id == focused?.id }
                    RoomPin(household.kid(kidId), rect, (i - (byKid.size - 1) / 2f) * 0.05f, i, sel != null, sel ?: mine.first(), mine.size)
                }
            }
        }
        Sparkles()
        // Reading gradients behind the HUD.
        Box(Modifier.fillMaxHeight().width(560.dp).background(Brush.horizontalGradient(0f to Rosetta.Night.copy(alpha = 0.97f), 0.6f to Rosetta.Night.copy(alpha = 0.8f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Rosetta.Night.copy(alpha = 0.92f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Rosetta.Night.copy(alpha = 0.95f)))))

        // ---------- Top: title and the players ----------
        Row(Modifier.fillMaxWidth().padding(start = 36.dp, end = 32.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("ROSETTA", fontFamily = FontFamily.Serif, fontSize = 30.sp, letterSpacing = 8.sp, color = Rosetta.Gold)
                Text("Tonight at the ${household.name.removePrefix("The ").removeSuffix(" family")}s'", fontSize = 15.sp, color = Rosetta.Cream.copy(alpha = 0.8f))
            }
            Spacer(Modifier.weight(1f))
            household.kids.forEach { k -> PlayerCard(k, household, Modifier.padding(start = 12.dp)) { onKid(k.id) } }
            Spacer(Modifier.width(16.dp))
            ParentsButton(household.parents) { onParents() }
        }

        // ---------- Left: the quest board ----------
        Column(Modifier.padding(start = 30.dp, top = 112.dp, bottom = 96.dp).width(420.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 6.dp)) {
                Text("⚔️  QUEST BOARD", fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Rosetta.Gold)
                Spacer(Modifier.weight(1f))
                val done = quests.count { it.stage == QuestStage.Done }
                Text("$done of ${quests.size} done", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Rosetta.Mist)
            }
            Spacer(Modifier.height(8.dp))
            if (quests.isEmpty()) Text("No quests yet today. Mom or Dad can add some from their phone.", fontSize = 16.sp, color = Rosetta.Mist, modifier = Modifier.padding(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)) {
                itemsIndexed(quests, key = { _, q -> q.id }) { i, q ->
                    QuestCard(q, household, if (i == 0) Modifier.focusRequester(first) else Modifier, onFocus = { focused = q }) { onOpenQuest(q) }
                }
            }
        }

        // ---------- Right: the TV-time portal and the family's goals ----------
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 96.dp).width(400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvPortal(household, onRequestTv, onWatch) { focused = null }
        }

        // ---------- Bottom: the family's goals and places ----------
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            val quiz = household.quiz
            RosettaButton(
                "Quiz Night", { focused = null; onTab(HomeTab.Quiz) }, icon = "🧠", tone = ButtonTone.Gold,
                sub = when { quiz == null -> "8 questions · play now"; quiz.done -> "${quiz.lit} of ${quiz.goal} right"; else -> "on now · ${quiz.answers.size}/${quiz.goal}" },
            )
            RosettaButton("Family Goal", { onTab(HomeTab.Stone) }, icon = "⭐", sub = "${household.glyphsEarned} of ${household.glyphs.size} stars → 🍿", tone = ButtonTone.Glass)
            RosettaButton("Treasure", { onTab(HomeTab.Shop) }, icon = "🎁", sub = "shop · wish list", tone = ButtonTone.Glass)
            RosettaButton("Squad", { onTab(HomeTab.Codex) }, icon = "🧦", sub = "${household.codex.count { it.discovered }} of ${household.codex.size}", tone = ButtonTone.Glass)
            RosettaButton("Streaks", { onTab(HomeTab.Passport) }, icon = "🔥", sub = "${household.stamps.count { it.earnedOn != null }} stamps", tone = ButtonTone.Glass)
            RosettaButton("Progress", { onTab(HomeTab.Growth) }, icon = "📈", sub = "for parents", tone = ButtonTone.Glass)
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            AiBadge(household.live)
            Spacer(Modifier.width(10.dp))
            Text("Phone code ", fontSize = 14.sp, color = Rosetta.Mist)
            Text(household.pairingCode, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Rosetta.Gold)
        }
    }
}

/** Where a quest happens in the house: its room, or for a daily task, the room it belongs to. */
private fun roomRect(h: Household, q: Quest): NormRect {
    if (!q.isHabit) return h.room(q.roomId).rect
    val t = q.title.lowercase()
    val own = h.rooms.firstOrNull { it.ownerId == q.kidId }
    return when {
        own != null && ("bed" in t || "room" in t || "bag" in t || "teeth" in t) -> own.rect
        else -> h.room("living").rect
    }
}

/** The dollhouse, alive: an 8-second Veo loop of the Home art (lamps, curtains, stars), over the still art. */
@Composable
private fun BoxScope.LivingHouse() {
    val context = LocalContext.current
    val id = remember { context.resources.getIdentifier("home_world", "raw", context.packageName) }
    var playing by remember { mutableStateOf(false) }
    AssetImage(Household0.HOME_ART, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    if (id == 0) return
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$id")))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onRenderedFirstFrame() { playing = true } }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    // A TextureView (not a SurfaceView) so the video zooms and pans with the camera like the rest of the world.
    AndroidView(
        factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (playing) 1f else 0f },
    )
}

/** Drifting golden motes over the world. */
@Composable
private fun Sparkles() {
    val t by rememberInfiniteTransition(label = "sp").animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        repeat(28) { i ->
            val seed = ((i * 7919) % 1000) / 1000f
            val x = (0.35f + (seed * 1.7f + i * 0.037f) % 0.65f) * size.width
            val y = (1f - (t + seed) % 1f) * size.height
            val a = (kotlin.math.sin((t * 12f + i) * 1.3f) * 0.5f + 0.5f) * 0.55f
            drawCircle(Rosetta.Gold.copy(alpha = a), radius = 1.5.dp.toPx() + (i % 3) * 0.8.dp.toPx(), center = Offset(x, y))
        }
    }
}

/** A child as a player: avatar with level ring, XP bar, coins, pocket money and streak. Opens their Progress. */
@Composable
private fun PlayerCard(kid: Kid, h: Household, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(onClick = onClick, modifier = modifier, radius = 40.dp, accent = Rosetta.Lime) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarRing(Household0.avatar(kid.avatar), kid.levelProgress, 54.dp, level = kid.level)
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(kid.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Rosetta.Cream)
                    Text("  ${kid.title}", fontSize = 12.sp, color = Rosetta.Lime)
                }
                Box(Modifier.padding(vertical = 3.dp).width(130.dp).height(6.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(kid.levelProgress.coerceAtLeast(0.03f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFF8CC21A), Rosetta.Lime))))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssetImage(Household0.COIN, Modifier.size(14.dp), contentScale = ContentScale.Fit)
                    Text(" ${kid.coins}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Rosetta.Gold)
                    Text("  💰${h.currency}${money(kid.money)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Rosetta.Moss)
                    Text("  🔥${kid.streak}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Rosetta.Ember)
                }
            }
        }
    }
}

/** Mom and Dad, side by side: opens the parent page (QR, link and what parents can do). */
@Composable
private fun ParentsButton(parents: List<Parent>, onClick: () -> Unit) {
    FocusCard(onClick = onClick, radius = 40.dp, accent = Rosetta.Sky) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            parents.forEachIndexed { i, p ->
                AssetImage(
                    Household0.avatar(p.avatar),
                    Modifier.offset(x = (-12 * i).dp).size(48.dp).clip(CircleShape).border(2.dp, Rosetta.Sky, CircleShape),
                )
            }
            Column(Modifier.offset(x = (-12 * (parents.size - 1) + 8).dp)) {
                Text("Parents", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Rosetta.Cream)
                Text("📱 open on your phone", fontSize = 12.sp, color = Rosetta.Sky)
            }
        }
    }
}

@Composable
private fun AiBadge(live: Boolean) {
    val pulse by rememberInfiniteTransition(label = "ai").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC0E162E)).border(1.dp, Rosetta.Moss.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background((if (live) Rosetta.Moss else Rosetta.Mist).copy(alpha = pulse)))
        Text(if (live) "  ✦ AI on" else "  ✦ AI offline", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream)
    }
}

/** A quest on the board: medallion, title, who, rewards and where it stands. Selecting it flies the camera to its room. */
@Composable
private fun QuestCard(q: Quest, h: Household, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val kid = h.kid(q.kidId)
    val done = q.stage == QuestStage.Done
    val habit = h.habits.firstOrNull { q.id.startsWith("h_${it.id}_") }
    val streak = habit?.streaks?.get(kid.id) ?: 0
    val accent = when { done -> Rosetta.Moss; q.isStudy -> Rosetta.Sky; q.isHabit -> Rosetta.Violet; else -> Rosetta.Gold }
    FocusCard(onClick = onClick, onFocus = onFocus, accent = accent, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp).graphicsLayer { alpha = if (done) 0.6f else 1f }, verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.12f))))
                    .border(1.5.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(if (done) "✅" else taskIcon(q), fontSize = 28.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(q.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${kid.name} · ${kindLabel(q)}${if (q.mustDo) " · must-do" else ""}",
                    fontSize = 12.sp, color = Rosetta.Mist, maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (done) "✓ Done" else status(q), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (done) Rosetta.Moss else accent, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    if (streak > 0) Text("🔥$streak  ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Rosetta.Ember)
                    if (habit != null && habit.money > 0) Text("💰${h.currency}${money(habit.money)}  ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rosetta.Moss)
                    Text("+${q.coinReward}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Rosetta.Gold)
                    AssetImage(Household0.COIN, Modifier.padding(start = 3.dp).size(14.dp), contentScale = ContentScale.Fit)
                }
            }
            Spacer(Modifier.width(8.dp))
            AssetImage(Household0.avatar(kid.avatar), Modifier.size(34.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape))
        }
    }
}

/** A floating pin over the room where a quest happens: the child's avatar and a beacon; the selected one shows its name. */
@Composable
private fun RoomPin(kid: Kid, room: NormRect, dx: Float, index: Int, focused: Boolean, q: Quest, count: Int) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(1600 + index * 230), RepeatMode.Reverse), label = "b")
    val ring by rememberInfiniteTransition(label = "ring").animateFloat(0f, 1f, infiniteRepeatable(tween(1600)), label = "r")
    val pinSize = if (focused) 54.dp else 40.dp
    val x = room.cx + dx // several quests in one room fan out side by side
    Box(Modifier.offset((x * 1440).dp - 110.dp, (room.cy * 810).dp - 70.dp - (bob * 7).dp).size(220.dp, 100.dp), contentAlignment = Alignment.TopCenter) {
        if (focused) Canvas(Modifier.size(90.dp).offset(y = -18.dp)) {
            drawCircle(Rosetta.Gold.copy(alpha = (1 - ring) * 0.8f), radius = 27.dp.toPx() + ring * 22.dp.toPx(), center = Offset(size.width / 2, size.height / 2), style = Stroke(3.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Box(Modifier.size(pinSize).clip(CircleShape).border(3.dp, if (focused) Rosetta.Gold else Color.White, CircleShape)) {
                    AssetImage(Household0.avatar(kid.avatar), Modifier.fillMaxSize())
                }
                if (count > 1) Text(
                    "$count", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Rosetta.Night,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).clip(CircleShape).background(Rosetta.Gold).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            if (focused) Text(
                "${taskIcon(q)} ${q.title}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rosetta.Night, maxLines = 1,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Rosetta.Gold).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** Lost Things a child has really put back live in the house, bobbing in their spots (nothing shows until earned). */
@Composable
private fun Residents(h: Household) {
    val residents = h.codex.filter { it.discovered && Household0.residence.containsKey(it.character) }
    if (residents.isEmpty()) return
    val bob by rememberInfiniteTransition(label = "res").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "b")
    residents.forEachIndexed { i, e ->
        val r = Household0.residence.getValue(e.character)
        val phase = if (i % 2 == 0) bob else 1f - bob
        AssetImage(
            Household0.thing(e.character),
            Modifier.offset((r.l * 1440).dp, (r.t * 810).dp - (phase * 4).dp).size(((r.r - r.l) * 1440).dp)
                .clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        )
    }
}

/** The family's deal as a portal: locked until today's must-do quests are done, then it opens onto the streaming apps. */
@Composable
private fun TvPortal(h: Household, onRequest: () -> Unit, onWatch: (WatchApp) -> Unit, onFocus: () -> Unit) {
    // Live, the server keeps the deal; offline, the TV works it out from today's must-do tasks.
    val tv = if (h.live) h.tvTime else h.quests.filter { it.mustDo }.let { m ->
        val all = m.all { it.stage == QuestStage.Done }
        TvTime(mustDo = m.size, done = m.count { it.stage == QuestStage.Done }, allDone = all, unlocked = all,
            waitingOn = m.filter { it.stage != QuestStage.Done }.map { "${h.kid(it.kidId).name}: ${it.title}" })
    }
    val progress by animateFloatAsState(if (tv.mustDo == 0) 1f else tv.done.toFloat() / tv.mustDo, tween(900), label = "tv")
    val spin by rememberInfiniteTransition(label = "portal").animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "s")
    val glow by rememberInfiniteTransition(label = "pg").animateFloat(0.5f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "g")
    val accent = if (tv.unlocked) Rosetta.Moss else Rosetta.Gold
    // The portal bursts open when TV time unlocks: a pop and the reward sound.
    val pop = remember { Animatable(1f) }
    var wasUnlocked by remember { mutableStateOf(tv.unlocked) }
    LaunchedEffect(tv.unlocked) {
        if (tv.unlocked && !wasUnlocked) {
            app.rosetta.tv.ui.kit.Sfx.play(app.rosetta.tv.ui.kit.Sfx.Reward)
            pop.snapTo(1.12f); pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        wasUnlocked = tv.unlocked
    }
    Column(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color(0xE81A2547), Color(0xF00C1328))))
            .border(2.dp, Brush.verticalGradient(listOf(accent.copy(alpha = 0.8f * glow), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }) {
                    val s = 10.dp.toPx()
                    drawArc(Brush.sweepGradient(listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.5f), accent.copy(alpha = 0f))), 0f, 360f, false,
                        style = Stroke(s * 2.2f), topLeft = Offset(s, s), size = androidx.compose.ui.geometry.Size(size.width - 2 * s, size.height - 2 * s))
                }
                Canvas(Modifier.fillMaxSize()) {
                    val s = 9.dp.toPx()
                    val sz = androidx.compose.ui.geometry.Size(size.width - s, size.height - s)
                    drawArc(Color(0x22FFFFFF), 0f, 360f, false, style = Stroke(s), topLeft = Offset(s / 2, s / 2), size = sz)
                    drawArc(accent, -90f, 360f * progress, false, style = Stroke(s, cap = StrokeCap.Round), topLeft = Offset(s / 2, s / 2), size = sz)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Real-time 3D: a gold padlock while TV time is locked, a glowing TV once it's earned.
                    key(tv.unlocked) {
                        app.rosetta.tv.ui.kit.Model3D(
                            if (tv.unlocked) "models/tv.glb" else "models/padlock.glb", Modifier.size(62.dp),
                            units = 1.8f, spinDegPerSec = if (tv.unlocked) 90f else 35f, tilt = 6f,
                        )
                    }
                    if (!tv.unlocked) Text(if (tv.mustDo == 0) "free" else "${tv.done}/${tv.mustDo}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Rosetta.Cream)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("📺 TV TIME", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = accent)
                Text(
                    when {
                        tv.unlocked -> "${tv.minutesLeft} min earned!"
                        tv.allDone && tv.requested -> "Asked Mom & Dad"
                        tv.allDone -> "All done!"
                        else -> "${tv.mustDo - tv.done} must-do to go"
                    },
                    fontFamily = FontFamily.Serif, fontSize = 25.sp, lineHeight = 29.sp, color = Rosetta.Cream,
                )
                Text(
                    when {
                        tv.unlocked && tv.granted && !tv.allDone -> "Mom & Dad unlocked it. Pick a show!"
                        tv.unlocked -> "Pick something to watch."
                        tv.allDone && tv.requested -> "Waiting for a yes on their phone."
                        tv.allDone -> "Ask a grown-up to say OK."
                        else -> "Then ${tv.tvMinutes} minutes of TV unlock."
                    },
                    fontSize = 14.sp, color = Rosetta.Mist,
                )
            }
        }
        if (!tv.allDone && tv.waitingOn.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            tv.waitingOn.take(3).forEach { Text("•  $it", fontSize = 14.sp, color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (tv.waitingOn.size > 3) Text("•  and ${tv.waitingOn.size - 3} more", fontSize = 14.sp, color = Rosetta.Mist)
        }
        when {
            tv.unlocked -> {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatchApp.entries.forEach { app -> RosettaButton(app.label, { onWatch(app) }, icon = app.icon, tone = ButtonTone.Lime) }
                }
            }
            tv.allDone && !tv.requested -> {
                Spacer(Modifier.height(10.dp))
                RosettaButton("Ask Mom & Dad for TV", onRequest, icon = "📺", tone = ButtonTone.Gold)
            }
        }
    }
}

private fun taskIcon(q: Quest) = when {
    q.icon.isNotBlank() -> q.icon
    q.isStudy -> "✏️"
    q.isHabit -> "⭐"
    q.roomId == "kitchen" -> "🍽️"
    q.roomId == "balcony" -> "🪴"
    else -> "🧸"
}

private fun kindLabel(q: Quest) = when {
    q.isStudy -> "homework"
    q.isHabit -> "daily"
    else -> "tidy-up"
}

private fun status(q: Quest) = when (q.stage) {
    QuestStage.Ready -> when { q.isHabit && q.proof == "voice" -> "🎙 Voice note"; q.isHabit && q.proof == "note" -> "✍️ Write a note"; q.isHabit && q.proof == "none" -> "✓ Tick on phone"; else -> "📷 Needs a photo" }
    QuestStage.AwaitingBefore -> "📷 Waiting for photo"
    QuestStage.Scanning, QuestStage.Verifying -> "✦ Checking…"
    QuestStage.Checking -> if (q.needsParent) "Waiting for a grown-up" else "✦ Checking…"
    QuestStage.Found, QuestStage.AwaitingAfter -> "${q.found} to put away"
    QuestStage.Partial -> "${q.home} of ${q.found} put away"
    QuestStage.Checked -> "Marked ${q.homework?.right ?: 0} of ${q.homework?.items?.size ?: 0}"
    QuestStage.Practising -> "Practice ${q.answers.size} of ${q.practice.size}"
    QuestStage.Done -> "Done"
}

internal fun money(v: Double) = if (v % 1.0 == 0.0) "%.0f".format(v) else "%.2f".format(v)
