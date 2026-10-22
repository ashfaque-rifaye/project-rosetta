package app.rosetta.tv.ui.quest

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.common.FittedPhoto
import app.rosetta.tv.ui.common.QrCode
import app.rosetta.tv.ui.kit.AvatarRing
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.Pill
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.kit.Sfx
import app.rosetta.tv.ui.theme.Rosetta
import dev.rosetta.visionoverlay.Detection
import dev.rosetta.visionoverlay.VisionOverlay
import dev.rosetta.visionoverlay.VisionOverlayStyle

private val PhotoW = 520.dp
private val PhotoH = PhotoW * (768f / 1376f)

@Composable
fun QuestScreen(
    household: Household,
    quest: Quest,
    joinUrl: String,
    onClaim: () -> Unit,
    onDevPhone: () -> Unit,
) {
    val kid = household.kid(quest.kidId)
    val room = household.room(quest.roomId)
    val root = remember { FocusRequester() }
    val claim = remember { FocusRequester() }
    LaunchedEffect(quest.stage) {
        when (quest.stage) {
            QuestStage.Found -> Sfx.play(Sfx.Found)
            QuestStage.Partial -> Sfx.play(Sfx.Home)
            QuestStage.Done -> Sfx.play(Sfx.Reward)
            else -> Unit
        }
        runCatching { if (quest.stage == QuestStage.Done) claim.requestFocus() else root.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF16224A), Rosetta.Night), radius = 1500f))
            .focusRequester(root)
            .focusable()
            .onPreviewKeyEvent {
                // Hidden developer hook: MENU stands in for the kid's phone (no visible UI).
                if (it.type == KeyEventType.KeyDown && it.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_MENU) { onDevPhone(); true } else false
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 36.dp, end = 30.dp, top = 22.dp, bottom = 20.dp)) {
            // ---------- Header ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarRing(Household0.avatar(kid.avatar), kid.levelProgress, 56.dp, level = kid.level)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${kid.name.uppercase()} · ${room.name.uppercase()}", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Black), color = Rosetta.Gold)
                    Text(quest.title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp), color = Rosetta.Cream)
                }
                Pill("+${quest.xpReward} XP", Rosetta.Lime)
                Spacer(Modifier.width(8.dp))
                Pill("+${quest.coinReward} COINS", Rosetta.Gold)
                Spacer(Modifier.width(8.dp))
                Pill(stageLabel(quest.stage), Rosetta.Sky, filled = true)
            }
            Spacer(Modifier.height(14.dp))

            Row(Modifier.weight(1f)) {
                // ---------- Left: photo stage + Keeper ----------
                Column(Modifier.width(PhotoW)) {
                    Box(
                        Modifier.size(PhotoW, PhotoH).clip(RoundedCornerShape(22.dp))
                            .border(2.dp, Brush.linearGradient(listOf(Rosetta.Gold.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(22.dp))
                            .background(Color(0xFF0E1630)),
                    ) {
                        AnimatedContent(photoFor(quest), transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(350)) }, label = "ph") { p ->
                            if (p == null) JoinPanel(household.pairingCode, kid.name, joinUrl)
                            else FittedPhoto(p, Modifier.fillMaxSize()) {
                                // Boxes are in whole-photo coordinates, so they sit inside the fitted photo, not the frame.
                                if (quest.stage in setOf(QuestStage.Found, QuestStage.AwaitingAfter, QuestStage.Partial)) {
                                    VisionOverlay(
                                        detections = quest.things.map { Detection(it.id, it.box.l, it.box.t, it.box.r, it.box.b, resolved = it.returned) },
                                        modifier = Modifier.fillMaxSize(),
                                        pulseUnresolved = quest.stage == QuestStage.Partial,
                                        style = VisionOverlayStyle(outline = Rosetta.Gold, fill = Rosetta.Gold.copy(alpha = 0.08f), badgeSize = 44.dp),
                                    ) { d -> AssetImage(Household0.thing(quest.things.first { it.id == d.id }.character), Modifier.fillMaxSize()) }
                                }
                            }
                        }
                        if (quest.working) ScanSweep()
                        if (quest.stage == QuestStage.Done) DoneBurst()
                    }
                    Spacer(Modifier.height(12.dp))
                    KeeperCard(quest.keeperLine)
                }
                Spacer(Modifier.width(18.dp))
                // ---------- Right: what Rosetta did ----------
                Column(Modifier.weight(1f)) {
                    AiSteps(quest, kid.name, Modifier)
                    Spacer(Modifier.height(16.dp))
                    LostThingsStrip(quest)
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(quest.stage == QuestStage.Done, enter = fadeIn() + scaleIn(initialScale = 0.8f)) {
                        RosettaButton(
                            "Claim rewards", onClaim, modifier = Modifier.focusRequester(claim).fillMaxWidth(),
                            icon = "🎁", sub = "+${quest.xpReward} XP · +${quest.coinReward} coins · a star for the Family Goal", tone = ButtonTone.Lime, big = true,
                        )
                    }
                    if (quest.stage != QuestStage.Done) NextStepHint(quest, kid.name)
                }
            }
        }
    }
}

