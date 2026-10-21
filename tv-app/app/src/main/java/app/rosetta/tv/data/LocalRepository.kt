package app.rosetta.tv.data

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
 * On-device household engine. Keeps the family's progression (XP, coins, codex, stamps, shop, stone)
 * and drives quests through the same stages and agent steps as the cloud crew. Until the backend is
 * deployed, the vision results for the reference room come from [Household0.referenceScan].
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
            keeperLine = "Snap your room, ${kid(it).name} 📸 Let's find the Lost Things.",
            log = it.log + ev(Agent.QuestMaster, "Quest opened on the TV · waiting for ${kid(it).name}'s phone"),
        )
    }

    override fun devPhoneUpload(questId: String) {
        val q = state.value.quest(questId)
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
                delay(500); log(id, Agent.QuestMaster, "Auto-approved (confidence ≥ 0.75) · carving glyph", tool = "complete_quest")
                delay(400)
                update(id) {
                    it.copy(
                        keeperLine = "ALL HOME. Absolute legend 🏆 Claim your glyph!",
                        log = it.log + ev(Agent.Keeper, "Celebrated", tool = "keeper_says"),
                    )
                }
                rewardFlow.tryEmit(grantRewards(id))
            }
        }
    }

    /** XP, coins, level, codex, stamp and glyph: applied once when a quest completes. */
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
        val glyph = h.glyphs.firstOrNull { it.id == q.glyphId }?.copy(earnedBy = kid.id)
        val time = SimpleDateFormat("h:mm a", Locale.US).format(Date())
        state.update {
            it.copy(
                kids = it.kids.map { k -> if (k.id == kid.id) k.copy(xp = xp, level = level, coins = k.coins + q.coinReward, streak = k.streak + 1) else k },
                codex = codex,
                stamps = stamps,
                today = it.today + DayEvent(time, kid.id, "${q.title} · ${q.found} Lost Things home", stampId),
            )
        }
        return QuestReward(id, kid.id, q.xpReward, q.coinReward, leveled, level, glyph, stamp, upgrades)
    }

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

    internal fun setLive(code: String) = state.update { it.copy(live = true, pairingCode = code) }

    override fun closeQuest(questId: String) {
        state.update { h ->
            val q = h.quests.firstOrNull { it.id == questId } ?: return@update h
            if (q.stage != QuestStage.Done) return@update h
            h.copy(
                quests = h.quests.filterNot { it.id == questId },
                glyphs = h.glyphs.map { if (it.id == q.glyphId) it.copy(earnedBy = q.kidId) else it },
            )
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
        val next = raid.copy(hits = raid.hits + Hit(kid.id, things[raidHits % things.size], dmg, crit, System.currentTimeMillis()))
        state.update { it.copy(raid = next) }
        if (next.defeated) state.update { hh ->
            // Raid loot: everyone on the team gets it (co-op, not competitive).
            hh.copy(kids = hh.kids.map { k ->
                var xp = k.xp + 150; var lvl = k.level
                while (xp >= 100 + (lvl - 1) * 50) { xp -= 100 + (lvl - 1) * 50; lvl++ }
                k.copy(coins = k.coins + 200, xp = xp, level = lvl)
            })
        }
        // Siblings cheer from their phones now and then.
        if (raidHits % 3 == 0) emote(h.kids[(raidHits + 1) % h.kids.size].id, listOf("🔥", "💀", "🫶", "⚡", "🏆")[raidHits % 5])
    }

    private fun kid(q: Quest) = state.value.kid(q.kidId)
    private fun ev(a: Agent, msg: String, conf: Float? = null, tool: String? = null) = AgentEvent(a, msg, System.currentTimeMillis() - t0, conf, tool)
    private fun log(id: String, a: Agent, msg: String, conf: Float? = null, tool: String? = null) =
        update(id) { it.copy(log = it.log + ev(a, msg, conf, tool), confidence = conf ?: it.confidence) }

    private fun update(id: String, f: (Quest) -> Quest) {
        state.update { h -> h.copy(quests = h.quests.map { if (it.id == id) f(it) else it }) }
    }
}

/** The household as it ships: the Rao family, mid-month, one glyph short of movie night. */
object Household0 {
    const val HOME_ART = "rosetta/home_base.webp"
    const val STONE_ART = "rosetta/stone.webp"
    const val MOVIE_ART = "rosetta/movie_night.webp"
    const val COIN = "rosetta/ui/coin.webp"
    const val BEFORE = "rosetta/reference/room_before.webp"
    const val AFTER_PARTIAL = "rosetta/reference/room_after_partial.webp"
    const val AFTER_DONE = "rosetta/reference/room_after_done.webp"

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

    /** Where each rescued Lost Thing lives once it moves into the house (room-relative spot, 0..1 of the art). */
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
    val residentLines = mapOf(
        "sock" to "still looking for my twin 👀", "dino" to "RAWR (politely)", "bricks" to "we stick together 🧱",
        "books" to "zzz… on the shelf finally", "sneakers" to "race you to the door", "teddy" to "best seat in the house 🫶",
        "jacket" to "hook life is good", "mug" to "kitchen gang 🍵",
    )

    private fun growth(seed: Int, improving: Boolean): Growth {
        val days = (0 until 28).map { d ->
            val trend = if (improving) d / 27f else 0.5f
            val weekend = d % 7 >= 5
            val quests = ((if (weekend) 2 else 1) + (trend * 2).toInt()).coerceAtMost(4)
            val minutes = (34 - (trend * 18).toInt() + (d * seed) % 6)
            val self = (quests * (0.2f + trend * 0.6f)).toInt()
            DayStat(listOf("M", "T", "W", "T", "F", "S", "S")[d % 7], if ((d * seed) % 9 == 4) 0 else quests, minutes, self)
        }
        return Growth(days, emptyMap(), emptyList())
    }

