package com.mutinymedia.android.data

import java.net.URLEncoder
import java.net.URI
import kotlinx.coroutines.CancellationException
import com.mutinymedia.android.domain.Episode
import com.mutinymedia.android.domain.Addon
import com.mutinymedia.android.domain.MediaItem
import com.mutinymedia.android.domain.StreamOption
import org.json.JSONArray
import org.json.JSONObject

/** Minimal native Stremio addon protocol client: manifests, catalogs/search, streams. */
class StremioClient(
    private val fetchJson: suspend (String, Int) -> JSONObject = { url, limit -> Network.getJson(url, limit) },
) {
    private suspend fun fetch(url: String, limit: Int = 2_000_000) = fetchJson(url, limit)
    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    /** Preserve configured addon paths and reject ambiguous manifest URLs. */
    fun normalizeBase(input: String): String {
        val raw = input.trim().replace(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://")
        require(raw.startsWith("https://", ignoreCase = true)) { "Paste an HTTPS add-on manifest URL" }
        val uri = URI(raw)
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "Invalid add-on URL" }
        require(raw.length < 2000) { "Add-on URL is too long" }
        val path = uri.rawPath.orEmpty().trimEnd('/')
        val basePath = if (path.endsWith("/manifest.json", ignoreCase = true)) path.dropLast("/manifest.json".length) else path
        return (uri.scheme.lowercase() + "://" + uri.rawAuthority + basePath).trimEnd('/')
    }
    fun manifestUrl(input: String): String = normalizeBase(input) + "/manifest.json"
    suspend fun inspectAddon(url: String): Addon {
        val base = normalizeBase(url)
        val json = fetch(manifestUrl(url), 500_000)
        require(json.optString("id").isNotBlank() && json.optString("name").isNotBlank() && json.has("resources")) { "Invalid Stremio manifest" }
        val id = json.optString("id").ifBlank { base }
        val resources = json.optJSONArray("resources") ?: JSONArray()
        fun supports(resource: String): Boolean = (0 until resources.length()).any { index ->
            val item = resources.opt(index)
            (if (item is JSONObject) item.optString("name") else item?.toString()) == resource
        }
        return Addon(id, json.optString("name", "Unnamed addon"), base, supports("catalog"), supports("stream"), supports("meta"))
    }
    suspend fun catalog(addon: Addon, query: String? = null): List<MediaItem> {
        if (!addon.supportsCatalog) return emptyList()
        val manifest = fetch("${addon.baseUrl}/manifest.json", 500_000)
        val specs = manifest.optJSONArray("catalogs") ?: return emptyList()
        val all = mutableListOf<MediaItem>()
        for (index in 0 until minOf(specs.length(), 30)) {
            val spec = specs.optJSONObject(index) ?: continue
            val type = spec.optString("type")
            val id = spec.optString("id")
            if (type !in listOf("movie", "series", "anime") || id.isBlank()) continue
            val extra = spec.optJSONArray("extra") ?: JSONArray()
            val canSearch = (0 until extra.length()).any { extra.optJSONObject(it)?.optString("name") == "search" }
            if (!query.isNullOrBlank() && !canSearch) continue
            val required = (0 until extra.length()).mapNotNull { extra.optJSONObject(it) }.filter { it.optBoolean("isRequired") }.map { it.optString("name") }
            if (required.any { it != "search" || query.isNullOrBlank() }) continue
            val slug = if (query.isNullOrBlank()) "" else "/search=${encode(query.trim())}"
            val data = try { fetch("${addon.baseUrl}/catalog/${encode(type)}/${encode(id)}$slug.json") } catch (e: CancellationException) { throw e } catch (_: Exception) { continue }
            val metas = data.optJSONArray("metas") ?: continue
            for (j in 0 until minOf(metas.length(), 40)) {
                val m = metas.optJSONObject(j) ?: continue
                val mediaId = m.optString("id")
                val name = m.optString("name")
                if (mediaId.isBlank() || name.isBlank()) continue
                all += MediaItem(mediaId, type, name,
                    m.optString("poster").takeIf(String::isNotBlank),
                    m.optString("description").takeIf(String::isNotBlank),
                    type == "anime" || mediaId.startsWith("kitsu:") || mediaId.startsWith("mal:"),
                    genres = m.catalogGenres(),
                    year = m.catalogYear(),
                    cast = m.catalogNames("cast"),
                )
            }
        }
        return all.distinctBy { it.type to it.id }.take(300)
    }
    /** Fetches full title metadata from an addon without discarding existing poster/description. */
    suspend fun metadata(addon: Addon, media: MediaItem): MediaItem {
        if (!addon.supportsMeta) return media
        val result = fetch("${addon.baseUrl}/meta/${encode(media.type)}/${encode(media.id)}.json")
        val meta = result.optJSONObject("meta") ?: return media
        val name = meta.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: media.title
        val poster = meta.optString("poster").takeIf { it.startsWith("https://", true) } ?: media.poster
        val description = meta.optString("description").takeIf { it.isNotBlank() && it != "null" } ?: media.description
        return media.copy(title = name, poster = poster, description = description,
            isAnime = media.isAnime || meta.optString("type") == "anime",
            genres = meta.catalogGenres().ifEmpty { media.genres },
            year = meta.catalogYear() ?: media.year,
            cast = meta.catalogNames("cast").ifEmpty { media.cast },
        )
    }

    suspend fun episodes(addon: Addon, media: MediaItem): List<Episode> {
        if (!addon.supportsMeta) return emptyList()
        val data = fetch("${addon.baseUrl}/meta/${encode(media.type)}/${encode(media.id)}.json")
        val videos = data.optJSONObject("meta")?.optJSONArray("videos") ?: return emptyList()
        return (0 until minOf(videos.length(), 2000)).mapNotNull { index ->
            val video = videos.optJSONObject(index) ?: return@mapNotNull null
            val id = video.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            Episode(id, video.optString("title").ifBlank { video.optString("name", id) },
                video.optInt("season", 1), video.optInt("episode", index + 1))
        }.sortedWith(compareBy({ it.season }, { it.number }))
    }

    suspend fun streams(addon: Addon, type: String, id: String): List<StreamOption> {
        if (!addon.supportsStreams) return emptyList()
        require(type in listOf("movie", "series", "anime")) { "Unsupported media type" }
        val data = fetch("${addon.baseUrl}/stream/${encode(type)}/${encode(id)}.json", 3_000_000)
        val entries = data.optJSONArray("streams") ?: return emptyList()
        return (0 until minOf(entries.length(), 120)).mapNotNull { i ->
            val stream = entries.optJSONObject(i) ?: return@mapNotNull null
            val title = listOf(stream.optString("name"), stream.optString("title")).filter(String::isNotBlank).joinToString(" · ")
            val fileName = stream.optJSONObject("behaviorHints")?.optString("filename")
            val hints = stream.optJSONObject("behaviorHints")
            val requestHeaders = hints?.optJSONObject("proxyHeaders")?.optJSONObject("request")
            val headers = requestHeaders?.keys()?.asSequence()?.mapNotNull { key ->
                val value = requestHeaders.optString(key)
                if (key.contains('\r') || key.contains('\n') || value.contains('\r') || value.contains('\n')) null else key to value
            }?.toMap().orEmpty()
            StreamOption("${addon.id}#$i", title.ifBlank { "Stream ${i+1}" },
                stream.optString("url").takeIf { it.startsWith("https://", true) }, addon.title, fileName, headers, stream.optString("externalUrl").takeIf { it.startsWith("https://", true) })
        }
    }
}

private fun JSONObject.catalogGenres(): List<String> = catalogNames("genres").ifEmpty { catalogNames("genre") }

private fun JSONObject.catalogNames(key: String): List<String> {
    val values = optJSONArray(key) ?: return emptyList()
    return (0 until minOf(values.length(), 32)).mapNotNull { i ->
        val name = when (val value = values.opt(i)) {
            is String -> value
            is JSONObject -> value.optString("name")
            else -> ""
        }.trim().take(100)
        name.takeIf { it.isNotEmpty() }
    }.distinct()
}

private val releaseYearPattern = Regex("""\b(18|19|20)\d{2}\b""")

private fun JSONObject.catalogYear(): Int? =
    sequenceOf(optString("year"), optString("releaseInfo"), optString("released"))
        .mapNotNull { releaseYearPattern.find(it)?.value?.toIntOrNull() }
        .firstOrNull()
