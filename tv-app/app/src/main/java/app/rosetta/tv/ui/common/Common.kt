package app.rosetta.tv.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.ui.theme.Rosetta

/** Small spaced caps label above headings. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Rosetta.Gold, modifier = modifier)
}
