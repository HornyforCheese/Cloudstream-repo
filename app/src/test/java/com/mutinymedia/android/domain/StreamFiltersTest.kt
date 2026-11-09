package com.mutinymedia.android.domain

import org.junit.Assert.*
import org.junit.Test

class StreamFiltersTest {
    private val sample = listOf(
        StreamOption("1", "1080p English Dub", "https://example.org/1.m3u8", "test"),
        StreamOption("2", "480p Japanese Audio English Sub", "https://example.org/2.mp4", "test"),
        StreamOption("3", "480p Dual Audio", "https://example.org/3.mp4", "test"),
        StreamOption("4", "Unlabelled release", "https://example.org/4.mp4", "test"),
    )
    @Test fun dubOnlyOnAnime() {
        assertEquals(listOf("1", "3"), StreamFilters.filterAnime(sample, true, AnimeStreamMode.DUB).map { it.id })
    }
    @Test fun subOnlyOnAnime() {
        assertEquals(listOf("2", "3"), StreamFilters.filterAnime(sample, true, AnimeStreamMode.SUB).map { it.id })
    }
    @Test fun nonAnimeUnfiltered() {
        assertEquals(4, StreamFilters.filterAnime(sample, false, AnimeStreamMode.DUB).size)
    }
    @Test fun bandwidthPrioritisesLowResolution() {
        val ordered = StreamFilters.rankForBandwidth(sample, UserSettings(lowBandwidth = true))
        assertTrue(ordered.first().title.contains("480p"))
    }
    @Test fun bitratesAddCorrectly() {
        assertEquals(764, UserSettings(videoKbps = 700, audioKbps = 64).targetKbps)
    }
    @Test fun playbackRejectsMalformedAndInsecureUrls() {
        listOf(null, "http://example.org/video", "https:///video", "https://user:pass@example.org/video", "magnet:?xt=123").forEach {
            assertFalse(StreamOption("test", "test", it, "test").playable)
        }
        assertTrue(StreamOption("test", "test", "https://example.org/video.m3u8?token=abc", "test").playable)
    }
    @Test fun strictModesExcludeUnknownSources() {
        val unknown = listOf(StreamOption("unknown", "480p unknown language", null, "test"))
        assertTrue(StreamFilters.filterAnime(unknown, true, AnimeStreamMode.DUB).isEmpty())
        assertTrue(StreamFilters.filterAnime(unknown, true, AnimeStreamMode.SUB).isEmpty())
        assertEquals(unknown, StreamFilters.filterAnime(unknown, true, AnimeStreamMode.ALL))
    }
    @Test fun bandwidthDoesNotReorderWhenDisabled() {
        assertEquals(sample, StreamFilters.rankForBandwidth(sample, UserSettings()))
    }
}
