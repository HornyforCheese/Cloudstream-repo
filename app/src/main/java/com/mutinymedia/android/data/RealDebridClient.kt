package com.mutinymedia.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.delay

/** Resolves HTTPS hoster links and torrent hashes through Real-Debrid. */
class RealDebridClient internal constructor(private val openConnection: (URL) -> HttpURLConnection) {
    constructor() : this({ it.openConnection() as HttpURLConnection })
    private suspend fun call(path: String, token: String, form: Map<String, String>? = null): JSONObject = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "Enter your Real-Debrid API token" }
        val connection = openConnection(URL("https://api.real-debrid.com/rest/1.0/$path"))
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 18000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (form != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(form.entries.joinToString("&") { (key, value) -> URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8") }.toByteArray()) }
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Real-Debrid returned HTTP $code: " + JSONObject(body.takeIf { it.startsWith("{") } ?: "{}").optString("error", "Check your token or link"))
            if (body.isBlank()) JSONObject() else JSONObject(body)
        } finally { connection.disconnect() }
    }

    suspend fun verify(token: String): String {
        val profile = call("user", token)
        return profile.optString("username").takeIf { it.isNotBlank() } ?: error("Real-Debrid did not return a username")
    }

    /** Real-Debrid converts a torrent hash to a hosted HTTPS link. This does not run a torrent engine. */
    suspend fun resolveTorrent(token: String, hash: String, fileIndex: Int?): String {
        require(hash.matches(Regex("[a-fA-F0-9]{40}"))) { "Invalid torrent info hash" }
        val magnet = "magnet:?xt=urn:btih:${hash.lowercase()}"
        val added = call("torrents/addMagnet", token, mapOf("magnet" to magnet))
        val id = added.optString("id").takeIf { it.isNotBlank() } ?: error("Real-Debrid did not accept the magnet")
        val encodedId = URLEncoder.encode(id, "UTF-8")
        var info = call("torrents/info/$encodedId", token)
        val files = info.optJSONArray("files") ?: error("Real-Debrid returned no torrent file list")
        require(files.length() > 0) { "The torrent has no files" }
        val selected = if (fileIndex != null && fileIndex in 0 until files.length()) fileIndex else {
            (0 until files.length()).maxByOrNull { files.optJSONObject(it)?.optLong("bytes") ?: 0L } ?: 0
        }
        val fileId = files.optJSONObject(selected)?.optInt("id", -1) ?: -1
        require(fileId >= 0) { "Unable to identify a torrent file" }
        call("torrents/selectFiles/$encodedId", token, mapOf("files" to fileId.toString()))
        repeat(12) {
            info = call("torrents/info/$encodedId", token)
            val status = info.optString("status")
            if (status == "downloaded") {
                val links = info.optJSONArray("links")
                val link = links?.optString(0).orEmpty()
                require(link.startsWith("https://", true)) { "Real-Debrid returned no downloadable HTTPS link" }
                return resolve(token, link)
            }
            if (status in listOf("error", "virus", "dead", "magnet_error")) error("Real-Debrid torrent failed: $status")
            delay(2_500)
        }
        error("Torrent is not cached yet. Real-Debrid is still processing it; retry later.")
    }

    suspend fun resolve(token: String, link: String): String {
        require(link.startsWith("https://", true)) { "Only HTTPS hoster links can be sent to Real-Debrid" }
        val result = call("unrestrict/link", token, mapOf("link" to link))
        val direct = result.optString("download")
        require(direct.startsWith("https://", true)) { "Real-Debrid did not return a playable HTTPS link" }
        return direct
    }
}
