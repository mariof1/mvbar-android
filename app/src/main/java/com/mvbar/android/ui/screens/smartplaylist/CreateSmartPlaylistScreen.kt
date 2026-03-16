package com.mvbar.android.ui.screens.smartplaylist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.*
import com.mvbar.android.ui.theme.*

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSmartPlaylistScreen(
    genres: List<Genre>,
    onBack: () -> Unit,
    onCreate: (name: String, sort: String, filters: SmartPlaylistFilters) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf("random") }
    var favoriteOnly by remember { mutableStateOf(false) }
    var maxResults by remember { mutableStateOf(500f) }
    val selectedGenres = remember { mutableStateListOf<String>() }
    var sortExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("New Smart Playlist", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                val filters = SmartPlaylistFilters(
                                    include = SmartFilterSet(genres = selectedGenres.toList()),
                                    favoriteOnly = favoriteOnly,
                                    maxResults = maxResults.toInt()
                                )
                                onCreate(name.trim(), selectedSort, filters)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Name
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan500,
                        unfocusedBorderColor = OnSurfaceDim,
                        focusedLabelColor = Cyan500,
                        cursorColor = Cyan500,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Sort mode
            item {
                Text("Sort By", style = MaterialTheme.typography.titleSmall, color = OnSurfaceDim)
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = sortExpanded,
                    onExpandedChange = { sortExpanded = it }
                ) {
                    OutlinedTextField(
                        value = SORT_OPTIONS.find { it.first == selectedSort }?.second ?: "Random",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan500,
                            unfocusedBorderColor = OnSurfaceDim,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        ),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        SORT_OPTIONS.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedSort = key
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Favorite only toggle
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

            // Max results slider
            item {
                Text(
                    "Max results: ${maxResults.toInt()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurfaceDim
                )
                Slider(
                    value = maxResults,
                    onValueChange = { maxResults = it },
                    valueRange = 10f..2000f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = Cyan500,
                        activeTrackColor = Cyan500,
                        inactiveTrackColor = WhiteOverlay15
                    )
                )
            }

            // Genre filter
            item {
                Text(
                    "Genre Filter (${selectedGenres.size} selected)",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurfaceDim
                )
            }

            items(genres.take(50)) { genre ->
                val isSelected = genre.name in selectedGenres
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) selectedGenres.remove(genre.name)
                        else selectedGenres.add(genre.name)
                    },
                    label = { Text("${genre.name} (${genre.trackCount})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan500.copy(alpha = 0.2f),
                        selectedLabelColor = Cyan400,
                        labelColor = OnSurfaceDim
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}
