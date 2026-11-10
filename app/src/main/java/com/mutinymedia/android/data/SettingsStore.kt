package com.mutinymedia.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mutinymedia.android.domain.AnimeStreamMode
import com.mutinymedia.android.domain.CodecPreference
import com.mutinymedia.android.domain.DEFAULT_OPENROUTER_MODEL
import com.mutinymedia.android.domain.ThemeFont
import com.mutinymedia.android.domain.ThemePreset
import com.mutinymedia.android.domain.UserSettings
import com.mutinymedia.android.domain.MediaItem
import com.mutinymedia.android.domain.MediaCollection
import com.mutinymedia.android.domain.StreamFilters
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

private val Context.mutinyDataStore by preferencesDataStore(name = "mutiny_media_settings")

class SettingsStore(private val context: Context) {
    private object K {
        val low = booleanPreferencesKey("low_bandwidth")
        val video = intPreferencesKey("preferred_video_kbps")
        val audio = intPreferencesKey("preferred_audio_kbps")
        val resolution = intPreferencesKey("maximum_resolution")
        val adaptive = booleanPreferencesKey("adaptive_hls")
        val codec = stringPreferencesKey("codec_preference")
        val anime = stringPreferencesKey("anime_stream_mode")
        val dualAudio = booleanPreferencesKey("anime_dual_audio_only")
        val uncensored = booleanPreferencesKey("anime_uncensored_only")
        val addons = stringPreferencesKey("stremio_addon_urls")
        val repos = stringPreferencesKey("cloudstream_repository_urls")
        val model = stringPreferencesKey("openrouter_model")
        val watchlist = stringPreferencesKey("watchlist_v1")
        val themePreset = stringPreferencesKey("theme_preset")
        val themeFont = stringPreferencesKey("theme_font")
        val exactResolution = intPreferencesKey("exact_source_resolution")
        val excludedAddons = stringPreferencesKey("excluded_addon_urls")
        val collections = stringPreferencesKey("collections_v1")
        val playbackPositions = stringPreferencesKey("playback_positions_v1")
    }
    val settings: Flow<UserSettings> = context.mutinyDataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { p ->
            UserSettings(
                lowBandwidth = p[K.low] ?: false,
                videoKbps = (p[K.video] ?: 700).coerceIn(200, 1500),
                audioKbps = (p[K.audio] ?: 64).takeIf { it in listOf(32, 48, 64) } ?: 64,
                resolution = (p[K.resolution] ?: 480).takeIf { it in listOf(240, 360, 480) } ?: 480,
                adaptive = p[K.adaptive] ?: true,
                codec = enumValueOrDefault(p[K.codec], CodecPreference.EFFICIENT),
                animeMode = enumValueOrDefault(p[K.anime], AnimeStreamMode.ALL),
                animeDualAudioOnly = p[K.dualAudio] ?: false,
                animeUncensoredOnly = p[K.uncensored] ?: false,
                addonUrls = parseStringList(p[K.addons]),
                extensionRepositories = parseStringList(p[K.repos]),
                openRouterModel = p[K.model] ?: DEFAULT_OPENROUTER_MODEL,
                themePreset = enumValueOrDefault(p[K.themePreset], ThemePreset.MUTINY),
                themeFont = enumValueOrDefault(p[K.themeFont], ThemeFont.DEFAULT),
                exactResolution = p[K.exactResolution]?.takeIf { it in StreamFilters.resolutions },
                excludedAddonUrls = parseStringList(p[K.excludedAddons]).toSet(),
            )
        }
    /** Local-only library. No account-sync claims; writes are atomic via DataStore. */
    val watchlist: Flow<List<MediaItem>> = context.mutinyDataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { decodeWatchlist(it[K.watchlist]) }

    val collections: Flow<List<MediaCollection>> = context.mutinyDataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { decodeCollections(it[K.collections]) }

    val playbackPositions: Flow<Map<String, Long>> = context.mutinyDataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { decodePlaybackPositions(it[K.playbackPositions]) }

    suspend fun savePlaybackPosition(key: String, positionMs: Long?) {
        context.mutinyDataStore.edit { p ->
            val positions = decodePlaybackPositions(p[K.playbackPositions]).toMutableMap()
            if (positions[key] == positionMs) return@edit
            positions.remove(key)
            if (positionMs != null) positions[key] = positionMs
            p[K.playbackPositions] = JSONArray(positions.entries.toList().takeLast(500).map {
                JSONObject().put("key", it.key).put("ms", it.value)
            }).toString()
        }
    }

