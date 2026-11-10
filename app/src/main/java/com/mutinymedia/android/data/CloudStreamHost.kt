package com.mutinymedia.android.data

import android.content.Context
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject

/**
 * CloudStream bytecode loader with explicit compatibility diagnostics.
 *
 * A CloudStream extension requires the original CloudStream host API (MainAPI,
 * APIHolder, networking and extractor interfaces). Loading a DEX alone cannot
 * provide those APIs. This class intentionally refuses unsupported plugins
 * rather than claiming that their providers were registered.
 */
class CloudStreamHost(private val context: Context) {
    data class ProviderStatus(
        val packageId: String,
        val pluginClass: String,
        val state: State,
        val detail: String,
    )
    enum class State { MISSING, INCOMPATIBLE, LOADABLE, FAILED }

    private val directory get() = File(context.filesDir, "cloudstream_extensions")
    private val loaders = mutableMapOf<String, ClassLoader>()

    suspend fun inspect(packageId: String): ProviderStatus = withContext(Dispatchers.IO) {
        require(packageId.matches(Regex("[a-f0-9]{64}"))) { "Invalid extension identifier" }
        val file = File(directory, "$packageId.cs3")
        if (!file.isFile) return@withContext ProviderStatus(packageId, "", State.MISSING, "Extension not installed")
        try {
            val className = ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.json") ?: error("Missing extension manifest")
                require(zip.getEntry("classes.dex")?.size != 0L) { "Missing or empty classes.dex" }
                JSONObject(zip.getInputStream(entry).use { Network.readLimited(it, 65_536) })
                    .optString("pluginClassName").also {
                        require(it.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))) {
                            "Missing or invalid plugin class name"
                        }
                    }
            }
            // Verify host API presence before allowing plugin constructors or load() to execute.
            val hostAvailable = runCatching {
                Class.forName("com.lagradost.cloudstream3.plugins.BasePlugin", false, context.classLoader)
                Class.forName("com.lagradost.cloudstream3.MainAPI", false, context.classLoader)
                Class.forName("com.lagradost.cloudstream3.APIHolder", false, context.classLoader)
                Class.forName("com.lagradost.cloudstream3.utils.ExtractorApi", false, context.classLoader)
                Class.forName("com.lagradost.cloudstream3.plugins.Plugin", false, context.classLoader)
            }.isSuccess
            if (!hostAvailable) return@withContext ProviderStatus(
                packageId, className, State.INCOMPATIBLE,
                "CloudStream host API (BasePlugin, MainAPI, APIHolder, ExtractorApi or Plugin) is missing. The extension cannot execute."
            )
            // Loading class metadata does not execute the plugin. Registration must be
            // performed by a compatible host and is not claimed here.
            val loader = synchronized(loaders) {
                loaders.getOrPut(packageId) {
                    DexClassLoader(file.absolutePath, context.codeCacheDir.absolutePath, null, context.classLoader)
                }
            }
            val pluginClass = loader.loadClass(className)
            ProviderStatus(packageId, className, State.INCOMPATIBLE,
                "Class ${pluginClass.name} resolved, but CloudStream plugin lifecycle and provider registration are not implemented.")
        } catch (e: Exception) {
            ProviderStatus(packageId, "", State.FAILED, e.message ?: "Could not inspect extension")
        } catch (e: LinkageError) {
            ProviderStatus(packageId, "", State.INCOMPATIBLE, e.message ?: "Incompatible CloudStream host ABI")
        }
    }

    fun unload(packageId: String) {
        synchronized(loaders) { loaders.remove(packageId) }
    }

    fun unloadAll() {
        synchronized(loaders) { loaders.clear() }
    }
}
