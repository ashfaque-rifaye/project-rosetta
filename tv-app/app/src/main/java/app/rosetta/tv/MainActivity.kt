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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
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
import app.rosetta.tv.ui.common.MessageBanner
import app.rosetta.tv.ui.study.QuizLoadingScreen
import app.rosetta.tv.ui.study.QuizScreen
import app.rosetta.tv.ui.study.StudyScreen
import app.rosetta.tv.ui.raid.ArenaLauncher
import androidx.activity.result.contract.ActivityResultContracts
import app.rosetta.tv.ui.growth.GrowthScreen
import app.rosetta.tv.ui.habit.HabitScreen
import app.rosetta.tv.ui.home.WatchApp
import app.rosetta.tv.ui.intro.IntroScreen
import app.rosetta.tv.ui.parents.ParentsScreen
import android.content.Intent
import android.widget.Toast
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
    data object Intro : Screen
    data object Home : Screen
    data class Quest(val id: String) : Screen
    data class Reward(val reward: QuestReward) : Screen
    data class Stone(val carvedGlyphId: String? = null) : Screen
    data object MovieNight : Screen
    data object Codex : Screen
    data object Passport : Screen
    data object Shop : Screen
    data object Raid : Screen
    data object Quiz : Screen
    data object QuizLoading : Screen
    data class Growth(val kidId: String? = null) : Screen
    data object Parents : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var keeper: KeeperVoice
    private lateinit var repo: RosettaRepository

    /** The home music waits for the intro film (it has its own score). */
    private var introDone = false

    /** When TV time opened a streaming app: the minutes away are reported as used when Rosetta comes back. */
    private var watchStartMs = 0L

    /** Earned TV time: open the first installed package of [app] (Fire TV can't lock other apps, so time is counted honestly). */
    private fun watch(app: WatchApp) {
        val launch = app.packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) ?: packageManager.getLeanbackLaunchIntentForPackage(it) }
        if (launch == null) { Toast.makeText(this, "${app.label} isn't installed on this TV", Toast.LENGTH_LONG).show(); return }
        watchStartMs = System.currentTimeMillis()
        runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { watchStartMs = 0L }
    }

    // The Unity arena hands back the raid's hits when the family presses Back.
    private val arena = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        ArenaLauncher.quizAnswers(r.data)?.let { answers ->
            val known = repo.household.value.quiz?.answers?.map { it.questionId }?.toSet().orEmpty()
            answers.filter { it.questionId !in known }.forEach { repo.answerQuiz(it.questionId, it.kidId, it.choice) }
        } ?: ArenaLauncher.hits(r.data)?.let(repo::applyArenaHits)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keeper = KeeperVoice(this)
        Sfx.init(this)
        repo = RosettaConfig.repository(this, lifecycleScope)

        if (savedInstanceState != null) introDone = true
        setContent {
            RosettaTheme {
                val household by repo.household.collectAsState()
                // Debug builds only: `--es screen <name>` opens a screen directly (for filming the demo).
                val direct = intent.getStringExtra("screen")?.takeIf { BuildConfig.DEBUG }?.let { name ->
                    when (name) {
                        "home" -> Screen.Home; "goal" -> Screen.Stone(); "treasure" -> Screen.Shop; "squad" -> Screen.Codex
                        "streaks" -> Screen.Passport; "parents" -> Screen.Parents; "movie" -> Screen.MovieNight
                        "arjun" -> Screen.Growth("arjun"); "meera" -> Screen.Growth("meera"); else -> null
                    }
                }
                if (direct != null && !introDone) { introDone = true; Sfx.musicOn() }
                var screen by remember { mutableStateOf<Screen>(direct ?: if (introDone) Screen.Home else Screen.Intro) }
                var pendingReward by remember { mutableStateOf<QuestReward?>(null) }

                var arenaQuizPending by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) { repo.rewards.collect { pendingReward = it } }
                BackHandler(enabled = screen != Screen.Home && screen !is Screen.Reward && screen != Screen.Intro) { screen = Screen.Home }
                LaunchedEffect(arenaQuizPending, household.quiz) {
                    val quiz = household.quiz
                    if (arenaQuizPending && quiz != null && !quiz.done && quiz.questions.isNotEmpty()) {
                        arenaQuizPending = false
                        ArenaLauncher.quizIntent(this@MainActivity, household)?.let(arena::launch)
                        // The game covers the TV; when it hands back the answers, the family lands on Home.
                        screen = Screen.Home
                    }
                }

                Box(Modifier.fillMaxSize().onPreviewKeyEvent {
                    when {
                        // Hidden developer hook: fast-forward stands in for a sibling's phone sending an emote.
                        it.type == KeyEventType.KeyDown && it.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            repo.emote(household.kids.random().id, listOf("🔥", "💀", "🫶", "⚡", "🏆", "😭").random()); true
                        }
                        // One Back press goes Home: on TV, Compose otherwise spends the first press clearing focus.
                        it.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_BACK && screen != Screen.Home && screen !is Screen.Reward && screen != Screen.Intro -> {
                            if (it.type == KeyEventType.KeyUp) { arenaQuizPending = false; screen = Screen.Home }
                            true
                        }
                        else -> false
                    }
                }) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.98f)) togetherWith fadeOut(tween(300)) },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Intro -> IntroScreen(onDone = {
                            if (!introDone) { introDone = true; Sfx.musicOn() }
                            screen = Screen.Home
                        })
                        Screen.Home -> HomeScreen(
                            household = household,
                            onOpenQuest = { q -> repo.openQuest(q.id); screen = Screen.Quest(q.id) },
                            onRequestTv = repo::requestTvTime,
                            onWatch = ::watch,
                            onKid = { screen = Screen.Growth(it) },
                            onParents = { screen = Screen.Parents },
                            onTab = { tab ->
                                screen = when (tab) {
                                    HomeTab.Codex -> Screen.Codex
                                    HomeTab.Passport -> Screen.Passport
                                    HomeTab.Shop -> Screen.Shop
                                    HomeTab.Stone -> Screen.Stone()
                                    HomeTab.Growth -> Screen.Growth()
                                    // Family Quiz Night runs in the Unity arena when it's installed, natively otherwise.
                                    HomeTab.Quiz -> {
                                        if (household.quiz == null || household.quiz?.done == true) repo.startQuiz()
                                        // On-device the quiz is ready at once; live, the AI takes a few seconds to write it,
                                        // so the arena opens (above) as soon as its questions land.
                                        arenaQuizPending = ArenaLauncher.isInstalled(this@MainActivity)
                                        if (arenaQuizPending) Screen.QuizLoading else Screen.Quiz
                                    }
                                    HomeTab.Raid -> ArenaLauncher.intent(this@MainActivity, household)
                                        ?.takeIf { ArenaLauncher.isInstalled(this@MainActivity) }
                                        ?.let { arena.launch(it); screen } ?: Screen.Raid
                                }
                            },
                        )
                        is Screen.Quest -> household.quests.firstOrNull { it.id == s.id }?.let { quest ->
                            LaunchedEffect(quest.keeperLine) {
                                // Live: the text lands first and the agents' Gemini voice a few seconds later, so wait for it.
                                val audio = if (!household.live) null else withTimeoutOrNull(25_000) {
                                    repo.household.map { h -> h.quests.firstOrNull { it.id == quest.id }?.takeIf { it.keeperLine == quest.keeperLine }?.keeperAudio }
                                        .first { it != null }
                                }
                                keeper.speak(quest.keeperLine, audio)
                            }
                            if (quest.isHabit) HabitScreen(
                                household = household,
                                quest = quest,
                                joinUrl = RosettaConfig.joinUrl(household.pairingCode),
                                onClaim = { pendingReward?.takeIf { it.questId == quest.id }?.let { screen = Screen.Reward(it) } },
                            ) else if (quest.isStudy) StudyScreen(
                                household = household,
                                quest = quest,
                                joinUrl = RosettaConfig.joinUrl(household.pairingCode),
                                onAnswer = { qid, choice -> repo.answerPractice(quest.id, qid, choice) },
                                onClaim = { pendingReward?.takeIf { it.questId == quest.id }?.let { screen = Screen.Reward(it) } },
                                onDevPhone = { repo.devPhoneUpload(quest.id) },
                            ) else QuestScreen(
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
                                    if (household.stoneComplete) "That's every star. The Family Goal is done. Movie night is yours!"
                                    else "A new star for the Family Goal. ${household.glyphsEarned} of ${household.glyphs.size}.",
                                )
                            }
                            StoneScreen(household, s.carvedGlyphId, onMovieNight = { screen = Screen.MovieNight }, onBack = { screen = Screen.Home })
                        }
                        Screen.MovieNight -> MovieNightScreen(household, onBack = { screen = Screen.Home })
                        Screen.Codex -> CodexScreen(household)
                        Screen.Passport -> PassportScreen(household)
                        Screen.Shop -> ShopScreen(household, onBuy = repo::buy, onEquip = repo::equip)
                        Screen.Raid -> RaidScreen(household, onDevHit = repo::devRaidHit, onDone = { screen = Screen.Home })
                        is Screen.Growth -> GrowthScreen(household, s.kidId)
                        Screen.Parents -> ParentsScreen(household, RosettaConfig.parentUrl(household.pairingCode), onProgress = { screen = Screen.Growth() })
                        Screen.QuizLoading -> QuizLoadingScreen(household)
                        Screen.Quiz -> QuizScreen(household, onAnswer = repo::answerQuiz, onDone = { screen = Screen.Home })
                    }
                }
                // Phone hype emotes burst over every screen.
                EmoteLayer(household)
                // Mom and Dad's messages from the parent page drop in on any screen; the Keeper reads them out.
                MessageBanner(household, onShow = { keeper.speak("Message from ${it.from}. ${it.text}", it.audio) })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (introDone) Sfx.musicOn()
        if (watchStartMs > 0) {
            val minutes = ((System.currentTimeMillis() - watchStartMs + 59_999) / 60_000).toInt()
            watchStartMs = 0L
            repo.useTvTime(minutes)
        }
    }
    override fun onPause() { Sfx.musicOff(); super.onPause() }

    override fun onDestroy() {
        keeper.shutdown()
        Sfx.release()
        super.onDestroy()
    }
}
