package app.rosetta.tv.ui.quest

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.rosetta.tv.data.DemoHousehold
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.LostThing
import app.rosetta.tv.data.Quest
import app.rosetta.tv.data.QuestStage
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.common.Eyebrow
import app.rosetta.tv.ui.common.QrCode
import app.rosetta.tv.ui.common.rememberImage
import app.rosetta.tv.ui.theme.Rosetta
import dev.rosetta.visionoverlay.Detection
import dev.rosetta.visionoverlay.VisionOverlay
import dev.rosetta.visionoverlay.VisionOverlayStyle
import kotlinx.coroutines.delay

private val PhotoW = 588.dp
private val PhotoH = PhotoW * (768f / 1376f)

@Composable
fun QuestScreen(
    household: Household,
    quest: Quest,
    joinUrl: String,
    onDemoBefore: () -> Unit,
    onDemoAfter: () -> Unit,
    onCarveGlyph: () -> Unit,
) {
    val kid = household.kid(quest.kidId)
    val room = household.room(quest.roomId)
    val action = remember { FocusRequester() }
    LaunchedEffect(quest.stage) { runCatching { action.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Rosetta.Deep, Rosetta.Night), radius = 1400f))) {
        Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 32.dp, top = 32.dp, bottom = 28.dp)) {
            // ---- The photo stage ----
            Column(Modifier.width(PhotoW)) {
                Eyebrow("${kid.name.uppercase()} · ${room.name.uppercase()}")
                Text(quest.title, style = MaterialTheme.typography.displaySmall, color = Rosetta.Cream, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                Box(
                    Modifier
                        .size(PhotoW, PhotoH)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Rosetta.Line, RoundedCornerShape(18.dp))
                        .background(Rosetta.Panel),
                ) {
                    AnimatedContent(
                        targetState = photoFor(quest),
                        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(400)) },
                        label = "photo",
                    ) { photo ->
                        if (photo == null) JoinPanel(household, kid.name, joinUrl)
                        else AssetImage(photo, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    }
                    if (quest.stage == QuestStage.Scanning || quest.stage == QuestStage.Verifying) ScanSweep()
                    if (quest.stage in listOf(QuestStage.Found, QuestStage.AwaitingAfter, QuestStage.Partial)) {
                        // Open-source component (tv-app/vision-overlay): boxes on the real objects, Lost Things perched above.
                        VisionOverlay(
                            detections = quest.things.map { Detection(it.id, it.box.l, it.box.t, it.box.r, it.box.b, resolved = it.returned) },
                            modifier = Modifier.fillMaxSize(),
                            pulseUnresolved = quest.stage == QuestStage.Partial,
                            style = VisionOverlayStyle(outline = Rosetta.Gold, fill = Rosetta.Gold.copy(alpha = 0.08f)),
                        ) { d ->
                            val thing = quest.things.first { it.id == d.id }
                            AssetImage(DemoHousehold.thingArt(thing.character), Modifier.fillMaxSize())
                        }
                    }
                    if (quest.stage == QuestStage.Done) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Rosetta.Gold.copy(alpha = 0.22f)))))
                    }
                }
                CrewStrip(quest.stage, Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.width(28.dp))

            // ---- The Keeper + Lost Things panel ----
            Column(Modifier.fillMaxHeight().weight(1f)) {
                Eyebrow("THE KEEPER")
                AnimatedContent(targetState = quest.keeperLine, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) }, label = "line") { line ->
                    Text(
                        "“$line”",
                        style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic, fontSize = 23.sp, lineHeight = 30.sp),
                        color = Rosetta.Cream,
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                    )
                }
                if (quest.things.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("LOST THINGS")
                        Spacer(Modifier.weight(1f))
                        Text("${quest.home}/${quest.found} home · ★ ${quest.starsEarned}", style = MaterialTheme.typography.labelLarge, color = Rosetta.Mist)
                    }
                    Spacer(Modifier.height(8.dp))
                    quest.things.forEach { ThingRow(it) }
                }
                Spacer(Modifier.weight(1f))
                ActionButton(quest, household.demo, Modifier.focusRequester(action), onDemoBefore, onDemoAfter, onCarveGlyph)
            }
        }
    }
}

private fun photoFor(q: Quest): String? = when (q.stage) {
    QuestStage.Ready, QuestStage.AwaitingBefore -> null
    QuestStage.Scanning, QuestStage.Found, QuestStage.AwaitingAfter -> q.beforePhoto
    QuestStage.Verifying, QuestStage.Partial, QuestStage.Done -> q.afterPhoto ?: q.beforePhoto
}

