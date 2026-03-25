package com.mvbar.android.ui.screens.browse

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mvbar.android.data.CountryFlags
import com.mvbar.android.data.model.*
import com.mvbar.android.ui.components.AlbumCard
import com.mvbar.android.ui.components.ArtistCard
import com.mvbar.android.ui.theme.*
import com.mvbar.android.viewmodel.BrowseState

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
    onLoadMoreLanguages: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        if (state.artists.isEmpty() && state.albums.isEmpty() && !state.isLoading) {
            onRefresh()
        }
    }

    val tabs = listOf("Artists", "Albums", "Genres", "Countries", "Languages")

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Browse",
            style = MaterialTheme.typography.headlineLarge,
            color = OnSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

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
                0 -> ArtistsGrid(state.artists, state.hasMoreArtists, state.isLoadingMore, onArtistClick, onLoadMoreArtists)
                1 -> AlbumsGrid(state.albums, state.hasMoreAlbums, state.isLoadingMore, onAlbumClick, onLoadMoreAlbums)
                2 -> GenresGrid(state.genres, state.hasMoreGenres, state.isLoadingMore, onGenreClick, onLoadMoreGenres)
                3 -> CountriesGrid(state.countries, state.hasMoreCountries, state.isLoadingMore, onCountryClick, onLoadMoreCountries)
                4 -> LanguagesGrid(state.languages, state.hasMoreLanguages, state.isLoadingMore, onLanguageClick, onLoadMoreLanguages)
            }
        }
    }
}

@Composable
private fun ArtistsGrid(
    artists: List<Artist>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onClick: (Artist) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()

    // Trigger load more when near end
    LaunchedEffect(gridState, hasMore, isLoadingMore, artists.size) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && artists.isNotEmpty() && lastVisible >= artists.size - 6
        }.collect { if (it) onLoadMore() }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(artists) { artist ->
            ArtistCard(artist = artist, onClick = { onClick(artist) })
        }
        if (isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                }
            }
        }
        // Bottom spacing for player bar
        items(2) { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onClick: (Album) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, hasMore, isLoadingMore, albums.size) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && albums.isNotEmpty() && lastVisible >= albums.size - 6
        }.collect { if (it) onLoadMore() }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums) { album ->
            AlbumCard(album = album, onClick = { onClick(album) })
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
private fun GenresGrid(
    genres: List<Genre>,
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
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(genres) { genre ->
            val colors = gradients[genres.indexOf(genre) % gradients.size]
            GenreChip(genre = genre, colors = colors, onClick = { onClick(genre) })
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
private fun GenreChip(genre: Genre, colors: List<androidx.compose.ui.graphics.Color>, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
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
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(countries) { country ->
            CountryCard(country = country, onClick = { onClick(country) })
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
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(languages) { language ->
            val colors = gradients[languages.indexOf(language) % gradients.size]
            CategoryChip(name = language.name, trackCount = language.trackCount, colors = colors, onClick = { onClick(language) })
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
private fun CategoryChip(name: String, trackCount: Int, colors: List<Color>, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
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
private fun CountryCard(country: Country, onClick: () -> Unit) {
    val flagUrl = CountryFlags.flagUrl(country.name)
    Card(
        onClick = onClick,
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
