package com.mutinymedia.android.data

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Runs explicitly trusted CloudStream extensions in the app process. */
class CloudStreamHost(context: Context) {
    private val context = context.applicationContext
    private val installer = CloudStreamInstaller(this.context)
    private val preferences = this.context.getSharedPreferences("cloudstream_lifecycle", Context.MODE_PRIVATE)
    private val active = mutableMapOf<String, Active>()
    private val failures = mutableMapOf<String, ProviderStatus>()

    enum class State { MISSING, DISABLED, ACTIVE, FAILED, INCOMPATIBLE }

    data class ProviderStatus(
        val packageId: String,
        val pluginClass: String,
        val state: State,
        val detail: String,
        val name: String = pluginClass,
        val version: Int = 0,
        val providers: List<String> = emptyList(),
        val hasSettings: Boolean = false,
    )

    internal data class Provider(val key: String, val api: MainAPI)

    private data class Active(
        val plugin: BasePlugin,
        val providers: List<Provider>,
        val manifest: CloudStreamInstaller.Manifest,
        val releaseResources: () -> Unit,
    )

    suspend fun restore(): List<ProviderStatus> = locked {
        installer.installed().map { id ->
            val file = installer.file(id)
            recoverBackup(file, backupOf(file))
            when {
                preferences.getBoolean("loading:$id", false) -> {
                    check(preferences.edit().remove("loading:$id").putBoolean("enabled:$id", false).commit())
                    inspectPackage(id).copy(state = State.FAILED, detail = "Initialization was interrupted. Use Reload to retry.")
                        .also { failures[id] = it }
                }
                preferences.getBoolean("enabled:$id", false) -> activate(id)
                else -> inspectPackage(id)
            }
        }
    }

    suspend fun inspect(id: String): ProviderStatus = locked { inspectPackage(id) }