@Composable
private fun JoinPanel(household: Household, kidName: String, joinUrl: String) {
    Row(Modifier.fillMaxSize().padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
        QrCode(joinUrl, Modifier.size(170.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp))
        Column(Modifier.padding(start = 26.dp)) {
            Text("$kidName, grab your phone", style = MaterialTheme.typography.headlineSmall, color = Rosetta.Cream)
            Text(
                "Scan the code, or open the Rosetta page and type",
                style = MaterialTheme.typography.bodyMedium,
                color = Rosetta.Mist,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            Text(household.pairingCode, style = MaterialTheme.typography.displayMedium, color = Rosetta.Gold)
            Text("Then take one photo of the room.", style = MaterialTheme.typography.bodyMedium, color = Rosetta.Mist)
        }
    }
}

@Composable
private fun ScanSweep() {
    val t by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse), label = "y",
    )
    Box(Modifier.fillMaxSize().background(Rosetta.Night.copy(alpha = 0.25f)))
    Box(
        Modifier
            .offset(y = PhotoH * t - 40.dp)
            .fillMaxWidth()
            .height(80.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Rosetta.Gold.copy(alpha = 0.35f), Color.Transparent))),
    )
}

@Composable
private fun ThingRow(thing: LostThing) {
    val alpha by animateFloatAsState(if (thing.returned) 0.55f else 1f, tween(500), label = "a")
    Row(Modifier.padding(vertical = 2.dp).graphicsLayer { this.alpha = alpha }, verticalAlignment = Alignment.CenterVertically) {
        val img = rememberImage(DemoHousehold.thingArt(thing.character))
        Box(Modifier.size(32.dp).clip(CircleShape).background(Rosetta.Panel)) {
            if (img != null) Image(BitmapPainter(img), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(thing.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp), color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("→ ${thing.home}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp), color = Rosetta.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (thing.returned) "✓ HOME" else "★ ${thing.stars}", style = MaterialTheme.typography.labelLarge, color = if (thing.returned) Rosetta.Moss else Rosetta.Gold)
    }
}

/** The agent crew, visible while it works: Scout (find) → Coach (plan) → Judge (verify) → Keeper (speak). */
@Composable
private fun CrewStrip(stage: QuestStage, modifier: Modifier) {
    val active = when (stage) {
        QuestStage.Scanning -> setOf("Scout", "Coach")
        QuestStage.Found -> setOf("Keeper")
        QuestStage.Verifying -> setOf("Judge")
        QuestStage.Partial, QuestStage.Done -> setOf("Judge", "Keeper")
        else -> emptySet()
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("AGENTS", style = MaterialTheme.typography.labelMedium, color = Rosetta.Mist, modifier = Modifier.padding(end = 4.dp))
        listOf("Scout" to "finds", "Coach" to "plans", "Judge" to "checks", "Keeper" to "speaks").forEach { (name, verb) ->
            val on = name in active
            Text(
                "${if (on) "●" else "○"} $name · $verb",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                maxLines = 1,
                color = if (on) Rosetta.Night else Rosetta.Mist,
                modifier = Modifier
                    .background(if (on) Rosetta.Gold else Rosetta.Panel, RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ActionButton(
    quest: Quest,
    demo: Boolean,
    modifier: Modifier,
    onDemoBefore: () -> Unit,
    onDemoAfter: () -> Unit,
    onCarveGlyph: () -> Unit,
) {
    val (label, hint, onClick) = when (quest.stage) {
        QuestStage.Ready, QuestStage.AwaitingBefore ->
            Triple(if (demo) "Demo: send sample photo" else "Waiting for the phone…", "Phone photo → Scout & Coach agents", onDemoBefore)
        QuestStage.Scanning -> Triple("Reading the room…", "Usually 2–4 seconds", {})
        QuestStage.Found, QuestStage.AwaitingAfter ->
            Triple(if (demo) "Demo: send 'after' photo" else "Snap again when done", "Phone photo → Judge agent compares", onDemoAfter)
        QuestStage.Verifying -> Triple("Checking on everyone…", "Judge compares before and after", {})
        QuestStage.Partial -> Triple(if (demo) "Demo: send final photo" else "Snap again when done", "Only the lost ones left", onDemoAfter)
        QuestStage.Done -> Triple("Carve the glyph", "Add it to the Family Stone", onCarveGlyph)
    }
    Column {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Rosetta.Gold.copy(alpha = 0.16f),
                contentColor = Rosetta.Gold,
                focusedContainerColor = Rosetta.Gold,
                focusedContentColor = Rosetta.Night,
            ),
            border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, Rosetta.Gold.copy(alpha = 0.5f)))),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
        }
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = Rosetta.Mist, modifier = Modifier.padding(top = 6.dp))
    }
}
