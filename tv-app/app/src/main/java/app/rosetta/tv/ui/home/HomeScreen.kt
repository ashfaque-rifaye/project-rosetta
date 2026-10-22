package app.rosetta.tv.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.ItemKind
import app.rosetta.tv.data.Kid
import app.rosetta.tv.data.Parent
import app.rosetta.tv.data.Quest
import app.rosetta.tv.data.QuestStage
import app.rosetta.tv.data.TvTime
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.kit.AvatarRing
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.FocusCard
import app.rosetta.tv.ui.kit.GoalMeter
import app.rosetta.tv.ui.kit.Pill
import app.rosetta.tv.ui.kit.RailHeader
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.theme.Rosetta

enum class HomeTab { Codex, Passport, Shop, Stone, Growth, Raid, Quiz }

/** The streaming apps TV time can open (Fire TV and Android TV package names). */
enum class WatchApp(val label: String, val icon: String, val packages: List<String>) {
    Prime("Prime Video", "🎬", listOf("com.amazon.avod", "com.amazon.amazonvideo.livingroom")),
    YouTube("YouTube", "▶️", listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.google.android.youtube.tvkids")),
    Netflix("Netflix", "🍿", listOf("com.netflix.ninja", "com.netflix.mediaclient")),
}

@Composable
fun HomeScreen(
    household: Household,
    onOpenQuest: (Quest) -> Unit,
    onTab: (HomeTab) -> Unit,
    onRequestTv: () -> Unit = {},
    onWatch: (WatchApp) -> Unit = {},
) {
    val today = household.quests.sortedWith(compareBy<Quest>({ it.stage == QuestStage.Done }, { !it.mustDo }, { it.kidId }))
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Rosetta.Night)) {
        // The family's house, softly in the background: a place, not a game board.
        AssetImage(
            Household0.HOME_ART, Modifier.fillMaxSize().graphicsLayer { alpha = 0.28f; scaleX = 1.08f; scaleY = 1.08f },
            contentScale = ContentScale.Crop,
        )
        household.shop.filter { it.kind == ItemKind.Decor && it.id in household.decor }.forEach { item ->
            val s = item.spot!!
            AssetImage(
                Household0.item(item.asset + "_cut"),
                Modifier.offset((1440 * s.l).dp, (810 * s.t).dp).size((1440 * (s.r - s.l)).dp, (810 * (s.b - s.t)).dp).graphicsLayer { alpha = 0.5f },
                contentScale = ContentScale.Fit,
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Rosetta.Night.copy(alpha = 0.55f), Rosetta.Night.copy(alpha = 0.92f)))))

        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 24.dp)) {
            TopBar(household)
            Spacer(Modifier.height(18.dp))
            FamilyStrip(household)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                TvTimeCard(household, Modifier.width(470.dp).fillMaxHeight(), onRequestTv, onWatch)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    RailHeader("Today", trailing = "${today.count { it.stage == QuestStage.Done }} of ${today.size} done")
                    Spacer(Modifier.height(10.dp))
                    if (today.isEmpty()) EmptyToday()
                    else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                        items(today, key = { it.id }) { q ->
                            TaskCard(q, household, if (q == today.first()) Modifier.focusRequester(first) else Modifier) { onOpenQuest(q) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                        GoalCard(household, Modifier.weight(1f)) { onTab(HomeTab.Stone) }
                        QuizCard(household, Modifier.weight(1f)) { onTab(HomeTab.Quiz) }
                        WishCard(household, Modifier.weight(1f)) { onTab(HomeTab.Shop) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RosettaButton("Progress", { onTab(HomeTab.Growth) }, icon = "📈", sub = "for parents", tone = ButtonTone.Glass)
                RosettaButton("Rewards", { onTab(HomeTab.Shop) }, icon = "🎁", sub = "shop · wish list", tone = ButtonTone.Glass)
                RosettaButton("Squad", { onTab(HomeTab.Codex) }, icon = "🧦", sub = "${household.codex.count { it.discovered }} of ${household.codex.size} found", tone = ButtonTone.Glass)
                RosettaButton("Passport & streaks", { onTab(HomeTab.Passport) }, icon = "🔥", sub = "${household.stamps.count { it.earnedOn != null }} stamps", tone = ButtonTone.Glass)
            }
        }
    }
}

