package app.rosetta.tv.ui.collection

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.CodexEntry
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.ItemKind
import app.rosetta.tv.data.Kid
import app.rosetta.tv.data.ShopItem
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.common.rememberImage
import app.rosetta.tv.ui.kit.AvatarRing
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.FocusCard
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.Pill
import app.rosetta.tv.ui.kit.RailHeader
import app.rosetta.tv.ui.kit.RarityFrame
import app.rosetta.tv.ui.kit.RarityRibbon
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.kit.StatChip
import app.rosetta.tv.ui.reward.ui
import app.rosetta.tv.ui.theme.Rosetta

@Composable
private fun ScreenFrame(eyebrow: String, title: String, subtitle: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1A2550), Rosetta.Night), radius = 1500f))) {
        Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 40.dp, top = 28.dp, bottom = 20.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 3.sp), color = Rosetta.Gold)
            Text(title, style = MaterialTheme.typography.displaySmall, color = Rosetta.Cream)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Rosetta.Mist)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/* ================================ CODEX ================================ */

@Composable
fun CodexScreen(household: Household) {
    var focused by remember { mutableStateOf(household.codex.first()) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    ScreenFrame("THE LOST THINGS CODEX", "Everyone you've brought home", "Rescue a Lost Thing more often and its card grows rarer. Earned, never random.") {
        Row(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Fixed(4), Modifier.weight(1f), contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(household.codex) { i, e ->
                    CodexCard(e, Modifier.then(if (i == 0) Modifier.focusRequester(first) else Modifier), onFocus = { focused = e })
                }
            }
            Spacer(Modifier.width(20.dp))
            CodexDetail(focused, household, Modifier.width(300.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun CodexCard(e: CodexEntry, modifier: Modifier, onFocus: () -> Unit) {
    val r = e.rarity.ui()
    FocusCard(onClick = onFocus, onFocus = onFocus, accent = r.color, radius = 18.dp, modifier = modifier.height(196.dp)) {
        RarityFrame(r, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (e.discovered) RarityRibbon(r) else Pill("UNDISCOVERED", Rosetta.Mist)
                Spacer(Modifier.height(6.dp))
                val img = rememberImage(Household0.thing(e.character))
                Box(Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B1226))) {
                    if (img != null) Image(
                        BitmapPainter(img), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        colorFilter = if (e.discovered) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.08f, 0.1f, 0.18f, 1f) }),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(if (e.discovered) e.name else "???", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (e.discovered) "×${e.rescues} rescued" else "Not yet found", fontSize = 11.sp, color = Rosetta.Mist)
            }
        }
    }
}

@Composable
private fun CodexDetail(e: CodexEntry, h: Household, modifier: Modifier) {
    val r = e.rarity.ui()
    Glass(modifier, glow = r.glow) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            AssetImage(Household0.thing(e.character), Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, r.color, RoundedCornerShape(16.dp)))
            Spacer(Modifier.height(12.dp))
            RarityRibbon(r)
            Text(if (e.discovered) e.name else "Undiscovered", style = MaterialTheme.typography.headlineSmall, color = Rosetta.Cream, modifier = Modifier.padding(top = 6.dp))
            Text(if (e.discovered) e.lore else "Somewhere in the house, something is waiting to be found.", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = Rosetta.Mist, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.weight(1f))
            if (e.discovered) {
                Text("RESCUES ${e.rescues}" + if (r.ordinal < 3) " · NEXT RARITY AT ${e.nextAt}" else " · MAX RARITY", fontSize = 11.sp, fontWeight = FontWeight.Black, color = r.color, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth((e.rescues.toFloat() / e.nextAt).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(50)).background(r.color))
                }
                e.firstBy?.let { Text("First found by ${h.kid(it).name}", fontSize = 12.sp, color = Rosetta.Mist, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

/* ================================ PASSPORT ================================ */

@Composable
fun PassportScreen(household: Household) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    ScreenFrame("FAMILY PASSPORT", "Stamps, days and streaks", "Every kind of quest has its own stamp. Collect them all.") {
        Row(Modifier.fillMaxSize()) {
            // Passport book spread (cream paper).
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFEFE3CB), Rosetta.Paper, Color(0xFFEFE3CB))))) {
                Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().background(Color(0x22000000)))
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(household.stamps) { i, s ->
                        FocusCard(onClick = {}, accent = Rosetta.Ember, radius = 16.dp, modifier = Modifier.height(176.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier)) {
                            Box(Modifier.fillMaxSize().background(Rosetta.Paper)) {
                                Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    AssetImage(
                                        Household0.stamp(s.asset),
                                        Modifier.size(118.dp).graphicsLayer { rotationZ = (i % 3 - 1) * 7f; alpha = if (s.earnedOn != null) 1f else 0.16f },
                                        contentScale = ContentScale.Fit,
                                    )
                                    Text(s.name.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Rosetta.Ink, letterSpacing = 1.4.sp, maxLines = 1)
                                    Text(s.earnedOn?.let { d -> "$d · ${household.kid(s.by!!).name}" } ?: "not yet", fontSize = 11.sp, color = if (s.earnedOn != null) Rosetta.Ember else Color(0xFF9A8F7A))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            // Day Card.
            Glass(Modifier.width(330.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Text("TODAY'S DAY CARD", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Gold)
                    Spacer(Modifier.height(10.dp))
                    household.today.forEach { d ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AssetImage(Household0.avatar(household.kid(d.kidId).avatar), Modifier.size(38.dp).clip(CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(d.time, fontSize = 11.sp, color = Rosetta.Mist)
                                Text(d.text, fontSize = 14.sp, color = Rosetta.Cream, maxLines = 2)
                            }
                        }
                    }
                    if (household.today.isEmpty()) Text("No quests yet today.", color = Rosetta.Mist)
                    Spacer(Modifier.weight(1f))
                    RailHeader("Streaks")
                    Spacer(Modifier.height(8.dp))
                    household.kids.forEach { k ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(k.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream, modifier = Modifier.width(80.dp))
                            repeat(7) { d -> Text(if (d < k.streak) "🔥" else "·", fontSize = 16.sp, color = Rosetta.Mist, modifier = Modifier.width(22.dp)) }
                        }
                    }
                }
            }
        }
    }
}

