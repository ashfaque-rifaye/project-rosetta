package app.rosetta.tv.data

import app.rosetta.tv.ui.kit.Sfx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device household engine for offline mode: drives quests through the same stages as the cloud and keeps
 * progression (XP, coins, stars, stamps, Squad) earned in this session only; nothing is pre-filled. In live mode
 * [CloudRepository] replaces the whole household with the server's every second ([applyRemote]).
 */
class LocalRepository(private val scope: CoroutineScope) : RosettaRepository {

    private val state = MutableStateFlow(Household0.initial())
    override val household: StateFlow<Household> = state
    private val rewardFlow = MutableSharedFlow<QuestReward>(extraBufferCapacity = 4)
    override val rewards: SharedFlow<QuestReward> = rewardFlow
    private val t0 = System.currentTimeMillis()

    override fun openQuest(questId: String) = update(questId) {
        if (it.stage != QuestStage.Ready) it else it.copy(
            stage = QuestStage.AwaitingBefore,
            keeperLine = if (it.isStudy) "Snap your homework, ${kid(it).name} 📸 The AI Tutor will take a look."
            else "Snap your room, ${kid(it).name} 📸 Let's find the Lost Things.",
            log = it.log + ev(Agent.QuestMaster, "Quest opened on the TV · waiting for ${kid(it).name}'s phone"),
        )
    }

    override fun devPhoneUpload(questId: String) {
        val q = state.value.quest(questId)
        if (q.isStudy) {
            if (q.stage == QuestStage.Ready || q.stage == QuestStage.AwaitingBefore) homework(questId)
            return
        }
        when (q.stage) {
            QuestStage.Ready, QuestStage.AwaitingBefore -> before(questId)
            QuestStage.Found, QuestStage.AwaitingAfter -> after(questId, final = false)
            QuestStage.Partial -> after(questId, final = true)
            else -> Unit
        }
    }

    private fun before(id: String) {
        update(id) {
            it.copy(
                stage = QuestStage.Scanning, beforePhoto = Household0.BEFORE, keeperLine = "Looking… 👀",
                log = it.log + ev(Agent.QuestMaster, "Photo received from phone · routing to Scout", tool = "scout_room"),
            )
        }
        scope.launch {
            delay(900); log(id, Agent.Scout, "Reading the room · bedroom, evening light, 1 photo")
            delay(1100); log(id, Agent.Scout, "5 out-of-place objects found", conf = 0.91f)
            update(id) { it.copy(things = Household0.referenceScan().map { t -> t }, stage = QuestStage.Found) }
            delay(700); log(id, Agent.Coach, "Homes assigned · sock→basket, Rex→toy box, bricks→bin, books→shelf, jacket→hooks", tool = "plan_homes")
            delay(600)
            update(id) {
                it.copy(
                    keeperLine = "5 Lost Things spotted! Bring them home, ${kid(it).name} 🏠",
                    log = it.log + ev(Agent.Keeper, "Spoke to ${kid(it).name}", tool = "keeper_says"),
                )
            }
        }
    }

