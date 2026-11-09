package com.mutinymedia.android.data

import com.mutinymedia.android.domain.ChatLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OpenRouterClient {
    suspend fun complete(apiKey: String, model: String, messages: List<ChatLine>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Connect OpenRouter in Settings first" }
        require(model.isNotBlank()) { "Specify an OpenRouter model ID" }
        val c = URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 12_000
            c.readTimeout = 45_000
            c.doOutput = true
            c.instanceFollowRedirects = false
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $apiKey")
            c.setRequestProperty("X-Title", "Mutiny Media Android")
            val body = JSONObject().put("model", model).put("stream", false)
                .put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", "You help users discover legally available movies, series, anime and games. Do not invent streams or claim availability without verification."))
                    messages.takeLast(16).forEach { put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text)) }
                })
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val response = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)?.let { Network.readLimited(it, 600_000) } ?: ""
            val data = runCatching { JSONObject(response) }.getOrNull()
            if (c.responseCode !in 200..299) error(data?.optJSONObject("error")?.optString("message") ?: "OpenRouter returned ${c.responseCode}")
            data?.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.takeIf(String::isNotBlank)
                ?: error("OpenRouter returned an empty reply")
        } finally { c.disconnect() }
    }
}
