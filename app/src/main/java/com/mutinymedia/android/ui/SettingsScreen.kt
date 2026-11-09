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
import com.mutinymedia.android.domain.UserSettings
import kotlin.math.roundToInt

enum class SettingsTab(val label: String) {
    PROVIDERS("Integrations"), LOW_DATA("Low bandwidth"), ANIME("Anime"), AI("Ask AI"), DOWNLOADS("Downloads"), ABOUT("About")
}

@Composable
fun SettingsScreen(vm: MutinyViewModel, s: UserSettings, addons: List<Addon>) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.PROVIDERS) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelTitle("Settings", "Organised into six categories instead of an endless list.")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsTab.entries.forEach { item ->
                FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.label) })
            }
        }
        when (tab) {
            SettingsTab.PROVIDERS -> IntegrationsSettings(vm, s, addons)
            SettingsTab.LOW_DATA -> BandwidthSettings(vm, s)
            SettingsTab.ANIME -> AnimeSettings(vm, s)
            SettingsTab.AI -> AiSettings(vm, s)
            SettingsTab.DOWNLOADS -> DirectDownloadSettings(vm)
            SettingsTab.ABOUT -> AboutSettings()
        }
    }
}

@Composable
private fun IntegrationsSettings(vm: MutinyViewModel, s: UserSettings, addons: List<Addon>) {
    var addonUrl by remember { mutableStateOf("") }
    var repoUrl by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    PanelTitle("Streaming providers", "Nuvio · CloudStream · Stremio. Actual capabilities and connection state, no dummy test buttons.")
    MutinyPanel("Nuvio • account integration") {
        Text("Android Nuvio account authentication and cloud watchlist sync are not wired yet.", style = MaterialTheme.typography.bodyMedium)
        Text("Status: not implemented. A separate, authenticated Nuvio adapter is required; this screen will not pretend a connection exists.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
    }
    MutinyPanel("CloudStream • repositories") {
        Text("Add a public HTTPS repo.json URL to verify and save its metadata. Running .cs3 extension binaries is not yet supported.")
        OutlinedTextField(value = repoUrl, onValueChange = { repoUrl = it }, label = { Text("HTTPS CloudStream repo.json") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { vm.addExtensionRepo(repoUrl); repoUrl = "" }, enabled = repoUrl.startsWith("https://") && !busy) { Text("Verify & save repository") }
        s.extensionRepositories.forEach { url ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(url, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                TextButton(onClick = { vm.removeExtensionRepo(url) }) { Text("Remove") }
            }
        }
    }
    MutinyPanel("Stremio • addon protocol") {
        Text("Working HTTPS manifest, catalog, and stream lookup. Stremio account login/sync remains separate work.")
        OutlinedTextField(value = addonUrl, onValueChange = { addonUrl = it },
            label = { Text("Addon URL or manifest.json") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { vm.addAddon(addonUrl); addonUrl = "" }, enabled = (addonUrl.trim().startsWith("https://", true) || addonUrl.trim().startsWith("stremio://", true)) && !busy) { Text("Connect add-on") }
        OutlinedButton(onClick = { vm.refreshAddons() }, enabled = !busy && s.addonUrls.isNotEmpty()) { Text("Retry saved addons") }
        if (s.addonUrls.isEmpty()) Text("No Stremio addons installed", color = MutedInk)
        s.addonUrls.forEach { url ->
            val live = addons.firstOrNull { it.baseUrl == url }
            Column(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Text(live?.title ?: url, fontWeight = FontWeight.SemiBold)
                Text(if (live != null) "Manifest verified • catalog=${live.supportsCatalog} • stream=${live.supportsStreams}" else "Not reachable or invalid manifest", color = if (live != null) Mint else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(url, modifier = Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.labelSmall, color = MutedInk)
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
        Text("Only compatible streams can be selected at this rate. This is not a guaranteed bandwidth cap or transcoder.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
    }
    MutinyPanel("Maximum preferred video bitrate") {
        Text("${slider.roundToInt()} kbps", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium, color = Mint)
        Slider(value = slider, onValueChange = { slider = ((it / 100).roundToInt() * 100).coerceIn(200, 1500).toFloat() },
            onValueChangeFinished = { vm.runJob { vm.store.setVideoKbps(slider.roundToInt()) } },
            valueRange = 200f..1500f, steps = 12)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("200 kbps", color = MutedInk)
            Text("1,500 kbps", color = MutedInk)
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
        Text("Used for ranking when names supply codec labels. Playback still depends on the Android device's decoder.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
        FilterChip(selected = s.codec == CodecPreference.EFFICIENT, onClick = { vm.runJob { vm.store.setCodec(CodecPreference.EFFICIENT) } },
            label = { Text("Prefer AV1 / HEVC") })
        FilterChip(selected = s.codec == CodecPreference.COMPATIBLE, onClick = { vm.runJob { vm.store.setCodec(CodecPreference.COMPATIBLE) } },
            label = { Text("Prefer H.264") })
    }
    MutinyPanel("Audio bitrate preference") {
        Text("Applies an audio track bitrate constraint when multiple tracks exist. A single higher-bitrate track may still be used for compatibility.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(32, 48, 64).forEach { amount ->
                FilterChip(selected = s.audioKbps == amount, onClick = { vm.runJob { vm.store.setAudioKbps(amount) } }, label = { Text("$amount kbps") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Automatic bitrate switching when supported", Modifier.weight(1f))
            Switch(checked = s.adaptive, onCheckedChange = { vm.runJob { vm.store.setAdaptive(it) } })
        }
        Text("HLS/DASH may expose adaptive tracks. A fixed MP4 or torrent cannot be converted to adaptive video by a setting.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
    }
    MutinyPanel("Preferred stream target") {
        Text("${slider.roundToInt() + s.audioKbps} kbps", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Mint)
        Text("Video + audio target, excluding transport overhead. Requires matching streams or upstream transcoding.", color = MutedInk)
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
        Text("Only applies to titles marked as anime. Unlabelled results are excluded in Dub/Sub mode rather than misidentified. Dual-audio releases may qualify for both.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
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
        Text("Does not fetch magnet links, torrent pieces or encrypted subscription content.", color = MutedInk)
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("HTTPS file URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("File name") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.download(url, name) }, enabled = url.startsWith("https://") && name.isNotBlank()) { Text("Queue download") }
        Text("Saved to the app's external Downloads directory. Android handles network interruptions and notifications.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
    }
}

@Composable
private fun AboutSettings() {
    PanelTitle("Mutiny Media for Android", "Native Kotlin + Jetpack Compose architecture")
    MutinyPanel("Engineering notes") {
        Text("Android min SDK 26 • Android TV/handheld friendly focus targets • responsive layouts")
        Text("Media3 for direct HTTPS MP4/HLS/DASH playback. It cannot run Windows Tauri/Rust modules as-is.")
        Text("Nuvio account authentication, executable CloudStream extensions, P2P engines, watchlist sync, notification playback service and Android TV certification are planned integration points, not completed features.")
    }
}
