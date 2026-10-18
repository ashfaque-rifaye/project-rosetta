package app.rosetta.tv.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.rosetta.tv.data.Episode
import app.rosetta.tv.data.SampleCatalog
import app.rosetta.tv.data.Shelf
import app.rosetta.tv.ui.theme.RosettaColors

@Composable
fun HomeScreen(shelves: List<Shelf> = SampleCatalog.shelves) {
    var focused by remember { mutableStateOf(shelves.first().episodes.first()) }
    val firstCard = remember { FocusRequester() }

    // D-pad users must land somewhere on launch; put focus on the first episode.
    LaunchedEffect(Unit) { firstCard.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HeroBackdrop(focused)
        Column(Modifier.fillMaxSize()) {
            Hero(focused, Modifier.padding(start = 58.dp, top = 36.dp, end = 58.dp))
            LazyColumn(
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(shelves) { shelfIndex, shelf ->
                    ShelfRow(
                        shelf = shelf,
                        onFocus = { focused = it },
                        firstCardModifier = if (shelfIndex == 0) Modifier.focusRequester(firstCard) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBackdrop(episode: Episode) {
    val top by animateColorAsState(episode.artTop, label = "heroTop")
    Box(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(
                Brush.verticalGradient(
                    listOf(top.copy(alpha = 0.45f), MaterialTheme.colorScheme.background),
                ),
            ),
    )
}

@Composable
private fun Hero(episode: Episode, modifier: Modifier = Modifier) {
    Column(modifier.height(210.dp)) {
        Text(
            "ROSETTA · FAMILY CHANNEL",
            style = MaterialTheme.typography.labelMedium,
            color = RosettaColors.Marigold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            episode.title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${episode.storyteller} · ${episode.year} · ${episode.minutes} min · told in ${episode.originalLanguage}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            episode.synopsis,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(640.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            episode.languages.forEach { LanguageChip(it) }
        }
    }
}

@Composable
private fun LanguageChip(language: String) {
    Box(
        Modifier
            .background(RosettaColors.Parchment.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(language, style = MaterialTheme.typography.labelMedium, color = RosettaColors.Parchment)
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    onFocus: (Episode) -> Unit,
    firstCardModifier: Modifier,
) {
    Column {
        Text(
            shelf.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 58.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 58.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(shelf.episodes) { index, episode ->
                EpisodeCard(
                    episode = episode,
                    onFocus = onFocus,
                    modifier = if (index == 0) firstCardModifier else Modifier,
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode, onFocus: (Episode) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = { /* Player lands in feasibility test 2. */ },
        modifier = modifier
            .size(width = 240.dp, height = 135.dp)
            .onFocusChanged { if (it.isFocused) onFocus(episode) },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, RosettaColors.Parchment), shape = shape),
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(episode.artTop, episode.artBottom))),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = RosettaColors.Parchment,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${episode.storyteller} · ${episode.year}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RosettaColors.Parchment.copy(alpha = 0.8f),
                )
            }
        }
    }
}
