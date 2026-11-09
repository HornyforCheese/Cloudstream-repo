package com.mutinymedia.android.data

import com.mutinymedia.android.domain.MediaItem

/**
 * Conversion boundary between CloudStream MainAPI search responses and Mutiny Media.
 * The actual MainAPI invocation must be supplied by a compatible CloudStream host.
 */
object CloudStreamSearchAdapter {
    data class SearchResult(
        val provider: String,
        val title: String,
        val url: String,
        val type: String,
        val posterUrl: String? = null,
        val description: String? = null,
    )

    /** Maps real CloudStream SearchResponse objects without depending on their binary ABI. */
    fun fromHostResponses(provider: String, responses: List<Any>): List<MediaItem> {
        fun property(instance: Any, name: String): String? = runCatching {
            val getter = "get" + name.replaceFirstChar { it.uppercase() }
            instance.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
                ?.invoke(instance)?.toString()
        }.getOrNull()
        return convert(responses.mapNotNull { item ->
            val name = property(item, "name") ?: return@mapNotNull null
            val url = property(item, "url") ?: return@mapNotNull null
            val type = property(item, "type").orEmpty()
            SearchResult(provider, name, url, when {
                type.contains("anime", true) || item.javaClass.simpleName.contains("Anime") -> "anime"
                type.contains("movie", true) -> "movie"
                else -> "series"
            }, property(item, "posterUrl"))
        })
    }

    fun convert(results: List<SearchResult>): List<MediaItem> =
        results.asSequence().filter { it.provider.isNotBlank() && it.title.isNotBlank() }
            .mapNotNull { item ->
                val type = when (item.type.lowercase()) {
                    "movie", "film" -> "movie"
                    "series", "tvseries", "tv", "anime" -> "series"
                    else -> return@mapNotNull null
                }
                val url = runCatching { Network.validateHttps(item.url) }.getOrNull()
                    ?: return@mapNotNull null
                val poster = item.posterUrl?.let { runCatching { Network.validateHttps(it) }.getOrNull() }
                MediaItem(
                    id = "cloudstream:${item.provider}:${url}",
                    type = type,
                    title = item.title.trim(),
                    poster = poster,
                    description = item.description?.takeIf { it.isNotBlank() },
                    isAnime = item.type.equals("anime", true),
                )
            }.distinctBy { it.id }.take(200).toList()
}
