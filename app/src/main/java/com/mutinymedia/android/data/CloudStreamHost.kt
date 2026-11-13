package com.mutinymedia.android.data

import android.content.Context
import android.content.res.Resources
import android.content.res.AssetManager
import com.lagradost.api.setContext
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.lang.ref.WeakReference

/** In-process execution is NOT a security sandbox. Only install extensions you trust. */
class CloudStreamHost(context: Context) {
    private val context = context.applicationContext
    data class ProviderStatus(val packageId: String, val pluginClass: String, val state: State, val detail: String)
    data class Provider(val id: String, val packageId: String, val name: String, val enabled: Boolean)
    enum class State { MISSING, INCOMPATIBLE, LOADABLE, ACTIVE, FAILED }
    private data class Loaded(val plugin: BasePlugin, val loader: ClassLoader, val providers: List<MainAPI>, val status: ProviderStatus, val assets: AssetManager?)
    companion object {
        // CloudStream's registries are process global. Serialize lifecycle changes across hosts.
        private val lock = Mutex()
        private val loaded = java.util.concurrent.ConcurrentHashMap<String, Loaded>()
    }
    private val preferences = this.context.getSharedPreferences("cloudstream_provider_state", Context.MODE_PRIVATE)
    private val recovery = this.context.getSharedPreferences("cloudstream_initialization_recovery", Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "cloudstream_extensions")
    private fun path(id: String): File {
        require(id.matches(Regex("[a-f0-9]{64}"))) { "Invalid extension identifier" }
        return File(directory, "$id.cs3")
    }
    fun providers(): List<Provider> = loaded.flatMap { (id, extension) ->
        extension.providers.map { api ->
            val key = providerId(id, api)
            Provider(key, id, api.name, preferences.getBoolean(key, true))
        }
    }
    private fun providerId(packageId: String, api: MainAPI): String {
        val identity = "${api.javaClass.name}|${api.name}|${api.mainUrl}"
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$packageId/$digest"
    }
    fun enable(id: String, enabled: Boolean) { preferences.edit().putBoolean(id, enabled).apply() }
    internal fun api(id: String): MainAPI {
        require(preferences.getBoolean(id, true)) { "CloudStream provider is disabled" }
        return loaded[id.substringBefore('/')]?.providers?.firstOrNull { providerId(id.substringBefore('/'), it) == id }
            ?: error("CloudStream provider is unavailable. Reload its extension in Settings.")
    }
    suspend fun inspect(packageId: String): ProviderStatus = withContext(Dispatchers.IO) {
        lock.withLock {
            loaded[packageId]?.let { return@withLock it.status }
            val file = path(packageId)
            if (!file.isFile) return@withLock ProviderStatus(packageId, "", State.MISSING, "Extension not installed")
            if (recovery.getBoolean(packageId, false)) return@withLock ProviderStatus(packageId, "", State.FAILED,
                "Previous initialization was interrupted. Reload to retry, or uninstall this extension.")
            var className = ""
            var plugin: BasePlugin? = null
            var assets: AssetManager? = null
            try {
                val manifest = CloudStreamPackage.validate(file)
                className = manifest.className
                // Android 14+ refuses dynamically loaded writable code.
                check(file.setReadOnly()) { "Could not make extension code read-only" }
                CloudStreamPlayback.initialize()
                setContext(WeakReference<Any>(context))
                check(recovery.edit().putBoolean(packageId, true).commit()) { "Could not save initialization recovery state" }
                val loader = DexClassLoader(file.absolutePath, context.codeCacheDir.absolutePath, null, context.classLoader)
                val type = loader.loadClass(className)
                require(BasePlugin::class.java.isAssignableFrom(type)) { "Plugin must extend CloudStream BasePlugin" }
                plugin = type.getDeclaredConstructor().newInstance() as BasePlugin
                plugin.filename = file.absolutePath
                if (manifest.resources) {
                    require(plugin is Plugin) { "Resource extensions must extend Android Plugin" }
                    // Match the pinned upstream resource loader: .cs3 need not contain AndroidManifest.xml.
                    assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                    val cookie = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                        .invoke(assets, file.absolutePath) as Int
                    require(cookie != 0) { "Extension resource path could not be loaded" }
                    @Suppress("DEPRECATION")
                    val resources = Resources(assets, context.resources.displayMetrics, context.resources.configuration)
                    plugin.resources = resources
                }
                if (plugin is Plugin) withContext(Dispatchers.Main) { plugin.load(context) } else plugin.load()
                val registered = synchronized(APIHolder.allProviders) { APIHolder.allProviders.filter { it.sourcePlugin == file.absolutePath } }
                registered.forEach { it.init() }
                val count = synchronized(extractorApis) { extractorApis.count { it.sourcePlugin == file.absolutePath } }
                require(registered.isNotEmpty() || count > 0) { "Plugin load completed without registering providers or extractors" }
                val status = ProviderStatus(packageId, className, State.ACTIVE, "Initialized: ${registered.size} providers, $count extractors")
                loaded[packageId] = Loaded(plugin, loader, registered, status, assets)
                recovery.edit().remove(packageId).commit()
                status
            } catch (e: Throwable) {
                if (e is VirtualMachineError || e is ThreadDeath) throw e
                try { plugin?.beforeUnload() } catch (cleanupError: Throwable) {
                    if (cleanupError is VirtualMachineError || cleanupError is ThreadDeath) throw cleanupError
                }
                cleanup(file.absolutePath)
                assets?.close()
                recovery.edit().remove(packageId).commit()
                if (e is CancellationException) throw e
                ProviderStatus(packageId, className, if (e is LinkageError) State.INCOMPATIBLE else State.FAILED,
                    "${e.javaClass.simpleName}: " + (if (plugin == null && e is IllegalArgumentException) "${e.message}. " else "") + "initialization failed against CloudStream v4.5.4. Check extension requirements or update the extension.")
            }
        }
    }
    private fun cleanup(filename: String) {
        val own = synchronized(APIHolder.allProviders) {
            APIHolder.allProviders.filter { it.sourcePlugin == filename }.also { APIHolder.allProviders.removeAll(it.toSet()) }
        }
        own.forEach(APIHolder::removePluginMapping)
        synchronized(extractorApis) { extractorApis.removeAll { it.sourcePlugin == filename } }
        val actions = com.lagradost.cloudstream3.actions.VideoClickActionHolder.allVideoClickActions
        synchronized(actions) { actions.removeAll { it.sourcePlugin == filename } }
    }
    suspend fun unload(packageId: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            recovery.edit().remove(packageId).commit()
            val extension = loaded.remove(packageId)
            try { extension?.plugin?.beforeUnload() }
            catch (e: Throwable) { if (e is VirtualMachineError || e is ThreadDeath) throw e }
            finally { cleanup(path(packageId).absolutePath); CloudStreamPlayback.remove(packageId); extension?.assets?.close() }
        }
    }
    suspend fun unloadAll() { loaded.keys.toList().forEach { unload(it) } }
}