@Composable
private fun TopBar(h: Household) {
    val pulse by rememberInfiniteTransition(label = "ai").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ROSETTA", fontFamily = FontFamily.Serif, fontSize = 30.sp, letterSpacing = 8.sp, color = Rosetta.Gold)
        Spacer(Modifier.width(18.dp))
        Text("Tonight with ${h.name.removePrefix("The ")}", fontSize = 20.sp, color = Rosetta.Cream.copy(alpha = 0.85f))
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC0E162E)).border(1.dp, Rosetta.Moss.copy(alpha = 0.5f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background((if (h.live) Rosetta.Moss else Rosetta.Mist).copy(alpha = pulse)))
            Text(if (h.live) "  ✦ AI on" else "  ✦ AI offline (this TV only)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream)
        }
        Spacer(Modifier.width(10.dp))
        Pill("Phone code  ${h.pairingCode}", Rosetta.Gold)
    }
}

/** Mom, Dad and the kids together: the parents are part of the family on screen, not just a phone. */
@Composable
private fun FamilyStrip(h: Household) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        h.parents.forEach { ParentChip(it) }
        Box(Modifier.width(1.dp).height(56.dp).background(Color.White.copy(alpha = 0.15f)))
        h.kids.forEach { KidChip(it, h) }
    }
}

@Composable
private fun ParentChip(p: Parent) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC0E162E)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(start = 5.dp, end = 18.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetImage(Household0.avatar(p.avatar), Modifier.size(48.dp).clip(CircleShape).border(2.dp, Rosetta.Sky.copy(alpha = 0.8f), CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Rosetta.Cream)
            Text("Parent", fontSize = 13.sp, color = Rosetta.Mist)
        }
    }
}

@Composable
private fun KidChip(kid: Kid, h: Household) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC0E162E)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(start = 4.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarRing(Household0.avatar(kid.avatar), kid.levelProgress, 52.dp, level = kid.level)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(kid.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Rosetta.Cream)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetImage(Household0.COIN, Modifier.size(15.dp), contentScale = ContentScale.Fit)
                Text(" ${kid.coins}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Rosetta.Gold)
                Text("   ${h.currency}${money(kid.money)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Rosetta.Moss)
                Text("   🔥 ${kid.streak}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Rosetta.Ember)
            }
        }
    }
}

