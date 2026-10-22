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
