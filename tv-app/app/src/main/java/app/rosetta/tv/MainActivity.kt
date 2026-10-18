package app.rosetta.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.rosetta.tv.ui.home.HomeScreen
import app.rosetta.tv.ui.theme.RosettaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RosettaTheme {
                HomeScreen()
            }
        }
    }
}
