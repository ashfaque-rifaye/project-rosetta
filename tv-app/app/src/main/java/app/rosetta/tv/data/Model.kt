package app.rosetta.tv.data

/** Rectangle in 0..1 image coordinates (left, top, right, bottom). Shared contract with the agents. */
data class NormRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val cx get() = (l + r) / 2f
    val cy get() = (t + b) / 2f
}

data class Kid(
    val id: String,
    val name: String,
    val age: Int,
    /** Avatar key → assets/rosetta/avatars/{avatar}.webp (changes when an avatar item is equipped). */
    val avatar: String,
    val level: Int = 1,
    val xp: Int = 0,
    val coins: Int = 0,
    /** Consecutive days with at least one finished quest. */
    val streak: Int = 0,
    val owned: Set<String> = emptySet(),
    val equipped: String? = null,
    val grade: String = "",
    /** Real pocket money earned (rupees), set by the parents' rates. */
    val money: Double = 0.0,
) {
    val xpForNext get() = 100 + (level - 1) * 50
    val levelProgress get() = (xp.toFloat() / xpForNext).coerceIn(0f, 1f)
    val title get() = when {
        level >= 10 -> "Rosetta Legend"
        level >= 6 -> "Super Star"
        level >= 3 -> "Rising Star"
        else -> "Starter"
    }
}

/** Mom and Dad: shown with the kids on the TV. */
data class Parent(val id: String, val name: String, val avatar: String)

/** A daily task the parents made (make the bed, read 10 pages, prayer time…); [streaks] per kid, in days. */
data class Habit(
    val id: String,
    val title: String,
    val icon: String,
    val kidIds: List<String>,
    val proof: String,
    val coins: Int,
    val money: Double,
    val streaks: Map<String, Int> = emptyMap(),
)

/** Something a kid is saving pocket money for. */
data class WishItem(val id: String, val kidId: String, val title: String, val price: Double, val status: String)

/** Today's TV time: earned when every must-do task is done (and a parent says OK, if they asked to). */
data class TvTime(
    val mustDo: Int = 0,
    val done: Int = 0,
    val allDone: Boolean = false,
    val unlocked: Boolean = false,
    val needsApproval: Boolean = false,
    val requested: Boolean = false,
    /** A parent unlocked today's TV time from their phone. */
    val granted: Boolean = false,
    val tvMinutes: Int = 45,
    val minutesLeft: Int = 45,
    val waitingOn: List<String> = emptyList(),
)

/** One finished task, as the server recorded it (the only source for Progress and streaks). */
data class HistoryRow(
    val kidId: String,
    val kind: String,
    val title: String,
    val habitId: String,
    val day: String,
    val atMs: Long,
    val minutes: Int,
    val coins: Int,
    val money: Double,
    val right: Int = 0,
    val total: Int = 0,
    val topic: String = "",
    val skills: List<String> = emptyList(),
)

/** ✦ Coach's notes for parents, written by the AI from real history. */
data class CoachNotes(val headline: String, val notes: List<String>)

/** A room of the Home Base diorama; [rect] locates it on the key art. */
data class Room(val id: String, val name: String, val rect: NormRect, val ownerId: String? = null)

enum class Rarity { Common, Rare, Epic, Legendary }

/** A household object Scout found in the photo, shown on TV as a Lost Thing character. */
data class LostThing(
    val id: String,
    val name: String,
    /** Character portrait key → assets/rosetta/things/{character}.webp */
    val character: String,
    val box: NormRect,
    val stars: Int,
    /** Where it belongs (Coach's micro-step). */
    val home: String,
    val returned: Boolean = false,
)

enum class QuestStage { Ready, AwaitingBefore, Scanning, Found, AwaitingAfter, Verifying, Partial, Checked, Practising, Checking, Done }

/** Tidy: a room photo becomes Lost Things. Study: a homework photo is marked, then practised. Habit: a daily task. */
enum class QuestKind { Tidy, Study, Habit }

enum class Agent(val label: String, val role: String) {
    QuestMaster("Quest Master", "orchestrates"),
    Scout("Scout", "finds"),
    Coach("Coach", "plans"),
    Judge("Judge", "verifies"),
    Router("Router", "escalates"),
    Keeper("Keeper", "speaks"),
    Tutor("Tutor", "marks"),
}

/** One line in the live Mission Control feed: which agent did what, when, and how sure it was. */
data class AgentEvent(val agent: Agent, val message: String, val atMs: Long, val confidence: Float? = null, val tool: String? = null)

