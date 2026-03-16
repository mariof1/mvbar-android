package com.mvbar.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.model.*
import com.mvbar.android.data.repository.MusicRepository
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseState(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTab: Int = 0,
    val hasMoreArtists: Boolean = true,
    val hasMoreAlbums: Boolean = true,
    val hasMoreGenres: Boolean = true,
    val isLoadingMore: Boolean = false
)

class BrowseViewModel : ViewModel() {
    private val repo = MusicRepository()
    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private val _artistTracks = MutableStateFlow<List<Track>>(emptyList())
    val artistTracks: StateFlow<List<Track>> = _artistTracks.asStateFlow()

    private val _albumTracks = MutableStateFlow<List<Track>>(emptyList())
    val albumTracks: StateFlow<List<Track>> = _albumTracks.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    val selectedAlbum: StateFlow<Album?> = _selectedAlbum.asStateFlow()

    private val _selectedArtist = MutableStateFlow<Artist?>(null)
    val selectedArtist: StateFlow<Artist?> = _selectedArtist.asStateFlow()

    private companion object {
        const val PAGE_SIZE = 50
    }

    fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                DebugLog.i("Browse", "Loading artists, albums, genres...")
                val artists = try {
                    val r = repo.getArtists(PAGE_SIZE, 0)
                    DebugLog.i("Browse", "Got ${r.artists.size} artists (total: ${r.total})")
                    _state.value = _state.value.copy(hasMoreArtists = r.artists.size >= PAGE_SIZE)
                    r.artists
                } catch (e: Exception) { DebugLog.e("Browse", "Artists failed", e); emptyList() }
                val albums = try {
                    val r = repo.getAlbums(PAGE_SIZE, 0)
                    DebugLog.i("Browse", "Got ${r.albums.size} albums (total: ${r.total})")
                    _state.value = _state.value.copy(hasMoreAlbums = r.albums.size >= PAGE_SIZE)
                    r.albums
                } catch (e: Exception) { DebugLog.e("Browse", "Albums failed", e); emptyList() }
                val genres = try {
                    val r = repo.getGenres(PAGE_SIZE, 0)
                    DebugLog.i("Browse", "Got ${r.genres.size} genres (total: ${r.total})")
                    _state.value = _state.value.copy(hasMoreGenres = r.genres.size >= PAGE_SIZE)
                    r.genres
                } catch (e: Exception) { DebugLog.e("Browse", "Genres failed", e); emptyList() }
                _state.value = _state.value.copy(artists = artists, albums = albums, genres = genres, isLoading = false)
            } catch (e: Exception) {
                DebugLog.e("Browse", "loadAll failed", e)
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun loadMoreArtists() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMoreArtists) return
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val r = repo.getArtists(PAGE_SIZE, s.artists.size)
                DebugLog.i("Browse", "Loaded ${r.artists.size} more artists (offset ${s.artists.size})")
                _state.value = _state.value.copy(
                    artists = s.artists + r.artists,
                    hasMoreArtists = r.artists.size >= PAGE_SIZE,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more artists failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun loadMoreAlbums() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMoreAlbums) return
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val r = repo.getAlbums(PAGE_SIZE, s.albums.size)
                DebugLog.i("Browse", "Loaded ${r.albums.size} more albums (offset ${s.albums.size})")
                _state.value = _state.value.copy(
                    albums = s.albums + r.albums,
                    hasMoreAlbums = r.albums.size >= PAGE_SIZE,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more albums failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun loadMoreGenres() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMoreGenres) return
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val r = repo.getGenres(PAGE_SIZE, s.genres.size)
                DebugLog.i("Browse", "Loaded ${r.genres.size} more genres (offset ${s.genres.size})")
                _state.value = _state.value.copy(
                    genres = s.genres + r.genres,
                    hasMoreGenres = r.genres.size >= PAGE_SIZE,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more genres failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun setTab(tab: Int) { _state.value = _state.value.copy(selectedTab = tab) }

    fun loadArtistDetail(artist: Artist) {
        _selectedArtist.value = artist
        viewModelScope.launch {
            try {
                artist.id?.let { id ->
                    DebugLog.i("Browse", "Loading artist tracks for id=$id")
                    _artistTracks.value = repo.getArtistTracks(id).tracks
                }
            } catch (e: Exception) {
                DebugLog.e("Browse", "Artist tracks failed", e)
            }
        }
    }

    fun loadAlbumTracks(albumName: String) {
        viewModelScope.launch {
            try {
                DebugLog.i("Browse", "Loading album tracks for '$albumName'")
                val response: AlbumDetailResponse = repo.getAlbumTracks(albumName)
                DebugLog.i("Browse", "Got ${response.tracks.size} tracks for album")
                _albumTracks.value = response.tracks
                _selectedAlbum.value = response.album
            } catch (e: Exception) {
                DebugLog.e("Browse", "Album tracks failed for '$albumName'", e)
            }
        }
    }
}
