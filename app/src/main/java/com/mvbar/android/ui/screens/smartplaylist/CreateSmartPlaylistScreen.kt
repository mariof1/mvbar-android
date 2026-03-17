package com.mvbar.android.ui.screens.smartplaylist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.*
import com.mvbar.android.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val SORT_OPTIONS = listOf(
    "random" to "Random",
    "most_played" to "Most Played",
    "least_played" to "Least Played",
    "recently_played" to "Recently Played",
    "newest_added" to "Newest Added",
    "oldest_added" to "Oldest Added",
    "title_asc" to "Title A→Z",
    "title_desc" to "Title Z→A",
    "artist_asc" to "Artist A→Z",
    "album_asc" to "Album A→Z"
)

private val SLIDER_STEPS = listOf(25, 50, 100, 150, 200, 300, 400, 500, 750, 1000, 1500, 2000)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateSmartPlaylistScreen(
    genres: List<Genre>,
    onBack: () -> Unit,
    onCreate: (name: String, sort: String, filters: SmartPlaylistFilters) -> Unit,
    onSuggest: (suspend (kind: String, query: String) -> SuggestResponse)? = null,
    // Edit mode
    editId: Int? = null,
    initialName: String = "",
    initialSort: String = "random",
    initialFilters: SmartPlaylistFilters = SmartPlaylistFilters(),
    initialArtistNames: List<Pair<Int, String>> = emptyList(),
    onUpdate: ((id: Int, name: String, sort: String, filters: SmartPlaylistFilters) -> Unit)? = null
) {
    val isEdit = editId != null

    var name by remember { mutableStateOf(initialName) }
    var selectedSort by remember { mutableStateOf(initialSort) }
    var favoriteOnly by remember { mutableStateOf(initialFilters.favoriteOnly) }
    var maxResultsIndex by remember {
        mutableStateOf(SLIDER_STEPS.indexOfFirst { it >= initialFilters.maxResults }.takeIf { it >= 0 } ?: 7)
    }
    var sortExpanded by remember { mutableStateOf(false) }

    // Include filters
    val includeArtists = remember { mutableStateListOf<Pair<Int, String>>().apply { addAll(initialArtistNames.filter { it.first in initialFilters.include.artists.toSet() }) } }
    var includeArtistsMode by remember { mutableStateOf(initialFilters.include.artistsMode) }
    val includeAlbums = remember { mutableStateListOf<String>().apply { addAll(initialFilters.include.albums) } }
    val includeGenres = remember { mutableStateListOf<String>().apply { addAll(initialFilters.include.genres) } }
    var includeGenresMode by remember { mutableStateOf(initialFilters.include.genresMode) }
    val includeYears = remember { mutableStateListOf<Int>().apply { addAll(initialFilters.include.years) } }
    val includeCountries = remember { mutableStateListOf<String>().apply { addAll(initialFilters.include.countries) } }

    // Exclude filters
    val excludeArtists = remember { mutableStateListOf<Pair<Int, String>>().apply { addAll(initialArtistNames.filter { it.first in initialFilters.exclude.artists.toSet() }) } }
    val excludeAlbums = remember { mutableStateListOf<String>().apply { addAll(initialFilters.exclude.albums) } }
    val excludeGenres = remember { mutableStateListOf<String>().apply { addAll(initialFilters.exclude.genres) } }
    val excludeYears = remember { mutableStateListOf<Int>().apply { addAll(initialFilters.exclude.years) } }
    val excludeCountries = remember { mutableStateListOf<String>().apply { addAll(initialFilters.exclude.countries) } }

    // Duration
    var durationMin by remember { mutableStateOf(initialFilters.duration?.min?.toString() ?: "") }
    var durationMax by remember { mutableStateOf(initialFilters.duration?.max?.toString() ?: "") }

    fun buildFilters() = SmartPlaylistFilters(
        include = SmartFilterSet(
            artists = includeArtists.map { it.first },
            artistsMode = includeArtistsMode,
            albums = includeAlbums.toList(),
            genres = includeGenres.toList(),
            genresMode = includeGenresMode,
            years = includeYears.toList(),
            countries = includeCountries.toList()
        ),
        exclude = SmartFilterSet(
            artists = excludeArtists.map { it.first },
            albums = excludeAlbums.toList(),
            genres = excludeGenres.toList(),
            years = excludeYears.toList(),
            countries = excludeCountries.toList()
        ),
        duration = if (durationMin.isNotBlank() || durationMax.isNotBlank())
            SmartDuration(min = durationMin.toIntOrNull(), max = durationMax.toIntOrNull()) else null,
        favoriteOnly = favoriteOnly,
        maxResults = SLIDER_STEPS[maxResultsIndex]
    )

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isEdit) "Edit Smart Playlist" else "New Smart Playlist", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                val filters = buildFilters()
                                if (isEdit && editId != null) {
                                    onUpdate?.invoke(editId, name.trim(), selectedSort, filters)
                                } else {
                                    onCreate(name.trim(), selectedSort, filters)
                                }
                                onBack()
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Icon(
                            Icons.Filled.Check, "Create",
                            tint = if (name.isNotBlank()) Cyan500 else OnSurfaceDim
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Name
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Sort mode
            item {
                SectionLabel("Sort By")
                ExposedDropdownMenuBox(
                    expanded = sortExpanded,
                    onExpandedChange = { sortExpanded = it }
                ) {
                    OutlinedTextField(
                        value = SORT_OPTIONS.find { it.first == selectedSort }?.second ?: "Random",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                        colors = textFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        SORT_OPTIONS.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { selectedSort = key; sortExpanded = false }
                            )
                        }
                    }
                }
            }

            // Favorites only
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Favorites only", color = OnSurface)
                    Switch(
                        checked = favoriteOnly,
                        onCheckedChange = { favoriteOnly = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Cyan500,
                            checkedThumbColor = OnSurface
                        )
                    )
                }
            }

            // Max results
            item {
                Text(
                    "Max results: ${SLIDER_STEPS[maxResultsIndex]}",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurfaceDim
                )
                Slider(
                    value = maxResultsIndex.toFloat(),
                    onValueChange = { maxResultsIndex = it.toInt() },
                    valueRange = 0f..(SLIDER_STEPS.size - 1).toFloat(),
                    steps = SLIDER_STEPS.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = Cyan500,
                        activeTrackColor = Cyan500,
                        inactiveTrackColor = WhiteOverlay15
                    )
                )
            }

            // Duration
            item {
                SectionLabel("Duration (seconds)")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = durationMin,
                        onValueChange = { durationMin = it.filter { c -> c.isDigit() } },
                        label = { Text("Min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = durationMax,
                        onValueChange = { durationMax = it.filter { c -> c.isDigit() } },
                        label = { Text("Max") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── INCLUDE SECTION ──
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "INCLUDE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Cyan400
                )
                HorizontalDivider(color = Cyan500.copy(alpha = 0.3f))
            }

            // Include Artists
            item {
                SearchableChipSection(
                    label = "Artists",
                    items = includeArtists.map { it.second },
                    onRemove = { idx -> includeArtists.removeAt(idx) },
                    kind = "artist",
                    onSuggest = onSuggest,
                    onAddArtist = { id, name -> includeArtists.add(id to name) }
                )
                if (includeArtists.size > 1) {
                    ModeToggle(mode = includeArtistsMode, onToggle = {
                        includeArtistsMode = if (includeArtistsMode == "any") "all" else "any"
                    })
                }
            }

            // Include Albums
            item {
                SearchableChipSection(
                    label = "Albums",
                    items = includeAlbums.toList(),
                    onRemove = { idx -> includeAlbums.removeAt(idx) },
                    kind = "album",
                    onSuggest = onSuggest,
                    onAddString = { includeAlbums.add(it) }
                )
            }

            // Include Genres
            item {
                SearchableChipSection(
                    label = "Genres",
                    items = includeGenres.toList(),
                    onRemove = { idx -> includeGenres.removeAt(idx) },
                    kind = "genre",
                    onSuggest = onSuggest,
                    onAddString = { includeGenres.add(it) }
                )
                if (includeGenres.size > 1) {
                    ModeToggle(mode = includeGenresMode, onToggle = {
                        includeGenresMode = if (includeGenresMode == "any") "all" else "any"
                    })
                }
            }

            // Include Years
            item {
                SearchableChipSection(
                    label = "Years",
                    items = includeYears.map { it.toString() },
                    onRemove = { idx -> includeYears.removeAt(idx) },
                    kind = "year",
                    onSuggest = onSuggest,
                    onAddYear = { includeYears.add(it) }
                )
            }

            // Include Countries
            item {
                SearchableChipSection(
                    label = "Countries",
                    items = includeCountries.toList(),
                    onRemove = { idx -> includeCountries.removeAt(idx) },
                    kind = "country",
                    onSuggest = onSuggest,
                    onAddString = { includeCountries.add(it) }
                )
            }

            // ── EXCLUDE SECTION ──
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "EXCLUDE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            }

            // Exclude Artists
            item {
                SearchableChipSection(
                    label = "Artists",
                    items = excludeArtists.map { it.second },
                    onRemove = { idx -> excludeArtists.removeAt(idx) },
                    kind = "artist",
                    onSuggest = onSuggest,
                    onAddArtist = { id, name -> excludeArtists.add(id to name) }
                )
            }

            // Exclude Albums
            item {
                SearchableChipSection(
                    label = "Albums",
                    items = excludeAlbums.toList(),
                    onRemove = { idx -> excludeAlbums.removeAt(idx) },
                    kind = "album",
                    onSuggest = onSuggest,
                    onAddString = { excludeAlbums.add(it) }
                )
            }

            // Exclude Genres
            item {
                SearchableChipSection(
                    label = "Genres",
                    items = excludeGenres.toList(),
                    onRemove = { idx -> excludeGenres.removeAt(idx) },
                    kind = "genre",
                    onSuggest = onSuggest,
                    onAddString = { excludeGenres.add(it) }
                )
            }

            // Exclude Years
            item {
                SearchableChipSection(
                    label = "Years",
                    items = excludeYears.map { it.toString() },
                    onRemove = { idx -> excludeYears.removeAt(idx) },
                    kind = "year",
                    onSuggest = onSuggest,
                    onAddYear = { excludeYears.add(it) }
                )
            }

            // Exclude Countries
            item {
                SearchableChipSection(
                    label = "Countries",
                    items = excludeCountries.toList(),
                    onRemove = { idx -> excludeCountries.removeAt(idx) },
                    kind = "country",
                    onSuggest = onSuggest,
                    onAddString = { excludeCountries.add(it) }
                )
            }

            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

// ── Reusable Components ──

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = OnSurfaceDim
    )
}

