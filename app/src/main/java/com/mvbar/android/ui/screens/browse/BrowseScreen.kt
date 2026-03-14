package com.mvbar.android.ui.screens.browse

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    val tabs = listOf("Artists", "Albums", "Genres")

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Browse",
            style = MaterialTheme.typography.headlineLarge,
            color = OnSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        TabRow(
            selectedTabIndex = state.selectedTab,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = Cyan500,
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
                0 -> ArtistsGrid(state.artists, onArtistClick)
                1 -> AlbumsGrid(state.albums, onAlbumClick)
                2 -> GenresGrid(state.genres)
            }
        }
    }
}

@Composable
private fun ArtistsGrid(artists: List<Artist>, onClick: (Artist) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(artists) { artist ->
            ArtistCard(artist = artist, onClick = { onClick(artist) })
        }
        // Bottom spacing for player bar
        items(2) { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun AlbumsGrid(albums: List<Album>, onClick: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums) { album ->
            AlbumCard(album = album, onClick = { onClick(album) })
        }
        items(2) { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun GenresGrid(genres: List<Genre>) {
    val gradients = listOf(
        listOf(Cyan500, Cyan700),
        listOf(Pink500, Pink400),
        listOf(Cyan600, Cyan900),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(genres) { genre ->
            val colors = gradients[genres.indexOf(genre) % gradients.size]
            GenreChip(genre = genre, colors = colors)
        }
        items(2) { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun GenreChip(genre: Genre, colors: List<androidx.compose.ui.graphics.Color>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors[0].copy(alpha = 0.3f))
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
