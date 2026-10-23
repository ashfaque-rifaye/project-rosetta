package app.rosetta.tv.ui.study

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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.HomeworkItem
import app.rosetta.tv.data.Quest
import app.rosetta.tv.data.QuestStage
import app.rosetta.tv.ui.common.FittedPhoto
import app.rosetta.tv.ui.kit.AvatarRing
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.Pill
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.kit.Sfx
import app.rosetta.tv.ui.quest.JoinPanel
import app.rosetta.tv.ui.quest.KeeperCard
import app.rosetta.tv.ui.quest.AiSteps
import app.rosetta.tv.ui.theme.Rosetta

private val SheetW = 500.dp
private val SheetH = SheetW * 0.75f // phone photos of a sheet are 4:3

/**
 * Homework Check: the kid snaps a worksheet, Tutor marks it on the TV (ticks, crosses and a hint per mistake, never the
 * answer), then a short practice round on exactly the skills that went wrong, answered with the remote or the phone.
 */
@Composable
fun StudyScreen(
    household: Household,
    quest: Quest,
    joinUrl: String,
    onAnswer: (questionId: String, choice: Int) -> Unit,
    onClaim: () -> Unit,
    onDevPhone: () -> Unit,
) {
    val kid = household.kid(quest.kidId)
    val root = remember { FocusRequester() }
    val claim = remember { FocusRequester() }
    LaunchedEffect(quest.stage) {
        when (quest.stage) {
            QuestStage.Checked -> Sfx.play(Sfx.Found)
            QuestStage.Done -> Sfx.play(Sfx.Reward)
            else -> Unit
        }
        runCatching { if (quest.stage == QuestStage.Done) claim.requestFocus() else if (quest.stage != QuestStage.Practising) root.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF16224A), Rosetta.Night), radius = 1500f))
            .focusRequester(root)
            .focusable()
            .onPreviewKeyEvent {
                // Hidden developer hook: MENU stands in for the kid's phone sending the homework photo.
                if (it.type == KeyEventType.KeyDown && it.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_MENU) { onDevPhone(); true } else false
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 36.dp, end = 30.dp, top = 22.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarRing(Household0.avatar(kid.avatar), kid.levelProgress, 56.dp, level = kid.level)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val subject = quest.homework?.let { "${it.subject.uppercase()} · ${it.topic.uppercase()}" } ?: "HOMEWORK"
                    Text("${kid.name.uppercase()} · $subject", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Black), color = Rosetta.Gold, maxLines = 1)
                    Text(quest.title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp), color = Rosetta.Cream)
                }
                Pill("+${quest.xpReward} XP", Rosetta.Lime)
                Spacer(Modifier.width(8.dp))
                Pill("+${quest.coinReward} COINS", Rosetta.Gold)
                Spacer(Modifier.width(8.dp))
                Pill(stageLabel(quest), Rosetta.Sky, filled = true)
            }
            Spacer(Modifier.height(14.dp))

            Row(Modifier.weight(1f)) {
                Column(Modifier.width(SheetW)) {
                    Box(
                        Modifier.size(SheetW, SheetH).clip(RoundedCornerShape(22.dp))
                            .border(2.dp, Brush.linearGradient(listOf(Rosetta.Gold.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(22.dp))
                            .background(Color(0xFF0E1630)),
                    ) {
                        AnimatedContent(quest.beforePhoto, transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(350)) }, label = "sheet") { p ->
                            if (p == null) JoinPanel(household.pairingCode, kid.name, joinUrl, what = "your homework")
                            else FittedPhoto(p, Modifier.fillMaxSize()) { quest.homework?.let { hw -> Marks(hw.items) } }
                        }
                        if (quest.working) SheetSweep()
                    }
                    Spacer(Modifier.height(12.dp))
                    KeeperCard(quest.keeperLine)
                    val wrong = quest.homework?.items?.filterNot { it.correct }.orEmpty()
                    if (quest.stage == QuestStage.Practising && wrong.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Hints(wrong)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    when {
                        quest.stage == QuestStage.Practising -> PracticeCard(quest, onAnswer, Modifier.weight(1f))
                        quest.homework != null -> {
                            AiSteps(quest, kid.name, Modifier.height(222.dp))
                            Spacer(Modifier.height(10.dp))
                            MarkingList(quest.homework.items, Modifier.weight(1f))
                        }
                        else -> AiSteps(quest, kid.name, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    AnimatedVisibility(quest.stage == QuestStage.Done, enter = fadeIn() + scaleIn(initialScale = 0.8f)) {
                        RosettaButton(
                            "Claim rewards", onClaim, modifier = Modifier.focusRequester(claim).fillMaxWidth(),
                            icon = "🎁", sub = "+${quest.xpReward} XP · +${quest.coinReward} coins · a star · Homework Hero stamp", tone = ButtonTone.Lime, big = true,
                        )
                    }
                }
            }
        }
    }
}

