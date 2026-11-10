package com.mutinymedia.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutinymedia.android.MutinyViewModel
import com.mutinymedia.android.domain.Addon
import com.mutinymedia.android.domain.AnimeStreamMode
import com.mutinymedia.android.domain.CodecPreference
import com.mutinymedia.android.domain.ThemeFont
import com.mutinymedia.android.domain.ThemePreset
import com.mutinymedia.android.domain.UserSettings
import kotlin.math.roundToInt

enum class SettingsTab(val label: String) {
    HOME("Settings"), ACCOUNT("Account"), WATCH_PARTY("Watch party"),
    APPEARANCE("Appearance"), LAYOUT("Layout"),
    CONTENT("Content & Discovery"), PLAYBACK("Playback"),
    PROVIDERS("Integrations"), TRACKING("Tracking"),
    TMDB("TMDB Enrichment"), MDBLIST("MDBList Ratings"),
    OMDB("OMDb Ratings"), LIVE_TV("Live TV"), DISCORD("Discord"),
    LOW_DATA("Low bandwidth"), ANIME("Anime"), AI("Ask AI"),
    DOWNLOADS("Downloads"), DEBRID("Debrid Services"), ABOUT("About")
}

@Composable
fun SettingsScreen(vm: MutinyViewModel, s: UserSettings, addons: List<Addon>) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.HOME) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tab != SettingsTab.HOME) {
            TextButton(onClick = { tab = if (tab in listOf(SettingsTab.TMDB, SettingsTab.MDBLIST, SettingsTab.OMDB, SettingsTab.LIVE_TV, SettingsTab.DISCORD)) SettingsTab.PROVIDERS else SettingsTab.HOME }) { Text("← Back") }
        }
        PanelTitle(tab.label, if (tab == SettingsTab.HOME) "Manage your Mutiny Media preferences and connected services." else "Mutiny Media Android")
        when (tab) {
            SettingsTab.HOME -> {
                MutinyPanel("Account & social") {
                    SettingsLink("Account", "Stremio and Nuvio sign-in and sync status") { tab = SettingsTab.ACCOUNT }
                    SettingsLink("Watch party", "Join friends with a party code") { tab = SettingsTab.WATCH_PARTY }
                }
                MutinyPanel("Personalisation") {
                    SettingsLink("Appearance", "Theme Studio, colours and fonts") { tab = SettingsTab.APPEARANCE }
                    SettingsLink("Layout", "Home, cards, details, streams and navigation") { tab = SettingsTab.LAYOUT }
                }
                MutinyPanel("Content") {
                    SettingsLink("Content & Discovery", "Manage addons and discovery sources") { tab = SettingsTab.CONTENT }
                    SettingsLink("Playback", "Player, subtitles and auto-play") { tab = SettingsTab.PLAYBACK }
                }
                MutinyPanel("Connections") {
                    SettingsLink("Integrations", "Streaming, debrid, metadata, Live TV and Discord") { tab = SettingsTab.PROVIDERS }
                    SettingsLink("Tracking", "Tracking providers and watch progress") { tab = SettingsTab.TRACKING }
                }
                MutinyPanel("More") {
                    SettingsLink("Ask AI", "OpenRouter recommendations") { tab = SettingsTab.AI }
                    SettingsLink("Downloads", "Direct file downloads") { tab = SettingsTab.DOWNLOADS }
                    SettingsLink("About", "App information") { tab = SettingsTab.ABOUT }
                }
            }
            SettingsTab.ACCOUNT -> { StremioAccountCard(vm); NuvioAccountCard(vm) }
            SettingsTab.PROVIDERS -> {
                MutinyPanel("Integrations") {
                    SettingsLink("Connected Services", "Stremio and Nuvio accounts") { tab = SettingsTab.ACCOUNT }
                    SettingsLink("Debrid Services", "Real-Debrid API key and connection") { tab = SettingsTab.DEBRID }
                    SettingsLink("TMDB Enrichment", "Metadata enrichment controls") { tab = SettingsTab.TMDB }
                    SettingsLink("MDBList Ratings", "External ratings providers") { tab = SettingsTab.MDBLIST }
                    SettingsLink("OMDb Ratings", "Personal API key for IMDb episode ratings") { tab = SettingsTab.OMDB }
                    SettingsLink("Live TV", "M3U playlists for live channels") { tab = SettingsTab.LIVE_TV }
                    SettingsLink("Discord", "Show what you're watching on Discord") { tab = SettingsTab.DISCORD }
                }
                IntegrationsSettings(vm, s, addons, showAccounts = false)
            }
            SettingsTab.CONTENT -> IntegrationsSettings(vm, s, addons, showAccounts = false)
            SettingsTab.PLAYBACK -> {
                Text("Playback settings currently available on Android:")
                SettingsLink("Low bandwidth", "Bitrate, resolution and codec preferences") { tab = SettingsTab.LOW_DATA }
                SettingsLink("Anime audio", "Dub/sub stream filters") { tab = SettingsTab.ANIME }
                Text("Subtitle selection and auto-play controls are not yet implemented.")
            }
            SettingsTab.LOW_DATA -> BandwidthSettings(vm, s)
            SettingsTab.ANIME -> AnimeSettings(vm, s)
            SettingsTab.DEBRID -> DebridSettings(vm)
            SettingsTab.AI -> AiSettings(vm, s)
            SettingsTab.DOWNLOADS -> DirectDownloadSettings(vm)
            SettingsTab.ABOUT -> AboutSettings()
            SettingsTab.APPEARANCE -> ThemeStudioSettings(vm, s)
            SettingsTab.WATCH_PARTY, SettingsTab.LAYOUT,
            SettingsTab.TRACKING, SettingsTab.TMDB, SettingsTab.MDBLIST,
            SettingsTab.OMDB, SettingsTab.LIVE_TV, SettingsTab.DISCORD -> {
                MutinyPanel(tab.label) {
                    Text("This integration or settings module is not implemented in the Android app yet. It is listed here to match the requested settings structure, but no connection or feature is claimed.")
                }
            }
        }
    }
}

