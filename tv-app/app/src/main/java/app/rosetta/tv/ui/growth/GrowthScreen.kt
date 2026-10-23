package app.rosetta.tv.ui.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.HistoryRow
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.Kid
import app.rosetta.tv.ui.kit.AvatarRing
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.reward.money
import app.rosetta.tv.ui.theme.Rosetta
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private const val DAYS = 14

/**
 * Progress, for parents: each kid's real activity from the server's history (nothing synthetic). A 14-day habit
 * calendar, homework accuracy, pocket money, and ✦ Coach's notes the AI writes once there is something real to say.
 */
@Composable
fun GrowthScreen(household: Household, kidId: String? = null) {
    var kidIdx by remember { mutableIntStateOf(household.kids.indexOfFirst { it.id == kidId }.coerceAtLeast(0)) }
    val kid = household.kids[kidIdx.coerceIn(0, household.kids.lastIndex)]
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val rows = household.history.filter { it.kidId == kid.id }
    val days = remember(household.todayDate) { lastDays(household.todayDate) }
    val week = days.takeLast(7).toSet()
    val thisWeek = rows.filter { it.day in week }
    val study = rows.filter { it.kind == "study" && it.total > 0 }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1A2550), Rosetta.Night), radius = 1500f))) {
        Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 40.dp, top = 26.dp, bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PROGRESS", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 3.sp), color = Rosetta.Gold)
                    Text("How ${kid.name} is doing", style = MaterialTheme.typography.displaySmall, color = Rosetta.Cream)
                }
                household.kids.forEachIndexed { i, k ->
                    RosettaButton(
                        k.name, { kidIdx = i }, Modifier.padding(start = 10.dp).then(if (i == kidIdx) Modifier.focusRequester(first) else Modifier),
                        icon = if (i == kidIdx) "★" else "☆", tone = if (i == kidIdx) ButtonTone.Lime else ButtonTone.Glass,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---------- Hero + headline numbers ----------
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Hero(kid, Modifier.width(380.dp).fillMaxHeight())
                val right = study.sumOf { it.right }
                val total = study.sumOf { it.total }
                StatTile("Tasks this week", "${thisWeek.size}", "on ${thisWeek.map { it.day }.distinct().size} of 7 days", Modifier.weight(1f))
                StatTile(
                    "Homework right", if (total == 0) "–" else "${right * 100 / total}%",
                    if (total == 0) "after the first homework check" else "$right of $total answers", Modifier.weight(1f),
                )
                StatTile(
                    "Pocket money", money(household.currency, kid.money),
                    "${money(household.currency, thisWeek.sumOf { it.money })} earned this week", Modifier.weight(1f), accent = Rosetta.Lime,
                )
                StatTile("Stars", "${household.glyphs.count { it.earnedBy == kid.id }}", "for the Family Goal", Modifier.weight(1f), accent = Rosetta.Ember)
            }
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HabitCalendar(household, kid, rows, days, Modifier.fillMaxWidth().weight(1f))
                    Homework(study, Modifier.fillMaxWidth().height(150.dp))
                }
                CoachCard(household, kid, rows.size, Modifier.width(440.dp).fillMaxHeight())
            }
        }
    }
}

/** The last [DAYS] days as yyyy-MM-dd in India time, ending today (the server's day when it sent one). */
private fun lastDays(today: String): List<String> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    runCatching { if (today.isNotBlank()) cal.time = fmt.parse(today)!! }
    cal.add(Calendar.DAY_OF_YEAR, -(DAYS - 1))
    return (0 until DAYS).map { fmt.format(cal.time).also { cal.add(Calendar.DAY_OF_YEAR, 1) } }
}

private fun weekday(day: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)!!
    SimpleDateFormat("EEEEE", Locale.US).format(d)
}.getOrDefault("")

