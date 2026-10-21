package app.rosetta.tv.ui.kit

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import app.rosetta.tv.R

/** UI sound effects (synthesized in-repo, see assets/sfx/make_sfx.py) + the background music loop. */
object Sfx {
    const val Move = 0
    const val Select = 1
    const val Found = 2
    const val Home = 3
    const val Reward = 4
    const val Carve = 5
    const val LevelUp = 6

    private var pool: SoundPool? = null
    private val ids = IntArray(7)
    private var music: MediaPlayer? = null

    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build().also { p ->
            ids[Move] = p.load(context, R.raw.sfx_move, 1)
            ids[Select] = p.load(context, R.raw.sfx_select, 1)
            ids[Found] = p.load(context, R.raw.sfx_found, 1)
            ids[Home] = p.load(context, R.raw.sfx_home, 1)
            ids[Reward] = p.load(context, R.raw.sfx_reward, 1)
            ids[Carve] = p.load(context, R.raw.sfx_carve, 1)
            ids[LevelUp] = p.load(context, R.raw.sfx_levelup, 1)
        }
        runCatching {
            val id = context.resources.getIdentifier("music_home", "raw", context.packageName)
            if (id != 0) music = MediaPlayer.create(context, id)?.apply { isLooping = true; setVolume(0.22f, 0.22f) }
        }
    }

    fun play(sound: Int, volume: Float = if (sound == Move) 0.35f else 0.8f) {
        pool?.play(ids[sound], volume, volume, 1, 0, 1f)
    }

    fun musicOn() = runCatching { music?.start() }
    fun musicOff() = runCatching { music?.pause() }
    fun release() { pool?.release(); pool = null; music?.release(); music = null }
}