data class Quest(
    val id: String,
    val kidId: String,
    val roomId: String,
    val title: String,
    val glyphId: String,
    val stage: QuestStage = QuestStage.Ready,
    val beforePhoto: String? = null,
    val afterPhoto: String? = null,
    val things: List<LostThing> = emptyList(),
    val keeperLine: String = "",
    /** The Keeper's line in the Polly voice (a cached audio file), when the agents sent one. */
    val keeperAudio: String? = null,
    val confidence: Float = 1f,
    val xpReward: Int = 60,
    val coinReward: Int = 25,
    val log: List<AgentEvent> = emptyList(),
    val needsParent: Boolean = false,
    val kind: QuestKind = QuestKind.Tidy,
    /** Study quests: Tutor's marking of the homework photo ([beforePhoto]). */
    val homework: Homework? = null,
    /** Study quests: practice questions on the skills that went wrong, and the first answer to each. */
    val practice: List<Question> = emptyList(),
    val answers: Map<String, Answer> = emptyMap(),
    val icon: String = "",
    /** How a habit is shown done: none (tick), note, voice or photo. */
    val proof: String = "photo",
    val mustDo: Boolean = false,
    /** ✦ What the AI honestly saw when a photo or voice note didn't fit the task. */
    val whatISee: String = "",
    /** A habit's check-in: the kid's note, or the ✦ summary of their voice note or photo. */
    val checkin: String = "",
    val checkinKind: String = "",
    val reward: QuestReward? = null,
) {
    val isStudy get() = kind == QuestKind.Study
    val isHabit get() = kind == QuestKind.Habit
    val practiceRight get() = answers.values.count { it.correct }
    val found get() = things.size
    val home get() = things.count { it.returned }
    val starsEarned get() = things.filter { it.returned }.sumOf { it.stars }
    val working get() = stage == QuestStage.Scanning || stage == QuestStage.Verifying
}

/** One marked question from a homework photo. [checkedBy] is "math" when exact arithmetic confirmed the verdict. */
data class HomeworkItem(
    val id: String,
    val n: Int,
    val question: String,
    val kidAnswer: String,
    val correct: Boolean,
    val box: NormRect,
    val skill: String,
    /** Only for wrong answers: points at the method, never the answer. */
    val hint: String,
    val checkedBy: String,
)

data class Homework(val subject: String, val topic: String, val items: List<HomeworkItem>, val confidence: Float) {
    val right get() = items.count { it.correct }
    val needsWork get() = items.filterNot { it.correct }.map { it.skill }.distinct()
}

/** A multiple-choice question (practice or quiz). [forKid] is set for quiz questions pitched at one child. */
data class Question(
    val id: String,
    val prompt: String,
    val choices: List<String>,
    val answer: Int,
    val why: String,
    val skill: String,
    val forKid: String = "",
    /** Quiz only: why the AI picked this question (for the parents), e.g. "Maths test on Friday". */
    val reason: String = "",
)

data class Answer(val choice: Int, val correct: Boolean)

/** Family Quiz Night: co-op, the family counts right answers together. */
data class Quiz(
    val id: String,
    val title: String,
    val questions: List<Question>,
    val answers: List<QuizAnswer> = emptyList(),
) {
    val lit get() = answers.count { it.correct }
    val goal get() = questions.size
    val done get() = answers.size >= questions.size
    val current get() = questions.firstOrNull { q -> answers.none { it.questionId == q.id } }
}

data class QuizAnswer(val questionId: String, val kidId: String, val choice: Int, val correct: Boolean)

data class Glyph(val id: String, val name: String, val asset: String, val earnedBy: String? = null) {
    val earned get() = earnedBy != null
}

/** Squad: every Lost Thing character the family has brought home; rarity grows with rescues (earned, never random). */
data class CodexEntry(val character: String, val name: String, val lore: String, val rescues: Int, val firstBy: String?) {
    val rarity get() = when {
        rescues >= 12 -> Rarity.Legendary
        rescues >= 6 -> Rarity.Epic
        rescues >= 3 -> Rarity.Rare
        else -> Rarity.Common
    }
    val nextAt get() = when (rarity) { Rarity.Common -> 3; Rarity.Rare -> 6; Rarity.Epic -> 12; Rarity.Legendary -> rescues }
    val discovered get() = rescues > 0
}

/** Passport stamp (Parky-style): one per quest type, with the date it was first earned. */
data class Stamp(val id: String, val name: String, val asset: String, val earnedOn: String?, val by: String?)

enum class ItemKind { Avatar, Decor }

data class ShopItem(
    val id: String,
    val name: String,
    val asset: String,
    val price: Int,
    val kind: ItemKind,
    /** For avatar items: which kid it fits and the avatar it switches to. */
    val kidId: String? = null,
    val avatar: String? = null,
    /** For decor: where it appears on the Home Base miniature (0..1). */
    val spot: NormRect? = null,
)

/** One finished quest, for the Day Card timeline. */
data class DayEvent(val time: String, val kidId: String, val text: String, val stamp: String?)

