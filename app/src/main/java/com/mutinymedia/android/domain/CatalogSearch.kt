package com.mutinymedia.android.domain

enum class CatalogType(val label: String) {
    ALL("All"), MOVIE("Movie"), TV("TV"), ANIME("Anime");

    fun matches(item: MediaItem): Boolean {
        val anime = item.isAnime || item.type == "anime"
        return when (this) {
            ALL -> true
            MOVIE -> !anime && item.type == "movie"
            TV -> !anime && item.type == "series"
            ANIME -> anime
        }
    }
}

data class CatalogFilters(
    val type: CatalogType = CatalogType.ALL,
    val genre: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
) {
    fun matches(item: MediaItem): Boolean {
        if (!type.matches(item)) return false
        if (genre != null && item.genres.none { it.equals(genre, ignoreCase = true) }) return false
        if (fromYear == null && toYear == null) return true
        val year = item.year ?: return false
        return year >= (fromYear ?: Int.MIN_VALUE) && year <= (toYear ?: Int.MAX_VALUE)
    }
}

data class CatalogSearchResult(
    val query: String,
    val items: List<MediaItem>,
    val aiCandidates: Int? = null,
    val totalCandidates: Int? = null,
)
