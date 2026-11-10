package com.mutinymedia.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import kotlin.coroutines.coroutineContext

object Network {
    internal const val CONNECT_TIMEOUT_MS = 10_000
    internal const val READ_TIMEOUT_MS = 16_000
    internal const val MAX_TIMEOUT_ATTEMPTS = 2

    internal fun shouldRetryTimeout(error: Exception, attempt: Int): Boolean =
        error is SocketTimeoutException && attempt + 1 < MAX_TIMEOUT_ATTEMPTS

    fun validateHttps(url: String): String {
        val uri = URI(url.trim())
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "Use a valid HTTPS URL without embedded credentials"
        }
        return uri.toASCIIString()
    }

    internal fun readLimited(input: InputStream, limitBytes: Int): String = input.use {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limitBytes) { "Provider response too large" }
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }

    suspend fun getText(url: String, limitBytes: Int = 2_000_000): String = withContext(Dispatchers.IO) {
        require(limitBytes > 0) { "Response limit must be positive" }
        var target = validateHttps(url)
        repeat(6) { attempt ->
            coroutineContext.ensureActive()
            val connection = URL(target).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MutinyMedia/1.0")
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    require(attempt < 5) { "Too many provider redirects" }
                    val location = connection.getHeaderField("Location") ?: error("Provider redirect has no destination")
                    target = validateHttps(URL(URL(target), location).toString())
                } else {
                    require(code in 200..299) { "Provider returned HTTP $code from ${URL(target).host} " }
                    return@withContext readLimited(connection.inputStream, limitBytes)
                }
            } finally { connection.disconnect() }
        }
        error("Too many provider redirects")
    }

    suspend fun getJson(url: String, limitBytes: Int = 2_000_000): JSONObject =
        JSONObject(getText(url, limitBytes))
}
