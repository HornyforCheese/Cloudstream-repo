package com.mutinymedia.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONObject

/** Downloads packages into private storage. Execution is handled separately by CloudStreamHost. */
class CloudStreamInstaller(private val context: Context) {
    companion object {
        fun packageId(url: String): String = MessageDigest.getInstance("SHA-256")
            .digest(Network.validateHttps(url).toByteArray()).joinToString("") { "%02x".format(it) }
    }
    suspend fun uninstall(id: String): Boolean = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[a-f0-9]{64}"))) { "Invalid extension identifier" }
        File(directory, "$id.cs3").delete()
    }
    private val directory get() = File(context.filesDir, "cloudstream_extensions").also { it.mkdirs() }
    fun installed(): Set<String> = directory.listFiles()?.filter { it.extension == "cs3" }?.map { it.nameWithoutExtension }?.toSet().orEmpty()

    /** Returns only packages whose manifests and bytecode are still present and readable. */
    fun registeredPackages(): Map<String, String> = buildMap {
        directory.listFiles()?.filter { it.extension == "cs3" }?.forEach { file ->
            runCatching {
                val manifest = CloudStreamPackage.validate(file)
                put(file.nameWithoutExtension, manifest.className)
            }
        }
    }

    suspend fun install(plugin: CloudStreamPlugin, beforeReplace: suspend () -> Unit = {}): String = withContext(Dispatchers.IO) {
        val url = Network.validateHttps(plugin.downloadUrl)
        val id = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val destination = File(directory, "$id.cs3")
        val temp = File.createTempFile("download_", ".tmp", directory)
        try {
            var target = url
            var downloaded = false
            for (redirect in 0..5) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val connection = URL(target).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = false
                    val code = connection.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        require(redirect < 5) { "Too many extension redirects" }
                        target = Network.validateHttps(URL(URL(target), connection.getHeaderField("Location") ?: error("Missing redirect destination")).toString())
                        continue
                    }
                    require(code == 200) { "Extension download failed: HTTP $code" }
                    require(connection.contentLengthLong <= CloudStreamPackage.MAX_BYTES) { "Extension exceeds 30 MB" }
                    var size = 0L
                    connection.inputStream.use { input -> temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            require(size <= CloudStreamPackage.MAX_BYTES) { "Extension exceeds 30 MB" }
                            output.write(buffer, 0, count)
                        }
                    } }
                    downloaded = true
                    break
                } finally { connection.disconnect() }
            }
            check(downloaded) { "Extension was not downloaded" }
            require(temp.length() > 0) { "Empty extension" }
            CloudStreamPackage.validate(temp)
            check(temp.setReadOnly()) { "Could not protect extension code" }
            beforeReplace()
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
