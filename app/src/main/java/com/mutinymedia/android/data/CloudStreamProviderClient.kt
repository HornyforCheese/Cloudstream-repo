package com.mutinymedia.android.data

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.mutinymedia.android.domain.Episode
import com.mutinymedia.android.domain.MediaItem
import com.mutinymedia.android.domain.StreamOption
import com.mutinymedia.android.domain.StreamSubtitle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.util.Collections

internal class CloudStreamProviderClient(private val host: CloudStreamHost) {
    data class Titles(val items: List<MediaItem>, val unavailableProviders: Int)
    data class Details(
        val item: MediaItem,
        val episodes: List<Episode>,
        val movieData: String?,
        val episodeData: Map<String, Pair<String, DubStatus>>,
    )

    suspend fun search(query: String): Titles = query { it.search(query).orEmpty() }

    suspend fun browse(): Titles = query { provider ->
        if (!provider.hasMainPage) emptyList()
        else provider.mainPage.take(8).flatMap { page ->
            val request = MainPageRequest(page.name, page.data, page.horizontalImages)
            provider.getMainPage(1, request)?.items.orEmpty().flatMap { it.list }
        }
    }

    private suspend fun query(block: suspend (MainAPI) -> List<SearchResponse>): Titles = host.withProviders { providers ->
        val slots = Semaphore(4)
        val responses = coroutineScope {
            providers.map { provider ->
                async {
                    slots.withPermit {
                        withTimeoutOrNull(35_000) {
                            pluginCall { block(provider.api).take(200).mapNotNull { it.toMediaItem(provider.key) } }
                        }
                    }
                }
            }.awaitAll()
        }
        Titles(responses.filterNotNull().flatten().distinctBy { it.id }, responses.count { it == null })
    }

    suspend fun details(item: MediaItem): Details = host.withProviders { providers ->
        val (api, url) = providerFor(item, providers)
        val load = withTimeoutOrNull(35_000) { pluginCall { api.load(url) } }
            ?: error("CloudStream provider could not load title details")
        val updated = item.copy(
            title = load.name,
            poster = load.posterUrl?.let(::https) ?: item.poster,
            description = load.plot,
            isAnime = load.type.isAnime(),
            genres = load.tags.orEmpty(),
            year = load.year,
            cast = load.actors.orEmpty().map { it.actor.name },
        )
        val parts = when (load) {
            is AnimeLoadResponse -> load.episodes.flatMap { (dub, episodes) -> episodes.map { it to dub } }
            is TvSeriesLoadResponse -> load.episodes.map { it to DubStatus.None }
            else -> emptyList()
        }
        val episodeData = mutableMapOf<String, Pair<String, DubStatus>>()
        val episodes = parts.mapIndexed { index, (episode, dub) ->
            val id = "${item.id}#episode-${index + 1}"
            episodeData[id] = episode.data to dub
            val number = episode.episode ?: index + 1
            val label = when (dub) {
                DubStatus.Dubbed -> " · Dub"
                DubStatus.Subbed -> " · Sub"
                DubStatus.None -> ""
            }
            Episode(id, (episode.name ?: "Episode $number") + label, episode.season ?: 1, number)
        }.sortedWith(compareBy({ it.season }, { it.number }))
        val movieData = when (load) {
            is MovieLoadResponse -> load.dataUrl
            is LiveStreamLoadResponse -> load.dataUrl
            else -> null
        }
        Details(updated, episodes, movieData, episodeData)
    }

    suspend fun streams(item: MediaItem, data: String, dub: DubStatus = DubStatus.None): List<StreamOption> =
        host.withProviders { providers ->
            val (api, _) = providerFor(item, providers)
            val links = Collections.synchronizedList(mutableListOf<ExtractorLink>())
            val subtitles = Collections.synchronizedList(mutableListOf<SubtitleFile>())
            withTimeoutOrNull(45_000) {
                pluginCall { api.loadLinks(data, false, { subtitles.add(it) }, { links.add(it) }) }
            } ?: error("CloudStream provider could not extract streams")
            val subtitleOptions = synchronized(subtitles) { subtitles.toList() }
                .mapNotNull { sub ->
                    val url = https(sub.url) ?: return@mapNotNull null
                    val mimeType = when (URI(url).path.substringAfterLast('.').lowercase()) {
                        "srt" -> "application/x-subrip"
                        "ttml", "xml" -> "application/ttml+xml"
                        "ssa", "ass" -> "text/x-ssa"
                        else -> "text/vtt"
                    }
                    StreamSubtitle(url, sub.lang, mimeType)
                }.distinctBy { it.url to it.language }
            synchronized(links) { links.toList() }.mapNotNull { link ->
                if (link is DrmExtractorLink || link is ExtractorLinkPlayList) return@mapNotNull null
                val url = https(link.url) ?: return@mapNotNull null
                val mime = when (link.type) {
                    ExtractorLinkType.M3U8, ExtractorLinkType.DASH -> link.type.getMimeType()
                    ExtractorLinkType.VIDEO -> null
                    else -> return@mapNotNull null
                }
                val name = buildList {
                    add(link.name)
                    if (link.quality in 144..4320 && !link.name.contains("${link.quality}p", true)) add("${link.quality}p")
                    if (dub == DubStatus.Dubbed) add("Dubbed")
                    if (dub == DubStatus.Subbed) add("Subbed")
                }.joinToString(" · ")
                val headers = link.getAllHeaders()
                StreamOption(
                    "cloudstream:" + CloudStreamInstaller.digest("$url:$mime:${headers.toSortedMap()}"),
                    name, url, api.name, headers = headers, mimeType = mime,
                    subtitles = subtitleOptions, cloudStreamSession = true,
                    declaredResolution = link.quality.takeIf { it in com.mutinymedia.android.domain.StreamFilters.resolutions },
                )
            }.distinctBy { it.id }
        }

    private fun providerFor(item: MediaItem, providers: List<CloudStreamHost.Provider>): Pair<MainAPI, String> {
        val path = item.id.removePrefix("cloudstream:")
        val key = path.substringBefore(':')
        val url = https(path.substringAfter(':', "")) ?: error("Invalid CloudStream title URL")
        val api = providers.firstOrNull { it.key == key }?.api
            ?: error("This title's CloudStream provider is disabled or removed")
        return api to url
    }

    private fun SearchResponse.toMediaItem(key: String): MediaItem? {
        val tvType = type ?: return null
        if (tvType == TvType.CustomMedia) return null
        val result = CloudStreamSearchAdapter.SearchResult(
            key, name, https(url) ?: return null,
            if (tvType.isMovieType()) "movie" else "series",
            posterUrl?.let(::https),
        )
        return CloudStreamSearchAdapter.convert(listOf(result)).firstOrNull()?.copy(isAnime = tvType.isAnime())
    }

    private fun TvType.isAnime() = this in setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private fun https(url: String): String? = runCatching {
        Network.validateHttps(if (url.startsWith("//")) "https:$url" else url)
    }.getOrNull()

    companion object {
        fun owns(item: MediaItem): Boolean = item.id.startsWith("cloudstream:")
    }
}
