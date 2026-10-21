package app.rosetta.tv.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Live mode: the household lives in DynamoDB behind the Rosetta API (see /infra). The kid's phone
 * uploads photos straight to S3; the agent crew (Scout, Coach, Judge, Keeper) writes results back.
 * The TV polls the household once a second, which is simple, cheap and survives flaky Wi-Fi.
 */
class CloudRepository(
    context: Context,
    private val apiUrl: String,
    private val scope: CoroutineScope,
    private val code: String = "R4KX",
) : RosettaRepository {

    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val photoDir = File(context.cacheDir, "photos").apply { mkdirs() }
    private val state = MutableStateFlow(DemoHousehold.initial().copy(demo = false, pairingCode = code))
    override val household: StateFlow<Household> = state

    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { state.value = fetch() }.onFailure { Log.w(TAG, "poll failed: ${it.message}") }
                delay(1000)
            }
        }
    }

    override fun openQuest(questId: String) = post("/household/$code/quests/$questId/open")
    override fun demoSendBefore(questId: String) = Unit // the phone drives live mode
    override fun demoSendAfter(questId: String) = Unit
    override fun closeQuest(questId: String) = post("/household/$code/quests/$questId/close")

    private fun post(path: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                http.newCall(
                    Request.Builder().url(apiUrl.trimEnd('/') + path)
                        .post("{}".toRequestBody("application/json".toMediaType())).build(),
                ).execute().close()
            }.onFailure { Log.w(TAG, "POST $path failed: ${it.message}") }
        }
    }

    private fun fetch(): Household {
        val body = http.newCall(Request.Builder().url("${apiUrl.trimEnd('/')}/household/$code").build()).execute().use {
            check(it.isSuccessful) { "HTTP ${it.code}" }
            it.body!!.string()
        }
        return parseHousehold(JSONObject(body))
    }

    private fun parseHousehold(j: JSONObject) = Household(
        name = j.getString("name"),
        kids = j.getJSONArray("kids").map { Kid(it.getString("id"), it.getString("name"), it.getInt("age")) },
        rooms = j.getJSONArray("rooms").map { Room(it.getString("id"), it.getString("name"), rect(it.getJSONArray("rect")), it.optString("ownerId").ifBlank { null }) },
        quests = j.getJSONArray("quests").map { parseQuest(it) },
        glyphs = j.getJSONArray("glyphs").map { Glyph(it.getString("id"), it.getString("name"), it.getString("asset"), it.optString("earnedBy").ifBlank { null }) },
        pairingCode = j.getString("code"),
        demo = false,
    )

    private fun parseQuest(q: JSONObject) = Quest(
        id = q.getString("id"),
        kidId = q.getString("kidId"),
        roomId = q.getString("roomId"),
        title = q.getString("title"),
        glyphId = q.getString("glyphId"),
        stage = QuestStage.valueOf(q.getString("stage")),
        beforePhoto = q.optString("beforeUrl").ifBlank { null }?.let { cachePhoto(q.getString("id") + "_before", it) },
        afterPhoto = q.optString("afterUrl").ifBlank { null }?.let { cachePhoto(q.getString("id") + "_after_" + q.optInt("attempt"), it) },
        things = q.optJSONArray("things")?.map {
            LostThing(
                id = it.getString("id"),
                name = it.getString("name"),
                character = it.getString("character"),
                box = rect(it.getJSONArray("box")),
                stars = it.optInt("stars", 1),
                home = it.optString("home"),
                returned = it.optBoolean("returned"),
            )
        } ?: emptyList(),
        keeperLine = q.optString("keeperLine"),
        confidence = q.optDouble("confidence", 1.0).toFloat(),
    )

    /** Presigned S3 URLs change on every poll; cache by logical name so the TV only downloads once. */
    private fun cachePhoto(name: String, url: String): String {
        val f = File(photoDir, "$name.jpg")
        if (!f.exists()) {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                check(r.isSuccessful) { "photo HTTP ${r.code}" }
                f.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
            }
        }
        return f.absolutePath
    }

    private fun rect(a: JSONArray) = NormRect(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat())

    private inline fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }

    private companion object { const val TAG = "RosettaCloud" }
}