    /** Tutor marks the sheet (replaying the real Nova Pro output for the demo sheets), then Practice takes over. */
    private fun homework(id: String) {
        val q = state.value.quest(id)
        val (sheet, marked, practice) =
            if (q.kidId == "meera") Triple(HomeworkDemo.MEERA_SHEET, HomeworkDemo.meera, HomeworkDemo.meeraPractice)
            else Triple(HomeworkDemo.ARJUN_SHEET, HomeworkDemo.arjun, HomeworkDemo.arjunPractice)
        update(id) {
            it.copy(
                stage = QuestStage.Scanning, beforePhoto = sheet, keeperLine = "The AI Tutor is reading your work… ✏️",
                log = it.log + ev(Agent.QuestMaster, "Homework photo received · routing to Tutor", tool = "mark_homework"),
            )
        }
        scope.launch {
            delay(900); log(id, Agent.Tutor, "Reading the sheet · ${marked.subject}, ${marked.items.size} questions")
            delay(1300)
            update(id) { it.copy(homework = marked, stage = QuestStage.Checked, confidence = marked.confidence) }
            log(id, Agent.Tutor, "${marked.right} of ${marked.items.size} right · ${marked.topic}", conf = marked.confidence)
            delay(700)
            val exact = marked.items.count { it.checkedBy == "math" }
            if (exact > 0) log(id, Agent.Tutor, "$exact answers double-checked with exact maths", tool = "mathcheck")
            delay(800)
            if (marked.needsWork.isEmpty()) {
                update(id) { it.copy(stage = QuestStage.Done, keeperLine = "Every answer right. Big brain energy 🧠🏆") }
                rewardFlow.tryEmit(grantRewards(id))
                return@launch
            }
            log(id, Agent.Coach, "${practice.size} practice questions on ${marked.needsWork.joinToString(", ")}", tool = "make_practice")
            update(id) { it.copy(stage = QuestStage.Practising, practice = practice, answers = emptyMap()) }
            delay(500)
            update(id) {
                it.copy(
                    keeperLine = "${marked.right} out of ${marked.items.size}, ${kid(it).name}! Two need another look. Quick practice round? 🎯",
                    log = it.log + ev(Agent.Keeper, "Cheered and set up practice", tool = "keeper_says"),
                )
            }
        }
    }

    override fun answerPractice(questId: String, questionId: String, choice: Int) {
        val q = state.value.quest(questId)
        if (q.stage != QuestStage.Practising || q.answers.containsKey(questionId)) return
        val pq = q.practice.first { it.id == questionId }
        val answers = q.answers + (questionId to Answer(choice, choice == pq.answer))
        update(questId) { it.copy(answers = answers) }
        Sfx.play(if (choice == pq.answer) Sfx.Home else Sfx.Move)
        if (answers.size == q.practice.size) scope.launch {
            delay(1600)
            val right = answers.values.count { it.correct }
            update(questId) {
                it.copy(
                    stage = QuestStage.Done,
                    keeperLine = "$right of ${q.practice.size} in practice. That's how skills level up, ${kid(it).name} ⚡",
                    log = it.log + ev(Agent.QuestMaster, "Practice finished", tool = "complete_quest"),
                )
            }
            rewardFlow.tryEmit(grantRewards(questId))
        }
    }

    override fun startQuiz() {
        if (state.value.quiz?.done == false) return
        state.update { it.copy(quiz = Quiz("quiz_${System.currentTimeMillis()}", "Family Quiz Night", HomeworkDemo.familyQuiz)) }
    }

    override fun answerQuiz(questionId: String, kidId: String, choice: Int) {
        val quiz = state.value.quiz ?: return
        if (quiz.done || quiz.answers.any { it.questionId == questionId }) return
        val q = quiz.questions.first { it.id == questionId }
        val next = quiz.copy(answers = quiz.answers + QuizAnswer(questionId, kidId, choice, choice == q.answer))
        state.update { it.copy(quiz = next) }
        if (next.done) state.update { hh ->
            // Co-op: every right answer is 5 coins for everyone; nobody is ranked.
            hh.copy(kids = hh.kids.map { k -> k.copy(coins = k.coins + 5 * next.lit) })
        }
    }

