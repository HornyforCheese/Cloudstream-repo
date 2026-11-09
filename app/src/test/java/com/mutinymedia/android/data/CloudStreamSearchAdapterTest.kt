package com.mutinymedia.android.data

import org.junit.Assert.*
import org.junit.Test

class CloudStreamSearchAdapterTest {
    @Test fun mapsMoviesSeriesAndAnimeWithoutInventingPlayableStreams() {
        val results = CloudStreamSearchAdapter.convert(listOf(
            CloudStreamSearchAdapter.SearchResult("ProviderA", "Film", "https://example.org/film", "movie",
                "https://example.org/poster.jpg", "Synopsis"),
            CloudStreamSearchAdapter.SearchResult("ProviderA", "Anime", "https://example.org/anime", "anime"),
            CloudStreamSearchAdapter.SearchResult("ProviderB", "TV", "https://example.org/tv", "tvseries"),
        ))
        assertEquals(3, results.size)
        assertEquals("movie", results[0].type)
        assertEquals("series", results[1].type)
        assertTrue(results[1].isAnime)
        assertEquals("Synopsis", results[0].description)
    }

    @Test fun ignoresUnsafeUrlsUnknownTypesAndDuplicateResults() {
        val valid = CloudStreamSearchAdapter.SearchResult("P", "Film", "https://example.org/film", "movie")
        val results = CloudStreamSearchAdapter.convert(listOf(
            valid, valid,
            CloudStreamSearchAdapter.SearchResult("P", "Bad", "javascript:alert(1)", "movie"),
            CloudStreamSearchAdapter.SearchResult("P", "Other", "https://example.org/other", "unknown"),
        ))
        assertEquals(1, results.size)
    }
}
