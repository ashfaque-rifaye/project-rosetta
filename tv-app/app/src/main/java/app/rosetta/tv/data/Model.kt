package app.rosetta.tv.data

/** Rectangle in 0..1 image coordinates (left, top, right, bottom). Shared contract with the agents. */
data class NormRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val cx get() = (l + r) / 2f
    val cy get() = (t + b) / 2f
}

data class Kid(val id: String, val name: String, val age: Int)

/** A room of the Home Base diorama; [rect] locates it on the key art. */
data class Room(val id: String, val name: String, val rect: NormRect, val ownerId: String? = null)

/** A household object the Scout agent found in the room photo, shown on TV as a Lost Thing character. */
data class LostThing(
    val id: String,
    val name: String,
    /** Character portrait key, e.g. "sock" → assets/rosetta/things/sock.webp. */
    val character: String,
    val box: NormRect,
    val stars: Int,
    /** Where it belongs; the Coach agent's micro-step. */
    val home: String,
    val returned: Boolean = false,
)

enum class QuestStage {
    /** On the board, not started. */
    Ready,
    /** TV shows the QR; waiting for the "before" photo. */
    AwaitingBefore,
    /** Scout + Coach are reading the photo. */
    Scanning,
    /** Lost Things are on screen; kid is tidying. */
    Found,
    /** Waiting for the "after" photo. */
    AwaitingAfter,
    /** Judge is comparing before/after. */
    Verifying,
    /** Some things went home, some are still lost. */
    Partial,
    /** Everything is home; glyph earned. */
    Done,
}

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
    /** Latest line from the Keeper (narrator), grounded in what the agents saw. */
    val keeperLine: String = "",
    /** Judge confidence; below the threshold the parent is asked instead of auto-approving. */
    val confidence: Float = 1f,
) {
    val found get() = things.size
    val home get() = things.count { it.returned }
    val starsEarned get() = things.filter { it.returned }.sumOf { it.stars }
}

/** A glyph carved into the Family Stone. */
data class Glyph(val id: String, val name: String, val asset: String, val earnedBy: String? = null) {
    val earned get() = earnedBy != null
}

data class Household(
    val name: String,
    val kids: List<Kid>,
    val rooms: List<Room>,
    val quests: List<Quest>,
    val glyphs: List<Glyph>,
    /** Short code the phone types (or scans as QR) to join this TV. */
    val pairingCode: String,
    /** True while running the bundled demo household instead of the cloud agents. */
    val demo: Boolean,
) {
    val glyphsEarned get() = glyphs.count { it.earned }
    val stoneComplete get() = glyphs.all { it.earned }
    fun kid(id: String) = kids.first { it.id == id }
    fun room(id: String) = rooms.first { it.id == id }
    fun quest(id: String) = quests.first { it.id == id }
}