    private fun after(id: String, final: Boolean) {
        update(id) {
            it.copy(
                stage = QuestStage.Verifying,
                afterPhoto = if (final) Household0.AFTER_DONE else Household0.AFTER_PARTIAL,
                keeperLine = "Checking… ⚖️",
                log = it.log + ev(Agent.QuestMaster, "New photo · routing to Judge", tool = "check_after"),
            )
        }
        scope.launch {
            delay(900); log(id, Agent.Judge, "Same room ✓ · new photo ✓ · comparing 5 items")
            delay(1300)
            if (!final) {
                log(id, Agent.Judge, "4 of 5 home · jacket still on the chair", conf = 0.88f)
                update(id) { q -> q.copy(stage = QuestStage.Partial, things = q.things.map { t -> t.copy(returned = t.id != "jacket") }) }
                delay(600)
                update(id) {
                    it.copy(
                        keeperLine = "4 home! Only the jacket left. Hook it by the window 🧥",
                        log = it.log + ev(Agent.Keeper, "Named what's left, kindly", tool = "keeper_says"),
                    )
                }
            } else {
                log(id, Agent.Judge, "5 of 5 home", conf = 0.94f)
                update(id) { q -> q.copy(stage = QuestStage.Done, things = q.things.map { t -> t.copy(returned = true) }) }
                delay(500); log(id, Agent.QuestMaster, "Auto-approved (confidence ≥ 0.75)", tool = "complete_quest")
                delay(400)
                update(id) {
                    it.copy(
                        keeperLine = "ALL HOME. Absolute legend 🏆 Claim your star!",
                        log = it.log + ev(Agent.Keeper, "Celebrated", tool = "keeper_says"),
                    )
                }
                rewardFlow.tryEmit(grantRewards(id))
            }
        }
    }