/* ================================ SHOP ================================ */

@Composable
fun ShopScreen(household: Household, onBuy: (kidId: String, itemId: String) -> Boolean, onEquip: (kidId: String, itemId: String?) -> Unit) {
    var kidId by remember { mutableStateOf(household.kids.first().id) }
    val kid = household.kid(kidId)
    var focused by remember { mutableStateOf<ShopItem?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    ScreenFrame("THE SHOP", "Spend what you earned", "Outfits for your avatar and decor that appears on your house.") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            household.kids.forEachIndexed { i, k ->
                RosettaButton(
                    k.name, { kidId = k.id; focused = null }, Modifier.padding(end = 10.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    icon = if (k.id == kidId) "★" else "☆", tone = if (k.id == kidId) ButtonTone.Lime else ButtonTone.Glass,
                )
            }
            Spacer(Modifier.weight(1f))
            StatChip("🪙", "${kid.coins} coins")
            Spacer(Modifier.width(10.dp))
            StatChip("🔥", "${kid.streak} day streak", tint = Rosetta.Ember)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                RailHeader("Outfits for ${kid.name}")
                Spacer(Modifier.height(8.dp))
                ItemRail(household.shop.filter { it.kind == ItemKind.Avatar && it.kidId == kidId }, kid, household, { focused = it }) { item ->
                    when {
                        item.id in kid.owned -> { onEquip(kid.id, if (kid.equipped == item.id) null else item.id); toast = if (kid.equipped == item.id) "Took off the ${item.name}" else "${kid.name} is wearing the ${item.name}!" }
                        onBuy(kid.id, item.id) -> toast = "${kid.name} bought the ${item.name}!"
                        else -> toast = "Not enough coins yet. Finish a quest!"
                    }
                }
                Spacer(Modifier.height(14.dp))
                RailHeader("Decor for the house")
                Spacer(Modifier.height(8.dp))
                ItemRail(household.shop.filter { it.kind == ItemKind.Decor }, kid, household, { focused = it }) { item ->
                    toast = when {
                        item.id in household.decor -> "The ${item.name} is already on the house"
                        onBuy(kid.id, item.id) -> "${kid.name} added the ${item.name} to the house!"
                        else -> "Not enough coins yet. Finish a quest!"
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Glass(Modifier.width(280.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val preview = focused?.takeIf { it.kind == ItemKind.Avatar }?.avatar ?: kid.avatar
                    AvatarRing(Household0.avatar(preview), kid.levelProgress, 170.dp, level = kid.level)
                    Text(kid.name, style = MaterialTheme.typography.headlineSmall, color = Rosetta.Cream, modifier = Modifier.padding(top = 10.dp))
                    Text("${kid.title} · level ${kid.level}", color = Rosetta.Mist, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    focused?.let { Text(it.name, style = MaterialTheme.typography.titleMedium, color = Rosetta.Gold) }
                    Spacer(Modifier.weight(1f))
                    toast?.let { Text(it, color = Rosetta.Lime, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ItemRail(items: List<ShopItem>, kid: Kid, h: Household, onFocus: (ShopItem) -> Unit, onClick: (ShopItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
        items(items, key = { it.id }) { item ->
            val owned = item.id in kid.owned || item.id in h.decor
            val equipped = kid.equipped == item.id
            FocusCard(onClick = { onClick(item) }, onFocus = { onFocus(item) }, accent = if (owned) Rosetta.Lime else Rosetta.Gold, radius = 18.dp, modifier = Modifier.width(132.dp).height(150.dp)) {
                Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val asset = if (item.kind == ItemKind.Avatar) Household0.avatar(item.avatar!!) else Household0.item(item.asset)
                    AssetImage(asset, Modifier.size(80.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.height(6.dp))
                    Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    when {
                        equipped -> Pill("WEARING", Rosetta.Lime, filled = true)
                        owned -> Pill(if (item.kind == ItemKind.Decor) "ON THE HOUSE" else "OWNED", Rosetta.Lime)
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            AssetImage(Household0.COIN, Modifier.size(18.dp), contentScale = ContentScale.Fit)
                            Text(" ${item.price}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = if (kid.coins >= item.price) Rosetta.Gold else Rosetta.Mist)
                        }
                    }
                }
            }
        }
    }
}
