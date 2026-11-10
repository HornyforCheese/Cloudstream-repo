package com.mutinymedia.android.data

import com.mutinymedia.android.domain.CatalogFilters
import com.mutinymedia.android.domain.CatalogType
import com.mutinymedia.android.domain.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSearchTest {
    private val movie = MediaItem("movie", "movie", "Night Camera", genres = listOf("Horror"), year = 2012)
    private val anime = MediaItem("anime", "series", "Blue Sky", isAnime = true, genres = listOf("Drama"), year = 2020)
    private val unknown = MediaItem("unknown", "series", "Mystery")

    @Test
    fun combinesTypeGenreAndYearWithoutMatchingUnknownMetadata() {
        assertTrue(CatalogFilters(CatalogType.MOVIE, "horror", 2010, 2012).matches(movie))
        assertFalse(CatalogFilters(CatalogType.MOVIE, "horror", 2013).matches(movie))
        assertFalse(CatalogFilters(CatalogType.TV).matches(anime))
        assertTrue(CatalogFilters(CatalogType.ANIME).matches(anime))
        assertFalse(CatalogFilters(fromYear = 2000).matches(unknown))
        assertFalse(CatalogFilters(genre = "Drama").matches(unknown))
    }

    @Test
    fun onlyReturnsExistingCatalogEntriesInModelOrder() {
        val response = "[1, 91, -1, 1, \"0\", 0]"
        assertEquals(listOf(anime, movie), parseCatalogMatches(response, listOf(movie, anime)))
    }
}
