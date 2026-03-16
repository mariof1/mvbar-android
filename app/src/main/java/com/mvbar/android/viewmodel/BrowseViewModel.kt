package com.mvbar.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.model.*
import com.mvbar.android.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseState(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTab: Int = 0
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

    fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val artists = try { repo.getArtists().artists } catch (_: Exception) { emptyList() }
                val albums = try { repo.getAlbums().albums } catch (_: Exception) { emptyList() }
                val genres = try { repo.getGenres().genres } catch (_: Exception) { emptyList() }
                _state.value = BrowseState(artists = artists, albums = albums, genres = genres)
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun setTab(tab: Int) { _state.value = _state.value.copy(selectedTab = tab) }

    fun loadArtistDetail(artist: Artist) {
        _selectedArtist.value = artist
        viewModelScope.launch {
            try {
                artist.id?.let { id ->
                    _artistTracks.value = repo.getArtistTracks(id).tracks
                }
            } catch (_: Exception) {}
        }
    }

    fun loadAlbumTracks(albumName: String) {
        viewModelScope.launch {
            try {
                val response: AlbumDetailResponse = repo.getAlbumTracks(albumName)
                _albumTracks.value = response.tracks
                _selectedAlbum.value = response.album
            } catch (_: Exception) {}
        }
    }
}