private fun stageLabel(q: Quest) = when (q.stage) {
    QuestStage.Ready, QuestStage.AwaitingBefore -> "WAITING FOR PHOTO"
    QuestStage.Scanning -> "✦ AI TUTOR READING"
    QuestStage.Checking -> "GROWN-UP CHECK"
    QuestStage.Checked -> "MARKED"
    QuestStage.Practising -> "PRACTICE ${q.answers.size + 1}/${q.practice.size}".takeIf { q.answers.size < q.practice.size } ?: "PRACTICE DONE"
    QuestStage.Done -> "COMPLETE"
    else -> ""
}

@Composable
private fun SheetSweep() {
    val t by rememberInfiniteTransition(label = "sw").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    Box(Modifier.fillMaxSize().background(Rosetta.Night.copy(alpha = 0.25f)))
    Box(Modifier.offset(y = SheetH * t - 50.dp).fillMaxWidth().height(100.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Rosetta.Moss.copy(alpha = 0.3f), Color.Transparent))))
    Box(Modifier.offset(y = SheetH * t).fillMaxWidth().height(2.dp).background(Rosetta.Moss))
}

/** Tutor's marks drawn on the photo: a tick or a cross beside each answer, in the box the model returned. */
@Composable
private fun Marks(items: List<HomeworkItem>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        items.forEachIndexed { i, it ->
            var shown by remember(it.id) { mutableStateOf(false) }
            LaunchedEffect(it.id) { kotlinx.coroutines.delay(180L * i); shown = true }
            if (!shown) return@forEachIndexed
            val color = if (it.correct) Rosetta.Lime else Rosetta.Rose
            val x = maxWidth * it.box.r
            val y = maxHeight * it.box.cy - 17.dp
            Box(
                Modifier.offset(maxWidth * it.box.l - 4.dp, maxHeight * it.box.t - 3.dp)
                    .size(maxWidth * (it.box.r - it.box.l) + 8.dp, maxHeight * (it.box.b - it.box.t) + 6.dp)
                    .border(2.dp, color.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.07f), RoundedCornerShape(8.dp)),
            )
            Box(
                Modifier.offset(x + 6.dp, y).size(34.dp).clip(CircleShape).background(color).border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(if (it.correct) "✓" else "✗", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Rosetta.Night) }
        }
    }
}

@Composable
private fun MarkingList(items: List<HomeworkItem>, modifier: Modifier) {
    val right = items.count { it.correct }
    Glass(modifier.fillMaxWidth(), radius = 24.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("✦ AI TUTOR'S MARKING", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Gold)
                    Text("Checked on the handwriting · maths double-checked in code", fontSize = 12.sp, color = Rosetta.Mist)
                }
                ScoreRing(right, items.size)
            }
            Spacer(Modifier.height(10.dp))
            val compact = items.size > 6  // a full workbook page: two columns, hints on the TV's hint card
            val rows = if (compact) items.chunked(2) else items.map { listOf(it) }
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                rows.forEachIndexed { i, pair ->
                    var shown by remember(pair.first().id) { mutableStateOf(false) }
                    LaunchedEffect(pair.first().id) { kotlinx.coroutines.delay(140L * i); shown = true }
                    androidx.compose.animation.AnimatedVisibility(shown, enter = fadeIn(tween(300)) + androidx.compose.animation.slideInHorizontally(tween(300)) { w -> w / 6 }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { pair.forEach { MarkRow(it, compact, Modifier.weight(1f)) } }
                    }
                }
            }
        }
    }
}

