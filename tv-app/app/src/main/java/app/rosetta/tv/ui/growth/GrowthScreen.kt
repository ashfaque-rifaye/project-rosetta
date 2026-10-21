package app.rosetta.tv.ui.growth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.Growth
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.theme.Rosetta
import kotlin.math.roundToInt

/**
 * Growth: how each kid's habits are developing, for parents (and kids who like seeing themselves level up).
 * Stat tiles for the headline numbers, one chart per measure (no dual axes), Coach agent notes in plain words.
 */
@Composable
fun GrowthScreen(household: Household) {
    var kidIdx by remember { mutableStateOf(0) }
    val kid = household.kids[kidIdx]
    val g = household.growth[kid.id] ?: return
    val color = Rosetta.KidSeries[kidIdx % Rosetta.KidSeries.size]
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1A2550), Rosetta.Night), radius = 1500f))) {
        Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 40.dp, top = 26.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GROWTH", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 3.sp), color = Rosetta.Gold)
                    Text("How ${kid.name} is levelling up", style = MaterialTheme.typography.displaySmall, color = Rosetta.Cream)
                }
                household.kids.forEachIndexed { i, k ->
                    RosettaButton(k.name, { kidIdx = i }, Modifier.padding(start = 10.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        icon = if (i == kidIdx) "★" else "☆", tone = if (i == kidIdx) ButtonTone.Lime else ButtonTone.Glass)
                }
            }
            Spacer(Modifier.height(14.dp))
            // ---------- Stat tiles ----------
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Quests this week", "${g.questsThisWeek}", delta(g.questsThisWeek - g.questsLastWeek, "vs last week", upIsGood = true), Modifier.weight(1f))
                StatTile("Avg tidy time", "${g.avgMinutesThisWeek.roundToInt()} min", delta((g.avgMinutesThisWeek - g.avgMinutesLastWeek).roundToInt(), "min vs last week", upIsGood = false), Modifier.weight(1f))
                StatTile("Starts on their own", "${(g.selfStartRate * 100).roundToInt()}%", "no reminder needed", Modifier.weight(1f))
                StatTile("Streak", "${kid.streak} days 🔥", "level ${kid.level} · ${kid.title}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.weight(1f)) {
                Column(Modifier.weight(1f)) {
                    ChartCard("Quests per day · last 4 weeks", Modifier.weight(1f)) { QuestBars(g, color) }
                    Spacer(Modifier.height(12.dp))
                    ChartCard("Minutes to tidy a room · lower is smoother", Modifier.weight(1f)) { MinutesLine(g, color) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.width(360.dp)) {
                    ChartCard("Skill balance", Modifier.height(168.dp)) { SkillBars(g, color) }
                    Spacer(Modifier.height(12.dp))
                    Glass(Modifier.fillMaxWidth().weight(1f)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(26.dp).clip(CircleShape).background(Rosetta.Lime.copy(alpha = 0.2f)).border(1.dp, Rosetta.Lime, CircleShape), contentAlignment = Alignment.Center) { Text("➜", color = Rosetta.Lime, fontSize = 13.sp) }
                                Spacer(Modifier.width(8.dp))
                                Text("COACH AGENT NOTES", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Cream)
                            }
                            Spacer(Modifier.height(8.dp))
                            g.insights.forEach { line ->
                                Row(Modifier.padding(vertical = 4.dp)) {
                                    Text("•", color = Rosetta.Gold, fontSize = 15.sp, modifier = Modifier.width(14.dp))
                                    Text(line, color = Rosetta.Cream, fontSize = 13.sp, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun delta(d: Int, unit: String, upIsGood: Boolean): String {
    if (d == 0) return "same as last week"
    val good = (d > 0) == upIsGood
    return "${if (d > 0) "▲" else "▼"} ${kotlin.math.abs(d)} $unit${if (good) " · nice" else ""}"
}

@Composable
private fun StatTile(label: String, value: String, sub: String, modifier: Modifier) {
    Glass(modifier.height(90.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = Rosetta.Mist)
            Text(value, fontSize = 27.sp, fontWeight = FontWeight.Black, color = Rosetta.Cream)
            Text(sub, fontSize = 12.sp, color = if (sub.contains("nice")) Rosetta.Lime else Rosetta.Mist, maxLines = 1)
        }
    }
}

@Composable
private fun ChartCard(title: String, modifier: Modifier, chart: @Composable () -> Unit) {
    Glass(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxSize()) { chart() }
        }
    }
}

/** 28 thin bars, rounded ends anchored to the baseline, 2px gaps, week separators; this week's bars at full colour. */
@Composable
private fun QuestBars(g: Growth, color: Color) {
    val grow = remember(g) { Animatable(0f) }
    LaunchedEffect(g) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val max = (g.days.maxOf { it.quests }).coerceAtLeast(1)
    Column(Modifier.fillMaxSize()) {
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            val n = g.days.size
            val gap = 4.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            // Recessive grid.
            for (i in 0..max) {
                val y = size.height - size.height * i / max
                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1f)
            }
            g.days.forEachIndexed { i, d ->
                val h = size.height * d.quests / max * grow.value
                val x = i * (bw + gap)
                val thisWeek = i >= n - 7
                if (h > 0) drawRoundRect(
                    color.copy(alpha = if (thisWeek) 1f else 0.45f), Offset(x, size.height - h), Size(bw, h), CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
                if (i % 7 == 0 && i > 0) drawLine(Color.White.copy(alpha = 0.18f), Offset(x - gap / 2, 0f), Offset(x - gap / 2, size.height), 1.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("3 wks ago", "2 wks ago", "last week", "this week").forEach {
                Text(it, fontSize = 11.sp, color = if (it == "this week") Rosetta.Cream else Rosetta.Mist, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 2px line + end marker with a direct label; the falling trend reads left to right. */
@Composable
private fun MinutesLine(g: Growth, color: Color) {
    val draw = remember(g) { Animatable(0f) }
    LaunchedEffect(g) { draw.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val pts = g.days.filter { it.quests > 0 }.map { it.minutes }
    val maxV = (pts.maxOrNull() ?: 1).coerceAtLeast(10)
    Row(Modifier.fillMaxSize()) {
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            for (i in 0..3) {
                val y = size.height * i / 3
                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1f)
            }
            if (pts.size < 2) return@Canvas
            val path = Path()
            val upto = (pts.size * draw.value).toInt().coerceAtLeast(2)
            pts.take(upto).forEachIndexed { i, v ->
                val x = size.width * i / (pts.size - 1)
                val y = size.height - size.height * v / maxV
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val lx = size.width * (upto - 1) / (pts.size - 1)
            val ly = size.height - size.height * pts[upto - 1] / maxV
            drawCircle(Rosetta.Night, 7.dp.toPx(), Offset(lx, ly))
            drawCircle(color, 5.dp.toPx(), Offset(lx, ly))
        }
        Column(Modifier.width(78.dp).padding(start = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("${pts.firstOrNull() ?: 0} min", fontSize = 12.sp, color = Rosetta.Mist)
            Text("${pts.lastOrNull() ?: 0} min", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Rosetta.Cream)
            Text("now", fontSize = 11.sp, color = Rosetta.Mist)
        }
    }
}

/** Labelled horizontal bars (a radar is harder to read); value printed in text ink, not series colour. */
@Composable
private fun SkillBars(g: Growth, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        g.skills.forEach { (name, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 13.sp, color = Rosetta.Cream, modifier = Modifier.width(78.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF))) {
                    Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
                }
                Text("${(v * 100).roundToInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rosetta.Mist, modifier = Modifier.width(32.dp).padding(start = 6.dp))
            }
        }
    }
}
