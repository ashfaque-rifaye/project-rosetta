package app.rosetta.tv.ui.intro

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import app.rosetta.tv.R
import app.rosetta.tv.ui.theme.Rosetta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The opening: an 8-second Veo film (a carved stone whose stars ignite, then a golden light sweep) with an original
 * Lyria sting, and the ROSETTA wordmark revealed over the light burst. Any key skips it.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${R.raw.intro}")))
            volume = 1f
            prepare()
            playWhenReady = true
        }
    }
    val word = remember { Animatable(0f) }
    val sweep = remember { Animatable(0f) }
    val fadeOut = remember { Animatable(0f) }
    val finished = remember { booleanArrayOf(false) }
    fun finish() { if (!finished[0]) { finished[0] = true; onDone() } }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) finish() }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) = finish()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        // The wordmark lands on the golden burst (~5.2 s), then everything fades into Home.
        delay(5200)
        launch { sweep.animateTo(1f, tween(1400, easing = LinearEasing)) }
        word.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
        delay(900)
        fadeOut.animateTo(1f, tween(600))
        finish()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) { finish(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // A soft vignette so the wordmark reads over the light.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = word.value * 0.55f }
            .background(Brush.radialGradient(listOf(Color(0xCC0A1020), Color.Transparent), radius = 900f)))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                alpha = word.value
                val s = 0.86f + 0.14f * word.value
                scaleX = s; scaleY = s
                translationY = (1f - word.value) * 24f * density
            },
        ) {
            Text(
                "ROSETTA", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 112.sp, letterSpacing = 22.sp,
                color = Rosetta.Gold,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width((420 * sweep.value).dp).height(2.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Rosetta.Gold, Color.Transparent))))
            Spacer(Modifier.height(14.dp))
            Text("Where the whole family levels up", fontSize = 24.sp, letterSpacing = 3.sp, color = Rosetta.Cream.copy(alpha = 0.9f))
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = fadeOut.value }.background(Rosetta.Night))
    }
}
