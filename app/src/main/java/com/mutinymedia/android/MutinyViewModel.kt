package com.mutinymedia.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mutinymedia.android.data.AccountRepository
import com.mutinymedia.android.data.AccountImport
import com.mutinymedia.android.data.AndroidServices
import android.content.Context
import com.mutinymedia.android.data.CloudStreamHost
import com.mutinymedia.android.data.CloudStreamInstaller
import com.mutinymedia.android.data.CloudStreamPlugin
import com.mutinymedia.android.data.CloudStreamProviderClient
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
    private val cloudHost = (application as MutinyApplication).cloudStreamHost
    private val cloudClient = CloudStreamProviderClient(cloudHost)
    val cloudHostStatuses = MutableStateFlow<List<CloudStreamHost.ProviderStatus>>(emptyList())
    val cloudStreamPlugins = MutableStateFlow<List<CloudStreamPlugin>>(emptyList())
    private var cloudDetails: CloudStreamProviderClient.Details? = null
    private val openrouter = OpenRouterClient()
    val freeOpenRouterModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    private val realDebrid = com.mutinymedia.android.data.RealDebridClient()
    val debridAccount = MutableStateFlow<String?>(null)
    val settings: StateFlow<UserSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val addons = MutableStateFlow<List<Addon>>(emptyList())
    val watchlist: StateFlow<List<MediaItem>> = store.watchlist.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val collections = store.collections.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val catalog = MutableStateFlow<List<MediaItem>>(emptyList())
    val episodes = MutableStateFlow<List<Episode>>(emptyList())
    val selectedEpisode = MutableStateFlow<String?>(null)
    val streams = MutableStateFlow<List<StreamOption>>(emptyList())
    val currentItem = MutableStateFlow<MediaItem?>(null)
    val activeStream = MutableStateFlow<StreamOption?>(null)
    val playbackPositions = store.playbackPositions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    var playbackStartMs = 0L
        private set
    var activePlaybackKey: String? = null
        private set
    val chat = MutableStateFlow<List<ChatLine>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val catalogSearch = MutableStateFlow<CatalogSearchResult?>(null)

    private val operationMutex = Mutex()
    private val providerSlots = Semaphore(4)
    private val providerWarnings = MutableStateFlow<String?>(null)
    private fun enabledAddons(): List<Addon> {
        val preferences = settings.value
        val priorities = preferences.addonUrls.withIndex().associate { it.value to it.index }
        return addons.value.filterNot { it.baseUrl in preferences.excludedAddonUrls }
            .sortedBy { priorities[it.baseUrl] ?: Int.MAX_VALUE }
    }

    private suspend fun <T> queryProviders(block: suspend (Addon) -> List<T>): List<T> = coroutineScope {
        val providers = enabledAddons()
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
            operationMutex.withLock {
                try {
                    cloudHostStatuses.value = cloudHost.restore()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    message.value = "Could not restore CloudStream extensions (${error.javaClass.simpleName})"
                }
            }
        }
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
    fun createCollection(name: String) = runJob { store.createCollection(name) }
    fun deleteCollection(name: String) = runJob { store.deleteCollection(name) }
    fun setCollectionTitle(name: String, item: MediaItem, saved: Boolean) = runJob {
        store.setCollectionTitle(name, item, saved)
        message.value = if (saved) "Saved to $name" else "Removed from $name"
    }
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
    fun setAddonEnabled(url: String, enabled: Boolean) = runJob {
        store.setAddonEnabled(url, enabled)
        catalog.value = emptyList()
        catalogSearch.value = null
    }
    fun addExtensionRepo(url: String) = runJob {
        val label = cloudstream.inspectRepository(url)
        store.addRepository(url.trim())
        message.value = "Saved CloudStream repository: $label"
    }
    fun installCloudStreamPlugin(plugin: CloudStreamPlugin) = runJob { installCloudExtension(plugin) }

    fun installCloudStreamUrl(url: String) = runJob {
        val validated = com.mutinymedia.android.data.Network.validateHttps(url)
        require(java.net.URI(validated).path.endsWith(".cs3", ignoreCase = true)) {
            "Paste a direct HTTPS .cs3 extension URL, or add a CloudStream repository URL above"
        }
        val name = java.net.URI(validated).path.substringAfterLast('/')
        installCloudExtension(CloudStreamPlugin(name, "Direct URL extension", 0, validated, validated))
    }

    private suspend fun installCloudExtension(plugin: CloudStreamPlugin) {
        try {
            val status = cloudHost.install(plugin)
            message.value = "${status.name}: ${status.detail}"
        } finally {
            cloudHostStatuses.value = cloudHost.statuses()
        }
    }

    fun uninstallCloudStreamPlugin(id: String) = runJob {
        cloudHost.uninstall(id)
        cloudHostStatuses.value = cloudHost.statuses()
        message.value = "CloudStream extension removed"
    }

    fun setCloudStreamEnabled(id: String, enabled: Boolean) = runJob {
        val status = cloudHost.setEnabled(id, enabled)
        cloudHostStatuses.value = cloudHost.statuses()
        message.value = "${status.name}: ${status.detail}"
    }

    fun reloadCloudStreamPlugin(id: String) = runJob {
        val status = cloudHost.reload(id)
        cloudHostStatuses.value = cloudHost.statuses()
        message.value = "${status.name}: ${status.detail}"
    }

    fun openCloudStreamSettings(id: String, context: Context) = runJob { cloudHost.openSettings(id, context) }

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

    private suspend fun loadProviderCatalogs(): List<MediaItem> = coroutineScope {
        val cloud = async { cloudClient.browse() }
        val stremio = async { providerResult { queryProviders { addon -> this@MutinyViewModel.stremio.catalog(addon) } }.orEmpty() }
        val results = cloud.await()
        showCloudWarnings(results)
        (stremio.await() + results.items).distinctBy { it.type to it.id }
    }

    private fun showCloudWarnings(results: CloudStreamProviderClient.Titles) {
        if (results.unavailableProviders > 0) {
            message.value = "${results.unavailableProviders} CloudStream provider(s) did not respond"
        }
    }

    private suspend fun savedCatalogItems(): List<MediaItem> {
        if (catalog.value.isEmpty() && (enabledAddons().any { it.supportsCatalog } || cloudHostStatuses.value.any { it.supportsCatalog })) {
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
        val results = coroutineScope {
            val cloudSearch = async { cloudClient.search(term) }
            val addonsSearch = async { providerResult { queryProviders { addon -> stremio.catalog(addon, term) } }.orEmpty() }
            val fallbackSearch = async {
                providerResult {
                    stremio.catalog(Addon("com.linvo.cinemeta", "Cinemeta", "https://v3-cinemeta.strem.io", true, false, true), term)
                }.orEmpty()
            }
            val fromCloud = cloudSearch.await()
            showCloudWarnings(fromCloud)
            addonsSearch.await() + fromCloud.items + saved + fallbackSearch.await()
        }
        catalogSearch.value = CatalogSearchResult(term, results.distinctBy { it.type to it.id })
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
        cloudDetails = null
        if (CloudStreamProviderClient.owns(media)) {
            val details = cloudClient.details(media)
            cloudDetails = details
            currentItem.value = details.item
            episodes.value = details.episodes
            when {
                details.episodes.isNotEmpty() -> message.value = "Select an episode to find streams"
                details.movieData != null -> showStreams(cloudClient.streams(details.item, details.movieData))
                else -> message.value = "CloudStream returned no supported episodes or playback data"
            }
            return
        }
        val metadataSources = enabledAddons().filter { it.supportsMeta } +
            Addon("com.linvo.cinemeta", "Cinemeta", "https://v3-cinemeta.strem.io", true, false, true)
        // Fetch metadata concurrently, but merge in configured-provider order.
        // One slow provider must not delay every provider after it.
        val metadataResults = coroutineScope {
            metadataSources.distinctBy { it.baseUrl }.map { source ->
                async { providerSlots.withPermit { providerResult { stremio.metadata(source, media) } } }
            }.awaitAll()
        }
        var enrichedItem = media
        for (candidate in metadataResults.filterNotNull()) {
            enrichedItem = enrichedItem.copy(
                title = if (enrichedItem.title == media.id && candidate.title != media.id) candidate.title else enrichedItem.title,
                poster = enrichedItem.poster ?: candidate.poster,
                description = enrichedItem.description ?: candidate.description,
                isAnime = enrichedItem.isAnime || candidate.isAnime,
                genres = enrichedItem.genres.ifEmpty { candidate.genres },
                year = enrichedItem.year ?: candidate.year,
                cast = enrichedItem.cast.ifEmpty { candidate.cast },
            )
        }
        currentItem.value = enrichedItem
        if (media.type in listOf("series", "anime") && !Regex(":\\d+:\\d+$").containsMatchIn(media.id)) {
            val metadataSources = enabledAddons().filter { it.supportsMeta } +
                com.mutinymedia.android.domain.Addon("com.linvo.cinemeta", "Cinemeta", "https://v3-cinemeta.strem.io", true, false, true)
            episodes.value = coroutineScope {
                metadataSources.distinctBy { it.baseUrl }.map { addon ->
                    async { providerSlots.withPermit { providerResult { stremio.episodes(addon, media) }.orEmpty() } }
                }.awaitAll()
            }.flatten().distinctBy { it.id }.sortedWith(compareBy({ it.season }, { it.number }))
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
        val details = cloudDetails
        if (CloudStreamProviderClient.owns(media)) {
            val (data, dub) = details?.episodeData?.get(episode.id) ?: error("CloudStream episode is no longer available")
            showStreams(cloudClient.streams(media, data, dub))
        } else {
            fetchStreams(media.copy(id = episode.id))
        }
    }
    private suspend fun fetchStreams(media: MediaItem) {
        if (enabledAddons().none { it.supportsStreams }) {
            message.value = "Enable a stream-capable addon under Settings → Integrations."
            return
        }
        val found = queryProviders { addon -> stremio.streams(addon, media.type, media.id) }
            .distinctBy { Triple(it.url ?: it.id, it.headers, it.externalUrl) }
        showStreams(found, providerWarnings.value)
    }

    private fun showStreams(found: List<StreamOption>, warning: String? = null) {
        // Keep raw provider results so the UI can change language and bandwidth filters without another request.
        streams.value = found
        val matched = StreamFilters.select(found, currentItem.value?.isAnime == true, settings.value)
        message.value = when {
            found.isEmpty() -> listOfNotNull("No streams returned by connected providers", warning).joinToString(". ")
            matched.isEmpty() -> "No sources match your active filters."
            matched.none { it.playable } && matched.any { it.hasTorrent } -> "Use Real-Debrid to resolve a supported torrent source."
            else -> warning
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
        val direct = when {
            stream.hasTorrent -> realDebrid.resolveTorrent(token, requireNotNull(stream.infoHash), stream.fileIndex)
            stream.playable -> realDebrid.resolve(token, requireNotNull(stream.url))
            else -> error("This source has no supported HTTPS link or torrent hash")
        }
        val sameSource = direct == stream.url
        play(stream.copy(url = direct, provider = "Real-Debrid", infoHash = null, fileIndex = null,
            headers = if (sameSource) stream.headers else emptyMap(),
            cloudStreamSession = sameSource && stream.cloudStreamSession,
            mimeType = stream.mimeType.takeIf { sameSource }))
    }
    fun playbackKey(item: MediaItem, episode: String?): String =
        CloudStreamInstaller.digest("${item.type}\u0000${item.id}\u0000${episode.orEmpty()}")

    fun recordPlayback(key: String?, positionMs: Long, durationMs: Long) {
        if (key == null) return
        viewModelScope.launch {
            try {
                store.savePlaybackPosition(key, resumablePosition(positionMs, durationMs))
            } catch (e: CancellationException) {
                throw e
            } catch (_: java.io.IOException) {
                message.value = "Could not save playback progress"
            }
        }
    }

    fun play(stream: StreamOption) {
        if (!stream.playable) {
            message.value = "Choose a playable HTTPS source or resolve a supported torrent with Real-Debrid."
            return
        }
        activePlaybackKey = currentItem.value?.let { playbackKey(it, selectedEpisode.value) }
        playbackStartMs = activePlaybackKey?.let { playbackPositions.value[it] } ?: 0L
        activeStream.value = stream
    }

    fun continuePlayback(stream: StreamOption, positionMs: Long) {
        playbackStartMs = positionMs.coerceAtLeast(0)
        activeStream.value = stream
    }

    fun playTestVideo() {
        currentItem.value = MediaItem("demo", "movie", "Big Buck Bunny • Creative Commons test video")
        activePlaybackKey = null
        continuePlayback(
            StreamOption("sample", "Big Buck Bunny", "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4", "Public sample"),
            0L,
        )
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
