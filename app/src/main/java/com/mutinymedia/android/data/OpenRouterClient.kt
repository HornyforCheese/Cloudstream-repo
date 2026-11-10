package com.mutinymedia.android.data

import com.mutinymedia.android.domain.ChatLine
import com.mutinymedia.android.domain.OpenRouterModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OpenRouterClient {
    suspend fun listFreeModels(): List<OpenRouterModel> = parseFreeModels(
        Network.getJson(
            "https://openrouter.ai/api/v1/models?input_modalities=text&output_modalities=text",
            limitBytes = 4_000_000,
        ),
    )

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
            when (val status = c.responseCode) {
                in 200..299 -> Unit
                401, 403 -> error("OpenRouter rejected the API key or its permissions. Check Settings → Ask AI.")
                402 -> error("OpenRouter requires credit for this request. Try a free model in Settings → Ask AI.")
                404 -> error("OpenRouter could not route to this model. Refresh free models in Settings → Ask AI.")
                429 -> error("OpenRouter rate limit reached. Wait or choose another available free model.")
                else -> error("OpenRouter returned HTTP $status.")
            }
            data?.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.takeIf(String::isNotBlank)
                ?: error("OpenRouter returned an empty reply")
        } finally { c.disconnect() }
    }
}

internal fun parseFreeModels(response: JSONObject): List<OpenRouterModel> {
    val models = response.optJSONArray("data") ?: error("OpenRouter returned no model list")
    return (0 until models.length()).mapNotNull { index ->
        val model = models.optJSONObject(index) ?: return@mapNotNull null
        val id = model.optString("id")
        val pricing = model.optJSONObject("pricing") ?: return@mapNotNull null
        if (!(id.endsWith(":free") || id == "openrouter/free") ||
            pricing.optString("prompt").toBigDecimalOrNull()?.signum() != 0 ||
            pricing.optString("completion").toBigDecimalOrNull()?.signum() != 0
        ) return@mapNotNull null
        OpenRouterModel(id, model.optString("name").ifBlank { id })
    }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
}
