package org.mutinymedia.fixture

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*

/** Executable test extension. Big Buck Bunny is Blender Foundation CC BY 3.0. */
class FixturePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FixtureProvider())
        registerExtractorAPI(FixtureExtractor())
    }
}
class FixtureProvider : MainAPI() {
    override var name = "Mutiny CC fixture"
    override var mainUrl = "https://peach.blender.org"
    override val hasMainPage = true
    override val mainPage = listOf(MainPageData("Creative Commons", mainUrl))
    override suspend fun search(query: String): List<SearchResponse> =
        when {
            query.contains("series", true) -> listOf(newTvSeriesSearchResponse("CC Episodes", "$mainUrl/series", TvType.TvSeries))
            query.contains("bunny", true) -> listOf(newMovieSearchResponse("Big Buck Bunny", "$mainUrl/bunny", TvType.Movie))
            else -> emptyList()
        }
    override suspend fun getMainPage(page: Int, request: MainPageRequest) =
        newHomePageResponse(HomePageList("Creative Commons", search("bunny")), false)
    override suspend fun load(url: String): LoadResponse =
        if (url.endsWith("/series")) newTvSeriesLoadResponse("CC Episodes", url, TvType.TvSeries,
            listOf(newEpisode("fixture-video") { season = 2; episode = 3; name = "Bunny episode" }))
        else newMovieLoadResponse("Big Buck Bunny", url, TvType.Movie, "fixture-video") {
            plot = "Blender Foundation Creative Commons short film"
            year = 2008
        }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        check(data == "fixture-video")
        return loadExtractor("https://fixture.mutinymedia.org/video", subtitleCallback, callback)
    }
}
class FixtureExtractor : ExtractorApi() {
    override val name = "Mutiny fixture extractor"
    override val mainUrl = "https://fixture.mutinymedia.org"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        callback(newExtractorLink(name, "Big Buck Bunny", "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4", ExtractorLinkType.VIDEO) {
            quality = 180
            this.referer = "https://peach.blender.org/"
            headers = mapOf("X-Mutiny-Fixture" to "verified")
        })
    }
}

/** Deliberate partial registration, used to verify rollback and isolation. */
class FailingPlugin : com.lagradost.cloudstream3.plugins.BasePlugin() {
    override fun load() {
        registerMainAPI(FixtureProvider())
        registerExtractorAPI(FixtureExtractor())
        throw IllegalStateException("Fixture failure after registration")
    }
}
class PortablePlugin : com.lagradost.cloudstream3.plugins.BasePlugin() {
    override fun load() { registerMainAPI(FixtureProvider()) }
}
