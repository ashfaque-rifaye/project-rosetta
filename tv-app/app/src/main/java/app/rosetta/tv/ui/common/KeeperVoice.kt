package app.rosetta.tv.ui.common

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * The Keeper's voice. On-device TextToSpeech in demo mode; in cloud mode the agents return
 * Amazon Polly audio and this falls back to TTS only if the clip can't be played.
 */
class KeeperVoice(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.US
            tts.setSpeechRate(0.92f)
            tts.setPitch(0.88f)
            pending?.let { speak(it) }
        }
    }

    fun speak(raw: String) {
        // Emojis are for the eyes; strip them (and symbol pictographs) before speaking.
        val line = raw.replace(Regex("""[\p{So}\p{Cn}\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{FE0F}]"""), "").replace(Regex("""\s+"""), " ").trim()
        if (line.isBlank()) return
        if (!ready) { pending = line; return }
        tts.speak(line, TextToSpeech.QUEUE_FLUSH, null, line.hashCode().toString())
    }

    fun shutdown() = tts.shutdown()
}
