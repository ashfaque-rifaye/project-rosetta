package app.rosetta.tv.ui.parents

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.rosetta.tv.data.Household
import app.rosetta.tv.data.Household0
import app.rosetta.tv.data.QuestStage
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.common.QrCode
import app.rosetta.tv.ui.kit.ButtonTone
import app.rosetta.tv.ui.kit.Glass
import app.rosetta.tv.ui.kit.RosettaButton
import app.rosetta.tv.ui.theme.Rosetta

/**
 * For Mom and Dad (and anyone trying Rosetta): the parent page is on the phone, so the TV shows how to get there —
 * a QR code, the link, the demo PIN — plus what's waiting for a grown-up right now.
 */
private const val DEMO_CODE = "R4KX"

@Composable
fun ParentsScreen(household: Household, parentUrl: String?, onProgress: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val needs = household.quests.count { it.needsParent || it.stage == QuestStage.Checking } +
        household.wishlist.count { it.status == "requested" } + (if (household.tvTime.requested) 1 else 0)

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1A2550), Rosetta.Night), radius = 1500f))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            // ---------- Left: who and what ----------
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    household.parents.forEach { p ->
                        AssetImage(Household0.avatar(p.avatar), Modifier.padding(end = 12.dp).size(84.dp).clip(CircleShape).border(3.dp, Rosetta.Sky, CircleShape))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("FOR MOM AND DAD", fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Rosetta.Sky)
                Text("Rosetta lives on your phone too", fontFamily = FontFamily.Serif, fontSize = 40.sp, lineHeight = 46.sp, color = Rosetta.Cream)
                Spacer(Modifier.height(14.dp))
                listOf(
                    "📱" to "Get a ping when homework is checked, a voice note arrives, or someone asks for TV time",
                    "✅" to "Approve what needs a grown-up, in one tap",
                    "🗓️" to "Add daily tasks: make the bed, read 10 pages, prayer time",
                    "🎙️" to "Replay your children's voice notes, kept as memories",
                    "💰" to "Set pocket money, add to the wish list, and choose TV minutes",
                    "📚" to "Tell the ✦ AI Tutor each child's class, subjects and next test",
                ).forEach { (icon, text) ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(icon, fontSize = 22.sp, modifier = Modifier.width(40.dp))
                        Text(text, fontSize = 18.sp, color = Rosetta.Cream)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RosettaButton("See Progress", onProgress, Modifier.focusRequester(first), icon = "📈", sub = "each child, on the TV", tone = ButtonTone.Gold, big = true)
                    if (needs > 0) Text("🙋 $needs waiting for a grown-up", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Rosetta.Ember)
                }
            }
            Spacer(Modifier.width(40.dp))
            // ---------- Right: how to open it ----------
            Glass(Modifier.width(470.dp), radius = 30.dp, glow = Rosetta.Sky.copy(alpha = 0.5f)) {
                Column(Modifier.fillMaxWidth().padding(30.dp).focusable(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (parentUrl == null) {
                        Text("The parent page needs Rosetta online.", fontSize = 20.sp, color = Rosetta.Cream)
                        Text("This TV is running offline right now.", fontSize = 16.sp, color = Rosetta.Mist)
                    } else {
                        Text("SCAN WITH YOUR PHONE CAMERA", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Rosetta.Sky)
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp)) {
                            QrCode(parentUrl, Modifier.size(250.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("or open", fontSize = 14.sp, color = Rosetta.Mist)
                        Text(parentUrl.removePrefix("https://"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Rosetta.Cream)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("FAMILY CODE", fontSize = 12.sp, letterSpacing = 2.sp, color = Rosetta.Mist)
                                Text(household.pairingCode, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, color = Rosetta.Gold)
                            }
                            // Only the public demo family shows its PIN; a real family's PIN stays off the TV kids watch.
                            if (household.pairingCode == DEMO_CODE) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("DEMO PIN", fontSize = 12.sp, letterSpacing = 2.sp, color = Rosetta.Mist)
                                Text("2468", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, color = Rosetta.Gold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("No app to install. Kids use the QR on any task.", fontSize = 13.sp, color = Rosetta.Mist)
                    }
                }
            }
        }
    }
}
