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
                DebugLog.i("Browse", "Loading artists, albums, genres...")
                val artists = try {
                    repo.getArtists().artists.also { DebugLog.i("Browse", "Got ${it.size} artists") }
                } catch (e: Exception) { DebugLog.e("Browse", "Artists failed", e); emptyList() }
                val albums = try {
                    repo.getAlbums().albums.also { DebugLog.i("Browse", "Got ${it.size} albums") }
                } catch (e: Exception) { DebugLog.e("Browse", "Albums failed", e); emptyList() }
                val genres = try {
                    repo.getGenres().genres.also { DebugLog.i("Browse", "Got ${it.size} genres") }
                } catch (e: Exception) { DebugLog.e("Browse", "Genres failed", e); emptyList() }
                _state.value = BrowseState(artists = artists, albums = albums, genres = genres)
            } catch (e: Exception) {
                DebugLog.e("Browse", "loadAll failed", e)
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
