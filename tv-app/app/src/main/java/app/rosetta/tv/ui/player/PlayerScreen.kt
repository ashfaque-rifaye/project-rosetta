// FilterChip is still @ExperimentalTvMaterial3Api in tv-material 1.0.
@file:kotlin.OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package app.rosetta.tv.ui.player

import android.view.View
import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.PlayerView
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.Episode
import app.rosetta.tv.data.PlaybackSpec
import app.rosetta.tv.ui.theme.RosettaColors
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(episode: Episode, spec: PlaybackSpec) {
    val context = LocalContext.current
    var audio by remember { mutableStateOf(spec.originalAudio.language) }
    var subtitle by remember { mutableStateOf<String?>(spec.subtitles.firstOrNull()?.language) }
    val player = remember(spec) { createRosettaPlayer(context, spec, audio, subtitle) }
    var active by remember { mutableStateOf(ActiveTracks(null, null)) }
    // Subtitles are drawn by Compose (not PlayerView) so we control placement and, later,
    // can make individual words focusable for Rosetta Notes.
    var cueLines by remember { mutableStateOf(emptyList<String>()) }
    val firstChip = remember { FocusRequester() }
    val root = remember { FocusRequester() }

    // The language bar hides after a few idle seconds so it never sits on top of the subtitles.
    var barVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    LaunchedEffect(barVisible, lastInteraction) {
        if (barVisible) {
            delay(BAR_IDLE_MS)
            barVisible = false
        }
    }
    LaunchedEffect(barVisible) {
        if (barVisible) firstChip.requestFocus() else root.requestFocus()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                active = tracks.active()
            }

            override fun onCues(cueGroup: CueGroup) {
                cueLines = cueGroup.cues.mapNotNull { cue -> cue.text?.toString()?.takeIf { it.isNotBlank() } }
            }
        }
        player.addListener(listener)
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(audio, subtitle) { player.selectLanguages(audio, subtitle) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(root)
            .focusable()
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key.nativeKeyCode) {
                    // Fire TV remote play/pause button.
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        player.playWhenReady = !player.playWhenReady
                        true
                    }
                    // Back always goes to the activity (returns to the channel).
                    AndroidKeyEvent.KEYCODE_BACK -> false
                    else -> {
                        lastInteraction = System.currentTimeMillis()
                        if (barVisible) {
                            false
                        } else {
                            // First press only wakes the bar, like the Fire TV system player.
                            barVisible = true
                            true
                        }
                    }
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                    subtitleView?.visibility = View.GONE
                }
            },
        )

        // Lift subtitles above the language bar only while it is on screen.
        val subtitleLift by animateDpAsState(if (barVisible) 300.dp else 48.dp, label = "subtitleLift")
        Subtitles(
            lines = cueLines,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 96.dp, end = 96.dp, bottom = subtitleLift),
        )

        AnimatedVisibility(
            visible = barVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            LanguageBar(
                episode = episode,
                spec = spec,
                audio = audio,
                subtitle = subtitle,
                active = active,
                onAudio = { audio = it },
                onSubtitle = { subtitle = it },
                firstChip = firstChip,
            )
        }
    }
}

private const val BAR_IDLE_MS = 5_000L

@Composable
private fun Subtitles(lines: List<String>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        lines.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 40.sp),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.66f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun LanguageBar(
    episode: Episode,
    spec: PlaybackSpec,
    audio: String,
    subtitle: String?,
    active: ActiveTracks,
    onAudio: (String) -> Unit,
    onSubtitle: (String?) -> Unit,
    firstChip: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 58.dp, end = 58.dp, top = 40.dp, bottom = 28.dp),
    ) {
        Text(episode.title, style = MaterialTheme.typography.titleLarge, color = RosettaColors.Parchment)
        Spacer(Modifier.height(12.dp))
        ChipRow("Listen in") {
            spec.audioOptions.forEachIndexed { i, option ->
                FilterChip(
                    selected = audio == option.language,
                    onClick = { onAudio(option.language) },
                    modifier = if (i == 0) Modifier.focusRequester(firstChip) else Modifier,
                ) { Text(option.label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        ChipRow("Subtitles") {
            FilterChip(selected = subtitle == null, onClick = { onSubtitle(null) }) { Text("Off") }
            spec.subtitles.forEach { option ->
                FilterChip(
                    selected = subtitle == option.language,
                    onClick = { onSubtitle(option.language) },
                ) { Text(option.label) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Now playing · audio: ${active.audioLanguage ?: "–"} · subtitles: ${active.subtitleLanguage ?: "off"}",
            style = MaterialTheme.typography.labelSmall,
            color = RosettaColors.Muted,
        )
    }
}

@Composable
private fun ChipRow(label: String, chips: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = RosettaColors.Muted,
            modifier = Modifier.width(110.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { chips() }
    }
}