private fun stageLabel(s: QuestStage) = when (s) {
    QuestStage.Ready, QuestStage.AwaitingBefore -> "WAITING FOR PHOTO"
    QuestStage.Scanning -> "✦ LOOKING"
    QuestStage.Found, QuestStage.AwaitingAfter -> "TIDY TIME"
    QuestStage.Verifying -> "✦ CHECKING"
    QuestStage.Partial -> "ALMOST"
    QuestStage.Checked, QuestStage.Practising -> "PRACTICE"
    QuestStage.Checking -> "GROWN-UP CHECK"
    QuestStage.Done -> "COMPLETE"
}

private fun photoFor(q: Quest): String? = when (q.stage) {
    QuestStage.Ready, QuestStage.AwaitingBefore -> null
    QuestStage.Scanning, QuestStage.Found, QuestStage.AwaitingAfter -> q.beforePhoto
    else -> q.afterPhoto ?: q.beforePhoto
}

@Composable
internal fun JoinPanel(code: String, kidName: String, joinUrl: String, what: String = "the room") {
    val pulse by rememberInfiniteTransition(label = "ph").animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1C2A57), Color(0xFF0E1630)))), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(10.dp)) {
                QrCode(joinUrl, Modifier.size(150.dp))
            }
            Spacer(Modifier.width(26.dp))
            Column {
                Text("📱  $kidName, grab your phone", style = MaterialTheme.typography.headlineSmall, color = Rosetta.Cream)
                Text("Scan, then snap $what.", style = MaterialTheme.typography.bodyLarge, color = Rosetta.Mist)
                Spacer(Modifier.height(10.dp))
                Text("CODE", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp), color = Rosetta.Mist)
                Text(code, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 6.sp), color = Rosetta.Gold)
            }
        }
    }
}

@Composable
private fun ScanSweep() {
    val t by rememberInfiniteTransition(label = "sw").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    Box(Modifier.fillMaxSize().background(Rosetta.Night.copy(alpha = 0.3f)))
    Box(Modifier.offset(y = PhotoH * t - 50.dp).fillMaxWidth().height(100.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Rosetta.Sky.copy(alpha = 0.35f), Color.Transparent))))
    Box(Modifier.offset(y = PhotoH * t).fillMaxWidth().height(2.dp).background(Rosetta.Sky))
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.DoneBurst() {
    val t by rememberInfiniteTransition(label = "db").animateFloat(0.4f, 0.8f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Rosetta.Gold.copy(alpha = 0.25f * t), Color.Transparent))))
    Text(
        "ALL TIDY!", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 6.sp), color = Rosetta.Gold,
        modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Rosetta.Night.copy(alpha = 0.75f)).padding(horizontal = 26.dp, vertical = 8.dp),
    )
}

