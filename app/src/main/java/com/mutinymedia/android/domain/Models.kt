package com.mutinymedia.android.domain

const val DEFAULT_OPENROUTER_MODEL = "nvidia/nemotron-3-ultra-550b-a55b:free"

data class OpenRouterModel(val id: String, val name: String)

enum class AnimeStreamMode { ALL, DUB, SUB }
enum class CodecPreference { EFFICIENT, COMPATIBLE }
enum class ThemePreset(val label: String) {
    MUTINY("Mutiny"), OCEAN("Deep Ocean"), FOREST("Emerald Cove"), EMBER("Ember")
}
enum class ThemeFont(val label: String) {
    DEFAULT("System"), SERIF("Serif"), MONOSPACE("Monospace")
}

data class UserSettings(
    val lowBandwidth: Boolean = false,
    val videoKbps: Int = 700,
    val audioKbps: Int = 64,
    val resolution: Int = 480,
    val adaptive: Boolean = true,
    val codec: CodecPreference = CodecPreference.EFFICIENT,
    val animeMode: AnimeStreamMode = AnimeStreamMode.ALL,
    val addonUrls: List<String> = emptyList(),
    val extensionRepositories: List<String> = emptyList(),
    val openRouterModel: String = DEFAULT_OPENROUTER_MODEL,
    val themePreset: ThemePreset = ThemePreset.MUTINY,
    val themeFont: ThemeFont = ThemeFont.DEFAULT,
) {
    val targetKbps: Int get() = videoKbps + audioKbps
}

data class MediaItem(
    val id: String,
    val type: String,
    val title: String,
    val poster: String? = null,
    val description: String? = null,
    val isAnime: Boolean = false,
)

data class Addon(
    val id: String,
    val title: String,
    val baseUrl: String,
    val supportsCatalog: Boolean,
    val supportsStreams: Boolean,
    val supportsMeta: Boolean = false,
)

data class StreamOption(
    val id: String,
    val title: String,
    val url: String?,
    val provider: String,
    val fileName: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val externalUrl: String? = null,
) {
    val playable: Boolean get() = runCatching {
        val uri = java.net.URI(url ?: return false)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
    val metadata: String get() = listOfNotNull(title, fileName).joinToString(" ")
}

data class Episode(val id: String, val title: String, val season: Int, val number: Int)

data class InstalledGame(val name: String, val packageName: String)
data class ChatLine(val fromUser: Boolean, val text: String)

object StreamFilters {
    /** Treat unlabelled releases as unknown; never call them a confirmed dub or sub. */
    fun isDub(text: String): Boolean {
        val t = text.lowercase()
        return Regex("\\b(eng(lish)?[ ._-]?dub|dubbed|dual[ ._-]?audio|en[ ._-]?dub)\\b").containsMatchIn(t)
    }
    fun isSub(text: String): Boolean {
        val t = text.lowercase()
        return Regex("\\b(subbed|english[ ._-]?sub|eng[ ._-]?sub|subtitles?|japanese[ ._-]?audio|jp[ ._-]?audio|dual[ ._-]?audio)\\b").containsMatchIn(t)
    }
    fun filterAnime(options: List<StreamOption>, isAnime: Boolean, mode: AnimeStreamMode): List<StreamOption> {
        if (!isAnime || mode == AnimeStreamMode.ALL) return options
        return options.filter { if (mode == AnimeStreamMode.DUB) isDub(it.metadata) else isSub(it.metadata) }
    }
    fun rankForBandwidth(options: List<StreamOption>, s: UserSettings): List<StreamOption> {
        if (!s.lowBandwidth) return options
        return options.sortedByDescending { option ->
            val text = option.metadata.lowercase()
            val resolution = Regex("\\b(240|360|480|720|1080|2160)p\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val kbps = Regex("\\b(\\d{3,5})[ ._-]?kbps\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
            var score = if (resolution == null) 0 else if (resolution <= s.resolution) 25 else -40
            if (kbps != null) score += if (kbps <= s.videoKbps) 30 else -40
            if (s.codec == CodecPreference.EFFICIENT && Regex("\\b(av1|hevc|x265|h265)\\b").containsMatchIn(text)) score += 8
            if (s.codec == CodecPreference.COMPATIBLE && Regex("\\b(h264|avc|x264)\\b").containsMatchIn(text)) score += 8
            score
        }
    }
}
