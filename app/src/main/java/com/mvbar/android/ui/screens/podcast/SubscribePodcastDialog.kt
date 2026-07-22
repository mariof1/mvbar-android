package com.mvbar.android.ui.screens.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mvbar.android.data.model.PodcastPreview
import com.mvbar.android.data.model.PodcastSearchResult
import com.mvbar.android.ui.components.ArtworkImage
import com.mvbar.android.ui.theme.*

private enum class SubscribeTab { SEARCH, RSS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribePodcastDialog(
    searchResults: List<PodcastSearchResult>,
    searchLoading: Boolean,
    preview: PodcastPreview?,
    previewLoading: Boolean,
    previewError: String?,
    subscribedFeedUrls: Set<String>,
    onSearch: (String) -> Unit,
    onPreview: (PodcastSearchResult) -> Unit,
    onClearPreview: () -> Unit,
    onSubscribe: (String) -> Unit,
    onClose: () -> Unit
) {
    var tab by remember { mutableStateOf(SubscribeTab.SEARCH) }
    var searchQuery by remember { mutableStateOf("") }
    var rssUrl by remember { mutableStateOf("") }
    var previewResult by remember { mutableStateOf<PodcastSearchResult?>(null) }
    val dialogHeightFraction = when {
        tab == SubscribeTab.SEARCH && (searchQuery.isNotBlank() || searchResults.isNotEmpty() || searchLoading) -> 0.76f
        tab == SubscribeTab.SEARCH -> 0.54f
        else -> 0.44f
    }

    previewResult?.let { result ->
        PodcastPreviewDialog(
            result = result,
            preview = preview,
            isLoading = previewLoading,
            error = previewError,
            isSubscribed = result.feedUrl != null && result.feedUrl in subscribedFeedUrls,
            onSubscribe = {
                result.feedUrl?.let(onSubscribe)
                previewResult = null
                onClearPreview()
                onClose()
            },
            onDismiss = {
                previewResult = null
                onClearPreview()
            }
        )
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(dialogHeightFraction),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                DialogHeader(onClose = onClose)
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubscribeTabChip(
                        selected = tab == SubscribeTab.SEARCH,
                        icon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(17.dp)) },
                        label = "Search",
                        onClick = { tab = SubscribeTab.SEARCH }
                    )
                    SubscribeTabChip(
                        selected = tab == SubscribeTab.RSS,
                        icon = { Icon(Icons.Filled.RssFeed, null, modifier = Modifier.size(17.dp)) },
                        label = "RSS",
                        onClick = { tab = SubscribeTab.RSS }
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (tab) {
                    SubscribeTab.SEARCH -> SearchPodcastTab(
                        modifier = Modifier.weight(1f),
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        searchResults = searchResults,
                        searchLoading = searchLoading,
                        subscribedFeedUrls = subscribedFeedUrls,
                        onSearch = onSearch,
                        onDetails = {
                            previewResult = it
                            onPreview(it)
                        },
                        onSubscribe = {
                            onSubscribe(it)
                            onClose()
                        }
                    )
                    SubscribeTab.RSS -> RssPodcastTab(
                        rssUrl = rssUrl,
                        onRssUrlChange = { rssUrl = it },
                        onSubscribe = {
                            onSubscribe(it)
                            onClose()
                        },
                        onClose = onClose
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Add Podcast",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Text(
                "Find a show or paste a feed",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, "Close", tint = OnSurfaceDim)
        }
    }
}

@Composable
private fun SubscribeTabChip(
    selected: Boolean,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = icon,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Cyan600,
            selectedLabelColor = OnSurface,
            selectedLeadingIconColor = OnSurface,
            containerColor = SurfaceElevated,
            labelColor = OnSurface
        ),
        shape = RoundedCornerShape(50)
    )
}

