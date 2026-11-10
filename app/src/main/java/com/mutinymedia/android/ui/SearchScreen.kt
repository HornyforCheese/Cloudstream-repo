package com.mutinymedia.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mutinymedia.android.MutinyViewModel
import com.mutinymedia.android.domain.CatalogFilters
import com.mutinymedia.android.domain.CatalogType
import com.mutinymedia.android.domain.MediaItem

@Composable
fun SearchScreen(vm: MutinyViewModel, onSelect: (MediaItem) -> Unit) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val watchlist by vm.watchlist.collectAsStateWithLifecycle()
    val result by vm.catalogSearch.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(CatalogType.ALL) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var genreMenu by remember { mutableStateOf(false) }
    var fromYear by rememberSaveable { mutableStateOf("") }
    var toYear by rememberSaveable { mutableStateOf("") }
    var shown by rememberSaveable { mutableIntStateOf(30) }

    val from = fromYear.toIntOrNull()
    val to = toYear.toIntOrNull()
    val invalidYears = (fromYear.isNotEmpty() && (from == null || from !in 1800..2099)) ||
        (toYear.isNotEmpty() && (to == null || to !in 1800..2099)) ||
        (from != null && to != null && from > to)
    val filters = CatalogFilters(type, genre, from, to)
    val saved = remember(catalog, watchlist) { (catalog + watchlist).distinctBy { it.type to it.id } }
    val genres = remember(saved, result, genre) {
        (saved + result?.items.orEmpty()).flatMap { it.genres }.plus(listOfNotNull(genre))
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }
    val visible = remember(saved, result, filters, invalidYears) {
        if (invalidYears) emptyList() else (result?.items ?: saved).filter(filters::matches)
    }

    fun searchTitles() {
        if (busy || invalidYears || query.isBlank()) return
        focus.clearFocus()
        shown = 30
        vm.searchTitles(query)
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PanelTitle("Search", "Find titles or describe what you want to watch.")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                placeholder = { Text("Title, actor, or idea") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchTitles() }),
            )
            Button(onClick = {
                focus.clearFocus()
                shown = 30
                vm.searchCatalogWithAi(query, filters)
            }, enabled = !busy && !invalidYears && query.isNotBlank(), modifier = Modifier.testTag("catalog-ask-ai")) { Text("Ask AI") }
        }
        Text("Ask AI searches your saved catalog titles and watchlist. The request and candidate title metadata are sent to OpenRouter.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { searchTitles() }, enabled = !busy && !invalidYears && query.isNotBlank()) {
                Text("Search titles")
            }
            OutlinedButton(onClick = {
                focus.clearFocus()
                shown = 30
                vm.clearCatalogSearch()
                vm.browseCatalogs()
            }, enabled = !busy) { Text("Load saved catalogs") }
        }

        MutinyPanel("Filters") {
            Text("Type", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogType.entries.forEach { choice ->
                    FilterChip(selected = type == choice, onClick = { type = choice; shown = 30 },
                        label = { Text(choice.label) })
                }
            }
            Box {
                OutlinedButton(onClick = { genreMenu = true }, enabled = genres.isNotEmpty()) {
                    Text(genre ?: "Any genre")
                }
                DropdownMenu(expanded = genreMenu, onDismissRequest = { genreMenu = false },
                    modifier = Modifier.heightIn(max = 360.dp)) {
                    DropdownMenuItem(text = { Text("Any genre") }, onClick = { genre = null; genreMenu = false; shown = 30 })
                    genres.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice) }, onClick = { genre = choice; genreMenu = false; shown = 30 })
                    }
                }
            }
            if (genres.isEmpty()) Text("Genres appear when supplied by catalog entries.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fromYear, { fromYear = it.filter(Char::isDigit).take(4); shown = 30 },
                    label = { Text("From year") }, singleLine = true, isError = invalidYears,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(toYear, { toYear = it.filter(Char::isDigit).take(4); shown = 30 },
                    label = { Text("To year") }, singleLine = true, isError = invalidYears,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            if (invalidYears) Text("Enter valid years in chronological order.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { type = CatalogType.ALL; genre = null; fromYear = ""; toYear = ""; shown = 30 }) {
                Text("Clear filters")
            }
        }

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val current = result
        val heading = when {
            current == null -> "Saved catalogs and watchlist"
            current.aiCandidates != null -> "AI results for “${current.query}”"
            else -> "Title results for “${current.query}”"
        }
        MutinyPanel(heading) {
            if (current?.aiCandidates != null) {
                Text("AI reviewed ${current.aiCandidates} of ${current.totalCandidates} eligible catalog entries.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (visible.isEmpty() && !busy && !invalidYears) Text("No titles match. Try different filters or load more catalog entries.")
            if (visible.isNotEmpty()) Text("${visible.size} results", style = MaterialTheme.typography.bodySmall)
            CatalogResultRows(visible.take(shown), onSelect) { vm.saveToWatchlist(it) }
            if (visible.size > shown) TextButton(onClick = { shown += 30 }) { Text("Show more") }
        }
    }
}

@Composable
internal fun CatalogResultRows(
    items: List<MediaItem>,
    onSelect: (MediaItem) -> Unit,
    onSave: (MediaItem) -> Unit,
) {
    items.forEach { item ->
        Row(Modifier.fillMaxWidth().clickable { onSelect(item) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!item.poster.isNullOrBlank()) {
                AsyncImage(model = item.poster, contentDescription = "${item.title} poster", modifier = Modifier.size(width = 72.dp, height = 106.dp), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Medium)
                val type = if (item.isAnime || item.type == "anime") "Anime" else if (item.type == "movie") "Movie" else "TV"
                val details = listOfNotNull(type, item.year?.toString()) + item.genres.take(3)
                Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onSave(item) }) { Text("Save to watchlist") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
    }
}
