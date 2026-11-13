package com.mutinymedia.android.data

import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** Structural validation only; DEX verification and linkage also occur in Android's loader. */
object CloudStreamPackage {
    const val MAX_BYTES = 30L * 1024 * 1024
    data class Manifest(val className: String, val resources: Boolean)
    fun validate(file: File): Manifest {
        require(file.length() in 1..MAX_BYTES) { "Empty extension or package exceeds 30 MB" }
        return ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            require(entries.size <= 4096 && entries.map { it.name }.distinct().size == entries.size) { "Invalid or duplicate ZIP entries" }
            require(entries.all { it.size >= 0 && it.size <= MAX_BYTES } && entries.sumOf { it.size } <= 90L * 1024 * 1024) { "Extension expands beyond limits" }
            val entry = zip.getEntry("manifest.json") ?: error("Missing extension manifest")
            val json = JSONObject(zip.getInputStream(entry).use { Network.readLimited(it, 65_536) })
            val name = json.optString("pluginClassName")
            require(name.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))) { "Missing or invalid plugin class name" }
            val dex = zip.getEntry("classes.dex") ?: error("Missing classes.dex")
            require(dex.size >= 112) { "Missing or empty classes.dex header" }
            zip.getInputStream(dex).use { input ->
                val header = ByteArray(8)
                require(input.read(header) == 8 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10)) && header[7] == 0.toByte()) { "Invalid DEX magic" }
                require(String(header, 4, 3, Charsets.US_ASCII) in setOf("035", "037", "038", "039", "040")) { "Unsupported DEX version" }
            }
            Manifest(name, json.optBoolean("requiresResources", false))
        }
    }
}
