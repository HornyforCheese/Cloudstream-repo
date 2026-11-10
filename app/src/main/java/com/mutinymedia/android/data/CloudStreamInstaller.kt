package com.mutinymedia.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONObject

/** Downloads packages into private storage. Does not execute untrusted bytecode. */
class CloudStreamInstaller(private val context: Context) {
    private val directory get() = File(context.filesDir, "cloudstream_extensions").also { it.mkdirs() }
    fun installed(): Set<String> = directory.listFiles()?.filter { it.extension == "cs3" }?.map { it.nameWithoutExtension }?.toSet().orEmpty()

    /** Returns only packages whose manifests and bytecode are still present and readable. */
    fun registeredPackages(): Map<String, String> = buildMap {
        directory.listFiles()?.filter { it.extension == "cs3" }?.forEach { file ->
            runCatching {
                ZipFile(file).use { zip ->
                    val manifest = zip.getEntry("manifest.json") ?: return@use
                    val dex = zip.getEntry("classes.dex") ?: return@use
                    if (dex.size == 0L) return@use
                    val data = zip.getInputStream(manifest).use { JSONObject(Network.readLimited(it, 65_536)) }
                    val className = data.optString("pluginClassName")
                    if (className.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))) put(file.nameWithoutExtension, className)
                }
            }
        }
    }

    suspend fun install(plugin: CloudStreamPlugin): String = withContext(Dispatchers.IO) {
        val url = Network.validateHttps(plugin.downloadUrl)
        val id = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val destination = File(directory, "$id.cs3")
        val temp = File.createTempFile("download_", ".tmp", directory)
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = false
                require(connection.responseCode == 200) { "Extension download failed: HTTP ${connection.responseCode}" }
                var size = 0L
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            require(size <= 30L * 1024 * 1024) { "Extension exceeds 30 MB" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            } finally { connection.disconnect() }
            require(temp.length() > 0) { "Empty extension" }
            ZipFile(temp).use { zip ->
                val entry = zip.getEntry("manifest.json")
                    ?: error("Not a CloudStream extension: manifest.json missing")
                val manifest = zip.getInputStream(entry).use { input -> Network.readLimited(input, 65_536) }
                require(manifest.isNotBlank()) { "Empty extension manifest" }
                val data = JSONObject(manifest)
                require(data.optString("pluginClassName").isNotBlank()) {
                    "CloudStream extension manifest has no pluginClassName"
                }
                require(zip.getEntry("classes.dex")?.size != 0L) {
                    "CloudStream extension has no nonempty classes.dex"
                }
                require(data.optString("pluginClassName").matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))) {
                    "Invalid CloudStream plugin class name"
                }
            }
            require(temp.renameTo(destination)) { "Could not save extension package" }
            id
        } finally { temp.delete() }
    }

    suspend fun uninstall(plugin: CloudStreamPlugin): Boolean = withContext(Dispatchers.IO) {
        val id = MessageDigest.getInstance("SHA-256").digest(Network.validateHttps(plugin.downloadUrl).toByteArray())
            .joinToString("") { "%02x".format(it) }
        File(directory, "$id.cs3").delete()
    }
}
