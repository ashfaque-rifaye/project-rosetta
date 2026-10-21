package app.rosetta.tv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The "demo household": the same states and JSON shapes the agent crew produces, replayed from
 * bundled assets with realistic timings. It lets judges run Rosetta with no account or cloud,
 * and it is what the demo video records when the backend is offline.
 */
class DemoRepository(private val scope: CoroutineScope) : RosettaRepository {

    private val state = MutableStateFlow(DemoHousehold.initial())
    override val household: StateFlow<Household> = state

    override fun openQuest(questId: String) = updateQuest(questId) {
        if (it.stage == QuestStage.Ready) it.copy(
            stage = QuestStage.AwaitingBefore,
            keeperLine = "Snap the room with your phone, ${kidName(it)}. Let's see who got lost today.",
        ) else it
    }

    override fun demoSendBefore(questId: String) {
        updateQuest(questId) {
            it.copy(stage = QuestStage.Scanning, beforePhoto = DemoHousehold.BEFORE_PHOTO, keeperLine = "Looking closely…")
        }
        scope.launch {
            delay(2600) // Scout + Coach latency on Nova Pro is ~2–4 s for one photo.
            updateQuest(questId) {
                it.copy(
                    stage = QuestStage.Found,
                    things = DemoHousehold.lostThings(),
                    keeperLine = "Five Lost Things in here, ${kidName(it)}. They miss their homes. Can you help them back?",
                    confidence = 0.91f,
                )
            }
        }
    }

    override fun demoSendAfter(questId: String) {
        val quest = state.value.quest(questId)
        val firstTry = quest.stage == QuestStage.Found || quest.stage == QuestStage.AwaitingAfter
        updateQuest(questId) {
            it.copy(
                stage = QuestStage.Verifying,
                afterPhoto = if (firstTry) DemoHousehold.AFTER_PARTIAL_PHOTO else DemoHousehold.AFTER_DONE_PHOTO,
                keeperLine = "Checking on everyone…",
            )
        }
        scope.launch {
            delay(2400)
            if (firstTry) {
                // Judge: every item home except the jacket, shown on TV with a specific nudge.
                updateQuest(questId) { q ->
                    q.copy(
                        stage = QuestStage.Partial,
                        things = q.things.map { t -> t.copy(returned = t.id != "jacket") },
                        keeperLine = "Four are home! The jacket is still lost on the chair. Its hook is waiting by the door.",
                        confidence = 0.88f,
                    )
                }
            } else {
                updateQuest(questId) { q ->
                    q.copy(
                        stage = QuestStage.Done,
                        things = q.things.map { t -> t.copy(returned = true) },
                        keeperLine = "Every Lost Thing is home. The Stone is ready for a new glyph.",
                        confidence = 0.94f,
                    )
                }
                state.update { h ->
                    val glyphId = h.quest(questId).glyphId
                    h.copy(glyphs = h.glyphs.map { g -> if (g.id == glyphId) g.copy(earnedBy = h.quest(questId).kidId) else g })
                }
            }
        }
    }

    override fun closeQuest(questId: String) {
        state.update { h -> h.copy(quests = h.quests.filterNot { it.id == questId && it.stage == QuestStage.Done }) }
    }

    private fun kidName(q: Quest) = state.value.kid(q.kidId).name

    private fun updateQuest(id: String, transform: (Quest) -> Quest) {
        state.update { h -> h.copy(quests = h.quests.map { if (it.id == id) transform(it) else it }) }
    }
}

/** Bundled household. Photos and character portraits live in assets/rosetta/. */
object DemoHousehold {
    const val HOME_ART = "rosetta/home_base.webp"
    const val STONE_ART = "rosetta/stone.webp"
    const val MOVIE_NIGHT_ART = "rosetta/movie_night.webp"
    const val BEFORE_PHOTO = "rosetta/demo/room_before.webp"
    const val AFTER_PARTIAL_PHOTO = "rosetta/demo/room_after_partial.webp"
    const val AFTER_DONE_PHOTO = "rosetta/demo/room_after_done.webp"

    fun thingArt(character: String) = "rosetta/things/$character.webp"
    fun glyphArt(asset: String) = "rosetta/glyphs/$asset.webp"

    // Room rectangles measured on the Home Base key art (1376×768).
    private val rooms = listOf(
        Room("arjun_room", "Arjun's room", NormRect(0.282f, 0.280f, 0.500f, 0.521f), ownerId = "arjun"),
        Room("meera_room", "Meera's room", NormRect(0.509f, 0.280f, 0.723f, 0.521f), ownerId = "meera"),
        Room("kitchen", "Kitchen", NormRect(0.282f, 0.540f, 0.500f, 0.820f)),
        Room("living", "Living room", NormRect(0.509f, 0.540f, 0.723f, 0.820f)),
        Room("balcony", "Balcony", NormRect(0.736f, 0.378f, 0.807f, 0.540f)),
    )

    fun initial() = Household(
        name = "The Rao household",
        kids = listOf(Kid("arjun", "Arjun", 8), Kid("meera", "Meera", 6)),
        rooms = rooms,
        quests = listOf(
            Quest("q_arjun_room", "arjun", "arjun_room", "Rescue the Lost Things", glyphId = "g_rescuer"),
            // These carve glyphs on the next Stone.
            Quest("q_balcony", "meera", "balcony", "Water the balcony garden", glyphId = "next_garden"),
            Quest("q_kitchen", "arjun", "kitchen", "Kitchen crew: clear the table", glyphId = "next_crew"),
        ),
        glyphs = listOf(
            Glyph("g_bed", "Bed Maker", "bed", "meera"),
            Glyph("g_books", "Book Keeper", "books", "arjun"),
            Glyph("g_shirt", "Fold Master", "shirt", "meera"),
            Glyph("g_plant", "Plant Guardian", "plant", "arjun"),
            Glyph("g_broom", "Sweep Hero", "broom", "arjun"),
            Glyph("g_dish", "Dish Hero", "dish", "meera"),
            Glyph("g_package", "Package Detective", "package", "arjun"),
            Glyph("g_shoes", "Shoe Parade", "shoes", "meera"),
            // The demo Stone has 9 slots: 8 carved, the 9th is tonight's quest.
            Glyph("g_rescuer", "Room Rescuer", "sock", null),
        ),
        pairingCode = "R4KX",
        demo = true,
    )

    /** What Scout + Coach return for the bundled "before" photo (boxes measured on that photo). */
    fun lostThings() = listOf(
        LostThing("sock", "A lonely sock", "sock", NormRect(0.219f, 0.898f, 0.309f, 0.970f), 1, "the laundry basket"),
        LostThing("dino", "Rex the dinosaur", "dino", NormRect(0.314f, 0.645f, 0.503f, 0.762f), 1, "the toy box"),
        LostThing("bricks", "The brick gang", "bricks", NormRect(0.511f, 0.755f, 0.610f, 0.866f), 1, "the brick bin"),
        LostThing("books", "Five sleepy books", "books", NormRect(0.676f, 0.779f, 0.808f, 0.940f), 1, "the bookshelf"),
        LostThing("jacket", "The crumpled jacket", "jacket", NormRect(0.570f, 0.289f, 0.682f, 0.596f), 1, "the hooks by the window"),
    )
}
