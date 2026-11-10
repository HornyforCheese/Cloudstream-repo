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
    val exactResolution: Int? = null,
    val excludedAddonUrls: Set<String> = emptySet(),
    val animeDualAudioOnly: Boolean = false,
    val animeUncensoredOnly: Boolean = false,
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
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val cast: List<String> = emptyList(),
)

data class MediaCollection(val name: String, val titles: List<MediaItem> = emptyList())

data class Addon(
    val id: String,
    val title: String,
    val baseUrl: String,
    val supportsCatalog: Boolean,
    val supportsStreams: Boolean,
    val supportsMeta: Boolean = false,
)

data class StreamSubtitle(val url: String, val language: String, val mimeType: String)

data class StreamOption(
    val id: String,
    val title: String,
    val url: String?,
    val provider: String,
    val fileName: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val externalUrl: String? = null,
    val infoHash: String? = null,
    val fileIndex: Int? = null,
    val mimeType: String? = null,
    val subtitles: List<StreamSubtitle> = emptyList(),
    val cloudStreamSession: Boolean = false,
    val declaredResolution: Int? = null,
    val addonUrl: String? = null,
) {
    val playable: Boolean get() = runCatching {
        val uri = java.net.URI(url ?: return false)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
    val hasTorrent: Boolean get() = infoHash?.matches(Regex("[a-fA-F0-9]{40}")) == true
    val metadata: String get() = listOfNotNull(title, fileName).joinToString(" ")
}

data class Episode(val id: String, val title: String, val season: Int, val number: Int)

data class ChatLine(val fromUser: Boolean, val text: String)

internal fun resumablePosition(positionMs: Long, durationMs: Long): Long? {
    if (durationMs <= 0 || positionMs < 10_000 || positionMs >= durationMs) return null
    val endMargin = (durationMs / 20).coerceIn(5_000, 60_000)
    return positionMs.takeIf { durationMs - it > endMargin }
}

object StreamFilters {
    val resolutions = listOf(144, 240, 360, 480, 540, 576, 720, 900, 1080, 1440, 2160, 4320)
    private val resolutionLabel = Regex("(?i)(?<![a-z0-9])(${resolutions.joinToString("|")})p(?![a-z0-9])")
    private val uhdLabel = Regex("(?i)(?<![a-z0-9])(?:4k|uhd)(?![a-z0-9])")
    private val dualAudioLabel = Regex("(?i)\\bdual[ ._-]?audio\\b")
    private val uncensoredLabel = Regex("(?i)\\b(?:uncensored|uncut)\\b")
    private val englishDubLabel = Regex("(?i)\\b(?:(?:english|eng|en)[ ._-]+(?:dub(?:bed)?|audio)|dubbed[ ._-]+(?:english|eng|en))\\b")
    private val englishLanguage = Regex("(?i)\\b(?:english|eng)\\b")
    private val bitrateLabel = Regex("\\b(\\d{3,5})[ ._-]?kbps\\b")

    fun resolutionOf(source: StreamOption): Int? {
        source.declaredResolution?.takeIf { it in resolutions }?.let { return it }
        val label = source.metadata
        val heights = resolutionLabel.findAll(label).map { it.groupValues[1].toInt() }.toMutableSet()
        if (uhdLabel.containsMatchIn(label)) heights += 2160
        return heights.singleOrNull()
    }

    fun select(options: List<StreamOption>, isAnime: Boolean, settings: UserSettings): List<StreamOption> {
        val eligible = filterAnime(options, isAnime, settings.animeMode).filter { source ->
            (source.addonUrl == null || source.addonUrl !in settings.excludedAddonUrls) &&
                (settings.exactResolution == null || resolutionOf(source) == settings.exactResolution) &&
                (!isAnime || !settings.animeDualAudioOnly || dualAudioLabel.containsMatchIn(source.metadata)) &&
                (!isAnime || !settings.animeUncensoredOnly || uncensoredLabel.containsMatchIn(source.metadata))
        }
        val priorities = settings.addonUrls.withIndex().associate { it.value to it.index }
        return rankForBandwidth(eligible.sortedBy { priorities[it.addonUrl] ?: Int.MAX_VALUE }, settings)
    }

    /** Treat unlabelled releases as unknown; never call them a confirmed dub or sub. */
    fun isDub(text: String): Boolean =
        englishDubLabel.containsMatchIn(text) ||
            (dualAudioLabel.containsMatchIn(text) && englishLanguage.containsMatchIn(text) &&
                !Regex("(?i)\\b(?:english|eng)[ ._-]+(?:sub(?:bed)?|subtitles?)\\b").containsMatchIn(text))
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
            val resolution = resolutionOf(option)
            val kbps = bitrateLabel.find(text)?.groupValues?.get(1)?.toIntOrNull()
            var score = if (resolution == null) 0 else if (resolution <= s.resolution) 25 else -40
            if (kbps != null) score += if (kbps <= s.videoKbps) 30 else -40
            if (s.codec == CodecPreference.EFFICIENT && Regex("\\b(av1|hevc|x265|h265)\\b").containsMatchIn(text)) score += 8
            if (s.codec == CodecPreference.COMPATIBLE && Regex("\\b(h264|avc|x264)\\b").containsMatchIn(text)) score += 8
            score
        }
    }
}
