package app.rosetta.tv.ui.raid

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.rosetta.tv.BuildConfig
import app.rosetta.tv.data.Hit
import app.rosetta.tv.data.Household
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Mess Boss fight in full 3D: "Rosetta Arena" is a companion Unity app (arena-unity/). The TV app hands it the
 * raid as JSON and gets the new hits back when the family presses Back. If the arena isn't installed, the native
 * Compose raid screen is used instead.
 */
object ArenaLauncher {
    const val PACKAGE = "app.rosetta.arena"
    private const val ACTIVITY = "com.unity3d.player.UnityPlayerGameActivity"

    fun isInstalled(context: Context): Boolean =
        launch(context) != null

    private fun launch(context: Context): Intent? =
        context.packageManager.getLeanbackLaunchIntentForPackage(PACKAGE) ?: context.packageManager.getLaunchIntentForPackage(PACKAGE)

    fun intent(context: Context, h: Household): Intent? {
        val raid = h.raid ?: return null
        // A clean explicit intent: the launcher intent carries FLAG_ACTIVITY_NEW_TASK, which makes Android cancel the
        // result immediately, so the arena's hits would never come back.
        val base = Intent().setComponent(launch(context)?.component ?: ComponentName(PACKAGE, ACTIVITY))
        val json = JSONObject()
            .put("mode", "raid")
            .put("bossId", raid.boss.id)
            .put("bossName", raid.boss.name)
            .put("tagline", raid.boss.tagline)
            .put("room", raid.boss.roomName)
            .put("maxHp", raid.boss.maxHp)
            .put("apiUrl", BuildConfig.ROSETTA_API_URL)
            .put("code", h.pairingCode)
            .put("kids", JSONArray(h.kids.map { JSONObject().put("id", it.id).put("name", it.name).put("avatar", "avatar_${it.avatar}") }))
            .put("hits", JSONArray(raid.hits.map { JSONObject().put("kidId", it.kidId).put("thing", it.thing).put("damage", it.damage).put("crit", it.crit) }))
        return base.putExtra("raid", json.toString())
    }

    /** The family Quiz Battle as the Unity "Stone Trial": questions in, answers back (see [quizAnswers]). */
    fun quizIntent(context: Context, h: Household): Intent? {
        val quiz = h.quiz ?: return null
        val base = Intent().setComponent(launch(context)?.component ?: ComponentName(PACKAGE, ACTIVITY))
        val json = JSONObject()
            .put("mode", "quiz")
            .put("code", h.pairingCode)
            .put("kids", JSONArray(h.kids.map { JSONObject().put("id", it.id).put("name", it.name).put("avatar", "avatar_${it.avatar}") }))
            .put("questions", JSONArray(quiz.questions.map { q ->
                JSONObject().put("id", q.id).put("prompt", q.prompt).put("choices", JSONArray(q.choices)).put("answer", q.answer)
                    .put("why", q.why).put("skill", q.skill).put("forKid", q.forKid)
            }))
            .put("answers", JSONArray(quiz.answers.map {
                JSONObject().put("questionId", it.questionId).put("kidId", it.kidId).put("choice", it.choice).put("correct", it.correct)
            }))
        return base.putExtra("raid", json.toString())
    }

    /** Quiz answers the arena reports back (including any it was given). */
    fun quizAnswers(result: Intent?): List<app.rosetta.tv.data.QuizAnswer>? {
        val json = result?.getStringExtra("raid") ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (o.optString("mode") != "quiz") return null
        val arr = o.optJSONArray("answers") ?: return null
        return (0 until arr.length()).map { i ->
            val a = arr.getJSONObject(i)
            app.rosetta.tv.data.QuizAnswer(a.getString("questionId"), a.getString("kidId"), a.getInt("choice"), a.optBoolean("correct"))
        }
    }

    /** Hits the arena reports back (everything, including the ones it was given). */
    fun hits(result: Intent?): List<Hit>? {
        val json = result?.getStringExtra("raid") ?: return null
        val arr = runCatching { JSONObject(json).getJSONArray("hits") }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Hit(o.getString("kidId"), o.optString("thing", "sock"), o.getInt("damage"), o.optBoolean("crit"), now - (arr.length() - i) * 1000L)
        }
    }
}
