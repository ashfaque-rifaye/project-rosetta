package app.rosetta.tv.ui.common

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * The Keeper's voice: a warm Amazon Polly voice. In live mode the agents send a clip with each line;
 * fixed and offline lines use clips bundled in assets/voice (index.json maps line -> file, made by
 * assets/voice/make_keeper_voice.py). The device's TextToSpeech is only the last resort.
 */
class KeeperVoice(context: Context) : TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private val tts = TextToSpeech(app, this)
    private var ready = false
    private var pending: String? = null
    private var player: MediaPlayer? = null
    private val bundled: Map<String, String> = runCatching {
        val json = JSONObject(app.assets.open("voice/index.json").bufferedReader().readText())
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.US
            tts.setSpeechRate(0.92f)
            tts.setPitch(0.88f)
            pending?.let { speakDevice(it) }
        }
    }

    /** Say [raw]: the Polly clip at [audio] if the agents sent one, else a bundled clip, else the device voice. */
    fun speak(raw: String, audio: String? = null) {
        if (raw.isBlank() && audio == null) return
        stop()
        val asset = bundled[raw.trim()]
        val played = when {
            audio != null && File(audio).exists() -> play { setDataSource(audio) }
            asset != null -> play { app.assets.openFd("voice/$asset").use { setDataSource(it.fileDescriptor, it.startOffset, it.length) } }
            else -> false
        }
        if (!played) speakDevice(raw)
    }

    private fun play(source: MediaPlayer.() -> Unit): Boolean = runCatching {
        player = MediaPlayer().apply {
            source()
            setOnCompletionListener { it.release(); if (player === it) player = null }
            prepare()
            start()
        }
    }.onFailure { Log.w("KeeperVoice", "clip failed, using the device voice: ${it.message}") }.isSuccess

    private fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        if (ready) tts.stop()
    }

    private fun speakDevice(raw: String) {
        // Emojis are for the eyes; strip them (and symbol pictographs) before speaking.
        val line = raw.replace(Regex("""[\p{So}\p{Cn}\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{FE0F}]"""), "").replace(Regex("""\s+"""), " ").trim()
        if (line.isBlank()) return
        if (!ready) { pending = line; return }
        tts.speak(line, TextToSpeech.QUEUE_FLUSH, null, line.hashCode().toString())
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}
