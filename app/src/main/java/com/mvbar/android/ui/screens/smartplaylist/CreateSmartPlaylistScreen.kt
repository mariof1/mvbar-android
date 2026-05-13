package com.mvbar.android.ui.screens.smartplaylist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    "title_asc" to "Title (A-Z)",
    "title_desc" to "Title (Z-A)",
    "artist_asc" to "Artist",
    "album_asc" to "Album"
)

private val SLIDER_STEPS = listOf(25, 50, 100, 150, 200, 300, 400, 500, 750, 1000, 1500, 2000)

// Card tint colors (approximating Tailwind slate/emerald/red used on web)
private val CardSlateBg = Color(0x4D1E293B)        // slate-800 @ 30%
private val CardSlateBorder = Color(0x4D334155)    // slate-700 @ 30%
private val CardEmeraldBg = Color(0x33064E3B)      // emerald-900 @ 20%
private val CardEmeraldBorder = Color(0x80065F46)  // emerald-800 @ 50%
private val EmeraldAccent = Color(0xFF34D399)      // emerald-400
private val CardRedBg = Color(0x337F1D1D)          // red-900 @ 20%
private val CardRedBorder = Color(0x80991B1B)      // red-800 @ 50%
private val RedAccent = Color(0xFFF87171)          // red-400

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateSmartPlaylistScreen(
    genres: List<Genre>,
    onBack: () -> Unit,
    onCreate: (name: String, sort: String, filters: SmartPlaylistFilters) -> Unit,
    onSuggest: (suspend (kind: String, query: String) -> SuggestResponse)? = null,
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

    val includeArtists = remember { mutableStateListOf<Pair<Int, String>>().apply { addAll(initialArtistNames.filter { it.first in initialFilters.include.artists.toSet() }) } }
    var includeArtistsMode by remember { mutableStateOf(initialFilters.include.artistsMode) }
    val includeAlbums = remember { mutableStateListOf<String>().apply { addAll(initialFilters.include.albums) } }
    val includeGenres = remember { mutableStateListOf<String>().apply { addAll(initialFilters.include.genres) } }
    var includeGenresMode by remember { mutableStateOf(initialFilters.include.genresMode) }
    val includeYears = remember { mutableStateListOf<Int>().apply { addAll(initialFilters.include.years) } }
    val includeCountries = remember { mutableStateListOf<String>().apply { addAll(initialFilters.include.countries) } }

    val excludeArtists = remember { mutableStateListOf<Pair<Int, String>>().apply { addAll(initialArtistNames.filter { it.first in initialFilters.exclude.artists.toSet() }) } }
    val excludeAlbums = remember { mutableStateListOf<String>().apply { addAll(initialFilters.exclude.albums) } }
    val excludeGenres = remember { mutableStateListOf<String>().apply { addAll(initialFilters.exclude.genres) } }
    val excludeYears = remember { mutableStateListOf<Int>().apply { addAll(initialFilters.exclude.years) } }
    val excludeCountries = remember { mutableStateListOf<String>().apply { addAll(initialFilters.exclude.countries) } }

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

    fun submit() {
        if (name.isBlank()) return
        val filters = buildFilters()
        if (isEdit && editId != null) {
            onUpdate?.invoke(editId, name.trim(), selectedSort, filters)
        } else {
            onCreate(name.trim(), selectedSort, filters)
        }
        onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General card
            SectionCard(
                bg = CardSlateBg,
                border = CardSlateBorder,
                title = "General",
                titleColor = OnSurface
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("My Smart Playlist") },
                    singleLine = true,
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                Text("Sort Order", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
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

                Spacer(Modifier.height(4.dp))
                Text(
                    "Max Tracks: ${SLIDER_STEPS[maxResultsIndex]}",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant
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

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = durationMin,
                        onValueChange = { durationMin = it.filter { c -> c.isDigit() } },
                        label = { Text("Min Duration (s)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = durationMax,
                        onValueChange = { durationMax = it.filter { c -> c.isDigit() } },
                        label = { Text("Max Duration (s)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Only include favorites", color = OnSurface)
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

            // Include card
            SectionCard(
                bg = CardEmeraldBg,
                border = CardEmeraldBorder,
                title = "Include Rules",
                titleColor = EmeraldAccent,
                titleIcon = Icons.Filled.Add
            ) {
                SearchableChipSection("Artists", includeArtists.map { it.second },
                    onRemove = { idx -> includeArtists.removeAt(idx) },
                    kind = "artist", onSuggest = onSuggest,
                    onAddArtist = { id, n -> includeArtists.add(id to n) },
                    accent = EmeraldAccent)
                if (includeArtists.size > 1) {
                    ModeToggle(includeArtistsMode, accent = EmeraldAccent) {
                        includeArtistsMode = if (includeArtistsMode == "any") "all" else "any"
                    }
                }
                SearchableChipSection("Albums", includeAlbums.toList(),
                    onRemove = { idx -> includeAlbums.removeAt(idx) },
                    kind = "album", onSuggest = onSuggest,
                    onAddString = { includeAlbums.add(it) },
                    accent = EmeraldAccent)
                SearchableChipSection("Genres", includeGenres.toList(),
                    onRemove = { idx -> includeGenres.removeAt(idx) },
                    kind = "genre", onSuggest = onSuggest,
                    onAddString = { includeGenres.add(it) },
                    accent = EmeraldAccent)
                if (includeGenres.size > 1) {
                    ModeToggle(includeGenresMode, accent = EmeraldAccent) {
                        includeGenresMode = if (includeGenresMode == "any") "all" else "any"
                    }
                }
                SearchableChipSection("Years", includeYears.map { it.toString() },
                    onRemove = { idx -> includeYears.removeAt(idx) },
                    kind = "year", onSuggest = onSuggest,
                    onAddYear = { includeYears.add(it) },
                    accent = EmeraldAccent)
                SearchableChipSection("Countries", includeCountries.toList(),
                    onRemove = { idx -> includeCountries.removeAt(idx) },
                    kind = "country", onSuggest = onSuggest,
                    onAddString = { includeCountries.add(it) },
                    accent = EmeraldAccent)
            }

            // Exclude card
            SectionCard(
                bg = CardRedBg,
                border = CardRedBorder,
                title = "Exclude Rules",
                titleColor = RedAccent,
                titleIcon = Icons.Filled.Remove
            ) {
                SearchableChipSection("Artists", excludeArtists.map { it.second },
                    onRemove = { idx -> excludeArtists.removeAt(idx) },
                    kind = "artist", onSuggest = onSuggest,
                    onAddArtist = { id, n -> excludeArtists.add(id to n) },
                    accent = RedAccent)
                SearchableChipSection("Albums", excludeAlbums.toList(),
                    onRemove = { idx -> excludeAlbums.removeAt(idx) },
                    kind = "album", onSuggest = onSuggest,
                    onAddString = { excludeAlbums.add(it) },
                    accent = RedAccent)
                SearchableChipSection("Genres", excludeGenres.toList(),
                    onRemove = { idx -> excludeGenres.removeAt(idx) },
                    kind = "genre", onSuggest = onSuggest,
                    onAddString = { excludeGenres.add(it) },
                    accent = RedAccent)
                SearchableChipSection("Years", excludeYears.map { it.toString() },
                    onRemove = { idx -> excludeYears.removeAt(idx) },
                    kind = "year", onSuggest = onSuggest,
                    onAddYear = { excludeYears.add(it) },
                    accent = RedAccent)
                SearchableChipSection("Countries", excludeCountries.toList(),
                    onRemove = { idx -> excludeCountries.removeAt(idx) },
                    kind = "country", onSuggest = onSuggest,
                    onAddString = { excludeCountries.add(it) },
                    accent = RedAccent)
            }

            Spacer(Modifier.height(8.dp))
        }

        // Sticky bottom action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = ::submit,
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan500,
                    contentColor = OnSurface,
                    disabledContainerColor = Cyan500.copy(alpha = 0.4f)
                ),
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (isEdit) "Save Changes" else "Create Playlist",
                    fontWeight = FontWeight.Medium
                )
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = OnSurface)
            }
        }
    }
}

