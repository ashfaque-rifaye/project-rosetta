package app.rosetta.tv.data

import android.net.Uri

/** An audio rendition. [uri] is null for the audio embedded in the video file (the original voice). */
data class AudioOption(val language: String, val label: String, val uri: Uri? = null)

data class SubtitleOption(val language: String, val label: String, val uri: Uri)

/**
 * What the pipeline produces per episode: the original video, separate dubbed audio files
 * (Polly output) and one WebVTT file per family language. Merged on-device by Media3.
 */
data class PlaybackSpec(
    val videoUri: Uri,
    val originalAudio: AudioOption,
    val dubs: List<AudioOption>,
    val subtitles: List<SubtitleOption>,
) {
    val audioOptions: List<AudioOption> get() = listOf(originalAudio) + dubs
}

/** Synthetic media bundled in debug builds (src/debug/assets/test) for feasibility test 2. */
object TestMedia {
    private fun asset(path: String) = Uri.parse("asset:///test/$path")

    val spec = PlaybackSpec(
        videoUri = asset("clip.mp4"),
        originalAudio = AudioOption("ta", "Original · Tamil"),
        dubs = listOf(AudioOption("en", "English dub", asset("dub_en.m4a"))),
        subtitles = listOf(
            SubtitleOption("en", "English", asset("subs_en.vtt")),
            SubtitleOption("ta", "தமிழ்", asset("subs_ta.vtt")),
            SubtitleOption("hi", "हिन्दी", asset("subs_hi.vtt")),
        ),
    )
}
