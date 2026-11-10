package com.mutinymedia.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mutinymedia.android.domain.AnimeStreamMode
import com.mutinymedia.android.domain.CodecPreference
import com.mutinymedia.android.domain.ThemeFont
import com.mutinymedia.android.domain.ThemePreset
import com.mutinymedia.android.domain.UserSettings
import com.mutinymedia.android.domain.MediaItem
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
        val addons = stringPreferencesKey("stremio_addon_urls")
        val repos = stringPreferencesKey("cloudstream_repository_urls")
        val model = stringPreferencesKey("openrouter_model")
        val watchlist = stringPreferencesKey("watchlist_v1")
        val themePreset = stringPreferencesKey("theme_preset")
        val themeFont = stringPreferencesKey("theme_font")
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
                addonUrls = parseStringList(p[K.addons]),
                extensionRepositories = parseStringList(p[K.repos]),
                openRouterModel = p[K.model] ?: "openai/gpt-4o-mini",
                themePreset = enumValueOrDefault(p[K.themePreset], ThemePreset.MUTINY),
                themeFont = enumValueOrDefault(p[K.themeFont], ThemeFont.DEFAULT),
            )
        }
    /** Local-only library. No account-sync claims; writes are atomic via DataStore. */
    val watchlist: Flow<List<MediaItem>> = context.mutinyDataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { decodeWatchlist(it[K.watchlist]) }

    suspend fun addToWatchlist(item: MediaItem) {
        context.mutinyDataStore.edit { p ->
            val current = decodeWatchlist(p[K.watchlist])
            p[K.watchlist] = encodeWatchlist((current.filterNot { it.id == item.id && it.type == item.type } + item).takeLast(500))
        }
    }
    suspend fun importAccountData(addonUrls: List<String>, items: List<MediaItem>) {
        context.mutinyDataStore.edit { p ->
            p[K.addons] = encodeStringList(parseStringList(p[K.addons]) + addonUrls)
            val existing = decodeWatchlist(p[K.watchlist])
            val merged = (existing + items).distinctBy { it.type to it.id }.take(500)
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
        (0 until minOf(array.length(), 500)).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id"); val type = o.optString("type"); val title = o.optString("title")
            if (id.isBlank() || title.isBlank() || type !in listOf("movie", "series", "anime")) null
            else MediaItem(id, type, title, o.optString("poster").takeIf(String::isNotBlank),
                o.optString("description").takeIf(String::isNotBlank), o.optBoolean("anime"))
        }
    }.getOrDefault(emptyList())
    private fun encodeWatchlist(items: List<MediaItem>): String = JSONArray().apply {
        items.forEach { item -> put(JSONObject().put("id", item.id).put("type", item.type)
            .put("title", item.title).put("poster", item.poster ?: "")
            .put("description", item.description ?: "").put("anime", item.isAnime)) }
    }.toString()

    suspend fun setThemePreset(value: ThemePreset) {
        context.mutinyDataStore.edit { it[K.themePreset] = value.name }
    }

    suspend fun randomizeThemePreset() {
        context.mutinyDataStore.edit { p ->
            val current = enumValueOrDefault(p[K.themePreset], ThemePreset.MUTINY)
            p[K.themePreset] = ThemePreset.entries.filterNot { it == current }.random().name
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
    suspend fun setModel(value: String) { require(value.isNotBlank()) { "Enter a model ID" }; context.mutinyDataStore.edit { it[K.model] = value.trim().take(120) } }
    suspend fun addAddon(url: String) { context.mutinyDataStore.edit { p -> p[K.addons] = encodeStringList(parseStringList(p[K.addons]) + url) } }
    suspend fun removeAddon(url: String) { context.mutinyDataStore.edit { p -> p[K.addons] = encodeStringList(parseStringList(p[K.addons]).filterNot { it == url }) } }
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
