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
 * Live mode: the Rosetta API (see /infra) is the family's source of truth. Kids' phones upload photos and voice
 * notes, the AI writes results back, and the server keeps every reward, streak, star and wish. The TV polls once a
 * second and mirrors the whole household; taps on the TV are posted and shown straight away until the next poll.
 */
class CloudRepository(
    context: Context,
    private val apiUrl: String,
    private val scope: CoroutineScope,
    private val code: String = "R4KX",
) : RosettaRepository {

    private val local = LocalRepository(scope).also { it.setLive(code) }
    private val http = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build() // the AI writes a quiz in ~20 s
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
    override fun buy(kidId: String, itemId: String): Boolean {
        val h = household.value
        val item = h.shop.first { it.id == itemId }
        if (!local.buy(kidId, itemId)) return false
        post("/household/$code/kids/$kidId/buy", JSONObject().put("itemId", itemId).put("price", item.price)
            .put("kind", if (item.kind == ItemKind.Decor) "decor" else "avatar").put("avatar", item.avatar ?: kidId))
        return true
    }
    override fun equip(kidId: String, itemId: String?) {
        local.equip(kidId, itemId)
        val avatar = itemId?.let { id -> household.value.shop.first { it.id == id }.avatar } ?: kidId
        post("/household/$code/kids/$kidId/equip", JSONObject().put("itemId", itemId ?: JSONObject.NULL).put("avatar", avatar))
    }
    override fun devPhoneUpload(questId: String) = Unit // the real phone drives live mode
    override fun startRaid() = local.startRaid()
    override fun emote(kidId: String, emoji: String) = local.emote(kidId, emoji)
    override fun devRaidHit() = local.devRaidHit()
    override fun applyArenaHits(hits: List<Hit>) = local.applyArenaHits(hits)
    override fun answerPractice(questId: String, questionId: String, choice: Int) {
        // Show the answer at once; the server decides when the round is done and pays out.
        local.applyRemote { h ->
            h.copy(quests = h.quests.map { q ->
                if (q.id != questId || q.answers.containsKey(questionId)) q
                else q.copy(answers = q.answers + (questionId to Answer(choice, q.practice.firstOrNull { it.id == questionId }?.answer == choice)))
            })
        }
        post("/household/$code/quests/$questId/answer", JSONObject().put("questionId", questionId).put("choice", choice))
    }
    override fun startQuiz() = post("/household/$code/quiz/start", JSONObject().put("count", 8))
    override fun answerQuiz(questionId: String, kidId: String, choice: Int) {
        local.applyRemote { h ->
            val quiz = h.quiz ?: return@applyRemote h
            if (quiz.answers.any { it.questionId == questionId }) return@applyRemote h
            val right = quiz.questions.firstOrNull { it.id == questionId }?.answer == choice
            h.copy(quiz = quiz.copy(answers = quiz.answers + QuizAnswer(questionId, kidId, choice, right)))
        }
        post("/household/$code/quiz/answer", JSONObject().put("questionId", questionId).put("kidId", kidId).put("choice", choice))
    }
    override fun requestTvTime() = post("/household/$code/tvtime/request")
    override fun useTvTime(minutes: Int) = post("/household/$code/tvtime/use", JSONObject().put("minutes", minutes))

    private val seenEmotes = mutableSetOf<String>()
    private val seenMessages = mutableSetOf<String>()
    private val rewarded = mutableSetOf<String>()
    private var primed = false // messages and rewards that happened before the TV started aren't replayed

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
            if (id.isNotBlank() && seenEmotes.add(id) && primed) local.emote(e.getString("kidId"), e.getString("emoji"))
        }
        json.optJSONArray("feed")?.objects()?.filter { it.optString("kind") == "message" }?.forEach { m ->
            if (seenMessages.add(m.getString("id")) && primed) {
                val audio = m.optJSONObject("data")?.optString("audioUrl")?.ifBlank { null }?.let(::cacheVoice)
                local.applyRemoteMessage(FamilyMessage(m.getString("id"), m.optString("title"), m.optString("body"), System.currentTimeMillis(), audio))
            }
        }
        val quests = json.getJSONArray("quests").objects().map(::parseQuest)
        val kids = json.getJSONArray("kids").objects().map(::parseKid)
        local.applyRemote { h ->
            h.copy(
                name = json.optString("name", h.name),
                kids = kids,
                parents = json.optJSONArray("parents")?.objects()?.map { Parent(it.getString("id"), it.optString("name"), it.optString("avatar", it.getString("id"))) } ?: h.parents,
                quests = quests,
                glyphs = stars(json),
                codex = squad(json.optJSONArray("squad")),
                stamps = stamps(json.optJSONArray("stamps")),
                decor = json.optJSONArray("decor")?.strings()?.toSet() ?: emptySet(),
                habits = json.optJSONArray("habits")?.objects()?.map(::parseHabit) ?: emptyList(),
                wishlist = json.optJSONArray("wishlist")?.objects()?.map {
                    WishItem(it.getString("id"), it.getString("kidId"), it.optString("title"), it.optDouble("price", 0.0), it.optString("status", "saving"))
                } ?: emptyList(),
                tvTime = json.optJSONObject("tvTime")?.let { parseTv(it, json) } ?: TvTime(),
                history = json.optJSONArray("history")?.objects()?.map(::parseHistory) ?: emptyList(),
                coach = json.optJSONObject("digest")?.let { d ->
                    d.keys().asSequence().associateWith { k ->
                        d.getJSONObject(k).let { CoachNotes(it.optString("headline"), it.optJSONArray("notes")?.strings() ?: emptyList()) }
                    }
                } ?: emptyMap(),
                currency = json.optJSONObject("settings")?.optString("currency", "₹") ?: "₹",
                memories = json.optJSONArray("memories")?.length() ?: 0,
                todayDate = json.optString("today"),
                quiz = json.optJSONObject("quiz")?.let { z ->
                    Quiz(
                        z.getString("id"), z.optString("title", "Family Quiz Night"), questions(z.optJSONArray("questions")),
                        z.optJSONArray("answers")?.objects()?.map {
                            QuizAnswer(it.getString("questionId"), it.getString("kidId"), it.getInt("choice"), it.optBoolean("correct"))
                        } ?: emptyList(),
                    )
                },
            )
        }
        // The reward reveal plays once per finished quest, with the numbers the server paid out.
        quests.filter { it.stage == QuestStage.Done && it.reward != null }.forEach { q ->
            if (rewarded.add(q.id) && primed) local.emitReward(q.reward!!)
        }
        primed = true
    }

    private fun parseKid(k: JSONObject) = Kid(
        id = k.getString("id"), name = k.optString("name"), age = k.optInt("age"), avatar = k.optString("avatar", k.getString("id")),
        level = k.optInt("level", 1), xp = k.optInt("xp"), coins = k.optInt("coins"), streak = k.optInt("streak"),
        owned = k.optJSONArray("owned")?.strings()?.toSet() ?: emptySet(),
        equipped = k.optString("equipped").takeIf { it.isNotBlank() && it != "null" },
        grade = k.optString("grade"), money = k.optDouble("money", 0.0),
    )

    private fun stars(json: JSONObject): List<Glyph> {
        val earned = json.optJSONArray("stars")?.objects() ?: emptyList()
        val goal = json.optInt("starsGoal", Household0.STAR_GOAL)
        return (0 until goal).map { i ->
            earned.getOrNull(i)?.let { Glyph(it.optString("id", "star${i + 1}"), it.optString("title"), "star", it.optString("earnedBy")) }
                ?: Glyph("star${i + 1}", "", "star", null)
        }
    }

    /** The Squad catalogue (art and stories) with the rescues the family really made. */
    private fun squad(a: JSONArray?): List<CodexEntry> {
        val real = a?.objects()?.associateBy { it.getString("character") } ?: emptyMap()
        return Household0.squadCatalog.map { e ->
            real[e.character]?.let { e.copy(rescues = it.optInt("rescues"), firstBy = it.optString("firstBy").ifBlank { null }) } ?: e
        }
    }

    private fun stamps(a: JSONArray?): List<Stamp> {
        val earned = a?.objects() ?: emptyList()
        return Household0.stampCatalog.map { s ->
            earned.firstOrNull { it.getString("id") == s.id }?.let { s.copy(earnedOn = it.optString("date"), by = it.optString("earnedBy")) } ?: s
        }
    }

    private fun parseHabit(h: JSONObject) = Habit(
        id = h.getString("id"), title = h.optString("title"), icon = h.optString("icon"),
        kidIds = h.optJSONArray("kidIds")?.strings() ?: emptyList(), proof = h.optString("proof", "none"),
        coins = h.optInt("coins"), money = h.optDouble("money", 0.0),
        streaks = h.optJSONObject("streaks")?.let { s -> s.keys().asSequence().associateWith { s.optInt(it) } } ?: emptyMap(),
    )

    private fun parseTv(t: JSONObject, json: JSONObject) = TvTime(
        mustDo = t.optInt("mustDo"), done = t.optInt("done"), allDone = t.optBoolean("allDone"), unlocked = t.optBoolean("unlocked"),
        needsApproval = t.optBoolean("needsApproval"), requested = t.optBoolean("requested"), granted = t.optBoolean("granted"),
        tvMinutes = t.optInt("tvMinutes", 45), minutesLeft = t.optInt("minutesLeft", 45),
        waitingOn = t.optJSONArray("waitingOn")?.objects()?.map { w ->
            val name = json.optJSONArray("kids")?.objects()?.firstOrNull { it.optString("id") == w.optString("kidId") }?.optString("name")
            if (name.isNullOrBlank()) w.optString("title") else "$name: ${w.optString("title")}"
        } ?: emptyList(),
    )

    private fun parseHistory(r: JSONObject) = HistoryRow(
        kidId = r.getString("kidId"), kind = r.optString("kind"), title = r.optString("title"), habitId = r.optString("habitId"),
        day = r.optString("day"), atMs = r.optLong("atMs"), minutes = r.optInt("minutes"), coins = r.optInt("coins"),
        money = r.optDouble("money", 0.0), right = r.optInt("right"), total = r.optInt("total"), topic = r.optString("topic"),
        skills = r.optJSONArray("skills")?.strings() ?: emptyList(),
    )

    private fun parseQuest(q: JSONObject): Quest {
        val id = q.getString("id")
        val checkin = q.optJSONObject("checkin")
        return Quest(
            id = id,
            kidId = q.getString("kidId"),
            roomId = q.optString("roomId"),
            title = q.getString("title"),
            glyphId = q.optString("glyphId"),
            stage = runCatching { QuestStage.valueOf(q.getString("stage")) }.getOrDefault(QuestStage.Ready),
            beforePhoto = q.optString("beforeUrl").ifBlank { null }?.let { cachePhoto(id + "_before_" + q.optLong("openedAtMs"), it) },
            afterPhoto = q.optString("afterUrl").ifBlank { null }?.let { cachePhoto(id + "_after_" + q.optInt("attempt"), it) },
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
            xpReward = q.optJSONObject("reward")?.optInt("xp") ?: when (q.optString("kind")) { "study" -> 80; "habit" -> 20; else -> 75 },
            coinReward = q.optJSONObject("reward")?.optInt("coins") ?: when (q.optString("kind")) { "study" -> 35; "habit" -> 10; else -> 30 },
            needsParent = q.optBoolean("needsParent"),
            kind = when (q.optString("kind")) { "study" -> QuestKind.Study; "habit" -> QuestKind.Habit; else -> QuestKind.Tidy },
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
            icon = q.optString("icon"),
            proof = q.optString("proof", "photo"),
            mustDo = q.optBoolean("mustDo"),
            whatISee = q.optString("whatISee"),
            checkin = checkin?.let { c -> c.optString("note").ifBlank { c.optString("summary") } } ?: "",
            checkinKind = when {
                checkin == null -> ""
                checkin.optString("audioUrl").isNotBlank() -> "voice"
                checkin.optString("photoUrl").isNotBlank() -> "photo"
                checkin.optString("note").isNotBlank() -> "note"
                else -> "tick"
            },
            reward = q.optJSONObject("lastReward")?.let { r ->
                QuestReward(
                    questId = id, kidId = q.getString("kidId"), xp = r.optInt("xp"), coins = r.optInt("coins"),
                    leveledUp = r.optBoolean("levelUp"), newLevel = r.optInt("level", 1),
                    glyph = if (r.optBoolean("star")) Glyph("star", q.getString("title"), "star", q.getString("kidId")) else null,
                    stamp = r.optJSONObject("stamp")?.let { s -> Stamp(s.optString("id"), s.optString("name"), s.optString("art"), s.optString("date"), s.optString("earnedBy")) },
                    codexUpgrades = emptyList(), money = r.optDouble("money", 0.0), streak = r.optInt("streak"),
                )
            },
        )
    }

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
            it.getInt("answer"), it.optString("why"), it.optString("skill"), it.optString("forKid"), it.optString("reason"),
        )
    } ?: emptyList()

    private fun rect(a: JSONArray) = NormRect(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat())
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private companion object { const val TAG = "RosettaCloud" }
}
