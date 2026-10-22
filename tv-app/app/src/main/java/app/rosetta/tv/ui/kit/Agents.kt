package app.rosetta.tv.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.Agent
import app.rosetta.tv.data.AgentEvent
import app.rosetta.tv.ui.theme.Rosetta

fun Agent.color(): Color = when (this) {
    Agent.QuestMaster -> Rosetta.Gold
    Agent.Scout -> Rosetta.Sky
    Agent.Coach -> Rosetta.Lime
    Agent.Judge -> Rosetta.Violet
    Agent.Router -> Rosetta.Ember
    Agent.Keeper -> Rosetta.Rose
    Agent.Tutor -> Rosetta.Moss
}

fun Agent.glyph(): String = when (this) {
    Agent.QuestMaster -> "✦"
    Agent.Scout -> "◎"
    Agent.Coach -> "➜"
    Agent.Judge -> "⚖"
    Agent.Router -> "⇄"
    Agent.Keeper -> "❝"
    Agent.Tutor -> "✎"
}

/**
 * Live agent graph (AI Mission Control): Quest Master at the hub, specialists around it.
 * Edges animate with travelling dashes while a specialist is working; recently active nodes glow.
 */
@Composable
fun AgentGraph(active: Set<Agent>, done: Set<Agent>, modifier: Modifier = Modifier) {
    val dash by rememberInfiniteTransition(label = "dash").animateFloat(0f, 40f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "d")
    val pulse by rememberInfiniteTransition(label = "np").animateFloat(0.85f, 1.12f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "p")
    // Node centres in 0..1 of the box.
    val pos = mapOf(
        Agent.QuestMaster to Offset(0.5f, 0.2f),
        Agent.Scout to Offset(0.08f, 0.72f),
        Agent.Tutor to Offset(0.245f, 0.72f),
        Agent.Coach to Offset(0.41f, 0.72f),
        Agent.Judge to Offset(0.585f, 0.72f),
        Agent.Router to Offset(0.75f, 0.72f),
        Agent.Keeper to Offset(0.92f, 0.72f),
    )
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.matchParentSize()) {
            val hub = pos.getValue(Agent.QuestMaster).let { Offset(it.x * size.width, it.y * size.height) }
            pos.forEach { (agent, p) ->
                if (agent == Agent.QuestMaster) return@forEach
                val end = Offset(p.x * size.width, p.y * size.height)
                val on = agent in active
                drawLine(
                    color = if (on) agent.color() else Color.White.copy(alpha = if (agent in done) 0.32f else 0.12f),
                    start = hub, end = end, strokeWidth = if (on) 3.dp.toPx() else 1.5.dp.toPx(),
                    pathEffect = if (on) PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -dash) else null,
                )
            }
        }
        pos.forEach { (agent, p) ->
            val on = agent in active
            val wasDone = agent in done
            val s = if (agent == Agent.QuestMaster) 50.dp else 40.dp
            Column(
                Modifier.offset(w * p.x - 36.dp, h * p.y - s / 2).width(72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(s)
                        .graphicsLayer { val k = if (on) pulse else 1f; scaleX = k; scaleY = k }
                        .clip(CircleShape)
                        .background(if (on) agent.color() else if (wasDone) agent.color().copy(alpha = 0.22f) else Color(0xFF16203D))
                        .border(2.dp, agent.color().copy(alpha = if (on || wasDone) 1f else 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(agent.glyph(), fontSize = if (agent == Agent.QuestMaster) 22.sp else 17.sp, color = if (on) Rosetta.Night else agent.color())
                }
                Text(agent.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.sp), color = if (on) agent.color() else Rosetta.Cream, maxLines = 1)
            }
        }
    }
}

/** Live activity feed rows (newest at the bottom), with confidence bars where the agent reported one. */
@Composable
fun AgentFeed(events: List<AgentEvent>, modifier: Modifier = Modifier, max: Int = 5) {
    Column(modifier) {
        events.takeLast(max).forEach { e ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(e.agent.color()))
                Spacer(Modifier.width(8.dp))
                Text(e.agent.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.sp), color = e.agent.color(), modifier = Modifier.width(66.dp), maxLines = 1)
                Text(e.message, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp), color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (e.confidence != null) {
                    Spacer(Modifier.width(8.dp))
                    ConfidenceBar(e.confidence, Modifier.width(46.dp))
                }
            }
        }
    }
}

@Composable
fun ConfidenceBar(value: Float, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(value).height(5.dp).clip(RoundedCornerShape(50)).background(if (value >= 0.75f) Rosetta.Lime else Rosetta.Ember))
        }
        Spacer(Modifier.width(4.dp))
        Text("${(value * 100).toInt()}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = Rosetta.Mist)
    }
}