@Composable
private fun Hero(kid: Kid, modifier: Modifier) {
    Glass(modifier, glow = Rosetta.Gold.copy(alpha = 0.4f)) {
        Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarRing(Household0.avatar(kid.avatar), kid.levelProgress, 104.dp, level = kid.level)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(kid.name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Rosetta.Cream)
                Text("${kid.title}${if (kid.grade.isBlank()) "" else if (kid.grade.startsWith("Grade")) " · ${kid.grade}" else " · Grade ${kid.grade}"}", fontSize = 14.sp, color = Rosetta.Mist)
                Spacer(Modifier.height(8.dp))
                Text("Level ${kid.level} · ${kid.xp} of ${kid.xpForNext} XP", fontSize = 13.sp, color = Rosetta.Lime)
                Text(
                    if (kid.streak > 0) "🔥 ${kid.streak} ${if (kid.streak == 1) "day" else "days"} in a row" else "No streak yet",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (kid.streak > 0) Rosetta.Ember else Rosetta.Mist,
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, modifier: Modifier, accent: Color = Rosetta.Gold) {
    Glass(modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.Center) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold), color = Rosetta.Mist)
            Text(value, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = accent, maxLines = 1)
            Text(sub, fontSize = 13.sp, color = Rosetta.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One row per daily task (plus homework and tidy-ups); a lit cell is a day it was really done. */
@Composable
private fun HabitCalendar(h: Household, kid: Kid, rows: List<HistoryRow>, days: List<String>, modifier: Modifier) {
    data class Line(val label: String, val color: Color, val streak: Int?, val done: Set<String>)
    val lines = h.habits.filter { kid.id in it.kidIds }.map { hb ->
        Line("${hb.icon.ifBlank { "✓" }} ${hb.title}", Rosetta.Lime, hb.streaks[kid.id] ?: 0, rows.filter { it.habitId == hb.id }.map { it.day }.toSet())
    } + listOf(
        Line("📘 Homework", Rosetta.Sky, null, rows.filter { it.kind == "study" }.map { it.day }.toSet()),
        Line("🧸 Tidy-ups", Rosetta.Gold, null, rows.filter { it.kind == "tidy" }.map { it.day }.toSet()),
    )
    Glass(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LAST 2 WEEKS", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Cream)
                Spacer(Modifier.weight(1f))
                Text("each square is a day it was done", fontSize = 12.sp, color = Rosetta.Mist)
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                lines.take(6).forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.label, fontSize = 16.sp, color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(200.dp))
                        days.forEach { d ->
                            val on = d in l.done
                            Box(
                                Modifier.padding(end = 6.dp).size(36.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (on) l.color else Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, if (d == days.last()) Rosetta.Cream.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(8.dp)),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        l.streak?.let { Text(if (it > 0) "🔥 $it" else "", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Rosetta.Ember) }
                    }
                }
                Row {
                    Spacer(Modifier.width(200.dp))
                    days.forEach { d -> Text(weekday(d), fontSize = 12.sp, color = Rosetta.Mist, modifier = Modifier.width(42.dp)) }
                }
            }
            if (h.habits.none { kid.id in it.kidIds }) {
                Spacer(Modifier.weight(1f))
                Text("Mom or Dad can add daily tasks like \"Make the bed\" or \"Read 10 pages\" from their phone. Each one gets its own row and streak.", fontSize = 14.sp, color = Rosetta.Mist)
            }
        }
    }
}

@Composable
private fun Homework(study: List<HistoryRow>, modifier: Modifier) {
    Glass(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("✦ HOMEWORK THE AI TUTOR CHECKED", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Cream)
            Spacer(Modifier.height(8.dp))
            if (study.isEmpty()) Text("Nothing checked yet. Snap a worksheet from the phone and it shows up here.", fontSize = 14.sp, color = Rosetta.Mist)
            study.sortedByDescending { it.atMs }.take(3).forEach { r ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.topic.ifBlank { r.title }, fontSize = 14.sp, color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(230.dp))
                    val p = r.right.toFloat() / r.total
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF))) {
                        Box(Modifier.fillMaxWidth(p.coerceAtLeast(0.03f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(if (p >= 0.8f) Rosetta.Lime else Rosetta.Gold))
                    }
                    Text("  ${r.right}/${r.total}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Rosetta.Gold)
                    if (r.skills.isNotEmpty()) Text("  practise: ${r.skills.joinToString(", ").lowercase()}", fontSize = 12.sp, color = Rosetta.Mist, maxLines = 1, modifier = Modifier.width(220.dp))
                }
            }
        }
    }
}

@Composable
private fun CoachCard(h: Household, kid: Kid, finished: Int, modifier: Modifier) {
    val notes = h.coach[kid.id]
    Glass(modifier, glow = Rosetta.Violet.copy(alpha = 0.35f)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", fontSize = 18.sp, color = Rosetta.Gold)
                Spacer(Modifier.width(8.dp))
                Text("COACH'S NOTES", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Cream)
            }
            Spacer(Modifier.height(4.dp))
            Text("Written by the AI from ${kid.name}'s real week, for Mom and Dad.", fontSize = 12.sp, color = Rosetta.Mist)
            Spacer(Modifier.height(14.dp))
            if (notes == null || (notes.headline.isBlank() && notes.notes.isEmpty())) {
                Text(
                    if (finished < 3) "Notes appear after 3 finished tasks. ${kid.name} has $finished so far." else "Notes are on the way. They refresh a few times a day.",
                    style = MaterialTheme.typography.titleMedium, color = Rosetta.Cream,
                )
                Spacer(Modifier.height(8.dp))
                Text("Only real activity counts: nothing here is sample data.", fontSize = 13.sp, color = Rosetta.Mist)
            } else {
                if (notes.headline.isNotBlank()) Text(notes.headline, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp, lineHeight = 27.sp), color = Rosetta.Gold)
                Spacer(Modifier.height(12.dp))
                notes.notes.take(4).forEach { n ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text("•", fontSize = 16.sp, color = Rosetta.Violet, modifier = Modifier.width(16.dp))
                        Text(n, fontSize = 15.sp, lineHeight = 21.sp, color = Rosetta.Cream, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
