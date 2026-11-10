package com.mutinymedia.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import java.lang.ref.WeakReference
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutinymedia.android.domain.UserSettings
import com.mutinymedia.android.ui.*

private enum class Section(val display: String, val glyph: String) {
    HOME("Home", "⌂"), BROWSE("Browse", "▣"), SEARCH("Search", "⌕"), AI("Ask AI", "✦"), SETTINGS("Settings", "⚙")
}

class MainActivity : ComponentActivity() {
    private val pluginActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VideoClickActionHolder.activity = WeakReference(this)
        VideoClickActionHolder.resultLauncher = { intent, options -> pluginActivityResult.launch(intent, options) }
        setContent { MutinyRoot() }
    }

    override fun onDestroy() {
        if (VideoClickActionHolder.activity?.get() === this) {
            VideoClickActionHolder.activity = null
            VideoClickActionHolder.resultLauncher = null
        }
        super.onDestroy()
    }
}

@Composable
fun MutinyRoot(vm: MutinyViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    MutinyTheme(settings.themePreset, settings.themeFont) { MutinyContent(vm, settings) }
}

@Composable
private fun MutinyContent(vm: MutinyViewModel, settings: UserSettings) {
    val addons by vm.addons.collectAsStateWithLifecycle()
    val watchlist by vm.watchlist.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val activeStream by vm.activeStream.collectAsStateWithLifecycle()
    val availableStreams by vm.streams.collectAsStateWithLifecycle()
    val currentItem by vm.currentItem.collectAsStateWithLifecycle()
    val selectedEpisode by vm.selectedEpisode.collectAsStateWithLifecycle()
    val isAnime = currentItem?.isAnime == true
    val playableStreams = remember(availableStreams, isAnime, settings) {
        com.mutinymedia.android.domain.StreamFilters.rankForBandwidth(
            com.mutinymedia.android.domain.StreamFilters.filterAnime(
                availableStreams, isAnime, settings.animeMode
            ), settings
        ).filter { it.playable }
    }
    var attemptedSources by remember(currentItem?.id, selectedEpisode) { mutableStateOf<Set<String>>(emptySet()) }
    fun nextSource(failed: com.mutinymedia.android.domain.StreamOption) {
        val attempted = attemptedSources + failed.id
        val next = playableStreams.firstOrNull { it.id !in attempted && it.id != failed.id }
        attemptedSources = attempted
        if (next == null) vm.report("Playback failed. No other playable HTTPS sources remain.")
        else {
            attemptedSources = attempted + next.id
            vm.activeStream.value = next
            vm.report("Trying alternative source: ${next.provider}")
        }
    }
    val searchState = rememberSaveableStateHolder()
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var playing by remember { mutableStateOf(false) }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var showTitleDetails by rememberSaveable { mutableStateOf(false) }
    BackHandler(playing || showTitleDetails || section != Section.HOME || showLibrary) {
        if (playing) { playing = false; attemptedSources = emptySet(); vm.activeStream.value = null }
        else if (showTitleDetails) showTitleDetails = false
        else if (showLibrary) showLibrary = false
        else section = Section.HOME
    }
    LaunchedEffect(activeStream) { if (activeStream != null) playing = true }
    if (playing && activeStream != null) {
        MutinyPlayer(activeStream!!, settings, onExit = { playing = false; attemptedSources = emptySet(); vm.activeStream.value = null },
            onPlaybackFailure = { nextSource(it) })
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        Scaffold(
            topBar = {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.mutiny_ship), contentDescription = null, modifier = Modifier.size(44.dp), contentScale = ContentScale.Fit)
                        Spacer(Modifier.width(9.dp))
                        Text("Mutiny Media", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("ANDROID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            },
            bottomBar = {
                if (!expanded) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Section.entries.forEach { s ->
                        NavigationBarItem(selected = section == s && !showLibrary,
                            onClick = { section = s; showLibrary = false; showTitleDetails = false },
                            icon = { Text(s.glyph, style = MaterialTheme.typography.titleMedium) },
                            label = { Text(s.display, maxLines = 1) })
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(remember { SnackbarHostState() })
            },
        ) { inner ->
            Row(Modifier.fillMaxSize().padding(inner)) {
                if (expanded) NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(20.dp))
                    Section.entries.forEach { s ->
                        NavigationRailItem(selected = section == s && !showLibrary,
                            onClick = { section = s; showLibrary = false; showTitleDetails = false },
                            icon = { Text(s.glyph, style = MaterialTheme.typography.titleLarge) },
                            label = { Text(s.display) })
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    if (msg != null) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(msg!!, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { vm.report(null) }) { Text("Dismiss") }
                            }
                        }
                    }
                    when {
                        showTitleDetails -> BrowseScreen(vm, settings, addons, detailOnly = true, onBack = { showTitleDetails = false })
                        showLibrary -> LibraryScreen(vm, onBack = { showLibrary = false }, onBrowse = { showLibrary = false; showTitleDetails = true })
                        section == Section.HOME -> HomeScreen(
                            watchlist = watchlist,
                            catalog = catalog,
                            onBrowse = { section = Section.BROWSE },
                            onSearch = { section = Section.SEARCH },
                            onSettings = { section = Section.SETTINGS },
                            onAI = { section = Section.AI },
                            onLibrary = { showLibrary = true },
                            onSelectTitle = { showTitleDetails = true; vm.loadStreams(it) },
                        )
                        section == Section.BROWSE -> BrowseScreen(vm, settings, addons)
                        section == Section.SEARCH -> searchState.SaveableStateProvider("catalog-search") {
                            SearchScreen(vm) { item ->
                                showTitleDetails = true
                                vm.loadStreams(item)
                            }
                        }
                        section == Section.AI -> AiScreen(vm, settings)
                        section == Section.SETTINGS -> SettingsScreen(vm, settings, addons)
                    }
                }
            }
        }
    }
}
