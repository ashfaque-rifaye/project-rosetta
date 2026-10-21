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

    fun speak(line: String) {
        if (line.isBlank()) return
        if (!ready) { pending = line; return }
        tts.speak(line, TextToSpeech.QUEUE_FLUSH, null, line.hashCode().toString())
    }

    fun shutdown() = tts.shutdown()
}
