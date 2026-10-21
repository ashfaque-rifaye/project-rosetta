package app.rosetta.tv.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Live mode: quests are driven by the agent crew behind the Rosetta API (see /infra). The kid's phone
 * uploads photos to S3; the crew writes stages, Lost Things, Keeper lines and its step log back.
 * The TV polls once a second and merges each quest into the household; progression (XP, coins,
 * codex, shop) stays on the TV via [LocalRepository].
 */
class CloudRepository(
    context: Context,
    private val apiUrl: String,
    private val scope: CoroutineScope,
    private val code: String = "R4KX",
) : RosettaRepository {

    private val local = LocalRepository(scope).also { it.setLive(code) }
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val photoDir = File(context.cacheDir, "photos").apply { mkdirs() }
    override val household: StateFlow<Household> = local.household
    override val rewards: SharedFlow<QuestReward> = local.rewards

    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { poll() }.onFailure { Log.w(TAG, "poll failed: ${it.message}") }
                delay(1000)
            }
        }
    }

    override fun openQuest(questId: String) { local.openQuest(questId); post("/household/$code/quests/$questId/open") }
    override fun closeQuest(questId: String) { local.closeQuest(questId); post("/household/$code/quests/$questId/close") }
    override fun buy(kidId: String, itemId: String) = local.buy(kidId, itemId)
    override fun equip(kidId: String, itemId: String?) = local.equip(kidId, itemId)
    override fun devPhoneUpload(questId: String) = Unit // the real phone drives live mode
    override fun startRaid() = local.startRaid()
    override fun emote(kidId: String, emoji: String) = local.emote(kidId, emoji)
    override fun devRaidHit() = local.devRaidHit()
    private val seenEmotes = mutableSetOf<String>()

    private fun post(path: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(apiUrl.trimEnd('/') + path).post("{}".toRequestBody("application/json".toMediaType())).build()).execute().close()
            }.onFailure { Log.w(TAG, "POST $path failed: ${it.message}") }
        }
    }

    private fun poll() {
        val body = http.newCall(Request.Builder().url("${apiUrl.trimEnd('/')}/household/$code").build()).execute().use {
            check(it.isSuccessful) { "HTTP ${it.code}" }
            it.body!!.string()
        }
        val json = JSONObject(body)
        json.optJSONArray("emotes")?.objects()?.forEach { e ->
            val id = e.optString("id")
            if (id.isNotBlank() && seenEmotes.add(id)) local.emote(e.getString("kidId"), e.getString("emoji"))
        }
        val quests = json.getJSONArray("quests")
        for (i in 0 until quests.length()) local.applyRemoteQuest(parseQuest(quests.getJSONObject(i)))
    }

    private fun parseQuest(q: JSONObject) = Quest(
        id = q.getString("id"),
        kidId = q.getString("kidId"),
        roomId = q.getString("roomId"),
        title = q.getString("title"),
        glyphId = q.getString("glyphId"),
        stage = QuestStage.valueOf(q.getString("stage")),
        beforePhoto = q.optString("beforeUrl").ifBlank { null }?.let { cachePhoto(q.getString("id") + "_before", it) },
        afterPhoto = q.optString("afterUrl").ifBlank { null }?.let { cachePhoto(q.getString("id") + "_after_" + q.optInt("attempt"), it) },
        things = q.optJSONArray("things")?.objects()?.map {
            LostThing(
                id = it.getString("id"), name = it.getString("name"), character = it.getString("character"),
                box = rect(it.getJSONArray("box")), stars = it.optInt("stars", 1), home = it.optString("home"),
                returned = it.optBoolean("returned"),
            )
        } ?: emptyList(),
        keeperLine = q.optString("keeperLine"),
        confidence = q.optDouble("confidence", 1.0).toFloat(),
        needsParent = q.optBoolean("needsParent"),
        log = q.optJSONArray("log")?.objects()?.map {
            AgentEvent(
                agent = runCatching { Agent.valueOf(it.getString("agent")) }.getOrDefault(Agent.QuestMaster),
                message = it.getString("message"),
                atMs = it.optLong("atMs"),
                confidence = if (it.has("confidence")) it.getDouble("confidence").toFloat() else null,
                tool = it.optString("tool").ifBlank { null },
            )
        } ?: emptyList(),
    )

    /** Presigned URLs change on every poll; cache by logical name so each photo downloads once. */
    private fun cachePhoto(name: String, url: String): String {
        val f = File(photoDir, "$name.jpg")
        if (!f.exists()) http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            check(r.isSuccessful) { "photo HTTP ${r.code}" }
            f.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
        return f.absolutePath
    }

    private fun rect(a: JSONArray) = NormRect(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat())
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private companion object { const val TAG = "RosettaCloud" }
}
