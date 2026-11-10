package com.mutinymedia.android.data

import com.mutinymedia.android.domain.MediaItem
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StremioClientTest {
    private val manifest = """{"id":"fixture","name":"Fixture","resources":["catalog",{"name":"stream"},"meta"],"catalogs":[{"type":"series","id":"top","extra":[{"name":"search"}]}]}"""
    @Test fun searchesEncodedQueryAndPreservesMediaType() = runBlocking {
        val urls = mutableListOf<String>()
        val client = StremioClient { url, _ ->
            urls += url
            JSONObject(if (url.endsWith("manifest.json")) manifest else """{"metas":[{"id":"tt123","name":"Title"}]}""")
        }
        val addon = client.inspectAddon("stremio://example.org/manifest.json")
        assertTrue(addon.supportsCatalog && addon.supportsStreams && addon.supportsMeta)
        val results = client.catalog(addon, "A & B")
        assertEquals("series", results.single().type)
        assertTrue(urls.last().endsWith("/catalog/series/top/search=A%20%26%20B.json"))
    }
    @Test fun streamHeadersAndUnsupportedSourcesArePreserved() = runBlocking {
        val client = StremioClient { url, _ ->
            JSONObject(if (url.endsWith("manifest.json")) manifest else """{"streams":[{"url":"https://example.org/video.m3u8","behaviorHints":{"filename":"dub.mkv","proxyHeaders":{"request":{"Referer":"https://example.org/"}}}},{"infoHash":"torrent"},{"externalUrl":"https://example.org/watch"}]}""")
        }
        val streams = client.streams(client.inspectAddon("https://example.org"), "movie", "tt123")
        assertTrue(streams[0].playable)
        assertEquals("https://example.org/", streams[0].headers["Referer"])
        assertEquals("dub.mkv", streams[0].fileName)
        assertFalse(streams[1].playable)
        assertEquals("https://example.org/watch", streams[2].externalUrl)
    }
    @Test fun episodesSortAndUseProviderVideoIds() = runBlocking {
        val client = StremioClient { url, _ ->
            JSONObject(if (url.endsWith("manifest.json")) manifest else """{"meta":{"videos":[{"id":"tt123:2:1","title":"Second season","season":2,"episode":1},{"id":"tt123:1:2","title":"Episode two","season":1,"episode":2},{"title":"No ID"}]}}""")
        }
        val episodes = client.episodes(client.inspectAddon("https://example.org"), MediaItem("tt123", "series", "Show"))
        assertEquals(listOf("tt123:1:2", "tt123:2:1"), episodes.map { it.id })
    }
    @Test fun searchesCatalogBeyondFifthManifestEntry() = runBlocking {
        val catalogs = (1..8).joinToString(",") { i ->
            """{"type":"movie","id":"catalog$i","extra":[{"name":"search"}]}"""
        }
        val client = StremioClient { url, _ ->
            JSONObject(if (url.endsWith("manifest.json"))
                """{"id":"fixture","name":"Fixture","resources":["catalog"],"catalogs":[$catalogs]}"""
            else """{"metas":[{"id":"tt${url.substringAfterLast("/catalog").substringBefore("/")}","name":"Movie"}]}""")
        }
        val addon = client.inspectAddon("https://example.org")
        assertEquals(8, client.catalog(addon, "Movie").size)
    }
    @Test fun enrichesTitleMetadataAndPreservesFallbackFields() = runBlocking {
        val client = StremioClient { url, _ ->
            JSONObject(if (url.endsWith("manifest.json")) manifest else
                """{"meta":{"name":"Verified title","poster":"https://example.org/poster.jpg","description":"Story details","type":"anime"}}""")
        }
        val addon = client.inspectAddon("https://example.org")
        val result = client.metadata(addon, MediaItem("tt123", "series", "tt123"))
        assertEquals("Verified title", result.title)
        assertEquals("https://example.org/poster.jpg", result.poster)
        assertEquals("Story details", result.description)
        assertTrue(result.isAnime)
    }
    @Test fun metadataRetainsExistingTitleWhenProviderOmitsDetails() = runBlocking {
        val client = StremioClient { url, _ ->
            JSONObject(if (url.endsWith("manifest.json")) manifest else """{"meta":{"poster":"javascript:invalid"}}""")
        }
        val addon = client.inspectAddon("https://example.org")
        val initial = MediaItem("tt123", "movie", "Existing", "https://example.org/old.jpg", "Existing description")
        assertEquals(initial, client.metadata(addon, initial))
    }

    @Test fun reportsCatalogEndpointFailureWhenNothingCanBeLoaded() = runBlocking {
        val client = StremioClient { url, _ ->
            if (url.endsWith("manifest.json")) JSONObject(manifest)
            else throw java.net.SocketTimeoutException("simulated timeout")
        }
        val addon = client.inspectAddon("https://example.org")
        val result = runCatching { client.catalog(addon, "test") }
        assertTrue("Catalog failure must be reported", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("catalog endpoints failed"))
    }

    @Test fun catalogDoesNotTruncateAfterFortyTitles() = runBlocking {
        val metas = (1..60).joinToString(",") { i ->
            """{"id":"tt$i","name":"Movie $i","poster":"javascript:bad"}"""
        }
        val client = StremioClient { url, _ ->
            JSONObject(if (url.endsWith("manifest.json"))
                """{"id":"fixture","name":"Fixture","resources":["catalog"],"catalogs":[{"type":"movie","id":"top"}]}"""
            else """{"metas":[$metas]}""")
        }
        val titles = client.catalog(client.inspectAddon("https://example.org"))
        assertEquals(60, titles.size)
        assertTrue(titles.all { it.poster == null })
    }

    @Test fun normalizesCinemetaAndStremioScheme() {
        val client = StremioClient()
        assertEquals("https://v3-cinemeta.strem.io", client.normalizeBase("https://v3-cinemeta.strem.io/manifest.json"))
        assertEquals("https://v3-cinemeta.strem.io/manifest.json", client.manifestUrl("stremio://v3-cinemeta.strem.io/manifest.json"))
        assertEquals("https://v3-cinemeta.strem.io/manifest.json", client.manifestUrl("https://v3-cinemeta.strem.io/"))
    }

    @Test fun retainsConfiguredPathAndEncodedSegments() {
        val client = StremioClient()
        val configured = "https://addons.example.org/%7Bconfig%7D/key%2Fvalue"
        assertEquals(configured, client.normalizeBase("$configured/manifest.json"))
        assertEquals("$configured/manifest.json", client.manifestUrl("$configured/manifest.json"))
        assertEquals("$configured/manifest.json", client.manifestUrl("$configured/"))
    }

    @Test fun rejectsQueryFragmentsCredentialsAndInsecureSchemes() {
        val client = StremioClient()
        val invalid = listOf(
            "http://example.org/manifest.json",
            "https://example.org/manifest.json?token=secret",
            "https://example.org/manifest.json#fragment",
            "https://user:password@example.org/manifest.json",
            "https:///manifest.json"
        )
        invalid.forEach { input ->
            assertTrue("Expected rejection of invalid manifest URL", runCatching { client.normalizeBase(input) }.isFailure)
        }
    }

    @Test fun inspectUsesExactConfiguredManifestEndpoint() = runBlocking {
        val urls = mutableListOf<String>()
        val client = StremioClient { url, _ ->
            urls += url
            JSONObject(manifest)
        }
        client.inspectAddon("https://addons.example.org/config%2Fabc/manifest.json")
        assertEquals(listOf("https://addons.example.org/config%2Fabc/manifest.json"), urls)
    }

    @Test fun rejectsNonManifestJson() = runBlocking {
        val client = StremioClient { _, _ -> JSONObject("{}") }
        assertTrue(runCatching { client.inspectAddon("https://example.org") }.isFailure)
    }
}