@Composable
private fun ModeToggle(mode: String, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text("Match ", color = OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onToggle) {
            Text(
                if (mode == "any") "ANY ▾" else "ALL ▾",
                color = Cyan400,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchableChipSection(
    label: String,
    items: List<String>,
    onRemove: (Int) -> Unit,
    kind: String,
    onSuggest: (suspend (String, String) -> SuggestResponse)?,
    onAddString: ((String) -> Unit)? = null,
    onAddArtist: ((Int, String) -> Unit)? = null,
    onAddYear: ((Int) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Any>>(emptyList()) }
    var showSearch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("$label (${items.size})")
            IconButton(onClick = { showSearch = !showSearch }) {
                Icon(
                    if (showSearch) Icons.Filled.Close else Icons.Filled.Add,
                    "Toggle search",
                    tint = Cyan400
                )
            }
        }

        // Selected chips
        if (items.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items.forEachIndexed { idx, item ->
                    InputChip(
                        selected = true,
                        onClick = { onRemove(idx) },
                        label = { Text(item, style = MaterialTheme.typography.bodySmall) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(16.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = Cyan500.copy(alpha = 0.2f),
                            selectedLabelColor = Cyan400
                        )
                    )
                }
            }
        }

        // Search field with autocomplete
        AnimatedVisibility(visible = showSearch) {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        searchJob?.cancel()
                        if (query.length >= 1 && onSuggest != null) {
                            searchJob = scope.launch {
                                delay(300) // debounce
                                try {
                                    val resp = onSuggest(kind, query)
                                    suggestions = parseSuggestions(resp, kind)
                                } catch (_: Exception) {
                                    suggestions = emptyList()
                                }
                            }
                        } else {
                            suggestions = emptyList()
                        }
                    },
                    placeholder = { Text("Search ${label.lowercase()}...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = if (kind == "year") KeyboardType.Number else KeyboardType.Unspecified
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        // For year, allow typing directly
                        if (kind == "year" && searchQuery.isNotBlank()) {
                            searchQuery.toIntOrNull()?.let { year ->
                                onAddYear?.invoke(year)
                                searchQuery = ""
                                suggestions = emptyList()
                            }
                        }
                    }),
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Suggestion dropdown
                if (suggestions.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.heightIn(max = 200.dp)) {
                            suggestions.take(10).forEach { suggestion ->
                                val displayText = when (suggestion) {
                                    is Pair<*, *> -> suggestion.second as String
                                    is Int -> suggestion.toString()
                                    else -> suggestion.toString()
                                }
                                Text(
                                    text = displayText,
                                    color = OnSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when {
                                                kind == "artist" && suggestion is Pair<*, *> -> {
                                                    @Suppress("UNCHECKED_CAST")
                                                    val pair = suggestion as Pair<Int, String>
                                                    onAddArtist?.invoke(pair.first, pair.second)
                                                }
                                                kind == "year" && suggestion is Int -> {
                                                    onAddYear?.invoke(suggestion)
                                                }
                                                else -> {
                                                    onAddString?.invoke(displayText)
                                                }
                                            }
                                            searchQuery = ""
                                            suggestions = emptyList()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseSuggestions(resp: SuggestResponse, kind: String): List<Any> {
    return resp.items.mapNotNull { element ->
        try {
            when (kind) {
                "artist" -> {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    Pair(id, name)
                }
                "year" -> {
                    element.jsonPrimitive.intOrNull
                }
                else -> {
                    element.jsonPrimitive.content
                }
            }
        } catch (_: Exception) { null }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan500,
    unfocusedBorderColor = OnSurfaceDim,
    focusedLabelColor = Cyan500,
    cursorColor = Cyan500,
    focusedTextColor = OnSurface,
    unfocusedTextColor = OnSurface
)
