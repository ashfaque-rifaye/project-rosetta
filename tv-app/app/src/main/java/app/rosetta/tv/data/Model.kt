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