    fun seedGrowth() = mapOf(
        "arjun" to growth(5, true).copy(
            skills = mapOf("Tidy" to 0.86f, "Care" to 0.55f, "Kitchen" to 0.62f, "Self-start" to 0.71f, "Streaks" to 0.8f),
            insights = listOf(
                "Arjun now tidies his room in about 19 min, down from 34 a month ago.",
                "He starts 7 of 10 quests on his own. Last month it was 3.",
                "Kitchen quests lag behind. Try pairing one with Meera on Saturday.",
            ),
        ),
        "meera" to growth(7, true).copy(
            skills = mapOf("Tidy" to 0.64f, "Care" to 0.9f, "Kitchen" to 0.58f, "Self-start" to 0.52f, "Streaks" to 0.6f),
            insights = listOf(
                "Meera loves care quests: plants and her teddy are her streak keepers.",
                "Her bedroom quests run long after 7 PM. Earlier slots finish twice as fast.",
                "Self-starts are climbing: 5 this week, up from 2.",
            ),
        ),
    )

    fun stampFor(q: Quest) = when (q.roomId) {
        "balcony" -> "s_garden"
        "kitchen" -> "s_kitchen"
        else -> "s_rescue"
    }

    private val rooms = listOf(
        Room("arjun_room", "Arjun's room", NormRect(0.282f, 0.280f, 0.500f, 0.521f), "arjun"),
        Room("meera_room", "Meera's room", NormRect(0.509f, 0.280f, 0.723f, 0.521f), "meera"),
        Room("kitchen", "Kitchen", NormRect(0.282f, 0.540f, 0.500f, 0.820f)),
        Room("living", "Living room", NormRect(0.509f, 0.540f, 0.723f, 0.820f)),
        Room("balcony", "Balcony", NormRect(0.736f, 0.378f, 0.807f, 0.540f)),
    )

    fun initial() = Household(
        name = "The Rao household",
        kids = listOf(
            Kid("arjun", "Arjun", 8, "arjun", level = 4, xp = 80, coins = 140, streak = 5, owned = setOf("hat"), equipped = null),
            Kid("meera", "Meera", 6, "meera", level = 3, xp = 40, coins = 95, streak = 3, owned = setOf("crown"), equipped = "crown").let { it.copy(avatar = "meera_crown") },
        ),
        rooms = rooms,
        quests = listOf(
            Quest("q_arjun_room", "arjun", "arjun_room", "Rescue the Lost Things", "g_rescuer", xpReward = 75, coinReward = 30),
            Quest("q_balcony", "meera", "balcony", "Water the balcony garden", "next_garden", xpReward = 40, coinReward = 15),
            Quest("q_kitchen", "arjun", "kitchen", "Kitchen crew: clear the table", "next_crew", xpReward = 50, coinReward = 20),
            Quest("q_meera_room", "meera", "meera_room", "Make the bed, feed the bookshelf", "next_bed", xpReward = 45, coinReward = 20),
        ),
        glyphs = listOf(
            Glyph("g_bed", "Bed Maker", "bed", "meera"), Glyph("g_books", "Book Keeper", "books", "arjun"),
            Glyph("g_shirt", "Fold Master", "shirt", "meera"), Glyph("g_plant", "Plant Guardian", "plant", "arjun"),
            Glyph("g_broom", "Sweep Hero", "broom", "arjun"), Glyph("g_dish", "Dish Hero", "dish", "meera"),
            Glyph("g_package", "Package Detective", "package", "arjun"), Glyph("g_shoes", "Shoe Parade", "shoes", "meera"),
            Glyph("g_rescuer", "Room Rescuer", "sock", null),
        ),
        codex = listOf(
            CodexEntry("sock", "The Lonely Sock", "Always one step from its pair. Loves the laundry basket.", 4, "arjun"),
            CodexEntry("dino", "Rex", "Fierce on the rug, shy in the toy box. Roars only at bedtime.", 2, "arjun"),
            CodexEntry("bricks", "The Brick Gang", "Three bricks who refuse to be apart. Home: the brick bin.", 6, "meera"),
            CodexEntry("books", "The Sleepy Books", "They nap wherever they land. Dream of the top shelf.", 1, "arjun"),
            CodexEntry("jacket", "The Crumpled Jacket", "Hangs around chairs. Secretly wants its hook.", 0, null),
            CodexEntry("sneakers", "The Sneaker Twins", "Run off in different directions. Meet again by the door.", 3, "meera"),
            CodexEntry("teddy", "Old Teddy", "Has seen everything. Asks only for a pillow.", 12, "meera"),
            CodexEntry("mug", "The Little Mug", "Forgotten on desks. Belongs back in the kitchen.", 0, null),
        ),
        stamps = listOf(
            Stamp("s_rescue", "Room Rescuer", "rescue", null, null),
            Stamp("s_dino", "Dino Wrangler", "dino", "12 Oct", "arjun"),
            Stamp("s_garden", "Garden Keeper", "garden", "9 Oct", "meera"),
            Stamp("s_kitchen", "Kitchen Crew", "kitchen", "15 Oct", "meera"),
            Stamp("s_parcel", "Package Detective", "parcel", "18 Oct", "arjun"),
            Stamp("s_night", "Night Owl", "night", null, null),
        ),
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
        decor = setOf("lemon"),
        raid = Raid(bosses.first()),
        growth = seedGrowth(),
        today = listOf(DayEvent("4:10 PM", "meera", "Shoe Parade · 2 Lost Things home", "s_parcel")),
        pairingCode = "R4KX",
        live = false,
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
