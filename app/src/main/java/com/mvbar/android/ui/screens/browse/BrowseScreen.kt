package com.mvbar.android.ui.screens.browse

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mvbar.android.data.CountryFlags
import com.mvbar.android.data.model.*
import com.mvbar.android.ui.LocalIsOnline
import com.mvbar.android.ui.components.AlbumCard
import com.mvbar.android.ui.components.ArtistCard
import com.mvbar.android.ui.theme.*
import com.mvbar.android.viewmodel.BrowseState
import kotlinx.coroutines.delay

private val BROWSE_LETTERS = listOf("#") + ('A'..'Z').map { it.toString() }
private val BROWSE_FILTERS = listOf<String?>(null) + BROWSE_LETTERS
private val BrowseArtworkCellMin = 118.dp
private val BrowseRailWidth = 44.dp

@Composable
fun BrowseScreen(
    state: BrowseState,
    onTabChange: (Int) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onGenreClick: (Genre) -> Unit = {},
    onCountryClick: (Country) -> Unit = {},
    onLanguageClick: (Language) -> Unit = {},
    onRefresh: () -> Unit,
    onLoadMoreArtists: () -> Unit = {},
    onLoadMoreAlbums: () -> Unit = {},
    onLoadMoreGenres: () -> Unit = {},
    onLoadMoreCountries: () -> Unit = {},
    onLoadMoreLanguages: () -> Unit = {},
    onArtistLetterSelected: (String?) -> Unit = {},
    onAlbumLetterSelected: (String?) -> Unit = {},
    onArtistLongPress: ((Artist) -> Unit)? = null,
    onAlbumLongPress: ((Album) -> Unit)? = null,
    bottomPadding: Dp = 0.dp
) {
    val isOnline = LocalIsOnline.current
    LaunchedEffect(Unit) {
        if (state.artists.isEmpty() && state.albums.isEmpty() && !state.isLoading) {
            onRefresh()
        }
    }

    val tabs = listOf("Artists", "Albums", "Genres", "Countries", "Languages")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = state.selectedTab,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = Cyan500,
            edgePadding = 0.dp,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = state.selectedTab == index,
                    onClick = { onTabChange(index) },
                    text = {
                        Text(
                            title,
                            color = if (state.selectedTab == index) Cyan500 else OnSurfaceDim
                        )
                    }
                )
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan500)
            }
        } else {
            when (state.selectedTab) {
                0 -> ArtistsGrid(
                    artists = state.artists,
                    playableArtists = state.offlinePlayableArtists,
                    isOnline = isOnline,
                    hasMore = state.hasMoreArtists,
                    isLoadingMore = state.isLoadingMore,
                    selectedLetter = state.artistLetter,
                    onClick = onArtistClick,
                    onLoadMore = onLoadMoreArtists,
                    onLetterSelected = onArtistLetterSelected,
                    onLongPress = onArtistLongPress,
                    bottomPadding = bottomPadding
                )
                1 -> AlbumsGrid(
                    albums = state.albums,
                    playableAlbums = state.offlinePlayableAlbums,
                    isOnline = isOnline,
                    hasMore = state.hasMoreAlbums,
                    isLoadingMore = state.isLoadingMore,
                    selectedLetter = state.albumLetter,
                    onClick = onAlbumClick,
                    onLoadMore = onLoadMoreAlbums,
                    onLetterSelected = onAlbumLetterSelected,
                    onLongPress = onAlbumLongPress,
                    bottomPadding = bottomPadding
                )
                2 -> GenresGrid(state.genres, state.offlinePlayableGenres, isOnline, state.hasMoreGenres, state.isLoadingMore, onGenreClick, onLoadMoreGenres)
                3 -> CountriesGrid(state.countries, state.offlinePlayableCountries, isOnline, state.hasMoreCountries, state.isLoadingMore, onCountryClick, onLoadMoreCountries)
                4 -> LanguagesGrid(state.languages, state.offlinePlayableLanguages, isOnline, state.hasMoreLanguages, state.isLoadingMore, onLanguageClick, onLoadMoreLanguages)
            }
        }
    }
}

