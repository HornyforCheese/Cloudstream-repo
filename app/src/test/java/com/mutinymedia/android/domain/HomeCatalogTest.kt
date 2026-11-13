package com.mutinymedia.android.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCatalogTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test fun requestedShelvesSeparateAnimeFormatsAndUseActualReleaseYears() {
        val titles = listOf(
            MediaItem("current-film", "movie", "Current film", year = 2026, rating = 8.0),
            MediaItem("current-show", "series", "Current show", year = 2026),
            MediaItem("modern-anime", "series", "Modern anime", isAnime = true, year = 2022),
            MediaItem("modern-film", "anime", "Modern anime film", isAnime = true, year = 2026, catalogFormat = "movie"),
            MediaItem("old-anime", "series", "Old anime", isAnime = true, year = 1998),
            MediaItem("old-film", "movie", "Old anime film", isAnime = true, year = 1988),
            MediaItem("untyped", "anime", "Anime with unknown format", year = 2026),
            MediaItem("future", "movie", "Future film", year = 2026, releaseDate = today.plusDays(1)),
        )
        val shelves = buildHomeShelves(titles, today)
        assertEquals(listOf(
            "Best movies of 2026", "Best series of 2026", "New Movies", "New Series",
            "Best anime series of the 2020s", "Best anime movies of the 2020s",
            "Best anime movies of all time", "Best anime series of all time",
        ), shelves.take(8).map { it.title })
        assertEquals(listOf("current-film"), shelves[0].items.map { it.id })
        assertEquals(listOf("current-show"), shelves[1].items.map { it.id })
        assertEquals(listOf("modern-anime"), shelves[4].items.map { it.id })
        assertEquals(listOf("modern-film"), shelves[5].items.map { it.id })
        assertEquals(setOf("modern-film", "old-film"), shelves[6].items.map { it.id }.toSet())
        assertEquals(setOf("modern-anime", "old-anime"), shelves[7].items.map { it.id }.toSet())
        assertTrue(shelves.none { it.items.any { movie -> movie.id == "future" } })
    }

    @Test fun ranksByScoreOrReleaseDateAndMergesCatalogMetadata() {
        val titles = listOf(
            MediaItem("same", "movie", "Twin", year = 2026),
            MediaItem("same", "movie", "Twin", year = 2026, poster = "https://images.example/twin.jpg", rating = 9.1),
            MediaItem("duplicate", "movie", "Twin", year = 2026, rating = 7.0),
            MediaItem("newer", "movie", "Newest", year = 2026, releaseDate = today.minusDays(1), rating = 7.2),
            MediaItem("earlier", "movie", "Earlier", year = 2026, releaseDate = today.minusDays(30), rating = 8.0),
            MediaItem("remake", "movie", "Twin", year = 1990),
        )
        val shelves = buildHomeShelves(titles, today)
        assertEquals(listOf("same", "earlier", "newer"), shelves[0].items.map { it.id })
        assertEquals("https://images.example/twin.jpg", shelves[0].items.first().poster)
        assertEquals("newer", shelves[2].items.first().id)
        assertTrue(shelves.last().items.any { it.id == "remake" })
    }
}
