package com.mutinymedia.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Downloads and validates executable archives before the host can activate them. */
class CloudStreamInstaller(context: Context) {
    private val directory = File(context.filesDir, "cloudstream_extensions")
    private val extensionClass = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")

    data class Manifest(val className: String, val name: String, val version: Int, val requiresResources: Boolean)

    fun installed(): Set<String> = directory.listFiles()
        ?.filter { it.isFile && it.extension == "cs3" && it.nameWithoutExtension.matches(packageIdPattern) }
        ?.mapTo(sortedSetOf()) { it.nameWithoutExtension }.orEmpty()

    fun file(id: String): File {
        require(id.matches(packageIdPattern)) { "Invalid extension identifier" }
        return File(directory, "$id.cs3")
    }

    fun manifest(file: File): Manifest = ZipFile(file).use { zip ->
        require(zip.size() <= 2048) { "Extension has too many ZIP entries" }
        val entries = zip.entries().toList()
        require(entries.map { it.name }.distinct().size == entries.size) { "Duplicate extension ZIP entries" }
        val entry = zip.getEntry("manifest.json") ?: error("Extension manifest is missing")
        val manifest = JSONObject(zip.getInputStream(entry).use { Network.readLimited(it, 65_536) })
        val className = manifest.optString("pluginClassName")
        require(className.matches(extensionClass)) { "Missing or invalid plugin class name" }
        val dex = zip.getEntry("classes.dex")
        require(dex != null && dex.size in 112..MAX_ARCHIVE_BYTES) { "Missing or invalid classes.dex" }
        val magic = ByteArray(8).also { bytes ->
            java.io.DataInputStream(zip.getInputStream(dex)).use { it.readFully(bytes) }
        }
        require(magic.copyOfRange(0, 4).contentEquals("dex\n".toByteArray()) && magic[7] == 0.toByte()) {
            "Invalid classes.dex header"
        }
        val total = entries.sumOf { it.size.coerceAtLeast(0) }
        require(total <= MAX_ARCHIVE_BYTES * 3) { "Expanded extension exceeds 90 MiB" }
        Manifest(className, manifest.optString("name").ifBlank { className.substringAfterLast('.') },
            manifest.optInt("version"), manifest.optBoolean("requiresResources"))
    }

    suspend fun download(plugin: CloudStreamPlugin): File = withContext(Dispatchers.IO) {
        var url = Network.validateHttps(plugin.downloadUrl)
        check(directory.isDirectory || directory.mkdirs()) { "Could not create extension directory" }
        val temp = File.createTempFile("download_", ".tmp", directory)
        try {
            repeat(6) {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = false
                    when (val code = connection.responseCode) {
                        301, 302, 303, 307, 308 -> {
                            val location = connection.getHeaderField("Location") ?: error("Extension redirect has no destination")
                            url = Network.validateHttps(URL(URL(url), location).toString())
                        }
                        200 -> {
                            require(connection.contentLengthLong <= MAX_ARCHIVE_BYTES) { "Extension exceeds 30 MiB" }
                            connection.inputStream.use { input ->
                                temp.outputStream().use { output ->
                                    check(temp.setReadOnly()) { "Could not secure extension bytecode as read-only" }
                                    val buffer = ByteArray(8192)
                                    var size = 0L
                                    while (true) {
                                        ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        size += count
                                        require(size <= MAX_ARCHIVE_BYTES) { "Extension exceeds 30 MiB" }
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                            manifest(temp)
                            return@withContext temp
                        }
                        else -> error("Extension download failed: HTTP $code")
                    }
                } finally {
                    connection.disconnect()
                }
            }
            error("Too many extension redirects")
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    companion object {
        private const val MAX_ARCHIVE_BYTES = 30L * 1024 * 1024
        private val packageIdPattern = Regex("[a-f0-9]{64}")

        fun packageId(url: String): String = digest(Network.validateHttps(url))
        internal fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
