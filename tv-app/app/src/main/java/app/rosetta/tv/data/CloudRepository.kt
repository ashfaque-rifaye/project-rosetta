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
    private val voiceDir = File(context.cacheDir, "voice").apply { mkdirs() }
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
    override fun applyArenaHits(hits: List<Hit>) = local.applyArenaHits(hits)
    override fun answerPractice(questId: String, questionId: String, choice: Int) {
        local.answerPractice(questId, questionId, choice)
        post("/household/$code/quests/$questId/answer", JSONObject().put("questionId", questionId).put("choice", choice))
    }
    override fun startQuiz() = post("/household/$code/quiz/start", JSONObject().put("count", 8))
    override fun answerQuiz(questionId: String, kidId: String, choice: Int) {
        local.answerQuiz(questionId, kidId, choice)
        post("/household/$code/quiz/answer", JSONObject().put("questionId", questionId).put("kidId", kidId).put("choice", choice))
    }
    private val seenEmotes = mutableSetOf<String>()
    private val seenMessages = mutableSetOf<String>()
    private var primed = false // messages already in the feed when the TV starts aren't replayed

    private fun post(path: String, body: JSONObject = JSONObject()) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(apiUrl.trimEnd('/') + path).post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().close()
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
        json.optJSONArray("feed")?.objects()?.filter { it.optString("kind") == "message" }?.forEach { m ->
            if (seenMessages.add(m.getString("id")) && primed) {
                val audio = m.optJSONObject("data")?.optString("audioUrl")?.ifBlank { null }?.let(::cacheVoice)
                local.applyRemoteMessage(FamilyMessage(m.getString("id"), m.optString("title"), m.optString("body"), System.currentTimeMillis(), audio))
            }
        }
        primed = true
        val quests = json.getJSONArray("quests")
        for (i in 0 until quests.length()) local.applyRemoteQuest(parseQuest(quests.getJSONObject(i)))
        json.optJSONObject("quiz")?.let { z ->
            local.applyRemoteQuiz(Quiz(
                z.getString("id"), z.optString("title", "Family Quiz Battle"), questions(z.optJSONArray("questions")),
                z.optJSONArray("answers")?.objects()?.map {
                    QuizAnswer(it.getString("questionId"), it.getString("kidId"), it.getInt("choice"), it.optBoolean("correct"))
                } ?: emptyList(),
            ))
        }
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
        keeperAudio = q.optString("keeperAudioUrl").ifBlank { null }?.let(::cacheVoice),
        confidence = q.optDouble("confidence", 1.0).toFloat(),
        needsParent = q.optBoolean("needsParent"),
        kind = if (q.optString("kind") == "study") QuestKind.Study else QuestKind.Tidy,
        homework = q.optJSONObject("homework")?.let { h ->
            Homework(
                h.optString("subject"), h.optString("topic"),
                h.optJSONArray("items")?.objects()?.map {
                    HomeworkItem(
                        it.getString("id"), it.optInt("n"), it.optString("question"), it.optString("kidAnswer"), it.optBoolean("correct"),
                        rect(it.getJSONArray("box")), it.optString("skill"), it.optString("hint"), it.optString("checkedBy", "ai"),
                    )
                } ?: emptyList(),
                h.optDouble("confidence", 1.0).toFloat(),
            )
        },
        practice = questions(q.optJSONArray("practice")),
        answers = q.optJSONObject("answers")?.let { a ->
            a.keys().asSequence().associateWith { k -> a.getJSONObject(k).let { Answer(it.getInt("choice"), it.optBoolean("correct")) } }
        } ?: emptyMap(),
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
        if (!f.exists()) download(url, f)
        return f.absolutePath
    }

    /** Gemini voice clips: the S3 key (the URL's path) names each clip, so it downloads once. */
    private fun cacheVoice(url: String): String? = runCatching {
        val f = File(voiceDir, url.substringBefore('?').substringAfterLast('/'))
        if (!f.exists()) download(url, f)
        f.absolutePath
    }.onFailure { Log.w(TAG, "voice clip failed: ${it.message}") }.getOrNull()

    private fun download(url: String, f: File) {
        val tmp = File(f.parentFile, f.name + ".part")
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            tmp.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
        tmp.renameTo(f)
    }

    private fun questions(a: JSONArray?) = a?.objects()?.map {
        Question(
            it.getString("id"), it.getString("prompt"), (0 until it.getJSONArray("choices").length()).map { i -> it.getJSONArray("choices").getString(i) },
            it.getInt("answer"), it.optString("why"), it.optString("skill"), it.optString("forKid"),
        )
    } ?: emptyList()

    private fun rect(a: JSONArray) = NormRect(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat())
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private companion object { const val TAG = "RosettaCloud" }
}