/** The family's deal, on the biggest tile: finish today's must-do tasks, then TV time unlocks. */
@Composable
private fun TvTimeCard(h: Household, modifier: Modifier, onRequest: () -> Unit, onWatch: (WatchApp) -> Unit) {
    // Live, the server keeps the deal; offline, the TV works it out from today's must-do tasks.
    val tv = if (h.live) h.tvTime else h.quests.filter { it.mustDo }.let { m ->
        val all = m.all { it.stage == QuestStage.Done }
        TvTime(mustDo = m.size, done = m.count { it.stage == QuestStage.Done }, allDone = all, unlocked = all,
            waitingOn = m.filter { it.stage != QuestStage.Done }.map { "${h.kid(it.kidId).name}: ${it.title}" })
    }
    val progress by animateFloatAsState(if (tv.mustDo == 0) 1f else tv.done.toFloat() / tv.mustDo, tween(900), label = "tv")
    val glow by rememberInfiniteTransition(label = "tvg").animateFloat(0.5f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "g")
    val accent = if (tv.unlocked) Rosetta.Moss else Rosetta.Gold
    Column(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF01A2547), Color(0xF00C1328))))
            .border(2.dp, Brush.verticalGradient(listOf(accent.copy(alpha = 0.7f * glow), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(28.dp))
            .padding(26.dp),
    ) {
        Text("TV TIME", fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = accent)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(progress, 150.dp, accent) {
                Text(if (tv.unlocked) "🔓" else "🔒", fontSize = 40.sp)
                Text(if (tv.mustDo == 0) "free" else "${tv.done}/${tv.mustDo}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Rosetta.Cream)
            }
            Spacer(Modifier.width(22.dp))
            Column {
                Text(
                    when {
                        tv.unlocked -> "${tv.minutesLeft} minutes earned!"
                        tv.allDone && tv.requested -> "Asked Mom and Dad"
                        tv.allDone -> "All done! Ask for TV"
                        else -> "${tv.mustDo - tv.done} task${if (tv.mustDo - tv.done == 1) "" else "s"} to go"
                    },
                    fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 34.sp, color = Rosetta.Cream,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        tv.unlocked -> "Pick something to watch together."
                        tv.allDone && tv.requested -> "A notification is on their phone."
                        tv.allDone -> "A grown-up says OK from their phone."
                        else -> "Finish today's must-do tasks and ${tv.tvMinutes} minutes of TV unlock."
                    },
                    fontSize = 16.sp, lineHeight = 22.sp, color = Rosetta.Mist,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!tv.allDone && tv.waitingOn.isNotEmpty()) {
            Text("STILL TO DO", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Rosetta.Mist)
            Spacer(Modifier.height(6.dp))
            tv.waitingOn.take(4).forEach { Text("•  $it", fontSize = 17.sp, lineHeight = 26.sp, color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (!tv.unlocked) {
            Spacer(Modifier.height(22.dp))
            Text("UNLOCKS", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Rosetta.Mist)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WatchApp.entries.forEach { app ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color(0x14FFFFFF)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 8.dp).graphicsLayer { alpha = 0.6f },
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text("${app.icon} ${app.label}", fontSize = 15.sp, color = Rosetta.Cream) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        when {
            tv.unlocked -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WatchApp.entries.forEach { app -> RosettaButton(app.label, { onWatch(app) }, icon = app.icon, tone = ButtonTone.Lime) }
            }
            tv.allDone && !tv.requested -> RosettaButton("Ask Mom & Dad for TV", onRequest, icon = "📺", tone = ButtonTone.Gold, big = true)
            else -> Text("✦ Rosetta checks each task, then tells Mom and Dad.", fontSize = 14.sp, color = Rosetta.Mist)
        }
    }
}

@Composable
private fun Ring(progress: Float, size: Dp, color: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = 12.dp.toPx()
            drawArc(Color(0x22FFFFFF), 0f, 360f, false, style = Stroke(s), topLeft = Offset(s / 2, s / 2), size = Size(this.size.width - s, this.size.height - s))
            drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color)), -90f, 360f * progress, false,
                style = Stroke(s, cap = StrokeCap.Round), topLeft = Offset(s / 2, s / 2), size = Size(this.size.width - s, this.size.height - s))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun TaskCard(q: Quest, h: Household, modifier: Modifier, onClick: () -> Unit) {
    val kid = h.kid(q.kidId)
    val done = q.stage == QuestStage.Done
    val streak = h.habits.firstOrNull { it.title == q.title }?.streaks?.get(kid.id) ?: 0
    FocusCard(onClick = onClick, modifier = modifier.size(250.dp, 236.dp), accent = if (done) Rosetta.Moss else Rosetta.Gold) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                    Text(taskIcon(q), fontSize = 28.sp)
                }
                Spacer(Modifier.weight(1f))
                AssetImage(Household0.avatar(kid.avatar), Modifier.size(40.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            Text(q.title, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${kid.name} · ${kindLabel(q)}${if (q.mustDo) " · must-do" else ""}", fontSize = 13.sp, color = Rosetta.Mist, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) Pill("✓ Done", Rosetta.Moss, filled = true) else Pill(status(q), if (q.stage == QuestStage.Ready) Rosetta.Gold else Rosetta.Sky)
                Spacer(Modifier.weight(1f))
                if (streak > 0) Text("🔥 $streak", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Rosetta.Ember)
                else Text("+${q.coinReward} ●", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Rosetta.Gold)
            }
        }
    }
}

private fun taskIcon(q: Quest) = when {
    q.icon.isNotBlank() -> q.icon
    q.isStudy -> "✏️"
    q.isHabit -> "⭐"
    else -> "🧸"
}

private fun kindLabel(q: Quest) = when {
    q.isStudy -> "homework"
    q.isHabit -> "daily task"
    else -> "tidy-up"
}

private fun status(q: Quest) = when (q.stage) {
    QuestStage.Ready -> when { q.isHabit && q.proof == "voice" -> "Send a voice note"; q.isHabit && q.proof == "note" -> "Write a note"; q.isHabit && q.proof == "none" -> "Tick it on the phone"; else -> "Needs a photo" }
    QuestStage.AwaitingBefore -> "Waiting for photo"
    QuestStage.Scanning, QuestStage.Verifying -> "✦ Checking…"
    QuestStage.Checking -> if (q.needsParent) "Waiting for a grown-up" else "✦ Checking…"
    QuestStage.Found, QuestStage.AwaitingAfter -> "${q.found} to put away"
    QuestStage.Partial -> "${q.home} of ${q.found} put away"
    QuestStage.Checked -> "Marked ${q.homework?.right ?: 0} of ${q.homework?.items?.size ?: 0}"
    QuestStage.Practising -> "Practice ${q.answers.size} of ${q.practice.size}"
    QuestStage.Done -> "Done"
}

@Composable
private fun EmptyToday() {
    Box(Modifier.fillMaxWidth().height(236.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x99101A36)), contentAlignment = Alignment.Center) {
        Text("No tasks yet today. Mom or Dad can add some from their phone.", fontSize = 18.sp, color = Rosetta.Mist)
    }
}