    /** XP, coins, level, Squad, stamp and a family star: applied once when a quest completes (offline mode). */
    private fun grantRewards(id: String): QuestReward {
        val h = state.value
        val q = h.quest(id)
        val kid = h.kid(q.kidId)
        var xp = kid.xp + q.xpReward
        var level = kid.level
        var leveled = false
        while (xp >= 100 + (level - 1) * 50) { xp -= 100 + (level - 1) * 50; level++; leveled = true }
        val upgrades = mutableListOf<CodexEntry>()
        val codex = h.codex.map { e ->
            if (q.things.any { it.character == e.character }) {
                val n = e.copy(rescues = e.rescues + 1, firstBy = e.firstBy ?: kid.id)
                if (n.rarity != e.rarity) upgrades += n
                n
            } else e
        }
        val stampId = Household0.stampFor(q)
        val today = SimpleDateFormat("d MMM", Locale.US).format(Date())
        val stamps = h.stamps.map { if (it.id == stampId && it.earnedOn == null) it.copy(earnedOn = today, by = kid.id) else it }
        val stamp = stamps.firstOrNull { it.id == stampId }
        val star = h.glyphs.firstOrNull { !it.earned }?.copy(name = q.title, earnedBy = kid.id)
        val time = SimpleDateFormat("h:mm a", Locale.US).format(Date())
        state.update {
            it.copy(
                kids = it.kids.map { k -> if (k.id == kid.id) k.copy(xp = xp, level = level, coins = k.coins + q.coinReward, streak = maxOf(k.streak, 1)) else k },
                glyphs = if (star == null) it.glyphs else it.glyphs.map { g -> if (g.id == star.id) star else g },
                codex = codex,
                stamps = stamps,
                history = it.history + HistoryRow(
                    kidId = kid.id, kind = q.kind.name.lowercase(), title = q.title, habitId = "",
                    day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }.format(Date()),
                    atMs = System.currentTimeMillis(), minutes = 0, coins = q.coinReward, money = 0.0,
                    right = q.homework?.right ?: 0, total = q.homework?.items?.size ?: 0, topic = q.homework?.topic ?: "",
                    skills = q.homework?.needsWork ?: emptyList(),
                ),
                today = it.today + DayEvent(
                    time, kid.id,
                    if (q.isStudy) "${q.title} · ${q.homework?.right ?: 0}/${q.homework?.items?.size ?: 0} right, practice ${q.practiceRight}/${q.practice.size}"
                    else "${q.title} · ${q.found} Lost Things home",
                    stampId,
                ),
            )
        }
        return QuestReward(id, kid.id, q.xpReward, q.coinReward, leveled, level, star, stamp, upgrades, streak = maxOf(kid.streak, 1))
    }

    /** Live mode: the server's household replaces ours (photos, messages and emotes stay as the TV has them). */
    internal fun applyRemote(next: (Household) -> Household) = state.update(next)

    internal fun emitReward(r: QuestReward) { rewardFlow.tryEmit(r) }

    /** Offline mode: TV time from today's must-do quests. */
    override fun requestTvTime() = Unit

    override fun useTvTime(minutes: Int) = Unit

    /**
     * Cloud mode: the agent crew owns quest state. Merge a quest from the API into the household and
     * grant rewards locally the moment it turns Done (progression lives on the TV for now).
     */
    internal fun applyRemoteQuest(remote: Quest) {
        val before = state.value.quests.firstOrNull { it.id == remote.id }
        state.update { h ->
            val exists = h.quests.any { it.id == remote.id }
            h.copy(quests = if (exists) h.quests.map { if (it.id == remote.id) remote.copy(xpReward = it.xpReward, coinReward = it.coinReward) else it } else h.quests + remote)
        }
        if (before != null && before.stage != QuestStage.Done && remote.stage == QuestStage.Done) rewardFlow.tryEmit(grantRewards(remote.id))
    }

    /** Cloud mode: the Quizmaster runs the quiz; pay the co-op coins once when it finishes. */
    internal fun applyRemoteQuiz(remote: Quiz) {
        val before = state.value.quiz
        state.update { it.copy(quiz = remote) }
        if (remote.done && (before == null || before.id != remote.id || !before.done)) state.update { hh ->
            hh.copy(kids = hh.kids.map { k -> k.copy(coins = k.coins + 10 * remote.lit) })
        }
    }

    internal fun setLive(code: String) = state.update { it.copy(live = true, pairingCode = code) }

    override fun closeQuest(questId: String) {
        state.update { h ->
            val q = h.quests.firstOrNull { it.id == questId } ?: return@update h
            if (q.stage != QuestStage.Done) return@update h
            h.copy(quests = h.quests.filterNot { it.id == questId })
        }
    }

    override fun buy(kidId: String, itemId: String): Boolean {
        val h = state.value
        val item = h.shop.first { it.id == itemId }
        val kid = h.kid(kidId)
        if (kid.coins < item.price || itemId in kid.owned || itemId in h.decor) return false
        state.update {
            it.copy(
                kids = it.kids.map { k -> if (k.id == kidId) k.copy(coins = k.coins - item.price, owned = k.owned + itemId) else k },
                decor = if (item.kind == ItemKind.Decor) it.decor + itemId else it.decor,
            )
        }
        if (item.kind == ItemKind.Avatar) equip(kidId, itemId)
        return true
    }

    override fun equip(kidId: String, itemId: String?) {
        state.update { h ->
            val kid = h.kid(kidId)
            val avatar = itemId?.let { id -> h.shop.first { it.id == id }.avatar } ?: Household0.baseAvatar(kidId)
            h.copy(kids = h.kids.map { if (it.id == kidId) it.copy(equipped = itemId, avatar = avatar ?: kid.avatar) else it })
        }
    }

    override fun startRaid() {
        state.update { if (it.raid != null) it else it.copy(raid = Raid(Household0.bosses.first())) }
    }

    override fun emote(kidId: String, emoji: String) {
        state.update { it.copy(emotes = (it.emotes + Emote(System.nanoTime(), kidId, emoji, System.currentTimeMillis())).takeLast(12)) }
    }

    internal fun applyRemoteMessage(m: FamilyMessage) {
        state.update { it.copy(messages = (it.messages + m).takeLast(6)) }
    }

    private var raidHits = 0
    override fun devRaidHit() {
        val h = state.value
        val raid = h.raid ?: return
        if (raid.defeated) return
        val things = listOf("sock", "teddy", "bricks", "sneakers", "books", "dino", "jacket", "mug")
        val kid = h.kids[raidHits % h.kids.size]
        val crit = raidHits % 4 == 3
        val base = 60 + (raidHits * 17) % 40
        val combo = raid.combo + 1
        val dmg = ((if (crit) base * 2 else base) * (1f + 0.1f * (combo - 1).coerceAtMost(5))).toInt()
        raidHits++
        setRaidHits(raid.hits + Hit(kid.id, things[raidHits % things.size], dmg, crit, System.currentTimeMillis()))
        // Siblings cheer from their phones now and then.
        if (raidHits % 3 == 0) emote(h.kids[(raidHits + 1) % h.kids.size].id, listOf("🔥", "💀", "🫶", "⚡", "🏆")[raidHits % 5])
    }

    override fun applyArenaHits(hits: List<Hit>) {
        val raid = state.value.raid ?: return
        if (hits.size > raid.hits.size) setRaidHits(hits)
    }

    private fun setRaidHits(hits: List<Hit>) {
        val wasDefeated = state.value.raid?.defeated == true
        val next = state.value.raid?.copy(hits = hits) ?: return
        state.update { it.copy(raid = next) }
        if (next.defeated && !wasDefeated) state.update { hh ->
            // Raid loot: everyone on the team gets it (co-op, not competitive).
            hh.copy(kids = hh.kids.map { k ->
                var xp = k.xp + 150; var lvl = k.level
                while (xp >= 100 + (lvl - 1) * 50) { xp -= 100 + (lvl - 1) * 50; lvl++ }
                k.copy(coins = k.coins + 200, xp = xp, level = lvl)
            })
        }
    }

    private fun kid(q: Quest) = state.value.kid(q.kidId)
    private fun ev(a: Agent, msg: String, conf: Float? = null, tool: String? = null) = AgentEvent(a, msg, System.currentTimeMillis() - t0, conf, tool)
    private fun log(id: String, a: Agent, msg: String, conf: Float? = null, tool: String? = null) =
        update(id) { it.copy(log = it.log + ev(a, msg, conf, tool), confidence = conf ?: it.confidence) }

    private fun update(id: String, f: (Quest) -> Quest) {
        state.update { h -> h.copy(quests = h.quests.map { if (it.id == id) f(it) else it }) }
    }
}

