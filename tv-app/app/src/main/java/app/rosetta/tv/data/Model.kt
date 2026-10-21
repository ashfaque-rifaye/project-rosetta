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
) {
    val xpForNext get() = 100 + (level - 1) * 50
    val levelProgress get() = (xp.toFloat() / xpForNext).coerceIn(0f, 1f)
    val title get() = when {
        level >= 10 -> "Grand Keeper"
        level >= 6 -> "Glyph Carver"
        level >= 3 -> "Pathfinder"
        else -> "Apprentice"
    }
}

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

enum class QuestStage { Ready, AwaitingBefore, Scanning, Found, AwaitingAfter, Verifying, Partial, Done }

enum class Agent(val label: String, val role: String) {
    QuestMaster("Quest Master", "orchestrates"),
    Scout("Scout", "finds"),
    Coach("Coach", "plans"),
    Judge("Judge", "verifies"),
    Router("Router", "escalates"),
    Keeper("Keeper", "speaks"),
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
    val confidence: Float = 1f,
    val xpReward: Int = 60,
    val coinReward: Int = 25,
    val log: List<AgentEvent> = emptyList(),
    val needsParent: Boolean = false,
) {
    val found get() = things.size
    val home get() = things.count { it.returned }
    val starsEarned get() = things.filter { it.returned }.sumOf { it.stars }
    val working get() = stage == QuestStage.Scanning || stage == QuestStage.Verifying
}

data class Glyph(val id: String, val name: String, val asset: String, val earnedBy: String? = null) {
    val earned get() = earnedBy != null
}

/** Codex: every Lost Thing character the family has rescued; rarity grows with rescues (earned, never random). */
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
    /** This week's Mess Boss raid (co-op), if one is running. */
    val raid: Raid? = null,
    /** Hype emotes sent from family phones; the TV bursts the newest ones. */
    val emotes: List<Emote> = emptyList(),
    /** 4-week habit history per kid, for the Growth tracker. */
    val growth: Map<String, Growth> = emptyMap(),
    val pairingCode: String,
    /** False until the cloud agent crew is connected; the TV then runs its on-device household engine. */
    val live: Boolean,
) {
    val glyphsEarned get() = glyphs.count { it.earned }
    val stoneComplete get() = glyphs.all { it.earned }
    val stoneProgress get() = glyphsEarned.toFloat() / glyphs.size
    fun kid(id: String) = kids.first { it.id == id }
    fun room(id: String) = rooms.first { it.id == id }
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
