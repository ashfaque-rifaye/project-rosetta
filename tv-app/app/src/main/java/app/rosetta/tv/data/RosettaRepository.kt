package app.rosetta.tv.data

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the TV needs. [LocalRepository] runs the household engine on the device (used until the
 * cloud agent crew is deployed); [CloudRepository] mirrors the household from the Rosetta API.
 */
interface RosettaRepository {
    val household: StateFlow<Household>

    /** Fires once per finished quest so the TV can play the reward reveal. */
    val rewards: SharedFlow<QuestReward>

    /** The TV opens a quest and shows the QR: the kid's phone becomes the camera. */
    fun openQuest(questId: String)

    /** Carve the glyph after the reward reveal; the quest leaves the board. */
    fun closeQuest(questId: String)

    fun buy(kidId: String, itemId: String): Boolean

    fun equip(kidId: String, itemId: String?)

    /**
     * Developer hook (remote MENU key): stands in for the kid's phone when no phone is connected,
     * using the bundled reference photos. Not part of the user-facing UI.
     */
    fun devPhoneUpload(questId: String)
}
