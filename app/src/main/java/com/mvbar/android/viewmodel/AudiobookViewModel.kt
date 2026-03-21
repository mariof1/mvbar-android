package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.*
import com.mvbar.android.data.repository.MusicRepository
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.PlayerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AudiobookViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MusicRepository.getInstance()
    val playerManager = PlayerManager.getInstance(app)

    private val _audiobooks = MutableStateFlow<List<Audiobook>>(emptyList())
    val audiobooks: StateFlow<List<Audiobook>> = _audiobooks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedAudiobook = MutableStateFlow<Audiobook?>(null)
    val selectedAudiobook: StateFlow<Audiobook?> = _selectedAudiobook.asStateFlow()

    private val _chapters = MutableStateFlow<List<AudiobookChapter>>(emptyList())
    val chapters: StateFlow<List<AudiobookChapter>> = _chapters.asStateFlow()

    private val _detailProgress = MutableStateFlow<AudiobookDetailProgress?>(null)
    val detailProgress: StateFlow<AudiobookDetailProgress?> = _detailProgress.asStateFlow()

    private val _playingChapter = MutableStateFlow<AudiobookChapter?>(null)
    val playingChapter: StateFlow<AudiobookChapter?> = _playingChapter.asStateFlow()

    private val _playingAudiobook = MutableStateFlow<Audiobook?>(null)
    val playingAudiobook: StateFlow<Audiobook?> = _playingAudiobook.asStateFlow()

    private var progressJob: Job? = null

    fun loadAudiobooks() {
        viewModelScope.launch {
            _isLoading.value = true
            // Cache first
            try {
                val cached = repo.getCachedAudiobooks()
                if (!cached.isNullOrEmpty()) _audiobooks.value = cached
            } catch (_: Exception) {}
            // Then API
            try {
                val books = ApiClient.api.getAudiobooks()
                _audiobooks.value = books
                DebugLog.i("Audiobooks", "Loaded ${books.size} audiobooks")
            } catch (e: Exception) {
                DebugLog.e("Audiobooks", "Failed to load audiobooks", e)
            }
            _isLoading.value = false
        }
    }

    fun loadAudiobookDetail(audiobookId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            // Cache first
            try {
                val cached = repo.getCachedAudiobookChapters(audiobookId)
                if (!cached.isNullOrEmpty()) _chapters.value = cached
            } catch (_: Exception) {}
            // Then API
            try {
                val resp = ApiClient.api.getAudiobookDetail(audiobookId)
                _selectedAudiobook.value = resp.audiobook
                _chapters.value = resp.chapters
                _detailProgress.value = resp.progress
                DebugLog.i("Audiobooks", "Loaded detail for audiobook $audiobookId: ${resp.chapters.size} chapters")
            } catch (e: Exception) {
                DebugLog.e("Audiobooks", "Failed to load audiobook detail", e)
            }
            _isLoading.value = false
        }
    }

    fun playChapter(audiobook: Audiobook, chapter: AudiobookChapter, resumePositionMs: Long = 0) {
        // Use negative ID convention to avoid collision with tracks and podcasts
        val pseudoTrack = Track(
            id = -(audiobook.id * 100000 + chapter.id),
            title = chapter.title,
            artist = audiobook.author,
            album = audiobook.title
        )
        val streamUrl = ApiClient.audiobookChapterStreamUrl(audiobook.id, chapter.id)
        val artUrl = ApiClient.audiobookArtUrl(audiobook.id)
        playerManager.playTracks(
            listOf(pseudoTrack), 0,
            customStreamUrls = mapOf(pseudoTrack.id to streamUrl),
            customArtUrls = mapOf(pseudoTrack.id to artUrl)
        )

        _playingChapter.value = chapter
        _playingAudiobook.value = audiobook

        // Seek to resume position after a brief delay
        if (resumePositionMs > 0) {
            viewModelScope.launch {
                delay(500)
                playerManager.seekTo(resumePositionMs)
            }
        }

        startProgressSync(audiobook.id, chapter.id)
    }

    fun continueListening(audiobook: Audiobook) {
        val progress = _detailProgress.value
        val chapters = _chapters.value
        if (progress != null && chapters.isNotEmpty()) {
            val chapter = chapters.find { it.id == progress.chapterId } ?: chapters.first()
            playChapter(audiobook, chapter, progress.positionMs)
        } else if (chapters.isNotEmpty()) {
            playChapter(audiobook, chapters.first())
        }
    }

    private fun startProgressSync(audiobookId: Int, chapterId: Int) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                try {
                    val state = playerManager.state.value
                    if (state.isPlaying) {
                        val posMs = state.position
                        ApiClient.api.updateAudiobookProgress(
                            audiobookId,
                            AudiobookProgressRequest(chapterId = chapterId, positionMs = posMs)
                        )
                    }
                } catch (e: Exception) {
                    DebugLog.e("Audiobooks", "Progress sync failed", e)
                }
            }
        }
    }

    fun stopPlayback() {
        progressJob?.cancel()
        _playingChapter.value = null
        _playingAudiobook.value = null
    }

    fun markFinished(audiobookId: Int) {
        viewModelScope.launch {
            try {
                ApiClient.api.markAudiobookFinished(audiobookId)
                loadAudiobooks()
                loadAudiobookDetail(audiobookId)
            } catch (e: Exception) {
                DebugLog.e("Audiobooks", "Failed to mark finished", e)
            }
        }
    }

    fun clearDetail() {
        _selectedAudiobook.value = null
        _chapters.value = emptyList()
        _detailProgress.value = null
    }
}
