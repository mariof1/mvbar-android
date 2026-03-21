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
    private var currentAudiobookId: Int? = null
    private var currentChaptersList: List<AudiobookChapter> = emptyList()

    init {
        viewModelScope.launch {
            playerManager.state.collect { state ->
                val trackId = state.currentTrack?.id ?: return@collect
                if (trackId >= 0 || currentAudiobookId == null) return@collect
                val abId = currentAudiobookId ?: return@collect
                val chId = -(trackId) - abId * 100000
                if (chId > 0) {
                    val chapter = currentChaptersList.find { it.id == chId }
                    if (chapter != null) _playingChapter.value = chapter
                }
            }
        }
    }

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
        _selectedAudiobook.value = null
        _chapters.value = emptyList()
        _detailProgress.value = null
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
        val chapters = _chapters.value
        currentAudiobookId = audiobook.id
        currentChaptersList = chapters

        val allPseudoTracks = chapters.map { ch ->
            Track(
                id = -(audiobook.id * 100000 + ch.id),
                title = ch.title,
                artist = audiobook.author,
                album = audiobook.title
            )
        }
        val allStreamUrls = chapters.associate { ch ->
            val pseudoId = -(audiobook.id * 100000 + ch.id)
            pseudoId to ApiClient.audiobookChapterStreamUrl(audiobook.id, ch.id)
        }
        val allArtUrls = chapters.associate { ch ->
            val pseudoId = -(audiobook.id * 100000 + ch.id)
            pseudoId to ApiClient.audiobookArtUrl(audiobook.id)
        }

        val startIndex = chapters.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)

        playerManager.playTracks(allPseudoTracks, startIndex, allStreamUrls, allArtUrls)

        _playingChapter.value = chapter
        _playingAudiobook.value = audiobook

        if (resumePositionMs > 0) {
            viewModelScope.launch {
                delay(500)
                playerManager.seekTo(resumePositionMs)
            }
        }

        startProgressSync(audiobook.id)
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

    private fun startProgressSync(audiobookId: Int) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                try {
                    val state = playerManager.state.value
                    if (state.isPlaying) {
                        val trackId = state.currentTrack?.id ?: continue
                        if (trackId >= 0) continue
                        val chId = -(trackId) - audiobookId * 100000
                        if (chId <= 0) continue
                        val posMs = state.position
                        ApiClient.api.updateAudiobookProgress(
                            audiobookId,
                            AudiobookProgressRequest(chapterId = chId, positionMs = posMs)
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
}
