package app.rosetta.tv.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the TV needs. Two implementations share this contract:
 * [DemoRepository] (bundled household, runs fully offline for judges) and the cloud repository
 * (phone uploads + agent crew over REST/WebSocket).
 */
interface RosettaRepository {
    val household: StateFlow<Household>

    /** The TV opens a quest and shows the QR/code: the kid's phone becomes the camera. */
    fun openQuest(questId: String)

    /** Demo only: stand in for the phone and send the sample "before" photo. */
    fun demoSendBefore(questId: String)

    /** Demo only: stand in for the phone and send the next sample "after" photo. */
    fun demoSendAfter(questId: String)

    /** After the reward is shown, the quest leaves the board. */
    fun closeQuest(questId: String)
}
