package com.mvbar.android.ui.screens.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mvbar.android.data.model.PodcastSearchResult
import com.mvbar.android.ui.theme.*

@Composable
fun SubscribePodcastDialog(
    searchResults: List<PodcastSearchResult>,
    searchLoading: Boolean,
    subscribedFeedUrls: Set<String>,
    onSearch: (String) -> Unit,
    onSubscribe: (String) -> Unit,
    onClose: () -> Unit
) {
    var tab by remember { mutableStateOf("search") } // "search" or "rss"
    var searchQuery by remember { mutableStateOf("") }
    var rssUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Title and close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Add Podcast",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, "Close", tint = OnSurfaceDim)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Tabs
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tab == "search",
                        onClick = { tab = "search" },
                        label = { Text("🔍 Search") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan600,
                            selectedLabelColor = OnSurface,
                            containerColor = SurfaceElevated,
                            labelColor = OnSurface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = tab == "rss",
                        onClick = { tab = "rss" },
                        label = { Text("📡 RSS URL") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan600,
                            selectedLabelColor = OnSurface,
                            containerColor = SurfaceElevated,
                            labelColor = OnSurface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(12.dp))

                if (tab == "search") {
                    // Search input
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search for podcasts...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { if (searchQuery.trim().length >= 2) onSearch(searchQuery) }
                            )
                        )
                        Button(
                            onClick = { onSearch(searchQuery) },
                            enabled = !searchLoading && searchQuery.trim().length >= 2,
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (searchLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = OnSurface,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Search, "Search", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Results
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (searchResults.isEmpty() && !searchLoading && searchQuery.isNotEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No podcasts found. Try a different search term.",
                                        color = OnSurfaceDim
                                    )
                                }
                            }
                        }

                        items(searchResults, key = { it.id }) { result ->
                            val isSubscribed = result.feedUrl != null && result.feedUrl in subscribedFeedUrls
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceElevated.copy(alpha = 0.5f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Art
                                if (result.imageUrl != null) {
                                    AsyncImage(
                                        model = result.imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SurfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🎙️", fontSize = 24.sp)
                                    }
                                }

                                // Info
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        result.title,
                                        color = OnSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        result.author,
                                        color = OnSurfaceDim,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (result.genre != null) {
                                        Text(
                                            "${result.genre} • ${result.episodeCount ?: "?"} episodes",
                                            color = OnSurfaceSubtle,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                // Subscribe button
                                if (isSubscribed) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SurfaceElevated
                                    ) {
                                        Text(
                                            "Subscribed",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            color = OnSurfaceDim,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            result.feedUrl?.let {
                                                onSubscribe(it)
                                                onClose()
                                            }
                                        },
                                        enabled = result.feedUrl != null,
                                        colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Subscribe", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // RSS URL input
                    Text(
                        "Enter a podcast RSS feed URL directly if you cannot find it through search.",
                        color = OnSurfaceDim,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rssUrl,
                        onValueChange = { rssUrl = it },
                        placeholder = { Text("https://example.com/podcast/feed.xml") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Filled.RssFeed, null) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (rssUrl.trim().isNotEmpty()) {
                                    onSubscribe(rssUrl.trim())
                                    onClose()
                                }
                            }
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (rssUrl.trim().isNotEmpty()) {
                                    onSubscribe(rssUrl.trim())
                                    onClose()
                                }
                            },
                            enabled = rssUrl.trim().isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Subscribe")
                        }
                    }
                }
            }
        }
    }
}

