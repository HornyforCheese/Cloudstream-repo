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
