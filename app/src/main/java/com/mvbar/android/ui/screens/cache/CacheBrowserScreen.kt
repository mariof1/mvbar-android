package com.mvbar.android.ui.screens.cache

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Type of cached content */
enum class CachedItemType { TRACK, EPISODE, AUDIOBOOK_CHAPTER }

/** Represents a cached item with metadata resolved from local DB */
data class CachedItem(
    val cacheKey: String,
    val type: CachedItemType,
    val id: Int,
    val title: String,
    val subtitle: String,
    val artUrl: String?,
    val sizeMb: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheBrowserScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<CachedItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalSizeMb by remember { mutableLongStateOf(0L) }
    var selectedFilter by remember { mutableStateOf<CachedItemType?>(null) }

    // Load cached items with metadata
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = loadCachedItems(context)
            items = loaded
            totalSizeMb = AudioCacheManager.getCacheSizeMb()
            isLoading = false
        }
    }

    val filteredItems = if (selectedFilter != null) items.filter { it.type == selectedFilter } else items
    val trackCount = items.count { it.type == CachedItemType.TRACK }
    val episodeCount = items.count { it.type == CachedItemType.EPISODE }
    val chapterCount = items.count { it.type == CachedItemType.AUDIOBOOK_CHAPTER }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Cached Content",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnSurface
                )
                Text(
                    "${items.size} items · ${totalSizeMb} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
            }
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("All (${items.size})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Cyan500.copy(alpha = 0.2f),
                    selectedLabelColor = Cyan400,
                    labelColor = OnSurfaceDim,
                    containerColor = Color.Transparent
                )
            )
            if (trackCount > 0) {
                FilterChip(
                    selected = selectedFilter == CachedItemType.TRACK,
                    onClick = { selectedFilter = if (selectedFilter == CachedItemType.TRACK) null else CachedItemType.TRACK },
                    label = { Text("Tracks ($trackCount)", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.MusicNote, null, Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan500.copy(alpha = 0.2f),
                        selectedLabelColor = Cyan400,
                        labelColor = OnSurfaceDim,
                        containerColor = Color.Transparent
                    )
                )
            }
            if (episodeCount > 0) {
                FilterChip(
                    selected = selectedFilter == CachedItemType.EPISODE,
                    onClick = { selectedFilter = if (selectedFilter == CachedItemType.EPISODE) null else CachedItemType.EPISODE },
                    label = { Text("Episodes ($episodeCount)", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.Podcasts, null, Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan500.copy(alpha = 0.2f),
                        selectedLabelColor = Cyan400,
                        labelColor = OnSurfaceDim,
                        containerColor = Color.Transparent
                    )
                )
            }
            if (chapterCount > 0) {
                FilterChip(
                    selected = selectedFilter == CachedItemType.AUDIOBOOK_CHAPTER,
                    onClick = { selectedFilter = if (selectedFilter == CachedItemType.AUDIOBOOK_CHAPTER) null else CachedItemType.AUDIOBOOK_CHAPTER },
                    label = { Text("Books ($chapterCount)", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan500.copy(alpha = 0.2f),
                        selectedLabelColor = Cyan400,
                        labelColor = OnSurfaceDim,
                        containerColor = Color.Transparent
                    )
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan500)
            }
        } else if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (items.isEmpty()) "No cached content" else "No items match filter",
                    color = OnSurfaceDim
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                items(filteredItems, key = { it.cacheKey }) { item ->
                    CachedItemRow(
                        item = item,
                        onRemove = {
                            scope.launch(Dispatchers.IO) {
                                AudioCacheManager.removeCachedItem(item.cacheKey)
                                val remaining = items.filter { it.cacheKey != item.cacheKey }
                                withContext(Dispatchers.Main) {
                                    items = remaining
                                    totalSizeMb = AudioCacheManager.getCacheSizeMb()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CachedItemRow(
    item: CachedItem,
    onRemove: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Art
        AsyncImage(
            model = item.artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Size
        Text(
            item.sizeMb,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceDim,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Delete button
        if (showConfirm) {
            TextButton(
                onClick = { onRemove(); showConfirm = false },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Remove", fontSize = 12.sp)
            }
        } else {
            IconButton(
                onClick = { showConfirm = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove",
                    tint = OnSurfaceDim,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Parse cache keys into CachedItems by extracting IDs from URLs
 * and looking up metadata from Room DB.
 */
private suspend fun loadCachedItems(context: android.content.Context): List<CachedItem> {
    val keys = AudioCacheManager.getCachedKeys()
    if (keys.isEmpty()) return emptyList()

    val db = MvbarDatabase.getInstance(context)
    val baseUrl = ApiClient.getBaseUrl()

    // Parse keys into type + ID
    data class ParsedKey(val key: String, val type: CachedItemType, val id: Int, val extraId: Int? = null)

    val parsed = keys.mapNotNull { key ->
        val path = key.removePrefix(baseUrl)
        when {
            // Track: api/library/tracks/{id}/stream
            path.startsWith("api/library/tracks/") -> {
                val id = path.removePrefix("api/library/tracks/").removeSuffix("/stream").toIntOrNull()
                id?.let { ParsedKey(key, CachedItemType.TRACK, it) }
            }
            // Episode: api/podcasts/episodes/{id}/stream
            path.startsWith("api/podcasts/episodes/") -> {
                val id = path.removePrefix("api/podcasts/episodes/").removeSuffix("/stream").toIntOrNull()
                id?.let { ParsedKey(key, CachedItemType.EPISODE, it) }
            }
            // Audiobook chapter: api/audiobooks/{bookId}/chapters/{chapterId}/stream
            path.startsWith("api/audiobooks/") -> {
                val parts = path.removePrefix("api/audiobooks/").removeSuffix("/stream").split("/chapters/")
                if (parts.size == 2) {
                    val bookId = parts[0].toIntOrNull()
                    val chapterId = parts[1].toIntOrNull()
                    if (bookId != null && chapterId != null) ParsedKey(key, CachedItemType.AUDIOBOOK_CHAPTER, chapterId, bookId)
                    else null
                } else null
            }
            else -> null
        }
    }

    // Batch-load metadata from DB
    val trackIds = parsed.filter { it.type == CachedItemType.TRACK }.map { it.id }
    val episodeIds = parsed.filter { it.type == CachedItemType.EPISODE }.map { it.id }

    val trackMap = if (trackIds.isNotEmpty()) {
        // Room has a limit on IN clause size, batch if needed
        trackIds.chunked(500).flatMap { chunk ->
            db.trackDao().getByIds(chunk)
        }.associateBy { it.id }
    } else emptyMap()

    val episodeMap = if (episodeIds.isNotEmpty()) {
        episodeIds.chunked(500).flatMap { chunk ->
            db.podcastDao().getEpisodesByIds(chunk)
        }.associateBy { it.id }
    } else emptyMap()

    return parsed.map { p ->
        val sizeBytes = AudioCacheManager.getCachedSizeBytes(p.key)
        val sizeMbStr = if (sizeBytes > 0) {
            val mb = sizeBytes / (1024.0 * 1024.0)
            if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.0f KB", sizeBytes / 1024.0)
        } else "—"

        when (p.type) {
            CachedItemType.TRACK -> {
                val track = trackMap[p.id]
                CachedItem(
                    cacheKey = p.key,
                    type = CachedItemType.TRACK,
                    id = p.id,
                    title = track?.title ?: "Track #${p.id}",
                    subtitle = buildString {
                        track?.artist?.let { append(it) }
                        track?.album?.let { if (isNotEmpty()) append(" · "); append(it) }
                        if (isEmpty()) append("Unknown")
                    },
                    artUrl = track?.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(p.id),
                    sizeMb = sizeMbStr
                )
            }
            CachedItemType.EPISODE -> {
                val ep = episodeMap[p.id]
                CachedItem(
                    cacheKey = p.key,
                    type = CachedItemType.EPISODE,
                    id = p.id,
                    title = ep?.title ?: "Episode #${p.id}",
                    subtitle = ep?.podcastTitle ?: "Podcast",
                    artUrl = ep?.podcastImagePath?.let { ApiClient.podcastArtPathUrl(it) }
                        ?: ep?.imageUrl
                        ?: ApiClient.episodeArtUrl(p.id),
                    sizeMb = sizeMbStr
                )
            }
            CachedItemType.AUDIOBOOK_CHAPTER -> {
                CachedItem(
                    cacheKey = p.key,
                    type = CachedItemType.AUDIOBOOK_CHAPTER,
                    id = p.id,
                    title = "Chapter #${p.id}",
                    subtitle = "Audiobook",
                    artUrl = p.extraId?.let { ApiClient.audiobookArtUrl(it) },
                    sizeMb = sizeMbStr
                )
            }
        }
    }.sortedWith(compareBy({ it.type.ordinal }, { it.title.lowercase() }))
}
