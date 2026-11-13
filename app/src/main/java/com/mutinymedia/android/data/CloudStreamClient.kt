package com.mutinymedia.android.data

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.mutinymedia.android.domain.MediaItem
import com.mutinymedia.android.domain.StreamOption
import com.mutinymedia.android.domain.SubtitleOption
import kotlinx.coroutines.*
import java.util.Collections
import java.net.URLEncoder
import java.net.URLDecoder

/** Typed boundary: provider URLs and episode data are opaque, never Stremio IDs. */
class CloudStreamClient(private val host: CloudStreamHost) {
    data class Details(val item: MediaItem, val episodes: List<com.mutinymedia.android.domain.Episode>, val movieData: String?)
    private val movieData = mutableMapOf<String, String>()
    private fun id(provider: String, kind: String, data: String) = "cloudstream:$provider:$kind:" + URLEncoder.encode(data, "UTF-8")
    private fun route(id: String): Triple<String, String, String> {
        val parts = id.split(':', limit = 4)
        require(parts.size == 4 && parts[0] == "cloudstream") { "Invalid CloudStream title identifier" }
        return Triple(parts[1], parts[2], URLDecoder.decode(parts[3], "UTF-8"))
    }
    private fun item(provider: String, response: SearchResponse) = MediaItem(
        id(provider, "title", response.url), if (response.type in setOf(TvType.Movie, TvType.AnimeMovie)) "movie" else "series",
        response.name, response.posterUrl, isAnime = response.type in setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    )
    var warnings: List<String> = emptyList(); private set
    private suspend fun all(providerId: String? = null, block: suspend (String, MainAPI) -> List<MediaItem>): List<MediaItem> {
        val failures = mutableListOf<String>()
        val result = host.providers().filter { it.enabled && (providerId == null || it.id == providerId) }.flatMap { provider ->
            try { call { block(provider.id, host.api(provider.id)) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failures += "${provider.name}: ${e.javaClass.simpleName}"; emptyList() }
        }
        warnings = failures
        return result.distinctBy { it.id }
    }
    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { withTimeout(45_000) { block() } }
        catch (e: TimeoutCancellationException) { throw IllegalStateException("CloudStream provider timed out", e) }
        catch (e: LinkageError) { throw IllegalStateException("Extension needs a different CloudStream API version", e) }
        catch (e: NotImplementedError) { throw IllegalStateException("Provider does not implement this operation", e) }
        catch (e: Throwable) {
            if (e is CancellationException || e is VirtualMachineError || e is ThreadDeath) throw e
            throw IllegalStateException("CloudStream provider failed (${e.javaClass.simpleName})", e)
        }
    }
    suspend fun search(query: String) = all { provider, api -> api.search(query).orEmpty().take(200).map { item(provider, it) } }
    suspend fun catalogs(providerId: String? = null) = all(providerId) { provider, api ->
        if (!api.hasMainPage) emptyList() else api.mainPage.take(10).flatMap { page ->
            api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages))?.items.orEmpty()
                .flatMap { it.list }.take(200).map { item(provider, it) }
        }
    }
    suspend fun details(media: MediaItem): Details = call {
        val (provider, kind, url) = route(media.id)
        require(kind == "title") { "Expected a CloudStream title" }
        val loaded = host.api(provider).load(url) ?: error("Provider returned no title metadata")
        val enriched = media.copy(title = loaded.name, poster = loaded.posterUrl ?: media.poster,
            description = loaded.plot, genres = loaded.tags.orEmpty(), year = loaded.year,
            cast = loaded.actors.orEmpty().map { it.actor.name })
        fun episodes(values: List<com.lagradost.cloudstream3.Episode>, suffix: String = "") = values.mapIndexed { index, ep ->
            com.mutinymedia.android.domain.Episode(id(provider, "episode", ep.data),
                (ep.name ?: "Episode ${ep.episode ?: index + 1}") + suffix, ep.season ?: 1, ep.episode ?: index + 1)
        }
        val episodes = when (loaded) {
            is TvSeriesLoadResponse -> episodes(loaded.episodes)
            is AnimeLoadResponse -> loaded.episodes.flatMap { (dub, values) -> episodes(values, if (dub == DubStatus.Dubbed) " • English dub" else " • Subbed") }
            else -> emptyList()
        }
        val data = (loaded as? MovieLoadResponse)?.dataUrl
        if (data != null) movieData[media.id] = data
        Details(enriched, episodes, data)
    }
    suspend fun streams(media: MediaItem): List<StreamOption> = call {
        val (provider, kind, value) = route(media.id)
        val api = host.api(provider)
        val data = if (kind == "episode") value else movieData[media.id] ?: details(media).movieData
            ?: error("Select an episode before extracting streams")
        val links = Collections.synchronizedList(mutableListOf<ExtractorLink>())
        val subtitles = Collections.synchronizedList(mutableListOf<SubtitleOption>())
        api.loadLinks(data, false, { sub ->
            if (runCatching { Network.validateHttps(sub.url) }.isSuccess) subtitles += SubtitleOption(sub.lang, sub.url)
        }, { link -> links += link })
        links.toList().filter { it.type in setOf(ExtractorLinkType.VIDEO, ExtractorLinkType.M3U8, ExtractorLinkType.DASH) && it !is DrmExtractorLink }
            .mapNotNull { link ->
                val url = runCatching { Network.validateHttps(link.url) }.getOrNull() ?: return@mapNotNull null
                val headers = link.headers.toMutableMap()
                if (link.referer.isNotBlank() && headers.keys.none { it.equals("referer", true) }) headers["Referer"] = link.referer
                val streamId = id(provider, "stream", url)
                CloudStreamPlayback.register(streamId, api.getVideoInterceptor(link))
                StreamOption(streamId, "${link.name} ${link.quality}p", url, api.name,
                    headers = headers, mimeType = when (link.type) {
                        ExtractorLinkType.M3U8 -> "application/x-mpegURL"
                        ExtractorLinkType.DASH -> "application/dash+xml"
                        else -> null
                    }, subtitles = subtitles.toList())
            }.distinctBy { it.url to it.headers }
    }
}
