package com.mutinymedia.fixture

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.newExtractorLink

private const val TITLE = "Mutiny CloudStream Fixture"
private const val SITE = "https://fixture.example"
private const val EPISODE = "$SITE/extract/episode-one"
private const val SAMPLE = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

class FixturePlugin : Plugin() {
    override fun load(context: Context) {
        checkNotNull(resources).assets.open("mutiny-fixture.txt").bufferedReader().use {
            check(it.readLine() == "fixture resource loaded")
        }
        registerMainAPI(FixtureProvider())
        registerExtractorAPI(FixtureExtractor())
    }
}

class FailingPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FixtureProvider())
        registerExtractorAPI(FixtureExtractor())
        error("Intentional failure after registration")
    }
}

private class FixtureProvider : MainAPI() {
    override var name = "Mutiny Fixture Provider"
    override var mainUrl = SITE
    override val hasMainPage = true
    override val mainPage = mainPageOf("$SITE/catalog" to "Fixture catalog")

    override suspend fun getMainPage(page: Int, request: MainPageRequest) =
        newHomePageResponse(request, listOf(title()), hasNext = false)

    override suspend fun search(query: String): List<SearchResponse> =
        if (TITLE.contains(query, ignoreCase = true)) listOf(title()) else emptyList()

    override suspend fun load(url: String) =
        newTvSeriesLoadResponse(TITLE, url, TvType.TvSeries, listOf(newEpisode(EPISODE) {
            name = "Fixture episode"
            season = 1
            episode = 1
        })) { plot = "Deterministic CloudStream fixture metadata" }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val extractor = synchronized(extractorApis) {
            extractorApis.lastOrNull { it.mainUrl == SITE && it.sourcePlugin == sourcePlugin }
        } ?: return false
        extractor.getUrl(data, SITE, subtitleCallback, callback)
        return true
    }

    private fun title() = newTvSeriesSearchResponse(TITLE, "$SITE/title")
}

private class FixtureExtractor : ExtractorApi() {
    override val name = "Mutiny Fixture Extractor"
    override val mainUrl = SITE
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        check(url == EPISODE && referer == SITE)
        subtitleCallback(SubtitleFile("English", "$SITE/captions.vtt"))
        callback(newExtractorLink(name, "Fixture playback", SAMPLE, ExtractorLinkType.VIDEO) {
            this.referer = SITE
            headers = mapOf("X-Fixture" to "registered")
            quality = 320
        })
    }
}