    suspend fun install(plugin: CloudStreamPlugin): ProviderStatus = locked {
        val id = CloudStreamInstaller.packageId(plugin.downloadUrl)
        val destination = installer.file(id)
        val backup = backupOf(destination)
        recoverBackup(destination, backup)
        val staged = installer.download(plugin)
        val wasEnabled = preferences.getBoolean("enabled:$id", false)
        try {
            val updating = destination.exists()
            if (updating) Files.createLink(backup.toPath(), destination.toPath())
            unload(id)
            move(staged, destination)
            failures.remove(id)
            val status = if (!updating || wasEnabled) activate(id) else inspectPackage(id)
            if (updating && status.state in setOf(State.FAILED, State.INCOMPATIBLE)) {
                move(backup, destination)
                if (wasEnabled) activate(id) else failures.remove(id)
                error("Update rejected (${status.detail}). Previous package restored.")
            }
            check(!backup.exists() || backup.delete()) { "Could not finish updating extension" }
            status
        } finally {
            staged.delete()
            if (backup.exists()) {
                withContext(NonCancellable) {
                    unload(id)
                    move(backup, destination)
                    if (wasEnabled) activate(id)
                }
            }
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean): ProviderStatus = locked {
        if (enabled) activate(id) else {
            installer.file(id)
            check(preferences.edit().putBoolean("enabled:$id", false).remove("loading:$id").commit())
            unload(id)
            failures.remove(id)
            inspectPackage(id)
        }
    }

    suspend fun reload(id: String): ProviderStatus = locked {
        installer.file(id)
        unload(id)
        activate(id)
    }

    suspend fun uninstall(id: String) = locked {
        val file = installer.file(id)
        val backup = backupOf(file)
        check(!backup.exists() || backup.delete()) { "Could not remove backup extension" }
        check(!file.exists() || file.delete()) { "Could not remove extension" }
        unload(id)
        failures.remove(id)
        check(preferences.edit().remove("enabled:$id").remove("loading:$id").commit())
    }

    suspend fun statuses(): List<ProviderStatus> = locked { installer.installed().map(::inspectPackage) }

    suspend fun openSettings(id: String, activity: Context) = locked {
        val settings = (active[id]?.plugin as? Plugin)?.openSettings ?: error("No extension settings available")
        val failure = withContext(Dispatchers.Main.immediate) { pluginCall { settings(activity); true } }
        check(failure == true) { "Extension settings could not open" }
    }

    internal suspend fun <T> withProviders(block: suspend (List<Provider>) -> T): T =
        locked { block(active.values.flatMap { it.providers }) }

    private fun inspectPackage(id: String): ProviderStatus {
        val file = installer.file(id)
        if (!file.isFile) return ProviderStatus(id, "", State.MISSING, "Extension not installed")
        active[id]?.let { return activeStatus(id, it) }
        failures[id]?.let { return it }
        return try {
            val manifest = installer.manifest(file)
            status(id, manifest, State.DISABLED, "Enable to initialize this extension")
        } catch (error: Exception) {
            ProviderStatus(id, "", State.FAILED, error.message ?: "Invalid extension package")
        }
    }

    private fun activate(id: String): ProviderStatus {
        active[id]?.let { return activeStatus(id, it) }
        failures.remove(id)
        val inspected = inspectPackage(id)
        if (inspected.state != State.DISABLED) return inspected
        val file = installer.file(id)
        val manifest = installer.manifest(file)
        check(preferences.edit().putBoolean("loading:$id", true).commit()) { "Could not persist extension recovery state" }
        var plugin: BasePlugin? = null
        var releaseResources: () -> Unit = {}
        val result = try {
            CloudStreamNetwork.initialize(context)
            check(file.setReadOnly()) { "Could not secure extension bytecode as read-only" }
            val loader = DexClassLoader(file.absolutePath, context.codeCacheDir.absolutePath, null, context.classLoader)
            val entry = loader.loadClass(manifest.className)
            check(entry.classLoader === loader) { "Entry point must be defined by the extension" }
            val instance = entry.asSubclass(BasePlugin::class.java).getConstructor().newInstance()
            plugin = instance
            instance.filename = file.absolutePath
            if (instance is Plugin) {
                if (manifest.requiresResources) {
                    val resources = pluginResources(file)
                    instance.resources = resources.first
                    releaseResources = resources.second
                }
                instance.load(context)
            } else {
                check(!manifest.requiresResources) { "Resource-bearing extensions must extend Android Plugin" }
                instance.load()
            }
            val providers = synchronized(APIHolder.allProviders) {
                APIHolder.allProviders.filter { it.sourcePlugin == file.absolutePath }
            }.map { api ->
                api.init()
                Provider("$id.${CloudStreamInstaller.digest(api.javaClass.name + ":" + api.name)}", api)
            }
            val extractors = synchronized(extractorApis) { extractorApis.count { it.sourcePlugin == file.absolutePath } }
            check(providers.isNotEmpty() || extractors > 0) { "Extension registered no providers or extractors" }
            val loaded = Active(instance, providers, manifest, releaseResources)
            active[id] = loaded
            activeStatus(id, loaded)
        } catch (error: Throwable) {
            active.remove(id)
            removeRegistrations(file.absolutePath, plugin, releaseResources)
            when (error) {
                is CancellationException, is VirtualMachineError, is ThreadDeath -> throw error
                is LinkageError, is ClassCastException, is ClassNotFoundException, is NoSuchMethodException ->
                    status(id, manifest, State.INCOMPATIBLE, "CloudStream v4.5.4 API mismatch (${error.javaClass.simpleName})")
                else -> status(id, manifest, State.FAILED, "Initialization failed (${error.cause?.javaClass?.simpleName ?: error.javaClass.simpleName})")
            }
        }
        check(preferences.edit().remove("loading:$id").putBoolean("enabled:$id", result.state == State.ACTIVE).commit())
        if (result.state != State.ACTIVE) failures[id] = result
        return result
    }

    private fun unload(id: String) {
        val loaded = active.remove(id) ?: return
        removeRegistrations(installer.file(id).absolutePath, loaded.plugin, loaded.releaseResources)
    }

    private fun removeRegistrations(path: String, plugin: BasePlugin?, releaseResources: () -> Unit) {
        try {
            pluginCall { plugin?.beforeUnload() }
        } finally {
            synchronized(APIHolder.allProviders) {
                APIHolder.allProviders.filter { it.sourcePlugin == path }.forEach(APIHolder::removePluginMapping)
                APIHolder.allProviders.removeAll { it.sourcePlugin == path }
            }
            synchronized(extractorApis) { extractorApis.removeAll { it.sourcePlugin == path } }
            val actions = VideoClickActionHolder.allVideoClickActions
            synchronized(actions) { actions.removeAll { it.sourcePlugin == path } }
            (plugin as? Plugin)?.resources = null
            pluginCall(releaseResources)
        }
    }

    private fun activeStatus(id: String, loaded: Active): ProviderStatus {
        val names = loaded.providers.map { it.api.name }
        return status(id, loaded.manifest, State.ACTIVE,
            if (names.isEmpty()) "Extractor extension active" else "${names.size} registered provider(s)")
            .copy(providers = names, hasSettings = (loaded.plugin as? Plugin)?.openSettings != null)
    }

    private fun status(id: String, manifest: CloudStreamInstaller.Manifest, state: State, detail: String) =
        ProviderStatus(id, manifest.className, state, detail, manifest.name, manifest.version)

    private fun backupOf(file: File) = File(file.parentFile, ".${file.nameWithoutExtension}.backup")

    private fun recoverBackup(file: File, backup: File) {
        if (backup.exists()) move(backup, file)
    }

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    }

    private fun pluginResources(file: File): Pair<Resources, () -> Unit> {
        if (Build.VERSION.SDK_INT >= 30) return pluginResources30(file)
        @Suppress("DEPRECATION")
        val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
        val cookie = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            .invoke(assets, file.absolutePath) as Int
        if (cookie == 0) {
            assets.close()
            error("Could not attach extension resources")
        }
        @Suppress("DEPRECATION")
        return Resources(assets, context.resources.displayMetrics, context.resources.configuration) to { assets.close() }
    }

    @RequiresApi(30)
    private fun pluginResources30(file: File): Pair<Resources, () -> Unit> {
        val provider = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use {
            ResourcesProvider.loadFromApk(it)
        }
        val loader = ResourcesLoader()
        val resources = context.createConfigurationContext(Configuration(context.resources.configuration)).resources
        try {
            loader.addProvider(provider)
            resources.addLoaders(loader)
        } catch (error: Exception) {
            loader.clearProviders()
            provider.close()
            throw error
        }
        return resources to {
            resources.removeLoaders(loader)
            loader.clearProviders()
            provider.close()
        }
    }

    private suspend fun <T> locked(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { runtimeMutex.withLock { block() } }

    companion object {
        private val runtimeMutex = Mutex()
    }
}

internal inline fun <T> pluginCall(block: () -> T): T? = try {
    block()
} catch (error: Throwable) {
    when (error) {
        is CancellationException, is VirtualMachineError, is ThreadDeath -> throw error
        else -> null
    }
}
