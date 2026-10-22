package app.rosetta.tv.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.FamilyMessage
import app.rosetta.tv.data.Household
import app.rosetta.tv.ui.theme.Rosetta
import kotlinx.coroutines.delay

/**
 * The newest message from a parent's phone drops in at the top of any screen for a few seconds, and [onShow]
 * lets the Keeper read it out. Only messages the TV received in the last few seconds are shown.
 */
@Composable
fun MessageBanner(household: Household, onShow: (FamilyMessage) -> Unit, modifier: Modifier = Modifier) {
    val latest = household.messages.lastOrNull()
    var shown by remember { mutableStateOf<FamilyMessage?>(null) }
    LaunchedEffect(latest?.id) {
        if (latest == null || System.currentTimeMillis() - latest.atMs > 5000) return@LaunchedEffect
        shown = latest
        onShow(latest)
        delay(8000)
        shown = null
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = shown != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val m = shown ?: return@AnimatedVisibility
            Row(
                Modifier
                    .padding(top = 28.dp)
                    .widthIn(max = 760.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Rosetta.Panel)
                    .border(2.dp, Rosetta.Gold, RoundedCornerShape(22.dp))
                    .padding(horizontal = 26.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💬", fontSize = 38.sp, modifier = Modifier.padding(end = 18.dp))
                Column {
                    Text(
                        "MESSAGE FROM ${m.from.uppercase()}", color = Rosetta.Gold, fontSize = 13.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    )
                    Text(m.text, style = MaterialTheme.typography.headlineSmall, color = Rosetta.Cream)
                }
            }
        }
    }
}
