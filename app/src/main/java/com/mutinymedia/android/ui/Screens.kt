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
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
    }
}

@Composable
fun MutinyPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = RaisedPurple),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Mint)
            content()
        }
    }
}

@Composable
fun HomeScreen(
    watchlist: List<MediaItem>,
    catalog: List<MediaItem>,
    onBrowse: () -> Unit,
    onSettings: () -> Unit,
    onAI: () -> Unit,
    onLibrary: () -> Unit,
    onSelectTitle: (MediaItem) -> Unit,
) {
    val featured = (watchlist + catalog).distinctBy { it.type to it.id }.filter { !it.poster.isNullOrBlank() }
    val anime = featured.filter { it.isAnime || it.type == "anime" }
    val movies = featured.filter { it.type == "movie" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text("Aha, mateys!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Mint)
            Text("Your next adventure awaits", color = MutedInk)
        }
        if (featured.isNotEmpty()) {
            val hero = featured.first()
            Box(Modifier.fillMaxWidth().height(225.dp).clickable { onSelectTitle(hero) }) {
                AsyncImage(model = hero.poster, contentDescription = hero.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f)))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text("FEATURED • MUTINY MEDIA", color = Mint, style = MaterialTheme.typography.labelMedium)
                    Text(hero.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text("Explore available streams  ›", color = Color.White)
                }
            }
        } else {
            MutinyPanel("Discover your next adventure", Modifier.padding(horizontal = 16.dp)) {
                Text("Add a Stremio catalog or import your account to populate your home screen with posters and titles.")
                Button(onClick = onBrowse) { Text("Discover titles") }
            }
        }
        if (watchlist.isNotEmpty()) {
            PosterShelf("From your library", watchlist, onSelectTitle, onLibrary)
        }
        if (anime.isNotEmpty()) PosterShelf("Anime voyages", anime, onSelectTitle, onBrowse)
        if (movies.isNotEmpty()) PosterShelf("Movies to explore", movies, onSelectTitle, onBrowse)
        if (catalog.isNotEmpty()) PosterShelf("Recently discovered", catalog, onSelectTitle, onBrowse)
        if (featured.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLibrary, modifier = Modifier.weight(1f)) { Text("Library") }
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Integrations") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBrowse, modifier = Modifier.weight(1f)) { Text("Search") }
            OutlinedButton(onClick = onAI, modifier = Modifier.weight(1f)) { Text("Ask AI") }
        }
    }
}

@Composable
private fun PosterShelf(title: String, items: List<MediaItem>, onSelectTitle: (MediaItem) -> Unit, onMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onMore) { Text("See all ›", color = Mint) }
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
                                Text("☠", style = MaterialTheme.typography.headlineLarge, color = Mint)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(item.type.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall, color = MutedInk)
                }
            }
        }
    }
}

@Composable
fun BrowseScreen(vm: MutinyViewModel, settings: UserSettings, addons: List<Addon>) {
    val context = LocalContext.current
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val selectedEpisode by vm.selectedEpisode.collectAsStateWithLifecycle()
    val streams by vm.streams.collectAsStateWithLifecycle()
    val current by vm.currentItem.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var directId by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("movie") }
    var anime by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PanelTitle("Discover & play", "Find streams using installed Stremio addons or a direct media ID.")
        if (addons.isEmpty()) Text("No Stremio addons connected. Open Settings → Integrations to add a provider.", color = MaterialTheme.colorScheme.error)
        MutinyPanel("Catalog search") {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Title search (if addon supports it)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.searchCatalog(query) }, enabled = !busy && query.isNotBlank()) { Text("Search titles") }
            catalog.take(40).forEach { item ->
                Row(Modifier.fillMaxWidth().clickable { vm.loadStreams(item) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!item.poster.isNullOrBlank()) {
                        AsyncImage(model = item.poster, contentDescription = "${item.title} poster", modifier = Modifier.size(width = 72.dp, height = 106.dp), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Medium)
                        Text("${item.type} • ${item.id}${if (item.isAnime) " • Anime" else ""}", style = MaterialTheme.typography.labelSmall, color = MutedInk)
                        TextButton(onClick = { vm.saveToWatchlist(item) }) { Text("Save to watchlist") }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
            }
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
                Text("${streams.size} sources returned • ${ranked.size} matched", color = MutedInk)
                if (isAnime) Text("Anime language mode: ${settings.animeMode.name}. Change in Settings → Anime.")
                if (settings.lowBandwidth) Text("Prioritising ≤${settings.resolution}p and ~${settings.videoKbps} kbps when providers supply metadata.", color = Mint)
                if (streams.isNotEmpty() && ranked.isEmpty()) Text("No verified ${settings.animeMode.name.lowercase()} tags. Switch to All in Anime settings to reveal unlabelled streams.", color = MaterialTheme.colorScheme.error)
                ranked.take(70).forEach { option ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(option.title, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(option.provider, color = MutedInk, style = MaterialTheme.typography.labelSmall)
                        Button(onClick = { vm.play(option) }, enabled = option.playable, modifier = Modifier.fillMaxWidth()) {
                            Text(if (option.playable) "Play HTTPS stream" else "Torrent / unsupported stream")
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
fun GamesScreen(vm: MutinyViewModel) {
    val games by vm.games.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelTitle("Games", "Launch installed Android games. Windows game executables cannot run natively here.")
        Button(onClick = { vm.refreshGames() }) { Text("Refresh installed games") }
        if (games.isEmpty()) Text("No installed launchable games are labelled as games by Android's package manager.", color = MutedInk)
        games.forEach { game ->
            MutinyPanel(game.name) {
                Text(game.packageName, style = MaterialTheme.typography.bodySmall, color = MutedInk)
                Button(onClick = { if (!vm.android.launchGame(game)) vm.report("Cannot launch ${game.name}") }) { Text("Launch game") }
            }
        }
        MutinyPanel("Remote game streaming") {
            Text("For PC gameplay, use a separate Sunshine/Moonlight setup. Native Android torrent-game streaming is not part of this build.")
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
            if (messages.isEmpty()) Text("Configure your API key under Settings → Ask AI, then ask for film, anime or game recommendations.", color = MutedInk)
            messages.forEach { msg ->
                Surface(color = if (msg.fromUser) MaterialTheme.colorScheme.surfaceVariant else RaisedPurple, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(if (msg.fromUser) "You" else "Mutiny AI", style = MaterialTheme.typography.labelSmall, color = Mint)
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
        if (saved.isEmpty()) Text("Your watchlist is empty. Save a title from Browse to find it here later.", color = MutedInk)
        saved.forEach { item ->
            MutinyPanel(item.title) {
                Text("${item.type} • ${item.id}", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.loadStreams(item); onBrowse() }) { Text("Find streams") }
                    OutlinedButton(onClick = { vm.removeFromWatchlist(item) }) { Text("Remove") }
                }
            }
        }
    }
}