/** The score as a ring: green when it's all right, gold otherwise. */
@Composable
private fun ScoreRing(right: Int, total: Int) {
    val p by androidx.compose.animation.core.animateFloatAsState(if (total == 0) 0f else right.toFloat() / total, tween(900), label = "score")
    val color = if (right == total) Rosetta.Lime else Rosetta.Gold
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val st = 6.dp.toPx()
            val sz = androidx.compose.ui.geometry.Size(size.width - st, size.height - st)
            val tl = androidx.compose.ui.geometry.Offset(st / 2, st / 2)
            drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, topLeft = tl, size = sz, style = androidx.compose.ui.graphics.drawscope.Stroke(st))
            drawArc(color, -90f, 360f * p, false, topLeft = tl, size = sz, style = androidx.compose.ui.graphics.drawscope.Stroke(st, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text("$right/$total", fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
    }
}

/** One marked answer: number badge, the question, the child's answer as a chip, a ✓/✗ medallion, and the hint. */
@Composable
private fun MarkRow(it: HomeworkItem, compact: Boolean = false, modifier: Modifier = Modifier) {
    val accent = if (it.correct) Rosetta.Lime else Rosetta.Rose
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f))), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Text("${it.n}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Rosetta.Cream)
            }
            Spacer(Modifier.width(10.dp))
            Text(it.question, fontSize = 15.sp, color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                it.kidAnswer.ifBlank { "—" }, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Rosetta.Night,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(Brush.radialGradient(listOf(accent, accent.copy(alpha = 0.6f)))).border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(if (it.correct) "✓" else "✗", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Rosetta.Night) }
        }
        if (!compact && !it.correct && it.hint.isNotBlank()) {
            Row(Modifier.padding(start = 34.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
                Text("✦ Hint ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rosetta.Gold)
                Text(it.hint, fontSize = 12.sp, color = Rosetta.Mist, maxLines = 2)
            }
        }
    }
}

/** Tutor's hints for the answers to fix: the method only, never the answer. */
@Composable
private fun Hints(wrong: List<HomeworkItem>) {
    Glass(Modifier.fillMaxWidth(), radius = 18.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✦ HINTS FROM THE AI TUTOR", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Moss)
            wrong.forEach {
                Column {
                    Text("${it.n}. ${it.question}  ${it.kidAnswer}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Rosetta.Rose, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(it.hint, fontSize = 14.sp, color = Rosetta.Cream, maxLines = 2)
                }
            }
        }
    }
}

/** The practice round: one question at a time, four big choices; after answering, the method is explained. */
@Composable
private fun PracticeCard(quest: Quest, onAnswer: (String, Int) -> Unit, modifier: Modifier) {
    var showing by remember { mutableStateOf<String?>(null) }
    val current = quest.practice.firstOrNull { it.id == showing } ?: quest.practice.firstOrNull { it.id !in quest.answers } ?: quest.practice.lastOrNull()
    LaunchedEffect(showing) { if (showing != null) { kotlinx.coroutines.delay(2200); showing = null } }
    Glass(modifier.fillMaxWidth(), radius = 22.dp, glow = Rosetta.Moss) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦ PRACTICE ROUND", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Moss)
                Spacer(Modifier.weight(1f))
                quest.practice.forEach { q ->
                    val a = quest.answers[q.id]
                    Box(Modifier.padding(start = 6.dp).size(12.dp).clip(CircleShape).background(
                        when { a == null -> Color.White.copy(alpha = 0.18f); a.correct -> Rosetta.Lime; else -> Rosetta.Rose },
                    ))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("On ${quest.homework?.needsWork?.joinToString(" & ")?.lowercase() ?: "this week's work"}", fontSize = 13.sp, color = Rosetta.Mist)
            Spacer(Modifier.height(14.dp))
            if (current != null) QuestionBlock(
                question = current,
                answered = quest.answers[current.id]?.choice,
                onPick = { choice -> showing = current.id; onAnswer(current.id, choice) },
            )
        }
    }
}
