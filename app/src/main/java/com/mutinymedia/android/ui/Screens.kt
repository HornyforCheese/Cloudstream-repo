package com.mutinymedia.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import coil3.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutinymedia.android.MutinyViewModel
import com.mutinymedia.android.R
import com.mutinymedia.android.domain.*

@Composable
fun PanelTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MutinyPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
            content()
        }
    }
}

@Composable
fun HomeScreen(
    watchlist: List<MediaItem>,
    catalog: List<MediaItem>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAI: () -> Unit,
    onLibrary: () -> Unit,
    onSelectTitle: (MediaItem) -> Unit,
) {
    val shelves = remember(catalog) { buildHomeShelves(catalog) }
    val hero = catalog.firstOrNull { !it.poster.isNullOrBlank() }
    var category by rememberSaveable { mutableStateOf("Discover") }
    var expandedShelf by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedShelf = shelves.firstOrNull { it.title == expandedShelf }
    val categories = listOf("Discover", "Movies", "Series", "Anime", "Library")
    val filtered = when (category) {
        "Movies" -> catalog.filter(CatalogType.MOVIE::matches)
        "Series" -> catalog.filter(CatalogType.TV::matches)
        "Anime" -> catalog.filter(CatalogType.ANIME::matches)
        else -> emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { name ->
                    FilterChip(selected = category == name, onClick = {
                        expandedShelf = null
                        if (name == "Library") onLibrary() else category = name
                    }, label = { Text(name) })
                }
            }
        }
        if (selectedShelf != null) {
            item {
                TextButton(onClick = { expandedShelf = null }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("‹ All categories") }
            }
            item { PosterShelf(selectedShelf.title, selectedShelf.items, onSelectTitle, limit = selectedShelf.items.size) }
        } else if (category == "Discover") {
            if (hero != null) item {
                Box(Modifier.fillMaxWidth().height(240.dp).clickable { onSelectTitle(hero) }) {
                    AsyncImage(model = hero.poster, contentDescription = hero.title,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("FROM YOUR CATALOGS", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                        Text(hero.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                        Button(onClick = { onSelectTitle(hero) }) { Text("Explore streams") }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("From your enabled catalogs", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onRefresh, enabled = !loading) { Text("Refresh catalogs") }
                    }
                    if (catalog.isEmpty() && !loading) {
                        Text("Aha mateys. Enable a catalog addon or install a CloudStream catalog provider to fill these shelves.")
                        TextButton(onClick = onSettings) { Text("Connect providers") }
                    } else {
                        Text("Best lists use available provider ratings. New releases are ordered by date or year.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(shelves.filterIndexed { index, shelf -> index < 8 || shelf.items.isNotEmpty() }, key = { it.title }) { shelf ->
                PosterShelf(shelf.title, shelf.items, onSelectTitle, onMore = { expandedShelf = shelf.title })
            }
            if (watchlist.isNotEmpty()) item { PosterShelf("My Library", watchlist, onSelectTitle, onMore = onLibrary) }
        } else {
            item { PosterShelf(category, filtered, onSelectTitle, limit = filtered.size) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSearch, modifier = Modifier.weight(1f)) { Text("Search") }
                OutlinedButton(onClick = onAI, modifier = Modifier.weight(1f)) { Text("Ask AI") }
            }
        }
    }
}

@Composable
private fun PosterShelf(
    title: String,
    items: List<MediaItem>,
    onSelectTitle: (MediaItem) -> Unit,
    onMore: (() -> Unit)? = null,
    limit: Int = 20,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (onMore != null && items.size > limit) {
                TextButton(onClick = onMore) { Text("See all ›", color = MaterialTheme.colorScheme.secondary) }
            }
        }
        if (items.isEmpty()) {
            Text("No matching titles from your enabled catalogs yet.", modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items.take(limit), key = { "${it.type}:${it.id}" }) { item ->
                Column(Modifier.width(132.dp).clickable { onSelectTitle(item) }) {
                    Card(Modifier.fillMaxWidth().height(194.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("☠", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
                            item.poster?.takeIf(String::isNotBlank)?.let {
                                AsyncImage(model = it, contentDescription = "${item.title} poster",
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    val details = listOfNotNull(item.year?.toString(), item.rating?.let { String.format(java.util.Locale.ROOT, "★ %.1f", it) })
                    Text(details.joinToString(" · ").ifBlank { item.type.replaceFirstChar(Char::uppercase) },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun BrowseScreen(vm: MutinyViewModel, settings: UserSettings, addons: List<Addon>, detailOnly: Boolean = false, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val cloudStatuses by vm.cloudHostStatuses.collectAsStateWithLifecycle()
    val hasCloudCatalog = cloudStatuses.any { it.supportsCatalog }
    val enabledAddons = addons.filterNot { it.baseUrl in settings.excludedAddonUrls }
    val collections by vm.collections.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val selectedEpisode by vm.selectedEpisode.collectAsStateWithLifecycle()
    val progress by vm.playbackPositions.collectAsStateWithLifecycle()
    val streams by vm.streams.collectAsStateWithLifecycle()
    val current by vm.currentItem.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var directId by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("movie") }
    var anime by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (detailOnly) {
            TextButton(onClick = onBack) { Text("‹ Back to titles") }
            PanelTitle(current?.title ?: "Loading title…", "Episodes and available sources")
        } else {
        PanelTitle("Discover & play", "Browse CloudStream providers and Stremio-compatible addons, or look up a Stremio media ID.")
        if (enabledAddons.isEmpty() && cloudStatuses.none { it.providers.isNotEmpty() }) Text("Connect a provider under Settings → Integrations.", color = MaterialTheme.colorScheme.error)
        MutinyPanel("Provider catalogs") {
            OutlinedButton(onClick = { vm.browseCatalogs() }, enabled = !busy && (hasCloudCatalog || enabledAddons.any { it.supportsCatalog })) {
                Text("Browse catalogs")
            }
            CatalogResultRows(catalog.take(40), { vm.loadStreams(it) }, { vm.saveToWatchlist(it) })
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        MutinyPanel("Direct Stremio stream lookup") {
            OutlinedTextField(value = directId, onValueChange = { directId = it },
                label = { Text("Content ID (series: tt1234567:1:1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("movie", "series").forEach { option ->
                    FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option.replaceFirstChar(Char::uppercase)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This title is anime", modifier = Modifier.weight(1f))
                Switch(checked = anime, onCheckedChange = { anime = it })
            }
            Button(onClick = { vm.lookup(type, directId, anime || directId.startsWith("kitsu:") || directId.startsWith("mal:")) },
                enabled = !busy && directId.isNotBlank() && enabledAddons.any { it.supportsStreams }) { Text("Find available streams") }
        }
        }
        if (detailOnly && busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Loading title details and provider results…", style = MaterialTheme.typography.bodySmall)
        }
        if (detailOnly && current == null && !busy) {
            Text("No title is selected. Return to Home or Search and choose a title.")
        }
        if (current != null) {
            MutinyPanel("Title details") {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    current!!.poster?.let { poster ->
                        AsyncImage(model = poster, contentDescription = "${current!!.title} poster",
                            modifier = Modifier.size(width = 96.dp, height = 144.dp), contentScale = ContentScale.Crop)
                    }
                    Column {
                        Text(current!!.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(current!!.type.replaceFirstChar(Char::uppercase), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        current!!.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        if (episodes.isNotEmpty()) {
            var season by remember(episodes) { mutableIntStateOf(episodes.first().season) }
            MutinyPanel("Choose an episode") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    episodes.map { it.season }.distinct().forEach { value ->
                        FilterChip(selected = season == value, onClick = { season = value }, label = { Text("Season $value") })
                    }
                }
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    episodes.filter { it.season == season }.forEach { episode ->
                        TextButton(onClick = { vm.selectEpisode(episode) }, enabled = !busy) {
                            Text("${if (selectedEpisode == episode.id) "▶ " else ""}${episode.number}. ${episode.title}")
                        }
                    }
                }
            }
        }
        if (current != null) {
            val isAnime = current!!.isAnime
            val ranked = StreamFilters.select(streams, isAnime, settings)
            MutinyPanel("Sources for ${current!!.title}") {
                OutlinedButton(onClick = { vm.saveToWatchlist(current!!) }) { Text("Save title to watchlist") }
                if (collections.isNotEmpty()) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { open = true }) { Text("Add to collection") }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            collections.forEach { collection ->
                                DropdownMenuItem(text = { Text(collection.name) }, onClick = {
                                    open = false
                                    current?.let { vm.setCollectionTitle(collection.name, it, true) }
                                })
                            }
                        }
                    }
                }
                Text("${streams.size} sources returned • ${ranked.size} matched", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!busy && streams.isEmpty()) {
                    Text(
                        if (episodes.isNotEmpty() && selectedEpisode == null) "Select an episode above to search for streams."
                        else if (episodes.isEmpty() && current!!.type in listOf("series", "anime")) "No episode list available. Try a different metadata provider or title."
                        else "No streams returned. Check your streaming add-ons under Settings → Integrations and try again.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (streams.isNotEmpty() && streams.none { it.playable }) {
                    Text("No directly playable HTTPS sources. Torrent hashes can be resolved with a connected Real-Debrid account.", color = MaterialTheme.colorScheme.error)
                }
                if (isAnime) Text("Anime language mode: ${settings.animeMode.name}. Change in Settings → Playback.")
                settings.exactResolution?.let { Text("Resolution filter: ${it}p only") }
                val resumeAt = progress[vm.playbackKey(current!!, selectedEpisode)]
                if (resumeAt != null) Text("Playback resumes at ${resumeAt / 60_000}:${(resumeAt / 1000 % 60).toString().padStart(2, '0')}")
                if (settings.lowBandwidth) Text("Prioritising ≤${settings.resolution}p and ~${settings.videoKbps} kbps when providers supply metadata.", color = MaterialTheme.colorScheme.secondary)
                if (streams.isNotEmpty() && ranked.isEmpty()) Text("No sources match your filters. Check Settings → Playback and Integrations.", color = MaterialTheme.colorScheme.error)
                ranked.take(70).forEach { option ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(option.title, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(option.provider, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Button(onClick = { vm.play(option) }, enabled = option.playable, modifier = Modifier.fillMaxWidth()) {
                            Text(if (option.playable) "Play HTTPS stream" else "Torrent / unsupported stream")
                        }
                        if (option.playable || option.hasTorrent) {
                            OutlinedButton(onClick = { vm.playViaRealDebrid(option) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (option.hasTorrent) "Resolve torrent with Real-Debrid" else "Resolve with Real-Debrid")
                            }
                        }
                        option.externalUrl?.let { url ->
                            OutlinedButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    .onFailure { vm.report("No app is available to open this source") }
                            }) { Text("Open provider website") }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
                }
            }
        }
    }
}

@Composable
fun AiScreen(vm: MutinyViewModel, settings: UserSettings) {
    val messages by vm.chat.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var question by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelTitle("Ask AI", "OpenRouter-powered recommendations. Model: ${settings.openRouterModel}")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) Text("Configure your API key under Settings → Ask AI, then ask for film, TV or anime recommendations.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            messages.forEach { msg ->
                Surface(color = if (msg.fromUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(if (msg.fromUser) "You" else "Mutiny AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(5.dp))
                        Text(msg.text)
                    }
                }
            }
        }
        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Ask for a recommendation") },
            modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
        Button(onClick = { vm.sendMessage(question); question = "" }, enabled = question.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Thinking…" else "Send")
        }
    }
}

@Composable
fun LibraryScreen(vm: MutinyViewModel, onBack: () -> Unit, onBrowse: () -> Unit) {
    val saved by vm.watchlist.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteName by remember { mutableStateOf<String?>(null) }
    val collection = collections.firstOrNull { it.name == selected }
    val items = collection?.titles ?: saved
    val pendingDelete = deleteName
    if (pendingDelete != null) {
        AlertDialog(onDismissRequest = { deleteName = null }, title = { Text("Delete collection?") },
            text = { Text(pendingDelete) },
            confirmButton = { TextButton(onClick = { vm.deleteCollection(pendingDelete); selected = null; deleteName = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteName = null }) { Text("Cancel") } })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelTitle("My Library", "Watchlist and collections saved on this device.")
        OutlinedButton(onClick = onBack) { Text("Back") }
        MutinyPanel("Collections") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = collection == null, onClick = { selected = null }, label = { Text("Watchlist") })
                collections.forEach { group ->
                    FilterChip(selected = selected == group.name, onClick = { selected = group.name }, label = { Text(group.name) })
                }
            }
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Collection name") }, singleLine = true)
            Button(onClick = { vm.createCollection(name); name = "" }, enabled = name.isNotBlank()) { Text("Create collection") }
            if (collection != null) TextButton(onClick = { deleteName = collection.name }) { Text("Delete this collection") }
        }
        if (items.isEmpty()) Text("Save titles from Browse or Search to find them here later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { item ->
            MutinyPanel(item.title) {
                Text(item.type.replaceFirstChar(Char::uppercase), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.loadStreams(item); onBrowse() }) { Text("Find streams") }
                    OutlinedButton(onClick = {
                        if (collection == null) vm.removeFromWatchlist(item) else vm.setCollectionTitle(collection.name, item, false)
                    }) { Text("Remove") }
                }
            }
        }
    }
}
