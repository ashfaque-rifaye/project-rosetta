package app.rosetta.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.rosetta.tv.data.QuestReward
import app.rosetta.tv.data.RosettaRepository
import app.rosetta.tv.ui.collection.CodexScreen
import app.rosetta.tv.ui.collection.PassportScreen
import app.rosetta.tv.ui.collection.ShopScreen
import app.rosetta.tv.ui.common.KeeperVoice
import app.rosetta.tv.ui.home.HomeScreen
import app.rosetta.tv.ui.home.HomeTab
import app.rosetta.tv.ui.kit.Sfx
import app.rosetta.tv.ui.quest.QuestScreen
import app.rosetta.tv.ui.reward.RewardScreen
import app.rosetta.tv.ui.stone.MovieNightScreen
import app.rosetta.tv.ui.stone.StoneScreen
import app.rosetta.tv.ui.theme.RosettaTheme
import app.rosetta.tv.ui.raid.RaidScreen
import app.rosetta.tv.ui.raid.EmoteLayer
import app.rosetta.tv.ui.growth.GrowthScreen
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

sealed interface Screen {
    data object Home : Screen
    data class Quest(val id: String) : Screen
    data class Reward(val reward: QuestReward) : Screen
    data class Stone(val carvedGlyphId: String? = null) : Screen
    data object MovieNight : Screen
    data object Codex : Screen
    data object Passport : Screen
    data object Shop : Screen
    data object Raid : Screen
    data object Growth : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var keeper: KeeperVoice
    private lateinit var repo: RosettaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keeper = KeeperVoice(this)
        Sfx.init(this)
        repo = RosettaConfig.repository(this, lifecycleScope)

        setContent {
            RosettaTheme {
                val household by repo.household.collectAsState()
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                var pendingReward by remember { mutableStateOf<QuestReward?>(null) }

                LaunchedEffect(Unit) { repo.rewards.collect { pendingReward = it } }
                BackHandler(enabled = screen != Screen.Home && screen !is Screen.Reward) { screen = Screen.Home }

                Box(Modifier.fillMaxSize().onPreviewKeyEvent {
                    // Hidden developer hook: fast-forward stands in for a sibling's phone sending an emote.
                    if (it.type == KeyEventType.KeyDown && it.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                        repo.emote(household.kids.random().id, listOf("🔥", "💀", "🫶", "⚡", "🏆", "😭").random()); true
                    } else false
                }) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.98f)) togetherWith fadeOut(tween(300)) },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen(
                            household = household,
                            onOpenQuest = { q -> repo.openQuest(q.id); screen = Screen.Quest(q.id) },
                            onTab = { tab ->
                                screen = when (tab) {
                                    HomeTab.Codex -> Screen.Codex
                                    HomeTab.Passport -> Screen.Passport
                                    HomeTab.Shop -> Screen.Shop
                                    HomeTab.Stone -> Screen.Stone()
                                    HomeTab.Growth -> Screen.Growth
                                    HomeTab.Raid -> Screen.Raid
                                }
                            },
                        )
                        is Screen.Quest -> household.quests.firstOrNull { it.id == s.id }?.let { quest ->
                            LaunchedEffect(quest.keeperLine) { keeper.speak(quest.keeperLine) }
                            QuestScreen(
                                household = household,
                                quest = quest,
                                joinUrl = RosettaConfig.joinUrl(household.pairingCode),
                                onClaim = { pendingReward?.takeIf { it.questId == quest.id }?.let { screen = Screen.Reward(it) } },
                                onDevPhone = { repo.devPhoneUpload(quest.id) },
                            )
                        }
                        is Screen.Reward -> RewardScreen(household, s.reward) {
                            repo.closeQuest(s.reward.questId)
                            pendingReward = null
                            screen = Screen.Stone(carvedGlyphId = s.reward.glyph?.id)
                        }
                        is Screen.Stone -> {
                            LaunchedEffect(s.carvedGlyphId) {
                                if (s.carvedGlyphId != null) keeper.speak(
                                    if (household.stoneComplete) "The last glyph is carved. The Family Stone is complete. Movie night is yours."
                                    else "A new glyph is carved into the Family Stone.",
                                )
                            }
                            StoneScreen(household, s.carvedGlyphId, onMovieNight = { screen = Screen.MovieNight }, onBack = { screen = Screen.Home })
                        }
                        Screen.MovieNight -> MovieNightScreen(household, onBack = { screen = Screen.Home })
                        Screen.Codex -> CodexScreen(household)
                        Screen.Passport -> PassportScreen(household)
                        Screen.Shop -> ShopScreen(household, onBuy = repo::buy, onEquip = repo::equip)
                        Screen.Raid -> RaidScreen(household, onDevHit = repo::devRaidHit, onDone = { screen = Screen.Home })
                        Screen.Growth -> GrowthScreen(household)
                    }
                }
                // Phone hype emotes burst over every screen.
                EmoteLayer(household)
                }
            }
        }
    }

    override fun onResume() { super.onResume(); Sfx.musicOn() }
    override fun onPause() { Sfx.musicOff(); super.onPause() }

    override fun onDestroy() {
        keeper.shutdown()
        Sfx.release()
        super.onDestroy()
    }
}
