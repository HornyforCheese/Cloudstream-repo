package com.mutinymedia.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

data class CloudStreamPlugin(
    val name: String,
    val description: String,
    val version: Int,
    val downloadUrl: String,
    val repository: String
)

/** CloudStream repository and plugin discovery. Never executes untrusted plugin code. */
class CloudStreamRegistry {
    suspend fun inspectRepository(url: String): String {
        val data = Network.getJson(Network.validateHttps(url), 500_000)
        require(data.optString("name").isNotBlank() && data.optJSONArray("pluginLists") != null) {
            "Invalid CloudStream repository: expected name and pluginLists"
        }
        return data.optString("name")
    }

    suspend fun discover(url: String): List<CloudStreamPlugin> {
        val repositoryUrl = Network.validateHttps(url)
        val repository = Network.getJson(repositoryUrl, 500_000)
        val lists = repository.optJSONArray("pluginLists") ?: error("Repository has no pluginLists")
        require(lists.length() <= 30) { "Too many plugin lists" }
        val result = mutableListOf<CloudStreamPlugin>()
        for (i in 0 until lists.length()) {
            val raw = lists.optString(i)
            if (raw.isBlank()) continue
            val listUrl = Network.validateHttps(URL(URL(repositoryUrl), raw).toString())
            val rawJson = Network.getText(listUrl, 2_000_000).trim()
            val plugins = if (rawJson.startsWith("[")) JSONArray(rawJson) else JSONObject(rawJson).optJSONArray("plugins") ?: continue
            for (j in 0 until minOf(plugins.length(), 500)) {
                val item = plugins.optJSONObject(j) ?: continue
                val name = item.optString("name")
                val path = item.optString("url")
                if (name.isBlank() || path.isBlank()) continue
                val resolved = runCatching { Network.validateHttps(URL(URL(listUrl), path).toString()) }.getOrNull() ?: continue
                result += CloudStreamPlugin(name, item.optString("description"), item.optInt("version"), resolved, repositoryUrl)
            }
        }
        return result.distinctBy { it.downloadUrl }
    }
}
