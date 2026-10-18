package app.rosetta.tv.ui.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.rosetta.tv.data.PlaybackSpec

private const val TAG = "RosettaPlayer"

/** Languages Media3 actually selected, read back from the player rather than from our request. */
data class ActiveTracks(val audioLanguage: String?, val subtitleLanguage: String?)

@OptIn(UnstableApi::class)
fun createRosettaPlayer(
    context: Context,
    spec: PlaybackSpec,
    initialAudio: String,
    initialSubtitle: String?,
): ExoPlayer {
    val dataSourceFactory = DefaultDataSource.Factory(context)

    val mainItem = MediaItem.Builder()
        .setUri(spec.videoUri)
        .setSubtitleConfigurations(
            spec.subtitles.map {
                MediaItem.SubtitleConfiguration.Builder(it.uri)
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(it.language)
                    .setLabel(it.label)
                    .build()
            },
        )
        .build()
    val main = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mainItem)

    // Each dub is its own file (one Polly output per language); merge them alongside the video.
    val dubs = spec.dubs.mapNotNull { dub ->
        dub.uri?.let { ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(it)) }
    }
    val source: MediaSource = if (dubs.isEmpty()) main else MergingMediaSource(main, *dubs.toTypedArray())

    return ExoPlayer.Builder(context).build().apply {
        // Set the viewer's languages before prepare() so the first frame already uses them
        // (otherwise the default selector briefly plays whichever audio track it likes).
        selectLanguages(initialAudio, initialSubtitle)
        setMediaSource(source)
        addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val active = tracks.active()
                Log.i(TAG, "tracks: audio=${active.audioLanguage} subs=${active.subtitleLanguage} " +
                    "audioGroups=${tracks.groups.count { it.type == C.TRACK_TYPE_AUDIO }} " +
                    "textGroups=${tracks.groups.count { it.type == C.TRACK_TYPE_TEXT }}")
            }
        })
        prepare()
    }
}

/** Ask Media3 for an audio language and an optional subtitle language (null turns subtitles off). */
fun ExoPlayer.selectLanguages(audio: String, subtitle: String?) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setPreferredAudioLanguage(audio)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle == null)
        .apply { if (subtitle != null) setPreferredTextLanguage(subtitle) }
        .build()
}

fun Tracks.active(): ActiveTracks {
    fun selectedLanguage(type: Int) = groups
        .filter { it.type == type && it.isSelected }
        .firstNotNullOfOrNull { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it).language }
        }
    return ActiveTracks(selectedLanguage(C.TRACK_TYPE_AUDIO), selectedLanguage(C.TRACK_TYPE_TEXT))
}
