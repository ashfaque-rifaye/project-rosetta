package app.rosetta.tv.data

import androidx.compose.ui.graphics.Color

/** One processed family recording, presented as an episode of the family channel. */
data class Episode(
    val id: String,
    val title: String,
    val storyteller: String,
    val year: String,
    val synopsis: String,
    /** Spoken language of the original recording. */
    val originalLanguage: String,
    /** Languages available as subtitles and/or dubbed audio after the pipeline runs. */
    val languages: List<String>,
    val minutes: Int,
    val artTop: Color,
    val artBottom: Color,
)

data class Shelf(val title: String, val episodes: List<Episode>)

/** Placeholder catalog until the AWS pipeline feeds real episodes. */
object SampleCatalog {
    private val dusk = Color(0xFFE07A3F) to Color(0xFF3A1C4A)
    private val river = Color(0xFF3F8FE0) to Color(0xFF14233F)
    private val leaf = Color(0xFF5FB36B) to Color(0xFF17301C)

    val shelves = listOf(
        Shelf(
            "Paati's Stories",
            listOf(
                Episode("p1", "The Monsoon of 1971", "Paati", "1971",
                    "The year the river reached the temple steps, and how the whole street cooked together for nine days.",
                    "Tamil", listOf("Tamil", "English", "Hindi"), 6, dusk.first, dusk.second),
                Episode("p2", "Kolam at Dawn", "Paati", "1958",
                    "Why every morning began with rice-flour patterns at the doorstep, and what the ants were for.",
                    "Tamil", listOf("Tamil", "English"), 4, dusk.first, dusk.second),
                Episode("p3", "The Wedding Train", "Paati", "1963",
                    "Forty relatives, one third-class carriage, and a missing bridegroom.",
                    "Tamil", listOf("Tamil", "English", "Hindi"), 8, dusk.first, dusk.second),
            ),
        ),
        Shelf(
            "Thatha's Journeys",
            listOf(
                Episode("t1", "First Job in Bombay", "Thatha", "1974",
                    "A ticket clerk's first week in a city that never stopped talking.",
                    "Tamil", listOf("Tamil", "English"), 7, river.first, river.second),
                Episode("t2", "Letters Home", "Thatha", "1976",
                    "Reading the letters he wrote to his mother, fifty years later.",
                    "Tamil", listOf("Tamil", "English", "Hindi"), 5, river.first, river.second),
            ),
        ),
        Shelf(
            "Replies from the grandkids",
            listOf(
                Episode("r1", "Arjun's Science Fair", "Arjun", "2026",
                    "A reply recorded in Seattle, translated for Paati.",
                    "English", listOf("English", "Tamil"), 2, leaf.first, leaf.second),
                Episode("r2", "Meera Learns Kolam", "Meera", "2026",
                    "Her first attempt, narrated in English, subtitled in Tamil.",
                    "English", listOf("English", "Tamil"), 3, leaf.first, leaf.second),
            ),
        ),
    )
}
