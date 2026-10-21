package app.rosetta.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.rosetta.tv.data.DemoRepository
import app.rosetta.tv.data.RosettaRepository
import app.rosetta.tv.ui.common.KeeperVoice
import app.rosetta.tv.ui.home.HomeScreen
import app.rosetta.tv.ui.quest.QuestScreen
import app.rosetta.tv.ui.stone.MovieNightScreen
import app.rosetta.tv.ui.stone.StoneScreen
import app.rosetta.tv.ui.theme.RosettaTheme

/** Where the TV is. Kept tiny on purpose: a TV app has few places and Back must always make sense. */
sealed interface Screen {
    data object Home : Screen
    data class Quest(val id: String) : Screen
    data class Stone(val carvedGlyphId: String? = null) : Screen
    data object MovieNight : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var keeper: KeeperVoice
    private lateinit var repo: RosettaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keeper = KeeperVoice(this)
        repo = RosettaConfig.repository(this, lifecycleScope)

        setContent {
            RosettaTheme {
                val household by repo.household.collectAsState()
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }

                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(350)) },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen(
                            household = household,
                            onOpenQuest = { q -> repo.openQuest(q.id); screen = Screen.Quest(q.id) },
                            onOpenStone = { screen = Screen.Stone() },
                        )
                        is Screen.Quest -> {
                            // A finished quest leaves the board while this screen is still fading out,
                            // so a missing quest just renders nothing (navigation already moved on).
                            val quest = household.quests.firstOrNull { it.id == s.id }
                            if (quest != null) {
                                LaunchedEffect(quest.keeperLine) { keeper.speak(quest.keeperLine) }
                                QuestScreen(
                                    household = household,
                                    quest = quest,
                                    joinUrl = RosettaConfig.joinUrl(household.pairingCode),
                                    onDemoBefore = { repo.demoSendBefore(quest.id) },
                                    onDemoAfter = { repo.demoSendAfter(quest.id) },
                                    onCarveGlyph = {
                                        screen = Screen.Stone(carvedGlyphId = quest.glyphId)
                                        repo.closeQuest(quest.id)
                                    },
                                )
                            }
                        }
                        is Screen.Stone -> {
                            LaunchedEffect(s.carvedGlyphId) {
                                if (s.carvedGlyphId != null) keeper.speak(
                                    if (household.stoneComplete) "The last glyph is carved. The Family Stone is complete. Movie night is yours."
                                    else "A new glyph is carved into the Family Stone.",
                                )
                            }
                            StoneScreen(
                                household = household,
                                carvedGlyphId = s.carvedGlyphId,
                                onMovieNight = { screen = Screen.MovieNight },
                                onBack = { screen = Screen.Home },
                            )
                        }
                        Screen.MovieNight -> MovieNightScreen(household, onBack = { screen = Screen.Home })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        keeper.shutdown()
        super.onDestroy()
    }
}
