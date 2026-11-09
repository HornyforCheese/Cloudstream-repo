package com.mutinymedia.android.data

import org.json.JSONObject

/** Read-only repository discovery. Running Android .cs3/.dex plugins requires an isolated runtime and security review. */
class CloudStreamRegistry {
    suspend fun inspectRepository(url: String): String {
        require(url.trim().startsWith("https://")) { "Use the HTTPS repo.json URL" }
        val data: JSONObject = Network.getJson(url.trim(), 500_000)
        require(data.optString("name").isNotBlank() && data.optJSONArray("pluginLists") != null) { "Invalid CloudStream repository: expected name and pluginLists" }
        return data.optString("name").ifBlank { data.optString("description", "Valid repository JSON") }
    }
}