    private fun decodePlaybackPositions(raw: String?): Map<String, Long> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildMap {
            for (index in 0 until minOf(array.length(), 500)) {
                val row = array.optJSONObject(index) ?: continue
                val key = row.optString("key")
                val position = row.optLong("ms", -1)
                if (key.isNotBlank() && position >= 10_000) put(key, position)
            }
        }
    }.getOrDefault(emptyMap())

    suspend fun createCollection(name: String) {
        val label = name.trim()
        require(label.length in 1..60) { "Enter a collection name up to 60 characters" }
        updateCollections { existing ->
            require(existing.none { it.name.equals(label, ignoreCase = true) }) { "A collection with that name already exists" }
            require(existing.size < 50) { "You can save up to 50 collections" }
            existing + MediaCollection(label)
        }
    }

    suspend fun deleteCollection(name: String) = updateCollections { list -> list.filterNot { it.name == name } }

    suspend fun setCollectionTitle(name: String, item: MediaItem, saved: Boolean) = updateCollections { list ->
        require(list.any { it.name == name }) { "Collection no longer exists" }
        list.map { collection ->
            if (collection.name != name) return@map collection
            val remaining = collection.titles.filterNot { it.type == item.type && it.id == item.id }
            require(!saved || remaining.size < 200) { "This collection already has 200 titles" }
            collection.copy(titles = if (saved) remaining + item else remaining)
        }
    }

    private suspend fun updateCollections(update: (List<MediaCollection>) -> List<MediaCollection>) {
        context.mutinyDataStore.edit { p ->
            p[K.collections] = JSONArray().apply {
                update(decodeCollections(p[K.collections])).forEach { collection ->
                    put(JSONObject().put("name", collection.name).put("titles", JSONArray(encodeWatchlist(collection.titles))))
                }
            }.toString()
        }
    }

    private fun decodeCollections(raw: String?): List<MediaCollection> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until minOf(array.length(), 50)).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val name = obj.optString("name").takeIf { it.length in 1..60 } ?: return@mapNotNull null
            MediaCollection(name, decodeWatchlist(obj.optJSONArray("titles")?.toString()).take(200))
        }
    }.getOrDefault(emptyList())

    suspend fun addToWatchlist(item: MediaItem) {
        context.mutinyDataStore.edit { p ->
            val current = decodeWatchlist(p[K.watchlist])
            p[K.watchlist] = encodeWatchlist((current.filterNot { it.id == item.id && it.type == item.type } + item).takeLast(2_000))
        }
    }
    suspend fun importAccountData(addonUrls: List<String>, items: List<MediaItem>) {
        context.mutinyDataStore.edit { p ->
            p[K.addons] = encodeStringList(parseStringList(p[K.addons]) + addonUrls)
            val existing = decodeWatchlist(p[K.watchlist])
            val merged = (existing + items).distinctBy { it.type to it.id }.take(2_000)
            p[K.watchlist] = encodeWatchlist(merged)
        }
    }
    suspend fun removeFromWatchlist(item: MediaItem) {
        context.mutinyDataStore.edit { p ->
            p[K.watchlist] = encodeWatchlist(decodeWatchlist(p[K.watchlist]).filterNot { it.id == item.id && it.type == item.type })
        }
    }
    private fun decodeWatchlist(raw: String?): List<MediaItem> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until minOf(array.length(), 2_000)).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id"); val type = o.optString("type"); val title = o.optString("title")
            if (id.isBlank() || title.isBlank() || type !in listOf("movie", "series", "anime")) null
            else MediaItem(id, type, title, o.optString("poster").takeIf(String::isNotBlank),
                o.optString("description").takeIf(String::isNotBlank), o.optBoolean("anime"),
                genres = parseStringList(o.optJSONArray("genres")?.toString()),
                year = o.optInt("year").takeIf { it in 1800..2099 },
                cast = parseStringList(o.optJSONArray("cast")?.toString()),
            )
        }
    }.getOrDefault(emptyList())
    private fun encodeWatchlist(items: List<MediaItem>): String = JSONArray().apply {
        items.forEach { item -> put(JSONObject().put("id", item.id).put("type", item.type)
            .put("title", item.title).put("poster", item.poster ?: "")
            .put("description", item.description ?: "").put("anime", item.isAnime)
            .put("genres", JSONArray(item.genres)).put("year", item.year).put("cast", JSONArray(item.cast))) }
    }.toString()

    suspend fun setThemePreset(value: ThemePreset) {
        context.mutinyDataStore.edit { it[K.themePreset] = value.name }
    }

    suspend fun randomizeAppearance() {
        context.mutinyDataStore.edit { p ->
            val current = enumValueOrDefault(p[K.themePreset], ThemePreset.MUTINY)
            p[K.themePreset] = ThemePreset.entries.filterNot { it == current }.random().name
            val font = enumValueOrDefault(p[K.themeFont], ThemeFont.DEFAULT)
            p[K.themeFont] = ThemeFont.entries.filterNot { it == font }.random().name
        }
    }

    suspend fun setThemeFont(value: ThemeFont) {
        context.mutinyDataStore.edit { it[K.themeFont] = value.name }
    }

    suspend fun setLowBandwidth(value: Boolean) { context.mutinyDataStore.edit { it[K.low] = value } }
    suspend fun setVideoKbps(value: Int) { context.mutinyDataStore.edit { it[K.video] = value.coerceIn(200, 1500) } }
    suspend fun setAudioKbps(value: Int) { context.mutinyDataStore.edit { it[K.audio] = value.takeIf { n -> n in listOf(32,48,64) } ?: 64 } }
    suspend fun setResolution(value: Int) { context.mutinyDataStore.edit { it[K.resolution] = value.takeIf { n -> n in listOf(240,360,480) } ?: 480 } }
    suspend fun setAdaptive(value: Boolean) { context.mutinyDataStore.edit { it[K.adaptive] = value } }
    suspend fun setCodec(value: CodecPreference) { context.mutinyDataStore.edit { it[K.codec] = value.name } }
    suspend fun setAnime(value: AnimeStreamMode) { context.mutinyDataStore.edit { it[K.anime] = value.name } }
    suspend fun setDualAudioOnly(value: Boolean) { context.mutinyDataStore.edit { it[K.dualAudio] = value } }
    suspend fun setUncensoredOnly(value: Boolean) { context.mutinyDataStore.edit { it[K.uncensored] = value } }
    suspend fun setModel(value: String) { require(value.isNotBlank()) { "Enter a model ID" }; context.mutinyDataStore.edit { it[K.model] = value.trim().take(120) } }
    suspend fun addAddon(url: String) { context.mutinyDataStore.edit { p -> p[K.addons] = encodeStringList(parseStringList(p[K.addons]) + url) } }
    suspend fun removeAddon(url: String) {
        context.mutinyDataStore.edit { p ->
            p[K.addons] = encodeStringList(parseStringList(p[K.addons]).filterNot { it == url })
            p[K.excludedAddons] = encodeStringList(parseStringList(p[K.excludedAddons]).filterNot { it == url })
        }
    }
    suspend fun setAddonEnabled(url: String, enabled: Boolean) {
        context.mutinyDataStore.edit { p ->
            val excluded = parseStringList(p[K.excludedAddons])
            p[K.excludedAddons] = encodeStringList(if (enabled) excluded - url else excluded + url)
        }
    }
    suspend fun moveAddon(url: String, direction: Int) {
        context.mutinyDataStore.edit { p ->
            val urls = parseStringList(p[K.addons]).toMutableList()
            val from = urls.indexOf(url)
            val to = from + direction
            if (from >= 0 && to in urls.indices) {
                urls.add(to, urls.removeAt(from))
                p[K.addons] = encodeStringList(urls)
            }
        }
    }
    suspend fun setExactResolution(value: Int?) {
        require(value == null || value in StreamFilters.resolutions)
        context.mutinyDataStore.edit { p ->
            if (value == null) p.remove(K.exactResolution) else p[K.exactResolution] = value
        }
    }
    suspend fun addRepository(url: String) { context.mutinyDataStore.edit { p -> p[K.repos] = encodeStringList(parseStringList(p[K.repos]) + url) } }
    suspend fun removeRepository(url: String) { context.mutinyDataStore.edit { p -> p[K.repos] = encodeStringList(parseStringList(p[K.repos]).filterNot { it == url }) } }
    companion object {
        private fun parseStringList(source: String?): List<String> = runCatching {
            val arr = JSONArray(source ?: "[]")
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.distinct()
        }.getOrDefault(emptyList())
        private fun encodeStringList(list: List<String>): String = JSONArray(list.distinct()).toString()
        private inline fun <reified T: Enum<T>> enumValueOrDefault(name: String?, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }
}