@Composable
internal fun KeeperCard(line: String) {
    Glass(Modifier.fillMaxWidth(), radius = 18.dp) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Rosetta.Rose.copy(alpha = 0.2f)).border(1.dp, Rosetta.Rose, CircleShape), contentAlignment = Alignment.Center) {
                Text("❝", color = Rosetta.Rose, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            AnimatedContent(line, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) }, label = "k") { l ->
                Text(
                    if (l.isBlank()) "…" else l,
                    style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 25.sp),
                    color = Rosetta.Cream, maxLines = 2,
                )
            }
        }
    }
}

/** One line of what Rosetta did with the kid's photo, in words a 6-year-old can read. ✦ marks the AI's own work. */
private enum class StepState { Done, Now, Later }

private data class Step(val text: String, val ai: Boolean, val state: StepState)

private fun st(done: Boolean, now: Boolean) = if (done) StepState.Done else if (now) StepState.Now else StepState.Later

private fun stepsFor(q: Quest, name: String): List<Step> {
    val s = q.stage
    val photo = q.beforePhoto != null && s != QuestStage.Ready && s != QuestStage.AwaitingBefore
    return if (q.isStudy) buildList {
        val hw = q.homework
        add(Step(if (photo) "Got $name's homework photo" else "Waiting for $name's homework photo", false, st(photo, !photo)))
        add(Step(if (hw != null) "Checked ${hw.items.size} answers: ${hw.right} right" else "Reading every answer", true, st(hw != null, s == QuestStage.Scanning)))
        if (hw?.items?.any { it.checkedBy == "math" } == true) add(Step("Double-checked the maths", true, StepState.Done))
        if (hw != null) {
            if (hw.items.any { !it.correct }) add(Step("Made a practice round on what to fix", true, st(q.practice.isNotEmpty(), q.practice.isEmpty() && s != QuestStage.Done)))
            else add(Step("All right! No practice needed", false, StepState.Done))
        }
        if (q.practice.isNotEmpty()) {
            val all = q.answers.size >= q.practice.size
            add(Step(if (all) "Practice: ${q.practiceRight} of ${q.practice.size} right" else "Practice: ${q.answers.size} of ${q.practice.size} answered", false, st(all, s == QuestStage.Practising)))
        }
        if (q.needsParent || s == QuestStage.Checking) add(Step("Mom or Dad takes a look", false, st(s == QuestStage.Done, s == QuestStage.Checking)))
        add(Step("Done!", false, st(s == QuestStage.Done, false)))
    } else buildList {
        val tidyAlready = s == QuestStage.Done && q.things.isEmpty()
        val after = s == QuestStage.Verifying || s == QuestStage.Partial || s == QuestStage.Done || s == QuestStage.Checking
        add(Step(if (photo) "Got $name's room photo" else "Waiting for $name's room photo", false, st(photo, !photo)))
        when {
            tidyAlready -> add(Step("The room already looks tidy!", true, StepState.Done))
            q.things.isNotEmpty() -> add(Step("Found ${q.found} ${if (q.found == 1) "thing" else "things"} out of place", true, StepState.Done))
            else -> add(Step("Looking for things out of place", true, st(false, s == QuestStage.Scanning)))
        }
        if (!tidyAlready) {
            add(Step("Put them back where they belong", false, st(after, s == QuestStage.Found || s == QuestStage.AwaitingAfter)))
            add(Step("Comparing the before and after photos", true, st(s == QuestStage.Partial || s == QuestStage.Done || s == QuestStage.Checking, s == QuestStage.Verifying)))
            if (s == QuestStage.Partial) add(Step("${q.home} of ${q.found} back home. Almost!", false, StepState.Now))
            if (s == QuestStage.Done && q.found > 0) add(Step("All ${q.found} back home", false, StepState.Done))
        }
        if (q.needsParent || s == QuestStage.Checking) add(Step("Mom or Dad takes a look", false, st(s == QuestStage.Done, s == QuestStage.Checking)))
        add(Step("Done!", false, st(s == QuestStage.Done, false)))
    }
}