@Composable
private fun ArtistsGrid(
    artists: List<Artist>,
    playableArtists: Set<String>,
    isOnline: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    selectedLetter: String?,
    onClick: (Artist) -> Unit,
    onLoadMore: () -> Unit,
    onLetterSelected: (String?) -> Unit,
    onLongPress: ((Artist) -> Unit)? = null,
    bottomPadding: Dp = 0.dp
) {
    val gridState = rememberLazyGridState()

    // Trigger load more when near end
    LaunchedEffect(gridState, hasMore, isLoadingMore, artists.size) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && artists.isNotEmpty() && lastVisible >= artists.size - 6
        }.collect { if (it) onLoadMore() }
    }

    LaunchedEffect(selectedLetter) {
        gridState.scrollToItem(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = BrowseArtworkCellMin),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 8.dp,
                end = BrowseRailWidth + 8.dp,
                bottom = bottomPadding + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (selectedLetter != null && artists.isEmpty() && !isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LetterEmptyState(kind = "artists", selectedLetter = selectedLetter)
                }
            }
            items(artists, key = { artist -> artist.id ?: artist.name }) { artist ->
                val isAvailable = isOnline || artist.name.trim().lowercase() in playableArtists
                ArtistCard(
                    artist = artist,
                    isAvailable = isAvailable,
                    onClick = { onClick(artist) },
                    onLongPress = onLongPress?.let { { it(artist) } }
                )
            }
            if (isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        AlphabetRail(
            selectedLetter = selectedLetter,
            onLetterSelected = onLetterSelected,
            bottomPadding = bottomPadding,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        LetterStepper(
            selectedLetter = selectedLetter,
            onLetterSelected = onLetterSelected,
            bottomPadding = bottomPadding,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    playableAlbums: Set<String>,
    isOnline: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    selectedLetter: String?,
    onClick: (Album) -> Unit,
    onLoadMore: () -> Unit,
    onLetterSelected: (String?) -> Unit,
    onLongPress: ((Album) -> Unit)? = null,
    bottomPadding: Dp = 0.dp
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, hasMore, isLoadingMore, albums.size) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && albums.isNotEmpty() && lastVisible >= albums.size - 6
        }.collect { if (it) onLoadMore() }
    }

    LaunchedEffect(selectedLetter) {
        gridState.scrollToItem(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = BrowseArtworkCellMin),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 8.dp,
                end = BrowseRailWidth + 8.dp,
                bottom = bottomPadding + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (selectedLetter != null && albums.isEmpty() && !isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LetterEmptyState(kind = "albums", selectedLetter = selectedLetter)
                }
            }
            items(albums, key = { album -> "${album.displayName}_${album.albumArtist ?: album.artist.orEmpty()}" }) { album ->
                val isAvailable = isOnline || album.displayName.trim().lowercase() in playableAlbums
                AlbumCard(
                    album = album,
                    isAvailable = isAvailable,
                    onClick = { onClick(album) },
                    onLongPress = onLongPress?.let { { it(album) } }
                )
            }
            if (isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        AlphabetRail(
            selectedLetter = selectedLetter,
            onLetterSelected = onLetterSelected,
            bottomPadding = bottomPadding,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        LetterStepper(
            selectedLetter = selectedLetter,
            onLetterSelected = onLetterSelected,
            bottomPadding = bottomPadding,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun AlphabetRail(
    selectedLetter: String?,
    onLetterSelected: (String?) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val items = remember { BROWSE_FILTERS }
    var bubbleLabel by remember { mutableStateOf<String?>(null) }
    var bubbleIndex by remember { mutableIntStateOf(0) }
    var bubbleToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(bubbleToken) {
        if (bubbleToken > 0) {
            delay(700)
            bubbleLabel = null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(BrowseRailWidth)
            .padding(end = 4.dp, top = 6.dp, bottom = bottomPadding + 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val rawHeight = (maxHeight - 10.dp) / items.size.toFloat()
        val itemHeight = rawHeight.coerceIn(12.dp, 22.dp)
        val totalRailHeight = itemHeight * items.size.toFloat() + 8.dp
        val fontSize = if (itemHeight < 14.dp) 8.sp else 10.sp
        val bubbleAlpha by animateFloatAsState(
            targetValue = if (bubbleLabel != null) 1f else 0f,
            animationSpec = tween(durationMillis = 140),
            label = "alphabetBubbleAlpha"
        )
        val bubbleScale by animateFloatAsState(
            targetValue = if (bubbleLabel != null) 1f else 0.82f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "alphabetBubbleScale"
        )
        val bubbleOffsetY by animateDpAsState(
            targetValue = itemHeight * (bubbleIndex + 0.5f) - totalRailHeight / 2f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "alphabetBubbleY"
        )

        Column(
            modifier = Modifier
                .height(totalRailHeight)
                .background(SurfaceDark.copy(alpha = 0.78f), RoundedCornerShape(18.dp))
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            items.forEachIndexed { index, letter ->
                val label = letter ?: "All"
                val selected = selectedLetter == letter
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.12f else 1f,
                    animationSpec = tween(durationMillis = 120),
                    label = "alphabetItemScale"
                )
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .width(if (letter == null) 34.dp else 26.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(if (selected) Cyan500.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable {
                            bubbleLabel = label
                            bubbleIndex = index
                            bubbleToken += 1
                            onLetterSelected(letter)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) Cyan500 else OnSurfaceDim,
                        fontSize = fontSize,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        val visibleBubbleLabel = bubbleLabel
        if (bubbleAlpha > 0.01f && visibleBubbleLabel != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-44).dp, y = bubbleOffsetY)
                    .graphicsLayer {
                        alpha = bubbleAlpha
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    },
                shape = CircleShape,
                color = Cyan500,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .width(if (visibleBubbleLabel == "All") 52.dp else 44.dp)
                        .height(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = visibleBubbleLabel,
                        color = Color.Black,
                        fontSize = if (visibleBubbleLabel == "All") 14.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LetterStepper(
    selectedLetter: String?,
    onLetterSelected: (String?) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val currentIndex = BROWSE_FILTERS.indexOf(selectedLetter).takeIf { it >= 0 } ?: 0
    val canPrevious = currentIndex > 0
    val canNext = currentIndex < BROWSE_FILTERS.lastIndex
    val previousLetter = if (canPrevious) BROWSE_FILTERS[currentIndex - 1] else null
    val nextLetter = if (canNext) BROWSE_FILTERS[currentIndex + 1] else null

    Surface(
        modifier = modifier.padding(bottom = bottomPadding + 12.dp),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceDark.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (canPrevious) onLetterSelected(previousLetter) },
                enabled = canPrevious,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Previous letter",
                    tint = if (canPrevious) Cyan500 else OnSurfaceDim.copy(alpha = 0.35f)
                )
            }
            Text(
                text = selectedLetter ?: "All",
                color = Cyan500,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(min = 32.dp)
            )
            IconButton(
                onClick = { if (canNext) onLetterSelected(nextLetter) },
                enabled = canNext,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Next letter",
                    tint = if (canNext) Cyan500 else OnSurfaceDim.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun LetterEmptyState(kind: String, selectedLetter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "No $kind starting with $selectedLetter",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceDim
        )
    }
}

@Composable
private fun GenresGrid(
    genres: List<Genre>,
    playableGenres: Set<String>,
    isOnline: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onClick: (Genre) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, hasMore, isLoadingMore, genres.size) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && genres.isNotEmpty() && lastVisible >= genres.size - 6
        }.collect { if (it) onLoadMore() }
    }

    val gradients = listOf(
        listOf(Cyan500, Cyan700),
        listOf(Pink500, Pink400),
        listOf(Cyan600, Cyan900),
    )
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(genres) { genre ->
            val colors = gradients[genres.indexOf(genre) % gradients.size]
            val isAvailable = isOnline || genre.name.trim().lowercase() in playableGenres
            GenreChip(genre = genre, colors = colors, isAvailable = isAvailable, onClick = { if (isAvailable) onClick(genre) })
        }
        if (isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                }
            }
        }
        items(2) { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun GenreChip(
    genre: Genre,
    colors: List<androidx.compose.ui.graphics.Color>,
    isAvailable: Boolean = true,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .graphicsLayer { alpha = if (isAvailable) 1f else 0.38f },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors[0].copy(alpha = 0.3f)),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column {
                Text(
                    genre.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface
                )
                Text(
                    "${genre.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
            }
        }
    }
}

@Composable
private fun CountriesGrid(
    countries: List<Country>,
    playableCountries: Set<String>,
    isOnline: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onClick: (Country) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, hasMore, isLoadingMore, countries.size) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && countries.isNotEmpty() && lastVisible >= countries.size - 6
        }.collect { if (it) onLoadMore() }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(countries) { country ->
            val isAvailable = isOnline || country.name.trim().lowercase() in playableCountries
            CountryCard(country = country, isAvailable = isAvailable, onClick = { if (isAvailable) onClick(country) })
        }
        if (isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                }
            }
        }
        items(2) { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun LanguagesGrid(
    languages: List<Language>,
    playableLanguages: Set<String>,
    isOnline: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onClick: (Language) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, hasMore, isLoadingMore, languages.size) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && languages.isNotEmpty() && lastVisible >= languages.size - 6
        }.collect { if (it) onLoadMore() }
    }

    val gradients = listOf(
        listOf(Color(0xFF6A1B9A), Color(0xFF4A148C)),
        listOf(Color(0xFF00838F), Color(0xFF006064)),
        listOf(Color(0xFFC62828), Color(0xFF8E0000)),
    )
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(languages) { language ->
            val colors = gradients[languages.indexOf(language) % gradients.size]
            val isAvailable = isOnline || language.name.trim().lowercase() in playableLanguages
            CategoryChip(name = language.name, trackCount = language.trackCount, colors = colors, isAvailable = isAvailable, onClick = { if (isAvailable) onClick(language) })
        }
        if (isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                }
            }
        }
        items(2) { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun CategoryChip(
    name: String,
    trackCount: Int,
    colors: List<Color>,
    isAvailable: Boolean = true,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .graphicsLayer { alpha = if (isAvailable) 1f else 0.38f },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors[0].copy(alpha = 0.3f)),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface
                )
                Text(
                    "$trackCount tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
            }
        }
    }
}

@Composable
private fun CountryCard(country: Country, isAvailable: Boolean = true, onClick: () -> Unit) {
    val flagUrl = CountryFlags.flagUrl(country.name)
    Card(
        onClick = onClick,
        modifier = Modifier.graphicsLayer { alpha = if (isAvailable) 1f else 0.38f },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Column {
            if (flagUrl != null) {
                AsyncImage(
                    model = flagUrl,
                    contentDescription = country.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        country.name.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = OnSurfaceDim
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    country.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${country.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim,
                    fontSize = 11.sp
                )
            }
        }
    }
}
