package app.rosetta.tv.ui.habit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.Quest
import app.rosetta.tv.data.QuestStage
import app.rosetta.tv.ui.common.QrCode
import app.rosetta.tv.ui.kit.AvatarRing
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.Pill
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.kit.Sfx
import app.rosetta.tv.ui.quest.KeeperCard
import app.rosetta.tv.ui.reward.money

/**
 * A daily task the parents set (make the bed, read 10 pages, prayer time…). The kid checks in from their phone with a
 * tick, a note, a voice note or a photo; ✦ the AI listens or looks and says honestly whether it fits the task.
 */
@Composable
fun HabitScreen(household: Household, quest: Quest, joinUrl: String, onClaim: () -> Unit) {
    val kid = household.kid(quest.kidId)
    val habit = household.habits.firstOrNull { quest.id.startsWith("h_${it.id}_") }
    val claim = remember { FocusRequester() }
    val root = remember { FocusRequester() }
    LaunchedEffect(quest.stage) {
        if (quest.stage == QuestStage.Done) Sfx.play(Sfx.Reward)
        runCatching { if (quest.stage == QuestStage.Done) claim.requestFocus() else root.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF16224A), app.rosetta.tv.ui.theme.Rosetta.Night), radius = 1500f))
            .focusRequester(root).focusable(),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 40.dp, top = 26.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarRing(Household0.avatar(kid.avatar), kid.levelProgress, 56.dp, level = kid.level)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${kid.name.uppercase()} · DAILY TASK", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Black), color = R.Gold)
                    Text(quest.title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp), color = R.Cream)
                }
                Pill("+${quest.coinReward} COINS", R.Gold)
                if (habit != null && habit.money > 0) { Spacer(Modifier.width(8.dp)); Pill("+${money(household.currency, habit.money)}", R.Lime) }
                val streak = habit?.streaks?.get(kid.id) ?: 0
                if (streak > 0) { Spacer(Modifier.width(8.dp)); Pill("🔥 $streak ${if (streak == 1) "DAY" else "DAYS"}", R.Ember) }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.weight(1f)) {
                // ---------- Left: the task and where it stands ----------
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Glass(Modifier.fillMaxWidth().weight(1f), radius = 26.dp, glow = if (quest.stage == QuestStage.Done) R.Lime else R.Gold.copy(alpha = 0.5f)) {
                        Column(Modifier.fillMaxSize().padding(28.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(quest.icon.ifBlank { habit?.icon?.ifBlank { null } ?: "✅" }, fontSize = 72.sp)
                                Spacer(Modifier.width(22.dp))
                                Column {
                                    Text(quest.title, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), color = R.Cream)
                                    Text(howTo(quest.proof), style = MaterialTheme.typography.titleMedium, color = R.Mist)
                                }
                            }
                            Spacer(Modifier.height(22.dp))
                            Status(quest, kid.name)
                        }
                    }
                    if (quest.keeperLine.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        KeeperCard(quest.keeperLine)
                    }
                }
                Spacer(Modifier.width(20.dp))
                // ---------- Right: phone check-in, then the claim ----------
                Column(Modifier.width(380.dp).fillMaxHeight()) {
                    Glass(Modifier.fillMaxWidth().weight(1f), radius = 24.dp) {
                        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            val pulse by rememberInfiniteTransition(label = "qr").animateFloat(0.95f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
                            Box(Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(10.dp)) {
                                QrCode(joinUrl, Modifier.size(170.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("📱  ${kid.name}, check in on your phone", style = MaterialTheme.typography.titleLarge, color = R.Cream)
                            Spacer(Modifier.height(6.dp))
                            Text("CODE  ${household.pairingCode}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp), color = R.Gold)
                            if (quest.proof == "voice" || quest.proof == "note") {
                                Spacer(Modifier.height(10.dp))
                                Text("Saved as a memory Mom and Dad can look back on.", fontSize = 13.sp, color = R.Mist)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    AnimatedVisibility(quest.stage == QuestStage.Done, enter = fadeIn() + scaleIn(initialScale = 0.8f)) {
                        RosettaButton(
                            "Claim rewards", onClaim, Modifier.focusRequester(claim).fillMaxWidth(),
                            icon = "🎁", sub = "+${quest.coinReward} coins${if (habit != null && habit.money > 0) " · ${money(household.currency, habit.money)}" else ""}",
                            tone = ButtonTone.Lime, big = true,
                        )
                    }
                }
            }
        }
    }
}

private val R = app.rosetta.tv.ui.theme.Rosetta

private fun howTo(proof: String) = when (proof) {
    "voice" -> "Tell Rosetta about it in a voice note on your phone."
    "note" -> "Write a short note about it on your phone."
    "photo" -> "Snap a photo of it on your phone."
    else -> "Tick it on your phone when it's done."
}

@Composable
private fun Status(q: Quest, name: String) {
    val what = when (q.checkinKind) { "voice" -> "voice note"; "photo" -> "photo"; "note" -> "note"; else -> "check-in" }
    when {
        q.stage == QuestStage.Done -> Banner("✓", "Done!", R.Lime) {
            if (q.checkin.isNotBlank()) Quote(q.checkin, ai = q.checkinKind == "voice" || q.checkinKind == "photo", label = "From $name's $what")
        }
        q.stage == QuestStage.Checking -> Banner("⏳", "Waiting for Mom or Dad to check", R.Sky) {
            if (q.checkin.isNotBlank()) Quote(q.checkin, ai = q.checkinKind == "voice" || q.checkinKind == "photo", label = "From $name's $what")
        }
        q.whatISee.isNotBlank() -> Banner("✦", "That didn't look like ${q.title.lowercase()} yet", R.Rose) {
            Quote(q.whatISee, ai = true, label = "What the AI noticed")
            Spacer(Modifier.height(6.dp))
            Text("Try again on your phone when it's done.", fontSize = 15.sp, color = R.Cream)
        }
        else -> Banner("•", "Waiting for $name", R.Mist) {
            Text("Nothing sent yet today.", fontSize = 15.sp, color = R.Mist)
        }
    }
}

@Composable
private fun Banner(icon: String, title: String, color: Color, body: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 17.sp, fontWeight = FontWeight.Black, color = R.Night)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = color)
        }
        Spacer(Modifier.height(12.dp))
        body()
    }
}

@Composable
private fun Quote(text: String, ai: Boolean, label: String) {
    Text((if (ai) "✦ " else "") + label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (ai) R.Gold else R.Mist)
    Text("“$text”", style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic, lineHeight = 28.sp), color = R.Cream, maxLines = 4)
}