/** What Rosetta did, step by step, plus the AI's honest note when a photo didn't fit the task. */
@Composable
internal fun AiSteps(quest: Quest, name: String, modifier: Modifier) {
    Glass(modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", fontSize = 16.sp, color = Rosetta.Gold)
                Spacer(Modifier.width(8.dp))
                Text("WHAT ROSETTA DID", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Cream)
            }
            if (quest.whatISee.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Rosetta.Rose.copy(alpha = 0.14f))
                        .border(1.dp, Rosetta.Rose.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("✦", fontSize = 15.sp, color = Rosetta.Rose)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("What the photo showed", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rosetta.Rose)
                        Text(quest.whatISee, fontSize = 16.sp, lineHeight = 21.sp, color = Rosetta.Cream, maxLines = 3)
                    }
                }
            }
            stepsFor(quest, name).forEach { StepRow(it) }
        }
    }
}

@Composable
private fun StepRow(step: Step) {
    val pulse by rememberInfiniteTransition(label = "step").animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (step.state) {
                StepState.Done -> Box(Modifier.size(20.dp).clip(CircleShape).background(Rosetta.Lime), contentAlignment = Alignment.Center) {
                    Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Rosetta.Night)
                }
                StepState.Now -> Box(Modifier.size(14.dp).clip(CircleShape).background(Rosetta.Sky.copy(alpha = pulse)))
                StepState.Later -> Box(Modifier.size(14.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape))
            }
        }
        Spacer(Modifier.width(12.dp))
        if (step.ai) Text("✦ ", fontSize = 15.sp, color = Rosetta.Gold)
        Text(
            step.text + if (step.state == StepState.Now && step.ai) "…" else "",
            fontSize = 16.sp,
            fontWeight = if (step.state == StepState.Now) FontWeight.Bold else FontWeight.Normal,
            color = when (step.state) { StepState.Done -> Rosetta.Cream; StepState.Now -> Rosetta.Sky; StepState.Later -> Rosetta.Mist.copy(alpha = 0.7f) },
            maxLines = 1,
        )
    }
}

@Composable
private fun LostThingsStrip(quest: Quest) {
    if (quest.things.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${quest.home}/${quest.found}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Rosetta.Gold)
        Text("HOME", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp), color = Rosetta.Mist, modifier = Modifier.padding(end = 6.dp))
        quest.things.forEach { t ->
            Box {
                AssetImage(
                    Household0.thing(t.character),
                    Modifier.size(40.dp).clip(CircleShape).border(2.dp, if (t.returned) Rosetta.Lime else Rosetta.Gold, CircleShape)
                        .graphicsLayer { alpha = if (t.returned) 0.55f else 1f },
                )
                if (t.returned) Text(
                    "✓", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Rosetta.Night,
                    modifier = Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(Rosetta.Lime).padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NextStepHint(quest: Quest, name: String) {
    val text = when (quest.stage) {
        QuestStage.Ready, QuestStage.AwaitingBefore -> "Waiting for $name's photo…"
        QuestStage.Scanning -> "✦ Rosetta is looking at the room…"
        QuestStage.Found, QuestStage.AwaitingAfter -> "Put them back, then snap the room again."
        QuestStage.Verifying -> "✦ Rosetta is comparing your two photos…"
        QuestStage.Partial -> "Almost! Snap again when the last ones are back."
        QuestStage.Checking -> "Waiting for Mom or Dad to take a look…"
        QuestStage.Checked, QuestStage.Practising, QuestStage.Done -> ""
    }
    val dots by rememberInfiniteTransition(label = "dots").animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "d")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0x991A2547)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Rosetta.Sky.copy(alpha = dots)))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Rosetta.Cream)
    }
}
