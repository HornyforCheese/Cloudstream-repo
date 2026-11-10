package com.mutinymedia.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import coil3.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    onBrowse: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAI: () -> Unit,
    onLibrary: () -> Unit,
    onSelectTitle: (MediaItem) -> Unit,
) {
    val items = (catalog + watchlist).distinctBy { it.type to it.id }
    val featured = items.filter { !it.poster.isNullOrBlank() }
    val anime = items.filter { it.isAnime || it.type == "anime" }
    val movies = items.filter { it.type == "movie" }
    val series = items.filter { it.type == "series" }
    var category by rememberSaveable { mutableStateOf("Discover") }
    val categories = listOf("Discover", "Movies", "Series", "Anime", "Library")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { name ->
                FilterChip(selected = category == name, onClick = {
                    category = name
                    if (name == "Library") onLibrary()
                }, label = { Text(name) })
            }
        }
        if (category == "Discover" && featured.isNotEmpty()) {
            val hero = featured.first()
            Box(Modifier.fillMaxWidth().height(290.dp).clickable { onSelectTitle(hero) }) {
                AsyncImage(model = hero.poster, contentDescription = hero.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FEATURED ON MUTINY MEDIA", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                    Text(hero.title, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, maxLines = 2)
                    hero.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = { onSelectTitle(hero) }) { Text("Explore streams") }
                }
            }
        }
        if (items.isEmpty()) {
            MutinyPanel("Welcome aboard", Modifier.padding(horizontal = 16.dp)) {
                Text("Connect a Stremio catalog or import your library to see movies, series and anime here.")
                Button(onClick = onSearch) { Text("Search for titles") }
                OutlinedButton(onClick = onSettings) { Text("Connect providers") }
            }
        }
        if (category == "Discover") {
            if (watchlist.isNotEmpty()) PosterShelf("Continue exploring • My Library", watchlist, onSelectTitle, onLibrary)
            if (catalog.isNotEmpty()) PosterShelf("Discover", catalog, onSelectTitle, onBrowse)
            if (movies.isNotEmpty()) PosterShelf("Movies", movies, onSelectTitle, onBrowse)
            if (series.isNotEmpty()) PosterShelf("Series", series, onSelectTitle, onBrowse)
            if (anime.isNotEmpty()) PosterShelf("Anime", anime, onSelectTitle, onBrowse)
        } else {
            val filtered = when (category) {
                "Movies" -> movies
                "Series" -> series
                "Anime" -> anime
                else -> watchlist
            }
            if (filtered.isNotEmpty()) PosterShelf(category, filtered, onSelectTitle, onBrowse)
            else Text("No $category titles loaded yet. Search to discover more.",
                modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSearch, modifier = Modifier.weight(1f)) { Text("Search") }
            OutlinedButton(onClick = onAI, modifier = Modifier.weight(1f)) { Text("Ask AI") }
        }
    }
}

@Composable
private fun PosterShelf(title: String, items: List<MediaItem>, onSelectTitle: (MediaItem) -> Unit, onMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onMore) { Text("See all ›", color = MaterialTheme.colorScheme.secondary) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.distinctBy { it.type to it.id }.take(20).forEach { item ->
                Column(Modifier.width(132.dp).clickable { onSelectTitle(item) }) {
                    if (!item.poster.isNullOrBlank()) {
                        Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                            AsyncImage(model = item.poster, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(194.dp))
                        }
                    } else {
                        Card(Modifier.fillMaxWidth().height(194.dp)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("☠", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(item.type.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun BrowseScreen(vm: MutinyViewModel, settings: UserSettings, addons: List<Addon>, detailOnly: Boolean = false, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val selectedEpisode by vm.selectedEpisode.collectAsStateWithLifecycle()
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
        PanelTitle("Discover & play", "Find streams using installed Stremio addons or a direct media ID.")
        if (addons.isEmpty()) Text("No Stremio addons connected. Open Settings → Integrations to add a provider.", color = MaterialTheme.colorScheme.error)
        MutinyPanel("Provider catalogs") {
            OutlinedButton(onClick = { vm.browseCatalogs() }, enabled = !busy && addons.any { it.supportsCatalog }) {
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
                enabled = !busy && directId.isNotBlank() && addons.isNotEmpty()) { Text("Find available streams") }
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
            val filtered = StreamFilters.filterAnime(streams, isAnime, settings.animeMode)
            val ranked = StreamFilters.rankForBandwidth(filtered, settings)
            MutinyPanel("Sources for ${current!!.title}") {
                OutlinedButton(onClick = { vm.saveToWatchlist(current!!) }) { Text("Save title to watchlist") }
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
                    Text("Providers returned sources, but none can play directly. Torrent sources require an unsupported engine.", color = MaterialTheme.colorScheme.error)
                }
                if (isAnime) Text("Anime language mode: ${settings.animeMode.name}. Change in Settings → Anime.")
                if (settings.lowBandwidth) Text("Prioritising ≤${settings.resolution}p and ~${settings.videoKbps} kbps when providers supply metadata.", color = MaterialTheme.colorScheme.secondary)
                if (streams.isNotEmpty() && ranked.isEmpty()) Text("No verified ${settings.animeMode.name.lowercase()} tags. Switch to All in Anime settings to reveal unlabelled streams.", color = MaterialTheme.colorScheme.error)
                ranked.take(70).forEach { option ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(option.title, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(option.provider, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Button(onClick = { vm.play(option) }, enabled = option.playable, modifier = Modifier.fillMaxWidth()) {
                            Text(if (option.playable) "Play HTTPS stream" else "Torrent / unsupported stream")
                        }
                        if (!option.url.isNullOrBlank() && option.url.startsWith("https://")) {
                            OutlinedButton(onClick = { vm.playViaRealDebrid(option) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Resolve with Real-Debrid")
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelTitle("My Library", "Saved locally on this device. No external account synchronization.")
        OutlinedButton(onClick = onBack) { Text("Back") }
        if (saved.isEmpty()) Text("Your watchlist is empty. Save a title from Browse to find it here later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        saved.forEach { item ->
            MutinyPanel(item.title) {
                Text("${item.type} • ${item.id}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.loadStreams(item); onBrowse() }) { Text("Find streams") }
                    OutlinedButton(onClick = { vm.removeFromWatchlist(item) }) { Text("Remove") }
                }
            }
        }
    }
}
