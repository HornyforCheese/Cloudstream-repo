package com.mutinymedia.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mutinymedia.android.data.AccountRepository
import com.mutinymedia.android.data.AccountImport
import com.mutinymedia.android.data.AndroidServices
import com.mutinymedia.android.data.CloudStreamRegistry
import com.mutinymedia.android.data.OpenRouterClient
import com.mutinymedia.android.data.SecretsStore
import com.mutinymedia.android.data.SettingsStore
import com.mutinymedia.android.data.StremioClient
import com.mutinymedia.android.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MutinyViewModel(application: Application) : AndroidViewModel(application) {
    val store = SettingsStore(application)
    val secrets = SecretsStore(application)
    val accounts = AccountRepository(secrets)
    val android = AndroidServices(application)
    val stremio = StremioClient()
    private val cloudstream = CloudStreamRegistry()
    private val cloudInstaller = com.mutinymedia.android.data.CloudStreamInstaller(application)
    private val cloudHost = com.mutinymedia.android.data.CloudStreamHost(application)
    val cloudHostStatuses = MutableStateFlow<List<com.mutinymedia.android.data.CloudStreamHost.ProviderStatus>>(emptyList())
    val installedCloudPlugins = MutableStateFlow(cloudInstaller.registeredPackages().keys)
    val cloudStreamPlugins = MutableStateFlow<List<com.mutinymedia.android.data.CloudStreamPlugin>>(emptyList())
    private val openrouter = OpenRouterClient()
    val freeOpenRouterModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    private val realDebrid = com.mutinymedia.android.data.RealDebridClient()
    val debridAccount = MutableStateFlow<String?>(null)
    val settings: StateFlow<UserSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val addons = MutableStateFlow<List<Addon>>(emptyList())
    val watchlist: StateFlow<List<MediaItem>> = store.watchlist.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val catalog = MutableStateFlow<List<MediaItem>>(emptyList())
    val episodes = MutableStateFlow<List<Episode>>(emptyList())
    val selectedEpisode = MutableStateFlow<String?>(null)
    val streams = MutableStateFlow<List<StreamOption>>(emptyList())
    val currentItem = MutableStateFlow<MediaItem?>(null)
    val activeStream = MutableStateFlow<StreamOption?>(null)
    val chat = MutableStateFlow<List<ChatLine>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val catalogSearch = MutableStateFlow<CatalogSearchResult?>(null)

    private val operationMutex = Mutex()
    private val providerSlots = Semaphore(4)
    private val providerWarnings = MutableStateFlow<String?>(null)
    private suspend fun <T> queryProviders(block: suspend (Addon) -> List<T>): List<T> = coroutineScope {
        val providers = addons.value
        val checks = providers.map { addon -> async {
            providerSlots.withPermit {
                try { block(addon) to null }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { null to addonFailure(addon.baseUrl, e) }
            }
        } }.awaitAll()
        val failures = checks.mapNotNull { it.second }
        if (providers.isNotEmpty() && failures.size == providers.size) {
            error("All ${providers.size} providers failed: " + failures.take(3).joinToString("; ") +
                (if (failures.size > 3) "; +${failures.size - 3} more failed" else ""))
        }
        providerWarnings.value = if (failures.isNotEmpty()) {
            "${providers.size - failures.size}/${providers.size} providers succeeded. " +
                failures.take(3).joinToString("; ") +
                (if (failures.size > 3) "; +${failures.size - 3} more failed" else "")
        } else null
        if (providerWarnings.value != null) message.value = providerWarnings.value
        checks.mapNotNull { it.first }.flatten()
    }

    private suspend fun <T> providerResult(block: suspend () -> T): T? =
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val reason = when (e) {
                is java.net.SocketTimeoutException -> "timeout"
                is java.net.UnknownHostException -> "DNS lookup failed"
                else -> e.javaClass.simpleName
            }
            message.value = "Metadata or episode lookup failed ($reason). Other providers will be tried."
            null
        }

    /** Diagnostic contains only a hostname, never configured paths or credentials. */
    private fun addonFailure(url: String, error: Exception): String {
        val host = runCatching { java.net.URI(url.replace(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://")).host }
            .getOrNull()?.take(80) ?: "invalid URL"
        val reason = when (error) {
            is java.net.SocketTimeoutException -> "timeout"
            is java.net.UnknownHostException -> "DNS lookup failed"
            is javax.net.ssl.SSLException -> "TLS error"
            is java.net.ConnectException -> "connection refused"
            is IllegalArgumentException -> "invalid URL or manifest"
            else -> "request failed (" + error.javaClass.simpleName + ")"
        }
        return "$host: $reason"
    }

    init {
        viewModelScope.launch {
            store.settings.map { it.addonUrls }.distinctUntilChanged().collectLatest { urls ->
                val checks = coroutineScope {
                    urls.map { url -> async {
                        providerSlots.withPermit {
                            try { stremio.inspectAddon(url) to null }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { null to addonFailure(url, e) }
                        }
                    } }.awaitAll()
                }
                val verified = checks.mapNotNull { it.first }
                val failures = checks.mapNotNull { it.second }
                // Keep previously verified providers during temporary outages, but respect removals.
                val previous = addons.value.associateBy { it.baseUrl }
                val result = urls.mapNotNull { url ->
                    val base = runCatching { stremio.normalizeBase(url) }.getOrNull()
                    verified.firstOrNull { it.baseUrl == base } ?: previous[base]
                }.distinctBy { it.baseUrl }
                if (addons.value.map { it.baseUrl } != result.map { it.baseUrl }) {
                    catalog.value = emptyList()
                    catalogSearch.value = null
                }
                addons.value = result
                if (failures.isNotEmpty()) message.value = "Stremio: ${verified.size}/${urls.size} addons verified; previously verified providers retained during outages. " + failures.take(3).joinToString("; ") + if (failures.size > 3) "; +${failures.size - 3} more failed" else ""
            }
        }
        // Restore sessions first, then import add-ons only when verification succeeds.
        // Import is additive; a failed remote request never clears locally saved add-ons.
        viewModelScope.launch {
            accounts.restoreStremio()
            if (accounts.stremioStatus.value.connected) {
                try { importAccount(accounts.pullStremio(), "Stremio") }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message.value = "Stremio signed in; addon sync failed. Retry import in Settings." }
            }
        }
        viewModelScope.launch {
            accounts.restoreNuvio()
            if (accounts.nuvioStatus.value.connected) {
                try { importAccount(accounts.pullNuvio(), "Nuvio") }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message.value = "Nuvio signed in; select or unlock your profile, then retry import." }
            }
        }
    }
    private suspend fun importAccount(data: AccountImport, label: String) {
        store.importAccountData(data.addons, data.items)
        message.value = data.warning ?: "$label imported: ${data.addons.size} addons and ${data.items.size} saved titles"
    }
    fun loginStremio(email: String, password: String) = runJob {
        accounts.loginStremio(email,password)
        importAccount(accounts.pullStremio(),"Stremio")
    }
    fun connectStremioKey(key: String) = runJob { accounts.importStremioKey(key);importAccount(accounts.pullStremio(),"Stremio") }
    fun syncStremio() = runJob { importAccount(accounts.pullStremio(),"Stremio") }
    fun verifyStremio() = runJob { accounts.restoreStremio() }
    fun logoutStremio() = runJob { val revoked=accounts.logoutStremio();message.value=if(revoked) "Signed out of Stremio; local titles and addons retained" else "Stremio sign-in cleared locally; remote revocation could not be confirmed" }
    fun loginNuvio(server: String,email: String,password: String) = runJob {
        accounts.loginNuvio(server,email,password)
        message.value="Signed in to Nuvio. Select a profile to synchronize its addons."
    }
    fun selectNuvioProfile(index: Int,pin: String) = runJob {
        accounts.selectNuvioProfile(index,pin)
        importAccount(accounts.pullNuvio(),"Nuvio")
    }
    fun syncNuvio() = runJob { importAccount(accounts.pullNuvio(),"Nuvio") }
    fun verifyNuvio() = runJob { accounts.restoreNuvio() }
    fun logoutNuvio() = runJob { val revoked=accounts.logoutNuvio();message.value=if(revoked) "Signed out of Nuvio; local titles and addons retained" else "Nuvio sign-in cleared locally; remote revocation could not be confirmed" }
    fun saveToWatchlist(item: MediaItem) = runJob { store.addToWatchlist(item); message.value = "Saved to local watchlist" }
    fun removeFromWatchlist(item: MediaItem) = runJob { store.removeFromWatchlist(item); message.value = "Removed from watchlist" }
    fun report(text: String?) { message.value = text }
    fun runJob(block: suspend () -> Unit) {
        viewModelScope.launch {
            operationMutex.withLock {
                busy.value = true
                try { block() } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message.value = e.message ?: "Operation failed" }
                finally { busy.value = false }
            }
        }
    }
    fun addAddon(url: String) = runJob {
        val addon = stremio.inspectAddon(url)
        store.addAddon(addon.baseUrl)
        message.value = "Added ${addon.title}"
    }
    fun refreshAddons() = runJob {
        val urls = settings.value.addonUrls
        val checks = urls.map { url ->
            try { stremio.inspectAddon(url) to null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { null to addonFailure(url, e) }
        }
        val result = checks.mapNotNull { it.first }
        val failures = checks.mapNotNull { it.second }
        val previous = addons.value.associateBy { it.baseUrl }
        addons.value = urls.mapNotNull { url ->
            val base = runCatching { stremio.normalizeBase(url) }.getOrNull()
            result.firstOrNull { it.baseUrl == base } ?: previous[base]
        }.distinctBy { it.baseUrl }
        message.value = "Verified ${result.size}/${urls.size} saved addons" +
            if (failures.isNotEmpty()) ". " + failures.take(3).joinToString("; ") +
                (if (failures.size > 3) "; +${failures.size - 3} more failed" else "") else ""
    }
    fun removeAddon(url: String) = runJob { store.removeAddon(url); message.value = "Addon removed" }
    fun addExtensionRepo(url: String) = runJob {
        val label = cloudstream.inspectRepository(url)
        store.addRepository(url.trim())
        message.value = "Saved CloudStream repository: $label (discovery only)"
    }
    fun installCloudStreamPlugin(plugin: com.mutinymedia.android.data.CloudStreamPlugin) = runJob {
        cloudInstaller.install(plugin)
        installedCloudPlugins.value = cloudInstaller.registeredPackages().keys
        cloudHostStatuses.value = installedCloudPlugins.value.map { cloudHost.inspect(it) }
        message.value = "Downloaded ${plugin.name}. Execution requires CloudStream runtime compatibility."
    }
    /** Direct .cs3 URLs can be pasted without first subscribing to a repository. */
    fun installCloudStreamUrl(url: String) = runJob {
        val validated = com.mutinymedia.android.data.Network.validateHttps(url)
        require(java.net.URI(validated).path.endsWith(".cs3", ignoreCase = true)) {
            "Paste a direct HTTPS .cs3 extension URL, or add a CloudStream repository URL above"
        }
        val name = java.net.URI(validated).path.substringAfterLast('/').removeSuffix(".cs3")
        val plugin = com.mutinymedia.android.data.CloudStreamPlugin(name, "Direct URL extension", 0, validated, validated)
        cloudInstaller.install(plugin)
        installedCloudPlugins.value = cloudInstaller.registeredPackages().keys
        cloudHostStatuses.value = installedCloudPlugins.value.map { cloudHost.inspect(it) }
        message.value = "Extension downloaded. CloudStream host compatibility is required before it can search."
    }
    fun uninstallCloudStreamPlugin(plugin: com.mutinymedia.android.data.CloudStreamPlugin) = runJob {
        cloudInstaller.uninstall(plugin)
        installedCloudPlugins.value = cloudInstaller.registeredPackages().keys
        cloudHost.unloadAll()
        cloudHostStatuses.value = installedCloudPlugins.value.map { cloudHost.inspect(it) }
        message.value = "Removed ${plugin.name}"
    }
    fun inspectCloudStreamHost() = runJob {
        cloudHostStatuses.value = installedCloudPlugins.value.map { cloudHost.inspect(it) }
        message.value = "Checked ${cloudHostStatuses.value.size} installed CloudStream packages for host compatibility"
    }
    override fun onCleared() {
        cloudHost.unloadAll()
        super.onCleared()
    }
    fun removeExtensionRepo(url: String) = runJob { store.removeRepository(url); cloudStreamPlugins.value = cloudStreamPlugins.value.filterNot { it.repository == url } }
    fun discoverCloudStreamPlugins() = runJob {
        val repos = settings.value.extensionRepositories
        require(repos.isNotEmpty()) { "Add a CloudStream repository first" }
        val discovered = mutableListOf<com.mutinymedia.android.data.CloudStreamPlugin>()
        val failures = mutableListOf<String>()
        repos.forEach { repo ->
            try { discovered += cloudstream.discover(repo) }
            catch (e: Exception) { failures += "$repo: ${e.message}" }
        }
        cloudStreamPlugins.value = discovered.distinctBy { it.downloadUrl }
        message.value = "Discovered ${cloudStreamPlugins.value.size} CloudStream plugins" +
            if (failures.isNotEmpty()) " (${failures.size} repositories failed)" else ""
    }
    fun browseCatalogs() = runJob {
        catalog.value = loadProviderCatalogs()
        if (catalog.value.isEmpty()) message.value = "No titles found in your configured catalogs."
    }

    private suspend fun loadProviderCatalogs(): List<MediaItem> =
        queryProviders { addon -> stremio.catalog(addon) }.distinctBy { it.type to it.id }

    private suspend fun savedCatalogItems(): List<MediaItem> {
        if (catalog.value.isEmpty() && addons.value.any { it.supportsCatalog }) {
            catalog.value = loadProviderCatalogs()
        }
        return (catalog.value + watchlist.value).distinctBy { it.type to it.id }
    }

    fun searchTitles(query: String) = runJob {
        val term = query.trim()
        if (term.isEmpty()) {
            catalogSearch.value = null
            return@runJob
        }
        catalogSearch.value = CatalogSearchResult(term, emptyList())
        val saved = (catalog.value + watchlist.value).filter { it.title.contains(term, ignoreCase = true) }
        val fromAddons = queryProviders { addon -> stremio.catalog(addon, term) }
        val publicMetadata = providerResult {
            stremio.catalog(Addon("com.linvo.cinemeta", "Cinemeta", "https://v3-cinemeta.strem.io", true, false, true), term)
        }.orEmpty()
        catalogSearch.value = CatalogSearchResult(term, (fromAddons + saved + publicMetadata).distinctBy { it.type to it.id })
    }

    fun searchCatalogWithAi(query: String, filters: CatalogFilters) = runJob {
        val term = query.trim()
        require(term.isNotEmpty()) { "Describe the titles you want to find." }
        val key = secrets.readOpenRouter().orEmpty()
        require(key.isNotBlank()) { "Connect OpenRouter in Settings → Ask AI before using AI search." }
        val saved = savedCatalogItems()
        require(saved.isNotEmpty()) { "Add a catalog under Settings → Integrations or save titles to your library first." }
        val eligible = saved.filter(filters::matches)
        val candidates = eligible.take(200)
        catalogSearch.value = CatalogSearchResult(term, emptyList(), candidates.size, eligible.size)
        if (candidates.isEmpty()) return@runJob
        val matches = openrouter.matchCatalog(key, settings.value.openRouterModel, term, candidates)
        catalogSearch.value = CatalogSearchResult(term, matches, candidates.size, eligible.size)
    }

    fun clearCatalogSearch() { catalogSearch.value = null }

    fun lookup(type: String, id: String, anime: Boolean) = runJob {
        if (id.isBlank()) error("Enter a Stremio content ID, such as tt1254207")
        val media = MediaItem(id.trim(), type, id.trim(), isAnime = anime)
        requestStreams(media)
    }
    fun loadStreams(media: MediaItem) = runJob { requestStreams(media) }
    private suspend fun requestStreams(media: MediaItem) {
        streams.value = emptyList()
        episodes.value = emptyList()
        selectedEpisode.value = null
        currentItem.value = media
        val metadataSources = addons.value.filter { it.supportsMeta } +
            Addon("com.linvo.cinemeta", "Cinemeta", "https://v3-cinemeta.strem.io", true, false, true)
        // Prefer a configured metadata provider, then use Cinemeta as a public fallback.
        for (source in metadataSources) {
            val enriched = providerResult { stremio.metadata(source, currentItem.value!!) } ?: continue
            currentItem.value = enriched
            if (enriched.poster != null && enriched.description != null && enriched.title != media.id) break
        }
        if (addons.value.none { it.supportsStreams }) {
            message.value = "No stream-capable Stremio addon is connected. Add or refresh a streaming provider in Settings → Integrations."
            return
        }
        if (media.type in listOf("series", "anime") && !Regex(":\\d+:\\d+$").containsMatchIn(media.id)) {
            val metadataSources = addons.value.filter { it.supportsMeta } +
                com.mutinymedia.android.domain.Addon("com.linvo.cinemeta", "Cinemeta", "https://v3-cinemeta.strem.io", true, false, true)
            episodes.value = metadataSources.flatMap { addon -> providerResult { stremio.episodes(addon, media) }.orEmpty() }
                .distinctBy { it.id }.sortedWith(compareBy({ it.season }, { it.number }))
            if (episodes.value.isNotEmpty()) {
                message.value = "Select an episode to find streams"
                return
            }
            message.value = "No episodes were returned for this series. Check the metadata provider or try another title."
            return
        }
        fetchStreams(media)
    }
    fun selectEpisode(episode: Episode) = runJob {
        val media = currentItem.value ?: return@runJob
        selectedEpisode.value = episode.id
        streams.value = emptyList()
        fetchStreams(media.copy(id = episode.id))
    }
    private suspend fun fetchStreams(media: MediaItem) {
        val found = queryProviders { addon -> stremio.streams(addon, media.type, media.id) }
            .distinctBy { Triple(it.url ?: it.id, it.headers, it.externalUrl) }
        // Keep raw provider results so the UI can change language and bandwidth filters without another request.
        streams.value = found
        val matched = StreamFilters.filterAnime(found, currentItem.value?.isAnime == true, settings.value.animeMode)
        message.value = when {
            found.isEmpty() -> listOfNotNull("No streams returned by connected providers", providerWarnings.value).joinToString(". ")
            matched.isEmpty() -> "No streams match the selected anime language filter. Switch to All in Settings → Anime."
            found.none { it.playable } -> "Sources were found, but none is a directly playable HTTPS stream. Torrent playback is not supported yet."
            else -> providerWarnings.value
        }
    }
    fun saveRealDebridKey(token: String) = runJob {
        if (token.isBlank()) {
            secrets.saveSecret("debrid_real_debrid", "")
            debridAccount.value = null
            message.value = "Real-Debrid API key removed"
        } else {
            val username = realDebrid.verify(token.trim())
            secrets.saveSecret("debrid_real_debrid", token.trim())
            debridAccount.value = username
            message.value = "Real-Debrid connected as $username"
        }
    }
    fun verifyRealDebrid() = runJob {
        val token = secrets.readSecret("debrid_real_debrid") ?: error("No Real-Debrid key saved")
        debridAccount.value = realDebrid.verify(token)
        message.value = "Real-Debrid connection verified"
    }
    fun playViaRealDebrid(stream: StreamOption) = runJob {
        val token = secrets.readSecret("debrid_real_debrid") ?: error("Add a Real-Debrid key under Settings → Integrations → Debrid Services")
        val link = stream.url ?: error("This stream does not provide a supported hoster link")
        val direct = realDebrid.resolve(token, link)
        activeStream.value = stream.copy(url = direct, provider = "Real-Debrid")
    }
    fun play(stream: StreamOption) {
        if (!stream.playable) { message.value = "This source requires a torrent/debrid engine that is not integrated yet"; return }
        activeStream.value = stream
    }
    fun playTestVideo() {
        currentItem.value = MediaItem("demo", "movie", "Big Buck Bunny • Creative Commons test video")
        activeStream.value = StreamOption("sample", "Big Buck Bunny", "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4", "Public sample")
    }
    fun download(url: String, filename: String) = runJob {
        val id = android.downloadFile(url, filename)
        message.value = "Android DownloadManager queued job #$id"
    }
    fun refreshOpenRouterModels() = runJob {
        val models = openrouter.listFreeModels()
        check(models.isNotEmpty()) { "OpenRouter returned no free text models. Try refreshing again later." }
        freeOpenRouterModels.value = models
    }

    fun saveOpenRouterKey(value: String) {
        try { secrets.saveOpenRouter(value); message.value = if (value.isBlank()) "OpenRouter key cleared" else "OpenRouter key encrypted and saved" }
        catch (e: Exception) { message.value = "Unable to store key: ${e.message}" }
    }
    fun sendMessage(text: String) = runJob {
        if (text.isBlank()) return@runJob
        val key = secrets.readOpenRouter().orEmpty()
        require(key.isNotBlank()) { "Connect OpenRouter in Settings first" }
        val updated = chat.value.takeLast(100) + ChatLine(true, text.trim())
        chat.value = updated
        val response = openrouter.complete(key, settings.value.openRouterModel, updated)
        chat.value = updated + ChatLine(false, response)
    }
}
