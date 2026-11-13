package com.mutinymedia.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.mutinymedia.android.domain.StreamFilters
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
    HOME("Settings"), ACCOUNT("Account"), APPEARANCE("Appearance"),
    CONTENT("Content & Discovery"), PLAYBACK("Playback"), PROVIDERS("Integrations"),
    LOW_DATA("Low bandwidth"), ANIME("Anime"), AI("Ask AI"),
    DOWNLOADS("Downloads"), DEBRID("Debrid Services"), ABOUT("About")
}

@Composable
fun SettingsScreen(vm: MutinyViewModel, s: UserSettings, addons: List<Addon>) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.HOME) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tab != SettingsTab.HOME) {
            TextButton(onClick = { tab = when (tab) {
                SettingsTab.DEBRID -> SettingsTab.PROVIDERS
                SettingsTab.LOW_DATA, SettingsTab.ANIME -> SettingsTab.PLAYBACK
                else -> SettingsTab.HOME
            } }) { Text("← Back") }
        }
        PanelTitle(tab.label, if (tab == SettingsTab.HOME) "Manage your Mutiny Media preferences and connected services." else "Mutiny Media Android")
        when (tab) {
            SettingsTab.HOME -> {
                MutinyPanel("Account & social") {
                    SettingsLink("Account", "Stremio and Nuvio sign-in and sync status") { tab = SettingsTab.ACCOUNT }
                }
                MutinyPanel("Personalisation") {
                    SettingsLink("Appearance", "Colour presets and fonts") { tab = SettingsTab.APPEARANCE }
                }
                MutinyPanel("Content") {
                    SettingsLink("Content & Discovery", "Manage addons and discovery sources") { tab = SettingsTab.CONTENT }
                    SettingsLink("Playback", "Source filters, subtitles and low bandwidth") { tab = SettingsTab.PLAYBACK }
                }
                MutinyPanel("Connections") {
                    SettingsLink("Integrations", "Streaming addons, extensions and Real-Debrid") { tab = SettingsTab.PROVIDERS }
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
                }
                IntegrationsSettings(vm, s, addons, showAccounts = false)
            }
            SettingsTab.CONTENT -> IntegrationsSettings(vm, s, addons, showAccounts = false)
            SettingsTab.PLAYBACK -> {
                ExactResolutionSettings(vm, s)
                SettingsLink("Low bandwidth", "Bitrate, resolution and codec preferences") { tab = SettingsTab.LOW_DATA }
                SettingsLink("Anime audio", "Dub/sub stream filters") { tab = SettingsTab.ANIME }
                Text("CloudStream subtitles can be selected in the player when tracks are available.")
            }
            SettingsTab.LOW_DATA -> BandwidthSettings(vm, s)
            SettingsTab.ANIME -> AnimeSettings(vm, s)
            SettingsTab.DEBRID -> DebridSettings(vm)
            SettingsTab.AI -> AiSettings(vm, s)
            SettingsTab.DOWNLOADS -> DirectDownloadSettings(vm)
            SettingsTab.ABOUT -> AboutSettings()
            SettingsTab.APPEARANCE -> ThemeStudioSettings(vm, s)

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
                    onClick = { if (settings.themePreset != preset) vm.runJob { vm.store.setThemePreset(preset) } },
                    label = { Text(preset.label) },
                )
            }
        }
        OutlinedButton(onClick = { vm.runJob { vm.store.randomizeAppearance() } }) {
            Text("Randomise colours and font")
        }
        Text("App font", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeFont.entries.forEach { font ->
                FilterChip(
                    selected = settings.themeFont == font,
                    onClick = { if (settings.themeFont != font) vm.runJob { vm.store.setThemeFont(font) } },
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
    val extensionContext = androidx.compose.ui.platform.LocalContext.current
    val hostStatuses by vm.cloudHostStatuses.collectAsStateWithLifecycle()
    PanelTitle("Streaming providers", "Sign in to Stremio or Nuvio, or connect streaming addons manually.")
    if (showAccounts) { StremioAccountCard(vm); NuvioAccountCard(vm) }
    MutinyPanel("CloudStream • repositories") {
        Text("Install extensions only from sources you trust. CloudStream extensions run with this app’s permissions. Active providers appear in Browse and Search.")
        OutlinedTextField(value = repoUrl, onValueChange = { repoUrl = it }, label = { Text("HTTPS CloudStream repo.json") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { vm.addExtensionRepo(repoUrl); repoUrl = "" }, enabled = repoUrl.startsWith("https://") && !busy) { Text("Verify & save repository") }
        OutlinedTextField(value = directExtensionUrl, onValueChange = { directExtensionUrl = it },
            label = { Text("Or paste a direct HTTPS .cs3 extension URL") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { vm.installCloudStreamUrl(directExtensionUrl); directExtensionUrl = "" },
            enabled = directExtensionUrl.trim().startsWith("https://", true) &&
                directExtensionUrl.trim().substringBefore('?').endsWith(".cs3", true) && !busy) {
            Text("Install extension from URL")
        }
        OutlinedButton(onClick = { vm.discoverCloudStreamPlugins() }, enabled = s.extensionRepositories.isNotEmpty() && !busy) {
            Text("Discover available extensions")
        }
        hostStatuses.forEach { status ->
            val active = status.state == com.mutinymedia.android.data.CloudStreamHost.State.ACTIVE
            Text(status.name.ifBlank { status.packageId.take(10) }, fontWeight = FontWeight.SemiBold)
            Text("${status.state} · ${status.detail}", style = MaterialTheme.typography.bodySmall)
            if (status.providers.isNotEmpty()) Text(status.providers.joinToString(), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.setCloudStreamEnabled(status.packageId, !active) }, enabled = !busy) {
                    Text(if (active) "Disable" else "Enable")
                }
                OutlinedButton(onClick = { vm.reloadCloudStreamPlugin(status.packageId) }, enabled = !busy) { Text("Reload") }
                if (status.hasSettings) {
                    OutlinedButton(onClick = { vm.openCloudStreamSettings(status.packageId, extensionContext) }, enabled = !busy) {
                        Text("Settings")
                    }
                }
                TextButton(onClick = { vm.uninstallCloudStreamPlugin(status.packageId) }, enabled = !busy) { Text("Uninstall") }
            }
        }
        plugins.forEach { plugin ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(plugin.name, fontWeight = FontWeight.SemiBold)
                Text("Version ${plugin.version} · ${plugin.description}", style = MaterialTheme.typography.bodySmall)
                val pluginId = remember(plugin.downloadUrl) {
                    com.mutinymedia.android.data.CloudStreamInstaller.packageId(plugin.downloadUrl)
                }
                val installed = hostStatuses.firstOrNull { it.packageId == pluginId }
                Button(
                    onClick = { vm.installCloudStreamPlugin(plugin) },
                    enabled = !busy && (installed == null || plugin.version == 0 || plugin.version > installed.version),
                ) { Text(if (installed == null) "Install" else "Update") }
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
        Text("Addons are tried in this order when sources otherwise rank equally.", style = MaterialTheme.typography.bodySmall)
        s.addonUrls.forEachIndexed { index, url ->
            val live = addons.firstOrNull { it.baseUrl == url }
            val enabled = url !in s.excludedAddonUrls
            Column(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Text(live?.title ?: runCatching { java.net.URI(url).host }.getOrNull() ?: "Saved addon", fontWeight = FontWeight.SemiBold)
                Text(when {
                    !enabled -> "Excluded from discovery and playback"
                    live != null -> "Manifest verified • catalog=${live.supportsCatalog} • stream=${live.supportsStreams}"
                    else -> "Manifest unavailable"
                }, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.setAddonEnabled(url, !enabled) }, enabled = !busy) {
                        Text(if (enabled) "Exclude" else "Enable")
                    }
                    TextButton(onClick = { vm.runJob { vm.store.moveAddon(url, -1) } }, enabled = !busy && index > 0) { Text("Move up") }
                    TextButton(onClick = { vm.runJob { vm.store.moveAddon(url, 1) } }, enabled = !busy && index < s.addonUrls.lastIndex) { Text("Move down") }
                    TextButton(onClick = { vm.removeAddon(url) }, enabled = !busy) { Text("Remove") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
            }
        }
    }
}

@Composable
private fun ExactResolutionSettings(vm: MutinyViewModel, settings: UserSettings) {
    var open by remember { mutableStateOf(false) }
    MutinyPanel("Exact source resolution") {
        Text("Matches provider labels and declared quality. Sources with unknown or conflicting labels are hidden when a resolution is selected.")
        Box {
            OutlinedButton(onClick = { open = true }) { Text(settings.exactResolution?.let { "${it}p only" } ?: "Any resolution") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                (listOf<Int?>(null) + StreamFilters.resolutions).forEach { height ->
                    DropdownMenuItem(text = { Text(height?.let { "${it}p only" } ?: "Any resolution") },
                        onClick = { open = false; vm.runJob { vm.store.setExactResolution(height) } })
                }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dual audio only", Modifier.weight(1f))
            Switch(s.animeDualAudioOnly, onCheckedChange = { vm.runJob { vm.store.setDualAudioOnly(it) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Uncensored / uncut only", Modifier.weight(1f))
            Switch(s.animeUncensoredOnly, onCheckedChange = { vm.runJob { vm.store.setUncensoredOnly(it) } })
        }
        Text("Only applies to anime. Filters use labels supplied by the provider, and a dual-audio label alone does not identify an English dub.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AiSettings(vm: MutinyViewModel, s: UserSettings) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var model by remember(s.openRouterModel) { mutableStateOf(s.openRouterModel) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val freeModels by vm.freeOpenRouterModels.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    PanelTitle("Ask AI", "Connect your own OpenRouter account. Keys are encrypted using Android Keystore.")
    MutinyPanel("OpenRouter") {
        Text("Existing key: ${if (vm.secrets.readOpenRouter().isNullOrBlank()) "not set" else "saved securely"}")
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("New OpenRouter API key") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveOpenRouterKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }
            OutlinedButton(onClick = { vm.saveOpenRouterKey("") }) { Text("Clear key") }
        }
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/settings/keys"))) }
                .onFailure { vm.report("No browser is available to open OpenRouter.") }
        }) { Text("Get an OpenRouter key") }
        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.runJob { vm.store.setModel(model) } }, enabled = model.isNotBlank()) { Text("Save model") }
        OutlinedButton(onClick = { vm.refreshOpenRouterModels() }, enabled = !busy) {
            Text("Refresh free models")
        }
        if (freeModels.isNotEmpty()) {
            Box {
                OutlinedButton(onClick = { modelMenuOpen = true }) { Text("Choose a free model") }
                DropdownMenu(
                    expanded = modelMenuOpen,
                    onDismissRequest = { modelMenuOpen = false },
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    freeModels.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                modelMenuOpen = false
                                model = option.id
                                vm.runJob { vm.store.setModel(option.id) }
                            },
                        )
                    }
                }
            }
        }
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
        Text("Stremio/Nuvio sign-in and account imports are available under Integrations. CloudStream providers use the v4.5.4 runtime. Nuvio plugin execution, P2P engines, background playback and Android TV certification require separate integrations.")
    }
}

@Composable
private fun DebridSettings(vm: MutinyViewModel) {
    var key by remember { mutableStateOf("") }
    val account by vm.debridAccount.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val saved = remember(account, busy) { vm.secrets.readSecret("debrid_real_debrid") != null }
    MutinyPanel("Real-Debrid") {
        Text("Use your Real-Debrid account to resolve supported HTTPS hoster links or torrent hashes returned by an addon.")
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