@Composable
private fun SearchPodcastTab(
    modifier: Modifier = Modifier,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<PodcastSearchResult>,
    searchLoading: Boolean,
    subscribedFeedUrls: Set<String>,
    onSearch: (String) -> Unit,
    onDetails: (PodcastSearchResult) -> Unit,
    onSubscribe: (String) -> Unit
) {
    val canSearch = searchQuery.trim().length >= 2 && !searchLoading

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search podcasts") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = OnSurfaceDim) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.trim().length >= 2) onSearch(searchQuery)
                    }
                )
            )
            FilledIconButton(
                onClick = { onSearch(searchQuery) },
                enabled = canSearch,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Cyan600,
                    contentColor = OnSurface,
                    disabledContainerColor = SurfaceElevated,
                    disabledContentColor = OnSurfaceSubtle
                ),
                modifier = Modifier.size(54.dp)
            ) {
                if (searchLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = OnSurface,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Search, "Search")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                searchQuery.isBlank() -> item {
                    DialogEmptyState("Search by show title, host, or network")
                }
                searchResults.isEmpty() && !searchLoading -> item {
                    DialogEmptyState("No matches")
                }
            }

            items(searchResults, key = { it.id }) { result ->
                SearchResultRow(
                    result = result,
                    isSubscribed = result.feedUrl != null && result.feedUrl in subscribedFeedUrls,
                    onDetails = { onDetails(result) },
                    onSubscribe = onSubscribe
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: PodcastSearchResult,
    isSubscribed: Boolean,
    onDetails: () -> Unit,
    onSubscribe: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated.copy(alpha = 0.55f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            model = result.imageUrl,
            contentDescription = null,
            placeholderIcon = Icons.Filled.RssFeed,
            iconSize = 24.dp,
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.title,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                result.author,
                color = OnSurfaceDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = listOfNotNull(
                result.genre,
                result.episodeCount?.let { "$it episodes" }
            ).joinToString(" - ")
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    color = OnSurfaceSubtle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onDetails,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface)
            ) {
                Icon(Icons.Filled.Info, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Details", style = MaterialTheme.typography.labelMedium)
            }

            if (isSubscribed) {
                Surface(shape = CircleShape, color = SurfaceDark) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        "Subscribed",
                        tint = Cyan400,
                        modifier = Modifier
                            .size(38.dp)
                            .padding(8.dp)
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = { result.feedUrl?.let(onSubscribe) },
                    enabled = result.feedUrl != null,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Cyan600,
                        contentColor = OnSurface
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Add", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun PodcastPreviewDialog(
    result: PodcastSearchResult,
    preview: PodcastPreview?,
    isLoading: Boolean,
    error: String?,
    isSubscribed: Boolean,
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = preview?.title?.takeIf { it.isNotBlank() } ?: result.title
    val author = preview?.author?.takeIf { it.isNotBlank() } ?: result.author
    val meta = listOfNotNull(
        preview?.language?.takeIf { it.isNotBlank() }?.uppercase(),
        (preview?.episodeCount ?: result.episodeCount)?.let { "$it episodes" },
        result.genre
    ).joinToString(" - ")
    val body = when {
        isLoading -> "Loading podcast description..."
        !error.isNullOrBlank() -> error
        else -> cleanPodcastDescription(preview?.description).ifBlank { "No description available." }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        titleContentColor = OnSurface,
        textContentColor = OnSurfaceDim,
        title = {
            Text(
                "Podcast details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    title,
                    color = OnSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                author.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        color = Cyan400,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        meta,
                        color = OnSurfaceSubtle,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Cyan400,
                            strokeWidth = 2.dp
                        )
                        Text(body, color = OnSurfaceDim, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(
                        body,
                        color = if (error.isNullOrBlank()) OnSurfaceDim else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = OnSurfaceDim)
            }
        },
        confirmButton = {
            if (isSubscribed) {
                TextButton(onClick = onDismiss) {
                    Text("Subscribed", color = Cyan400)
                }
            } else {
                Button(
                    onClick = onSubscribe,
                    enabled = result.feedUrl != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Add")
                }
            }
        }
    )
}

@Composable
private fun RssPodcastTab(
    rssUrl: String,
    onRssUrlChange: (String) -> Unit,
    onSubscribe: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = rssUrl,
            onValueChange = onRssUrlChange,
            placeholder = { Text("https://example.com/feed.xml") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Filled.RssFeed, null, tint = OnSurfaceDim) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val value = rssUrl.trim()
                    if (value.isNotEmpty()) onSubscribe(value)
                }
            )
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onSubscribe(rssUrl.trim()) },
                enabled = rssUrl.trim().isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                shape = RoundedCornerShape(50)
            ) {
                Text("Add")
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "Private and independent feeds work here too.",
            color = OnSurfaceDim,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DialogEmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = OnSurfaceDim,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
