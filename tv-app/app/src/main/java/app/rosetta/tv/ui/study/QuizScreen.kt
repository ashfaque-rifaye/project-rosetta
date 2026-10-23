package app.rosetta.tv.ui.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.Question
import app.rosetta.tv.data.Quiz
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.kit.AvatarRing
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.FocusCard
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.kit.Sfx
import app.rosetta.tv.ui.theme.Rosetta

/** Question prompt plus four big remote-friendly choices. After an answer, the right one turns green and the method shows. */
@Composable
internal fun QuestionBlock(question: Question, answered: Int?, onPick: (Int) -> Unit, promptSize: Int = 30) {
    val first = remember(question.id) { FocusRequester() }
    LaunchedEffect(question.id) { kotlinx.coroutines.delay(250); runCatching { first.requestFocus() } }
    Column {
        Text(question.prompt, fontSize = promptSize.sp, lineHeight = (promptSize + 6).sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream)
        Spacer(Modifier.height(16.dp))
        listOf(0 to 1, 2 to 3).forEach { (a, b) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                listOf(a, b).forEach { i ->
                    val isRight = answered != null && i == question.answer
                    val isWrongPick = answered != null && i == answered && i != question.answer
                    val tint = when { isRight -> Rosetta.Lime; isWrongPick -> Rosetta.Rose; else -> null }
                    FocusCard(
                        onClick = { if (answered == null) onPick(i) },
                        accent = tint ?: Rosetta.Gold,
                        radius = 18.dp,
                        modifier = Modifier.weight(1f).height(64.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Row(
                            Modifier.fillMaxSize().background(tint?.copy(alpha = 0.22f) ?: Color.Transparent).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(tint ?: Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Text("ABCD"[i].toString(), fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (tint != null) Rosetta.Night else Rosetta.Mist)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(question.choices[i], fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(answered != null, enter = fadeIn() + scaleIn(initialScale = 0.9f)) {
            val right = answered == question.answer
            Text(
                (if (right) "Yes! " else "Nearly. ") + question.why,
                fontSize = 16.sp, color = if (right) Rosetta.Lime else Rosetta.Cream,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x661A2547)).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Family Quiz Night: ✦ questions the AI writes for each child's class, subjects and upcoming tests, taking turns.
 * Every right answer lights a star on the board. Co-op only: the team total, never a ranking.
 */
@Composable
fun QuizScreen(household: Household, onAnswer: (questionId: String, kidId: String, choice: Int) -> Unit, onDone: () -> Unit) {
    val quiz = household.quiz
    if (quiz == null) {
        // Live mode: the AI is writing tonight's questions.
        Box(Modifier.fillMaxSize().background(Rosetta.Night), contentAlignment = Alignment.Center) {
            val a by rememberInfiniteTransition(label = "w").animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
            Text("✦ Rosetta is writing tonight's questions…", fontSize = 26.sp, color = Rosetta.Gold.copy(alpha = a))
        }
        return
    }
    var showing by remember { mutableStateOf<String?>(null) }
    val current = quiz.questions.firstOrNull { it.id == showing } ?: quiz.current
    LaunchedEffect(showing) { if (showing != null) { kotlinx.coroutines.delay(2300); showing = null } }
    val finished = quiz.done && showing == null
    LaunchedEffect(finished) { if (finished) Sfx.play(Sfx.Reward) }
    val back = remember { FocusRequester() }
    LaunchedEffect(finished) { if (finished) { kotlinx.coroutines.delay(300); runCatching { back.requestFocus() } } }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1B2552), Rosetta.Night), radius = 1600f))) {
        Row(Modifier.fillMaxSize().padding(start = 36.dp, end = 30.dp, top = 24.dp, bottom = 22.dp)) {
            // ---------- Left: the star board ----------
            Column(Modifier.width(400.dp).fillMaxHeight()) {
                Text("TONIGHT", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.5.sp), color = Rosetta.Gold)
                Text("Family Quiz Night", style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp), color = Rosetta.Cream)
                Text("Questions for each child's class. Every right answer lights a star.", fontSize = 14.sp, color = Rosetta.Mist)
                Spacer(Modifier.height(8.dp))
                val bySkill = quiz.questions.filter { it.forKid.isNotBlank() }.groupBy { it.forKid }
                    .map { (k, qs) -> "${household.kids.firstOrNull { it.id == k }?.name ?: k}: ${qs.map { it.skill }.distinct().joinToString(", ").lowercase()}" }
                Text(
                    "✦ Made by AI · " + bySkill.joinToString(" · "),
                    fontSize = 13.sp, color = Rosetta.Gold, maxLines = 2,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Rosetta.Gold.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(16.dp))
                StoneTrial(quiz, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${quiz.lit}", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Rosetta.Gold)
                    Text(" of ${quiz.goal} right, as a team", fontSize = 18.sp, color = Rosetta.Cream)
                }
            }
            Spacer(Modifier.width(26.dp))
            // ---------- Right: whose turn + question ----------
            Glass(Modifier.weight(1f).fillMaxHeight(), radius = 26.dp, glow = Rosetta.Gold) {
                Column(Modifier.padding(26.dp)) {
                    if (finished) {
                        Spacer(Modifier.weight(1f))
                        Text("QUIZ COMPLETE!", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.5.sp), color = Rosetta.Gold)
                        Text("${quiz.lit} of ${quiz.goal} right together", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (quiz.lit > 0) "Everyone gets +${5 * quiz.lit} coins. Teamwork wins. 🏆"
                            else "Tough round! Next quiz has new questions. 💪",
                            fontSize = 20.sp, color = Rosetta.Lime,
                        )
                        Spacer(Modifier.height(24.dp))
                        RosettaButton("Back home", onDone, modifier = Modifier.focusRequester(back).fillMaxWidth(), icon = "🏠", tone = ButtonTone.Gold, big = true)
                        Spacer(Modifier.weight(1f))
                    } else if (current != null) {
                        val kid = household.kids.firstOrNull { it.id == current.forKid } ?: household.kids[quiz.answers.size % household.kids.size]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarRing(Household0.avatar(kid.avatar), kid.levelProgress, 54.dp, level = kid.level)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${kid.name.uppercase()}'S TURN", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Gold)
                                Text("Question ${quiz.questions.indexOf(current) + 1} of ${quiz.goal} · ${current.skill.ifBlank { "review" }}", fontSize = 14.sp, color = Rosetta.Mist)
                                if (current.reason.isNotBlank()) Text("✦ ${current.reason}", fontSize = 13.sp, color = Rosetta.Gold, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        QuestionBlock(
                            question = current,
                            answered = quiz.answers.firstOrNull { it.questionId == current.id }?.choice,
                            onPick = { choice ->
                                showing = current.id
                                Sfx.play(if (choice == current.answer) Sfx.Home else Sfx.Move)
                                onAnswer(current.id, kid.id, choice)
                            },
                            promptSize = 34,
                        )
                    }
                }
            }
        }
    }
}

/** The star board: one slot per question, glowing gold when that question was answered right. */
@Composable
private fun StoneTrial(quiz: Quiz, modifier: Modifier) {
    val pulse by rememberInfiniteTransition(label = "st").animateFloat(0.6f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p")
    Box(modifier.clip(RoundedCornerShape(24.dp)).border(2.dp, Rosetta.Gold.copy(alpha = 0.35f), RoundedCornerShape(24.dp))) {
        AssetImage(Household0.STONE_ART, Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f }, contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x660A1020), Color(0xCC0A1020)))))
        val cols = 3
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            quiz.questions.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { q ->
                        val a = quiz.answers.firstOrNull { it.questionId == q.id }
                        val lit by animateFloatAsState(if (a?.correct == true) 1f else 0f, tween(700), label = "lit")
                        val isNext = q.id == quiz.current?.id
                        Box(
                            Modifier.size(78.dp).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF2A2116).copy(alpha = 0.85f))
                                .border(2.dp, when { lit > 0.5f -> Rosetta.Gold; isNext -> Rosetta.Cream.copy(alpha = pulse); a != null -> Rosetta.Rose.copy(alpha = 0.5f); else -> Color.White.copy(alpha = 0.12f) }, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Rosetta.Gold.copy(alpha = 0.75f * lit), Color.Transparent))))
                            Text(
                                if (a == null) "${quiz.questions.indexOf(q) + 1}" else if (a.correct) "★" else "·",
                                fontSize = if (a?.correct == true) 34.sp else 22.sp, fontWeight = FontWeight.Black,
                                color = if (a?.correct == true) Color(0xFFFFE7A8) else Rosetta.Mist, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** While the AI writes tonight's questions, before the 3D game opens: the family, a spinning star, and what's coming. */
@Composable
fun QuizLoadingScreen(household: Household) {
    val a by rememberInfiniteTransition(label = "ql").animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2A2350), Rosetta.Night), radius = 1400f)), contentAlignment = Alignment.Center) {
        AssetImage(Household0.STONE_ART, Modifier.fillMaxSize().graphicsLayer { alpha = 0.12f }, contentScale = ContentScale.Crop)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TONIGHT", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = Rosetta.Violet)
            Text("Family Quiz Night", fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 64.sp, color = Rosetta.Cream)
            app.rosetta.tv.ui.kit.Model3D("models/star.glb", Modifier.size(260.dp), units = 1.9f, spinDegPerSec = 110f)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                household.kids.forEach { k ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AvatarRing(Household0.avatar(k.avatar), k.levelProgress, 96.dp, level = k.level)
                        Text(k.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream, modifier = Modifier.padding(top = 6.dp))
                        Text(k.grade, fontSize = 14.sp, color = Rosetta.Mist)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("✦ Rosetta is writing 8 questions, just right for each of you…", fontSize = 24.sp, color = Rosetta.Gold.copy(alpha = a))
            Text("Take turns. Every right answer lights a star for the whole family.", fontSize = 17.sp, color = Rosetta.Mist, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