/** The Rao family as the app first meets them: nothing earned yet. Every coin, star and streak comes from real tasks. */
object Household0 {
    const val HOME_ART = "rosetta/home_base.webp"
    const val STONE_ART = "rosetta/stone.webp"
    const val MOVIE_ART = "rosetta/movie_night.webp"
    const val COIN = "rosetta/ui/coin.webp"
    const val BEFORE = "rosetta/reference/room_before.webp"
    const val AFTER_PARTIAL = "rosetta/reference/room_after_partial.webp"
    const val AFTER_DONE = "rosetta/reference/room_after_done.webp"
    const val STAR_GOAL = 9

    fun thing(c: String) = "rosetta/things/$c.webp"
    fun glyph(a: String) = "rosetta/glyphs/$a.webp"
    fun avatar(a: String) = "rosetta/avatars/$a.webp"
    fun item(a: String) = "rosetta/shop/$a.webp"
    fun stamp(a: String) = "rosetta/stamps/$a.webp"
    fun baseAvatar(kidId: String) = kidId

    val bosses = listOf(
        Boss("kraken", "The Sock Kraken", "Eight arms. Zero matching socks.", "kraken", "Playroom", 1200),
        Boss("golem", "The Laundry Golem", "Built from every shirt you dropped this week.", "golem", "Laundry corner", 1500),
        Boss("tornado", "The Toy Tornado", "Spins faster every time a toy is left out.", "tornado", "Living room", 1800),
    )
    fun boss(a: String) = "rosetta/bosses/$a.webp"
    const val ARENA = "rosetta/bosses/arena.webp"

