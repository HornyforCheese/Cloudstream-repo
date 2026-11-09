package com.mutinymedia.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real-Debrid link unrestriction only. Torrent magnet conversion is not supported here. */
class RealDebridClient {
    private suspend fun call(path: String, token: String, link: String? = null): JSONObject = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "Enter your Real-Debrid API token" }
        val connection = (URL("https://api.real-debrid.com/rest/1.0/$path").openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 18000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (link != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(("link=" + URLEncoder.encode(link, "UTF-8")).toByteArray()) }
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Real-Debrid returned HTTP $code: " + JSONObject(body.takeIf { it.startsWith("{") } ?: "{}").optString("error", "Check your token or link"))
            JSONObject(body)
        } finally { connection.disconnect() }
    }

    suspend fun verify(token: String): String {
        val profile = call("user", token)
        return profile.optString("username").takeIf { it.isNotBlank() } ?: error("Real-Debrid did not return a username")
    }

    suspend fun resolve(token: String, link: String): String {
        require(link.startsWith("https://", true)) { "Only HTTPS hoster links can be sent to Real-Debrid" }
        val result = call("unrestrict/link", token, link)
        val direct = result.optString("download")
        require(direct.startsWith("https://", true)) { "Real-Debrid did not return a playable HTTPS link" }
        return direct
    }
}