@Composable
private fun SectionCard(
    bg: Color,
    border: Color,
    title: String,
    titleColor: Color,
    titleIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (titleIcon != null) {
                Icon(titleIcon, null, tint = titleColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
        }
        content()
    }
}

@Composable
private fun ModeToggle(mode: String, accent: Color, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text("Match ", color = OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
        TextButton(
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                if (mode == "any") "ANY ▾" else "ALL ▾",
                color = accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall
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
    onAddYear: ((Int) -> Unit)? = null,
    accent: Color = Cyan400
) {
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Any>>(emptyList()) }
    var showSearch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$label${if (items.isNotEmpty()) " (${items.size})" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant
            )
            IconButton(
                onClick = { showSearch = !showSearch },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (showSearch) Icons.Filled.Close else Icons.Filled.Add,
                    "Toggle search",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

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
                            selectedContainerColor = accent.copy(alpha = 0.2f),
                            selectedLabelColor = accent
                        )
                    )
                }
            }
        }

        AnimatedVisibility(visible = showSearch) {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        searchJob?.cancel()
                        if (query.isNotEmpty() && onSuggest != null) {
                            searchJob = scope.launch {
                                delay(300)
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

                if (suggestions.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 250.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            suggestions.forEach { suggestion ->
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
                "year" -> element.jsonPrimitive.intOrNull
                else -> element.jsonPrimitive.content
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