    /** The Squad's characters and their stories (a description, not progress: everyone starts undiscovered). */
    val squadCatalog = listOf(
        CodexEntry("sock", "The Lonely Sock", "Always one step from its pair. Loves the laundry basket.", 0, null),
        CodexEntry("dino", "Rex", "Fierce on the rug, shy in the toy box. Roars only at bedtime.", 0, null),
        CodexEntry("bricks", "The Brick Gang", "Three bricks who refuse to be apart. Home: the brick bin.", 0, null),
        CodexEntry("books", "The Sleepy Books", "They nap wherever they land. Dream of the top shelf.", 0, null),
        CodexEntry("jacket", "The Crumpled Jacket", "Hangs around chairs. Secretly wants its hook.", 0, null),
        CodexEntry("sneakers", "The Sneaker Twins", "Run off in different directions. Meet again by the door.", 0, null),
        CodexEntry("teddy", "Old Teddy", "Has seen everything. Asks only for a pillow.", 0, null),
        CodexEntry("mug", "The Little Mug", "Forgotten on desks. Belongs back in the kitchen.", 0, null),
    )

    /** Passport stamps that can be earned (ids match the server's family.STAMPS). */
    val stampCatalog = listOf(
        Stamp("s_homework", "Homework Hero", "homework", null, null),
        Stamp("s_rescue", "Room Rescuer", "rescue", null, null),
        Stamp("s_daily", "Daily Hero", "night", null, null),
        Stamp("s_quiz", "Quiz Night Star", "parcel", null, null),
        Stamp("s_kitchen", "Kitchen Crew", "kitchen", null, null),
        Stamp("s_garden", "Garden Keeper", "garden", null, null),
    )

    fun stampFor(q: Quest) = when {
        q.isStudy -> "s_homework"
        q.isHabit -> "s_daily"
        q.roomId == "balcony" -> "s_garden"
        q.roomId == "kitchen" -> "s_kitchen"
        else -> "s_rescue"
    }

    fun emptyStars() = (1..STAR_GOAL).map { Glyph("star$it", "", "star", null) }

    private val rooms = listOf(
        Room("arjun_room", "Arjun's room", NormRect(0.282f, 0.280f, 0.500f, 0.521f), "arjun"),
        Room("meera_room", "Meera's room", NormRect(0.509f, 0.280f, 0.723f, 0.521f), "meera"),
        Room("kitchen", "Kitchen", NormRect(0.282f, 0.540f, 0.500f, 0.820f)),
        Room("living", "Living room", NormRect(0.509f, 0.540f, 0.723f, 0.820f)),
        Room("balcony", "Balcony", NormRect(0.736f, 0.378f, 0.807f, 0.540f)),
    )
    /** Where each Lost Thing lives in the house once a child has put it back (spots on the Home art). */
    val residence = mapOf(
        "sock" to NormRect(0.300f, 0.455f, 0.335f, 0.515f),
        "dino" to NormRect(0.445f, 0.455f, 0.480f, 0.515f),
        "bricks" to NormRect(0.540f, 0.455f, 0.575f, 0.515f),
        "books" to NormRect(0.395f, 0.455f, 0.430f, 0.515f),
        "sneakers" to NormRect(0.655f, 0.455f, 0.690f, 0.515f),
        "teddy" to NormRect(0.600f, 0.755f, 0.640f, 0.815f),
        "jacket" to NormRect(0.680f, 0.755f, 0.715f, 0.815f),
        "mug" to NormRect(0.330f, 0.755f, 0.365f, 0.815f),
    )

    fun room(id: String) = rooms.firstOrNull { it.id == id } ?: Room(id, "", NormRect(0.4f, 0.4f, 0.6f, 0.6f))