@Composable
private fun ThemeStudioSettings(vm: MutinyViewModel, settings: UserSettings) {
    MutinyPanel("Theme Studio") {
        Text("Colour preset", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemePreset.entries.forEach { preset ->
                FilterChip(
                    selected = settings.themePreset == preset,
                    onClick = { vm.runJob { vm.store.setThemePreset(preset) } },
                    label = { Text(preset.label) },
                )
            }
        }
        OutlinedButton(onClick = { vm.runJob { vm.store.randomizeThemePreset() } }) {
            Text("Randomise theme")
        }
        Text("App font", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeFont.entries.forEach { font ->
                FilterChip(
                    selected = settings.themeFont == font,
                    onClick = { vm.runJob { vm.store.setThemeFont(font) } },
                    label = { Text(font.label) },
                )
            }
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IntegrationsSettings(vm: MutinyViewModel, s: UserSettings, addons: List<Addon>, showAccounts: Boolean = true) {
    var addonUrl by remember { mutableStateOf("") }
    var repoUrl by remember { mutableStateOf("") }
    var directExtensionUrl by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val plugins by vm.cloudStreamPlugins.collectAsStateWithLifecycle()
    val installedPlugins by vm.installedCloudPlugins.collectAsStateWithLifecycle()
    val hostStatuses by vm.cloudHostStatuses.collectAsStateWithLifecycle()
    PanelTitle("Streaming providers", "Sign in to Stremio or Nuvio, or connect streaming addons manually.")
    if (showAccounts) { StremioAccountCard(vm); NuvioAccountCard(vm) }
    MutinyPanel("CloudStream • repositories") {
        Text("Add a CloudStream repository, then discover its available extension packages. Executing .cs3 plugins is not yet supported.")
        OutlinedTextField(value = repoUrl, onValueChange = { repoUrl = it }, label = { Text("HTTPS CloudStream repo.json") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { vm.addExtensionRepo(repoUrl); repoUrl = "" }, enabled = repoUrl.startsWith("https://") && !busy) { Text("Verify & save repository") }
        OutlinedTextField(value = directExtensionUrl, onValueChange = { directExtensionUrl = it },
            label = { Text("Or paste a direct HTTPS .cs3 extension URL") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { vm.installCloudStreamUrl(directExtensionUrl); directExtensionUrl = "" },
            enabled = directExtensionUrl.trim().startsWith("https://", true) &&
                directExtensionUrl.trim().substringBefore('?').endsWith(".cs3", true) && !busy) {
            Text("Download extension from URL")
        }
        OutlinedButton(onClick = { vm.discoverCloudStreamPlugins() }, enabled = s.extensionRepositories.isNotEmpty() && !busy) {
            Text("Discover available extensions")
        }
        if (plugins.isNotEmpty()) Text("${plugins.size} extension packages found. Installation and execution require a compatible runtime.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { vm.inspectCloudStreamHost() }, enabled = installedPlugins.isNotEmpty() && !busy) {
            Text("Check installed extension compatibility")
        }
        hostStatuses.forEach { status ->
            Text("${status.packageId.take(10)}: ${status.state} • ${status.detail}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        plugins.forEach { plugin ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(plugin.name, fontWeight = FontWeight.SemiBold)
                Text("Version ${plugin.version} • ${plugin.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val pluginId = remember(plugin.downloadUrl) {
                    java.security.MessageDigest.getInstance("SHA-256").digest(plugin.downloadUrl.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                }
                Text(if (pluginId in installedPlugins) "Package downloaded • runtime not available" else "Not installed",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pluginId in installedPlugins) {
                        OutlinedButton(onClick = { vm.uninstallCloudStreamPlugin(plugin) }, enabled = !busy) { Text("Remove") }
                    } else {
                        Button(onClick = { vm.installCloudStreamPlugin(plugin) }, enabled = !busy) { Text("Download extension") }
                    }
                }
            }
        }
        s.extensionRepositories.forEach { url ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(url, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                TextButton(onClick = { vm.removeExtensionRepo(url) }) { Text("Remove") }
            }
        }
    }
    MutinyPanel("Stremio • addon protocol") {
        Text("HTTPS manifests, catalogs and direct streams. Add providers manually here or import them from your signed-in account.")
        OutlinedTextField(value = addonUrl, onValueChange = { addonUrl = it },
            label = { Text("Addon URL or manifest.json") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { vm.addAddon(addonUrl); addonUrl = "" }, enabled = (addonUrl.trim().startsWith("https://", true) || addonUrl.trim().startsWith("stremio://", true)) && !busy) { Text("Connect add-on") }
        OutlinedButton(onClick = { vm.refreshAddons() }, enabled = !busy && s.addonUrls.isNotEmpty()) { Text("Retry saved addons") }
        if (s.addonUrls.isEmpty()) Text("No Stremio addons installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.addonUrls.forEach { url ->
            val live = addons.firstOrNull { it.baseUrl == url }
            Column(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Text(live?.title ?: url, fontWeight = FontWeight.SemiBold)
                Text(if (live != null) "Manifest verified • catalog=${live.supportsCatalog} • stream=${live.supportsStreams}" else "Not reachable or invalid manifest", color = if (live != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(url, modifier = Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { vm.removeAddon(url) }) { Text("Remove") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
            }
        }
    }
}

@Composable
private fun BandwidthSettings(vm: MutinyViewModel, s: UserSettings) {
    var slider by remember(s.videoKbps) { mutableFloatStateOf(s.videoKbps.toFloat()) }
    PanelTitle("Ultra Low Bandwidth Mode", "Prefer lightweight sources and limit selected tracks when the provider exposes compatible quality options.")
    MutinyPanel("Enable low-bandwidth preferences") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use ultra-low-bandwidth mode", Modifier.weight(1f))
            Switch(s.lowBandwidth, onCheckedChange = { vm.runJob { vm.store.setLowBandwidth(it) } })
        }
        Text("Only compatible streams can be selected at this rate. This is not a guaranteed bandwidth cap or transcoder.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    MutinyPanel("Maximum preferred video bitrate") {
        Text("${slider.roundToInt()} kbps", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
        Slider(value = slider, onValueChange = { slider = ((it / 100).roundToInt() * 100).coerceIn(200, 1500).toFloat() },
            onValueChangeFinished = { vm.runJob { vm.store.setVideoKbps(slider.roundToInt()) } },
            valueRange = 200f..1500f, steps = 12)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("200 kbps", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("1,500 kbps", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    MutinyPanel("Maximum video resolution") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(240, 360, 480).forEach { level ->
                FilterChip(selected = s.resolution == level, onClick = { vm.runJob { vm.store.setResolution(level) } }, label = { Text("${level}p") })
            }
        }
    }
    MutinyPanel("Video codec preference") {
        Text("Used for ranking when names supply codec labels. Playback still depends on the Android device's decoder.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        FilterChip(selected = s.codec == CodecPreference.EFFICIENT, onClick = { vm.runJob { vm.store.setCodec(CodecPreference.EFFICIENT) } },
            label = { Text("Prefer AV1 / HEVC") })
        FilterChip(selected = s.codec == CodecPreference.COMPATIBLE, onClick = { vm.runJob { vm.store.setCodec(CodecPreference.COMPATIBLE) } },
            label = { Text("Prefer H.264") })
    }
    MutinyPanel("Audio bitrate preference") {
        Text("Applies an audio track bitrate constraint when multiple tracks exist. A single higher-bitrate track may still be used for compatibility.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(32, 48, 64).forEach { amount ->
                FilterChip(selected = s.audioKbps == amount, onClick = { vm.runJob { vm.store.setAudioKbps(amount) } }, label = { Text("$amount kbps") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Automatic bitrate switching when supported", Modifier.weight(1f))
            Switch(checked = s.adaptive, onCheckedChange = { vm.runJob { vm.store.setAdaptive(it) } })
        }
        Text("HLS/DASH may expose adaptive tracks. A fixed MP4 or torrent cannot be converted to adaptive video by a setting.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    MutinyPanel("Preferred stream target") {
        Text("${slider.roundToInt() + s.audioKbps} kbps", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Text("Video + audio target, excluding transport overhead. Requires matching streams or upstream transcoding.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnimeSettings(vm: MutinyViewModel, s: UserSettings) {
    PanelTitle("Anime languages", "Choose whether anime searches display every source, English dub only, or Japanese audio/sub sources.")
    MutinyPanel("Stream language mode") {
        AnimeStreamMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = s.animeMode == mode, onClick = { vm.runJob { vm.store.setAnime(mode) } })
                Spacer(Modifier.width(6.dp))
                Text(when (mode) {
                    AnimeStreamMode.ALL -> "All streams (including unknown languages)"
                    AnimeStreamMode.DUB -> "English dubbed streams only"
                    AnimeStreamMode.SUB -> "Japanese audio / sub-labelled streams only"
                }, modifier = Modifier.weight(1f))
            }
        }
        Text("Only applies to titles marked as anime. Unlabelled results are excluded in Dub/Sub mode rather than misidentified. Dual-audio releases may qualify for both.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AiSettings(vm: MutinyViewModel, s: UserSettings) {
    var key by remember { mutableStateOf("") }
    var model by remember(s.openRouterModel) { mutableStateOf(s.openRouterModel) }
    PanelTitle("Ask AI", "Connect your own OpenRouter account. Keys are encrypted using Android Keystore.")
    MutinyPanel("OpenRouter") {
        Text("Existing key: ${if (vm.secrets.readOpenRouter().isNullOrBlank()) "not set" else "saved securely"}")
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("New OpenRouter API key") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveOpenRouterKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }
            OutlinedButton(onClick = { vm.saveOpenRouterKey("") }) { Text("Clear key") }
        }
        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.runJob { vm.store.setModel(model) } }, enabled = model.isNotBlank()) { Text("Save model") }
    }
}

@Composable
private fun DirectDownloadSettings(vm: MutinyViewModel) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("video.mp4") }
    PanelTitle("Downloads", "Native Android DownloadManager for direct HTTPS files.")
    MutinyPanel("Save a direct file") {
        Text("Does not fetch magnet links, torrent pieces or encrypted subscription content.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("HTTPS file URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("File name") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.download(url, name) }, enabled = url.startsWith("https://") && name.isNotBlank()) { Text("Queue download") }
        Text("Saved to the app's external Downloads directory. Android handles network interruptions and notifications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutSettings() {
    PanelTitle("Mutiny Media for Android", "Native Kotlin + Jetpack Compose architecture")
    MutinyPanel("Engineering notes") {
        Text("Android min SDK 26 • Android TV/handheld friendly focus targets • responsive layouts")
        Text("Media3 for direct HTTPS MP4/HLS/DASH playback. It cannot run Windows Tauri/Rust modules as-is.")
        Text("Stremio/Nuvio sign-in and account imports are available under Integrations. Executable CloudStream/Nuvio plugins, P2P engines, background playback and Android TV certification require separate integrations.")
    }
}

@Composable
private fun DebridSettings(vm: MutinyViewModel) {
    var key by remember { mutableStateOf("") }
    val account by vm.debridAccount.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val saved = remember(account, busy) { vm.secrets.readSecret("debrid_real_debrid") != null }
    MutinyPanel("Real-Debrid") {
        Text("Resolve supported HTTPS hoster links through your own Real-Debrid account. Torrent magnets and cached torrent conversion are not supported yet.")
        Text(if (account != null) "Connected: $account" else if (saved) "API key saved (not verified in this session)" else "No API key saved", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Real-Debrid API token") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { vm.saveRealDebridKey(key); key = "" }, enabled = key.isNotBlank() && !busy) { Text("Verify and save API key") }
        if (saved) {
            OutlinedButton(onClick = { vm.verifyRealDebrid() }, enabled = !busy) { Text("Check connection") }
            TextButton(onClick = { vm.saveRealDebridKey("") }, enabled = !busy) { Text("Remove saved key") }
        }
        Text("Credentials are encrypted locally with Android Keystore and are never stored in the source repository.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
