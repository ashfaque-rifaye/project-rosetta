package app.rosetta.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.rosetta.tv.data.SampleCatalog
import app.rosetta.tv.data.TestMedia
import app.rosetta.tv.ui.home.HomeScreen
import app.rosetta.tv.ui.player.PlayerScreen
import app.rosetta.tv.ui.theme.RosettaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RosettaTheme {
                var playingId by rememberSaveable { mutableStateOf<String?>(null) }
                val playing = playingId?.let { id ->
                    SampleCatalog.shelves.flatMap { it.episodes }.firstOrNull { it.id == id }
                }

                if (playing != null) {
                    BackHandler { playingId = null }
                    // Every episode plays the bundled test media until the pipeline feeds real specs.
                    PlayerScreen(episode = playing, spec = TestMedia.spec)
                } else {
                    HomeScreen(onPlay = { playingId = it.id })
                }
            }
        }
    }
}
