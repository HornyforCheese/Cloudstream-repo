package com.mutinymedia.android.domain

import java.time.LocalDate
import java.util.Locale

data class HomeShelf(val title: String, val items: List<MediaItem>)

internal fun mergeCatalogTitles(catalog: List<MediaItem>): List<MediaItem> {
    val titles = LinkedHashMap<Pair<String, String>, MediaItem>()
    for (item in catalog) {
        val key = item.type to item.id
        val existing = titles[key]
        titles[key] = existing?.copy(
            poster = existing.poster?.takeIf(String::isNotBlank) ?: item.poster,
            description = existing.description?.takeIf(String::isNotBlank) ?: item.description,
            isAnime = existing.isAnime || item.isAnime,
            genres = (existing.genres + item.genres).distinct(),
            year = existing.year ?: item.year,
            cast = existing.cast.ifEmpty { item.cast },
            rating = existing.rating ?: item.rating,
            releaseDate = existing.releaseDate ?: item.releaseDate,
            catalogFormat = existing.catalogFormat ?: item.catalogFormat,
        ) ?: item
    }
    return titles.values.toList()
}

fun buildHomeShelves(catalog: List<MediaItem>, today: LocalDate = LocalDate.now()): List<HomeShelf> {
    fun MediaItem.releaseYear() = year ?: releaseDate?.year
    fun MediaItem.format() = catalogFormat ?: type
    fun MediaItem.anime() = isAnime || type == "anime"
    val releases = mergeCatalogTitles(catalog).filter { item ->
        item.releaseDate?.let { !it.isAfter(today) } ?: ((item.releaseYear() ?: today.year) <= today.year)
    }
    val best = compareByDescending<MediaItem> { it.rating?.takeIf { rating -> rating in 0.01..10.0 } ?: -1.0 }
        .thenByDescending { !it.poster.isNullOrBlank() }
    val newest = compareByDescending<MediaItem> { it.releaseYear() ?: 0 }
        .thenByDescending { it.releaseDate }
        .then(best)
    val nowYear = today.year
    val spotlightYear = 2026
    val recent = today.minusMonths(12)
    fun MediaItem.recentlyReleased() = releaseDate?.let { !it.isBefore(recent) } ?: (releaseYear() == nowYear)
    fun MediaItem.hasGenre(vararg names: String) =
        genres.any { tag -> names.any { name -> tag.trim().equals(name, ignoreCase = true) } }

    val ranked = releases.sortedWith(best)
    val recentFirst = releases.sortedWith(newest)
    val whitespace = Regex("\\s+")
    val editionKeys = releases.associate { item ->
        val year = item.releaseYear()
        val edition = if (year == null) item.id
            else "${item.title.trim().lowercase(Locale.ROOT).replace(whitespace, " ")}@$year"
        (item.type to item.id) to (item.format() to edition)
    }
    fun shelf(title: String, candidates: List<MediaItem> = ranked, matches: (MediaItem) -> Boolean): HomeShelf {
        val items = candidates.filter(matches).groupBy { editionKeys.getValue(it.type to it.id) }.values.map { editions ->
            val primary = editions.first()
            primary.takeUnless { it.poster.isNullOrBlank() }
                ?: primary.copy(poster = editions.firstNotNullOfOrNull { it.poster?.takeIf(String::isNotBlank) })
        }
        return HomeShelf(title, items)
    }

    return listOf(
        shelf("Best movies of $spotlightYear") { it.format() == "movie" && !it.anime() && it.releaseYear() == spotlightYear },
        shelf("Best series of $spotlightYear") { it.format() == "series" && !it.anime() && it.releaseYear() == spotlightYear },
        shelf("New Movies", recentFirst) { it.format() == "movie" && !it.anime() && it.recentlyReleased() },
        shelf("New Series", recentFirst) { it.format() == "series" && !it.anime() && it.recentlyReleased() },
        shelf("Best anime series of the 2020s") { it.anime() && it.format() == "series" && it.releaseYear()?.let { year -> year in 2020..2029 } == true },
        shelf("Best anime movies of the 2020s") { it.anime() && it.format() == "movie" && it.releaseYear()?.let { year -> year in 2020..2029 } == true },
        shelf("Best anime movies of all time") { it.anime() && it.format() == "movie" },
        shelf("Best anime series of all time") { it.anime() && it.format() == "series" },
        shelf("Science Fiction & Fantasy") { it.hasGenre("Science Fiction", "Sci-Fi", "Sci-Fi & Fantasy", "Fantasy") },
        shelf("Horror & Thrillers") { it.hasGenre("Horror", "Thriller") },
        shelf("Action & Adventure") { it.hasGenre("Action", "Adventure", "Action & Adventure") },
        shelf("Comedy") { it.hasGenre("Comedy") },
        shelf("More Anime") { it.format() == "anime" },
        shelf("More from your catalogs") { true },
    )
}