@Composable
private fun GoalCard(h: Household, modifier: Modifier, onClick: () -> Unit) {
    val left = h.glyphs.size - h.glyphsEarned
    FocusCard(onClick = onClick, modifier = modifier.height(150.dp)) {
        AssetImage(Household0.MOVIE_ART, Modifier.matchParentSize().graphicsLayer { alpha = 0.3f }, contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("FAMILY GOAL", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Rosetta.Gold)
            Text("Movie Night 🍿", fontFamily = FontFamily.Serif, fontSize = 24.sp, color = Rosetta.Cream)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                h.glyphs.forEach { g -> Text(if (g.earned) "★" else "☆", fontSize = 24.sp, color = if (g.earned) Rosetta.Gold else Color.White.copy(alpha = 0.3f)) }
            }
            Text(if (left == 0) "Unlocked! ⭐ All ${h.glyphs.size} stars" else "⭐ ${h.glyphsEarned} of ${h.glyphs.size} stars · $left to go", fontSize = 13.sp, color = Rosetta.Mist)
        }
    }
}

@Composable
private fun QuizCard(h: Household, modifier: Modifier, onClick: () -> Unit) {
    val quiz = h.quiz
    FocusCard(onClick = onClick, modifier = modifier.height(150.dp), accent = Rosetta.Violet) {
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Rosetta.Violet.copy(alpha = 0.22f), Color.Transparent))))
        Text("?", fontFamily = FontFamily.Serif, fontSize = 120.sp, color = Rosetta.Violet.copy(alpha = 0.16f), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp))
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(if (quiz != null && !quiz.done) "ON NOW" else "TONIGHT", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Rosetta.Violet)
            Text("Family Quiz Night", fontFamily = FontFamily.Serif, fontSize = 24.sp, color = Rosetta.Cream)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    quiz == null -> "✦ 8 questions made for each child's class and tests"
                    quiz.done -> "Last time: ${quiz.lit} of ${quiz.goal} right together"
                    else -> "${quiz.answers.size} of ${quiz.goal} answered · ${quiz.lit} right so far"
                },
                fontSize = 13.sp, lineHeight = 18.sp, color = Rosetta.Mist,
            )
        }
    }
}

@Composable
private fun WishCard(h: Household, modifier: Modifier, onClick: () -> Unit) {
    val wish = h.wishlist.filter { it.status != "bought" }.maxByOrNull { w -> h.kids.firstOrNull { it.id == w.kidId }?.money?.div(w.price.coerceAtLeast(1.0)) ?: 0.0 }
    FocusCard(onClick = onClick, modifier = modifier.height(150.dp), accent = Rosetta.Moss) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("SAVING UP", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Rosetta.Moss)
            if (wish == null) {
                Text("Wish list", fontFamily = FontFamily.Serif, fontSize = 24.sp, color = Rosetta.Cream)
                Spacer(Modifier.weight(1f))
                Text("Good habits earn real pocket money. Parents add wishes from their phone.", fontSize = 13.sp, lineHeight = 18.sp, color = Rosetta.Mist)
            } else {
                val kid = h.kid(wish.kidId)
                Text(wish.title, fontFamily = FontFamily.Serif, fontSize = 22.sp, color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                val p = (kid.money / wish.price.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Brush.horizontalGradient(listOf(Rosetta.Moss, Rosetta.Lime))))
                }
                Spacer(Modifier.height(6.dp))
                Text("${kid.name}: ${h.currency}${money(kid.money)} of ${h.currency}${money(wish.price)}", fontSize = 13.sp, color = Rosetta.Mist)
            }
        }
    }
}

internal fun money(v: Double) = if (v % 1.0 == 0.0) "%.0f".format(v) else "%.2f".format(v)
