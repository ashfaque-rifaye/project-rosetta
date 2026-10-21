package app.rosetta.tv.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.rosetta.tv.data.DemoHousehold
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.NormRect
import app.rosetta.tv.data.Quest
import app.rosetta.tv.data.QuestStage
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.common.Eyebrow
import app.rosetta.tv.ui.theme.Rosetta

@Composable
fun HomeScreen(
    household: Household,
    onOpenQuest: (Quest) -> Unit,
    onOpenStone: () -> Unit,
) {
    val active = household.quests.filter { it.stage != QuestStage.Done }
    var focusedQuest by remember { mutableStateOf<Quest?>(null) }
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstCard.requestFocus() } }

    // "Camera": the house sits right of the quest cards; focusing a quest glides its room to the open stage.
    val target = focusedQuest?.let { household.room(it.roomId).rect }
    val zoomTarget = if (target != null) 1.28f else 1f
    val stageX = if (target != null) 0.68f else 0.62f
    val stageY = if (target != null) 0.50f else 0.52f
    val anim = tween<Float>(1000, easing = FastOutSlowInEasing)
    val zoom by animateFloatAsState(zoomTarget, anim, label = "zoom")
    // With a centered pivot, a point p lands at 0.5 + (p - 0.5) * zoom + t; solve t so the room lands on the stage.
    val panX by animateFloatAsState(stageX - 0.5f - ((target?.cx ?: 0.5f) - 0.5f) * zoomTarget, anim, label = "panX")
    val panY by animateFloatAsState(stageY - 0.5f - ((target?.cy ?: 0.5f) - 0.5f) * zoomTarget, anim, label = "panY")
    val spotX by animateFloatAsState(target?.cx ?: 0.5f, anim, label = "spotX")
    val spotY by animateFloatAsState(target?.cy ?: 0.5f, anim, label = "spotY")
    val spotDark by animateFloatAsState(if (target != null) 0.62f else 0.12f, anim, label = "spotDark")

    BoxWithConstraints(Modifier.fillMaxSize().background(Rosetta.Night)) {
        val w = maxWidth
        val h = maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = panX * size.width
                    translationY = panY * size.height
                },
        ) {
            AssetImage(DemoHousehold.HOME_ART, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            // Soft spotlight: the rest of the house falls into dusk around the focused room.
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(spotX * size.width, spotY * size.height)
                drawRect(
                    Brush.radialGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Rosetta.Night.copy(alpha = spotDark),
                        center = c,
                        radius = size.width * 0.20f,
                    ),
                )
            }
            active.forEach { q ->
                Waypoint(household.room(q.roomId).rect, w.value, h.value, highlighted = q.id == focusedQuest?.id)
            }
        }

        // Left reading gradient + title + quest cards.
        Box(
            Modifier
                .fillMaxHeight()
                .width(420.dp)
                .background(Brush.horizontalGradient(listOf(Rosetta.Night.copy(alpha = 0.92f), Rosetta.Night.copy(alpha = 0.0f)))),
        )
        Column(Modifier.padding(start = 48.dp, top = 36.dp).width(330.dp)) {
            Eyebrow("ROSETTA")
            Spacer(Modifier.height(6.dp))
            Text("Tonight at home", style = MaterialTheme.typography.displaySmall, color = Rosetta.Cream)
            Text(
                "${active.size} rooms are waiting. Help the Lost Things find their way home.",
                style = MaterialTheme.typography.bodyMedium,
                color = Rosetta.Mist,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            )
            active.forEachIndexed { i, q ->
                QuestCard(
                    quest = q,
                    household = household,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .then(if (i == 0) Modifier.focusRequester(firstCard) else Modifier)
                        .onFocusChanged { if (it.isFocused) focusedQuest = q },
                    onClick = { onOpenQuest(q) },
                )
            }
        }

        // Top-right: join code + demo badge.
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (household.demo) Pill("DEMO HOUSEHOLD", Rosetta.Mist)
            Pill("JOIN ${household.pairingCode}", Rosetta.Gold)
        }

        // Bottom-right: the Family Stone.
        StoneWidget(
            household = household,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 28.dp)
                .onFocusChanged { if (it.isFocused) focusedQuest = null },
            onClick = onOpenStone,
        )
    }
}

@Composable
private fun Waypoint(r: NormRect, w: Float, h: Float, highlighted: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "t")
    val size = if (highlighted) 26.dp else 20.dp
    Canvas(
        Modifier
            .offset((r.cx * w).dp - 30.dp, (r.cy * h).dp - 30.dp)
            .size(60.dp),
    ) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        drawCircle(Rosetta.Waypoint.copy(alpha = (1f - t) * 0.6f), radius = size.toPx() / 2 + t * 22.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
        drawCircle(Rosetta.Waypoint.copy(alpha = 0.35f), radius = size.toPx() / 2 + 4.dp.toPx(), center = c)
        drawCircle(Rosetta.Waypoint, radius = size.toPx() / 2 - 2.dp.toPx(), center = c)
    }
}

@Composable
private fun QuestCard(quest: Quest, household: Household, modifier: Modifier, onClick: () -> Unit) {
    val kid = household.kid(quest.kidId)
    val room = household.room(quest.roomId)
    Surface(
        onClick = onClick,
        modifier = modifier.width(330.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Rosetta.Panel.copy(alpha = 0.78f),
            focusedContainerColor = Rosetta.Deep,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Rosetta.Line)),
            focusedBorder = Border(BorderStroke(2.dp, Rosetta.Gold)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Rosetta.Gold.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text(kid.name.take(1), style = MaterialTheme.typography.titleLarge, color = Rosetta.Gold) }
            Column(Modifier.padding(start = 14.dp)) {
                Text("${kid.name.uppercase()} · ${room.name.uppercase()}", style = MaterialTheme.typography.labelMedium, color = Rosetta.Mist)
                Text(quest.title, style = MaterialTheme.typography.titleMedium, color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(questStatus(quest), style = MaterialTheme.typography.bodyMedium, color = Rosetta.Gold)
            }
        }
    }
}

private fun questStatus(q: Quest) = when (q.stage) {
    QuestStage.Ready -> "Waiting for a scan"
    QuestStage.AwaitingBefore, QuestStage.Scanning -> "Scanning the room…"
    QuestStage.Found, QuestStage.AwaitingAfter, QuestStage.Verifying -> "${q.found} Lost Things found"
    QuestStage.Partial -> "${q.home} of ${q.found} home"
    QuestStage.Done -> "All home"
}

@Composable
private fun StoneWidget(household: Household, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(300.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Rosetta.Panel.copy(alpha = 0.86f), focusedContainerColor = Rosetta.Deep),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Rosetta.Line)),
            focusedBorder = Border(BorderStroke(2.dp, Rosetta.Gold)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AssetImage(DemoHousehold.STONE_ART, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.padding(start = 12.dp)) {
                Eyebrow("THE FAMILY STONE")
                Text("${household.glyphsEarned} of ${household.glyphs.size} glyphs", style = MaterialTheme.typography.titleMedium, color = Rosetta.Cream)
                val left = household.glyphs.size - household.glyphsEarned
                Text(
                    if (left == 0) "Movie night unlocked!" else "$left more to unlock movie night",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Rosetta.Gold,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .background(Rosetta.Night.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