data class Household(
    val name: String,
    val kids: List<Kid>,
    val rooms: List<Room>,
    val quests: List<Quest>,
    val glyphs: List<Glyph>,
    val codex: List<CodexEntry>,
    val stamps: List<Stamp>,
    val shop: List<ShopItem>,
    /** Decor bought for the house, shown on the miniature. */
    val decor: Set<String>,
    val today: List<DayEvent>,
    /** This week's Mess Boss raid (co-op), if one is running. Superseded by [quiz] on 22 Oct; kept for old saves. */
    val raid: Raid? = null,
    /** The family Quiz Battle built from this week's homework, if one is running. */
    val quiz: Quiz? = null,
    /** Hype emotes sent from family phones; the TV bursts the newest ones. */
    val emotes: List<Emote> = emptyList(),
    /** Messages Mom and Dad send from the parent page; the TV shows the newest and the Keeper reads it out. */
    val messages: List<FamilyMessage> = emptyList(),
    /** 4-week habit history per kid, for the Growth tracker. */
    val growth: Map<String, Growth> = emptyMap(),
    val pairingCode: String,
    /** False until the cloud agent crew is connected; the TV then runs its on-device household engine. */
    val live: Boolean,
    val parents: List<Parent> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val wishlist: List<WishItem> = emptyList(),
    val tvTime: TvTime = TvTime(),
    val history: List<HistoryRow> = emptyList(),
    val coach: Map<String, CoachNotes> = emptyMap(),
    val currency: String = "₹",
    val memories: Int = 0,
    val todayDate: String = "",
) {
    val glyphsEarned get() = glyphs.count { it.earned }
    val stoneComplete get() = glyphs.all { it.earned }
    val stoneProgress get() = glyphsEarned.toFloat() / glyphs.size
    fun kid(id: String) = kids.first { it.id == id }
    fun room(id: String) = rooms.firstOrNull { it.id == id } ?: Room(id, "", NormRect(0.4f, 0.4f, 0.6f, 0.6f))
    fun parentOrNull(id: String) = parents.firstOrNull { it.id == id }
    fun quest(id: String) = quests.first { it.id == id }
}

/** Reward summary the TV celebrates after a quest. */
data class QuestReward(
    val questId: String,
    val kidId: String,
    val xp: Int,
    val coins: Int,
    val leveledUp: Boolean,
    val newLevel: Int,
    val glyph: Glyph?,
    val stamp: Stamp?,
    val codexUpgrades: List<CodexEntry>,
    val money: Double = 0.0,
    val streak: Int = 0,
)


/* ----------------------------- Mess Boss raids ----------------------------- */

/** A weekly co-op boss the AI spawns from a big messy space; every item put away deals damage. */
data class Boss(
    val id: String,
    val name: String,
    val tagline: String,
    val asset: String,
    val roomName: String,
    val maxHp: Int,
)

/** One verified put-away: Judge confirmed [thing] went home, dealing [damage] to the boss. */
data class Hit(val kidId: String, val thing: String, val damage: Int, val crit: Boolean, val atMs: Long)

data class Raid(val boss: Boss, val hits: List<Hit> = emptyList(), val endsAt: String = "Sun 7 PM") {
    val dealt get() = hits.sumOf { it.damage }
    val hp get() = (boss.maxHp - dealt).coerceAtLeast(0)
    val defeated get() = hp == 0
    /** Consecutive hits within 20 s build a combo multiplier. */
    val combo get(): Int {
        var c = 0
        for (i in hits.indices.reversed()) { if (i == hits.lastIndex || hits[i + 1].atMs - hits[i].atMs < 20_000) c++ else break }
        return c
    }
    fun dealtBy(kidId: String) = hits.filter { it.kidId == kidId }.sumOf { it.damage }
}

/** A hype emote sent from a phone (🔥 💀 🫶 …) that bursts onto the TV. */
data class Emote(val id: Long, val kidId: String, val emoji: String, val atMs: Long)

/** A message from a parent's phone ("Proud of you!") that the TV shows on any screen. [atMs] is when the TV got it. */
data class FamilyMessage(val id: String, val from: String, val text: String, val atMs: Long, val audio: String? = null)

/* ----------------------------- Growth tracker ----------------------------- */

data class DayStat(
    val label: String,
    /** Quests finished that day. */
    val quests: Int,
    /** Minutes from quest open to "all home" (lower = more fluent). */
    val minutes: Int,
    /** Quests the kid started without a parent prompt. */
    val selfStarted: Int,
)

/** A kid's habit picture: 28 days of stats, skill balance (0..1) and the Coach agent's plain-language notes. */
data class Growth(val days: List<DayStat>, val skills: Map<String, Float>, val insights: List<String>) {
    val thisWeek get() = days.takeLast(7)
    val lastWeek get() = days.dropLast(7).takeLast(7)
    val questsThisWeek get() = thisWeek.sumOf { it.quests }
    val questsLastWeek get() = lastWeek.sumOf { it.quests }
    val avgMinutesThisWeek get() = thisWeek.filter { it.quests > 0 }.map { it.minutes }.average().takeIf { !it.isNaN() } ?: 0.0
    val avgMinutesLastWeek get() = lastWeek.filter { it.quests > 0 }.map { it.minutes }.average().takeIf { !it.isNaN() } ?: 0.0
    val selfStartRate get() = thisWeek.sumOf { it.selfStarted }.toFloat() / questsThisWeek.coerceAtLeast(1)
}