    fun initial() = Household(
        name = "The Rao family",
        kids = listOf(Kid("arjun", "Arjun", 8, "arjun", grade = "Grade 3"), Kid("meera", "Meera", 6, "meera", grade = "Grade 1")),
        rooms = rooms,
        quests = listOf(
            Quest("q_arjun_homework", "arjun", "arjun_room", "Fractions homework", "", xpReward = 80, coinReward = 35, kind = QuestKind.Study, mustDo = true),
            Quest("q_meera_homework", "meera", "meera_room", "Adding and taking away", "", xpReward = 80, coinReward = 35, kind = QuestKind.Study, mustDo = true),
            Quest("q_arjun_room", "arjun", "arjun_room", "Tidy your room", "", xpReward = 75, coinReward = 30),
            Quest("q_meera_room", "meera", "meera_room", "Tidy your room", "", xpReward = 75, coinReward = 30),
            Quest("q_kitchen", "arjun", "kitchen", "Kitchen crew: clear the table", "", xpReward = 75, coinReward = 30),
            Quest("q_balcony", "meera", "balcony", "Water the balcony garden", "", xpReward = 75, coinReward = 30),
        ),
        glyphs = emptyStars(),
        codex = squadCatalog,
        stamps = stampCatalog,
        shop = listOf(
            ShopItem("hat", "Explorer hat", "hat", 60, ItemKind.Avatar, "arjun", "arjun_hat"),
            ShopItem("cape", "Hero cape", "cape", 120, ItemKind.Avatar, "arjun", "arjun_cape"),
            ShopItem("crown", "Paper crown", "crown", 50, ItemKind.Avatar, "meera", "meera_crown"),
            ShopItem("shades", "Star shades", "shades", 90, ItemKind.Avatar, "meera", "meera_shades"),
            ShopItem("lights", "Fairy lights", "lights", 80, ItemKind.Decor, spot = NormRect(0.30f, 0.17f, 0.70f, 0.27f)),
            ShopItem("lemon", "Lemon tree", "lemon", 70, ItemKind.Decor, spot = NormRect(0.80f, 0.70f, 0.88f, 0.84f)),
            ShopItem("telescope", "Telescope", "telescope", 110, ItemKind.Decor, spot = NormRect(0.74f, 0.25f, 0.81f, 0.37f)),
            ShopItem("cat", "Sleepy cat", "cat", 150, ItemKind.Decor, spot = NormRect(0.56f, 0.74f, 0.63f, 0.83f)),
            ShopItem("kite", "Red kite", "kite", 40, ItemKind.Decor, spot = NormRect(0.80f, 0.08f, 0.88f, 0.22f)),
            ShopItem("popcorn", "Popcorn machine", "popcorn", 200, ItemKind.Decor, spot = NormRect(0.66f, 0.68f, 0.72f, 0.80f)),
        ),
        decor = emptySet(),
        raid = null,
        today = emptyList(),
        pairingCode = "R4KX",
        live = false,
        parents = listOf(Parent("mom", "Mom", "mom"), Parent("dad", "Dad", "dad")),
    )

    /** Scout + Coach output for the reference room photo (boxes measured on the image). */
    fun referenceScan() = listOf(
        LostThing("sock", "A lonely sock", "sock", NormRect(0.219f, 0.898f, 0.309f, 0.970f), 1, "the laundry basket"),
        LostThing("dino", "Rex the dinosaur", "dino", NormRect(0.314f, 0.645f, 0.503f, 0.762f), 1, "the toy box"),
        LostThing("bricks", "The brick gang", "bricks", NormRect(0.511f, 0.755f, 0.610f, 0.866f), 1, "the brick bin"),
        LostThing("books", "Five sleepy books", "books", NormRect(0.676f, 0.779f, 0.808f, 0.940f), 1, "the bookshelf"),
        LostThing("jacket", "The crumpled jacket", "jacket", NormRect(0.570f, 0.289f, 0.682f, 0.596f), 1, "the hooks by the window"),
    )
}
