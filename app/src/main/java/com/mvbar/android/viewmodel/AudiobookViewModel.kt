package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.ActivityQueue
import com.mvbar.android.data.NetworkMonitor
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.local.entity.toEntity
import com.mvbar.android.data.local.entity.toModel
import com.mvbar.android.data.model.*
import com.mvbar.android.data.repository.MusicRepository
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.PlayerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AudiobookViewModel(app: Application) : AndroidViewModel(app) {
    private val db = MvbarDatabase.getInstance(app)
    private val repo = MusicRepository.getInstance(db)
    val playerManager = PlayerManager.getInstance(app)

    private val _audiobooks = MutableStateFlow<List<Audiobook>>(emptyList())
    val audiobooks: StateFlow<List<Audiobook>> = _audiobooks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()
    private var detailJob: Job? = null
    private var detailGeneration = 0L

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
            if (!NetworkMonitor.isOnline.value) {
                _isLoading.value = false
                return@launch
            }
            // Then API
            try {
                val books = ApiClient.api.getAudiobooks()
                _audiobooks.value = books
                db.audiobookDao().insertAudiobooks(books.map { it.toEntity() })
                DebugLog.i("Audiobooks", "Loaded ${books.size} audiobooks")
            } catch (e: Exception) {
                DebugLog.e("Audiobooks", "Failed to load audiobooks", e)
            }
            _isLoading.value = false
        }
    }

    fun loadAudiobookDetail(audiobookId: Int) {
        val generation = ++detailGeneration
        detailJob?.cancel()
        _detailLoading.value = true
        _selectedAudiobook.value = _audiobooks.value.firstOrNull { it.id == audiobookId }
        _chapters.value = emptyList()
        _detailProgress.value = null
        detailJob = viewModelScope.launch {
            try {
                // Detail routes may open before the list screen has loaded.
                try {
                    val book = db.audiobookDao().getAudiobook(audiobookId)?.toModel()
                    val cached = repo.getCachedAudiobookChapters(audiobookId).orEmpty()
                    if (generation != detailGeneration) return@launch
                    if (book != null) _selectedAudiobook.value = book
                    _chapters.value = cached
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.e("Audiobooks", "Failed to read cached detail", e)
                }
                if (!NetworkMonitor.isOnline.value) return@launch
                val resp = ApiClient.api.getAudiobookDetail(audiobookId)
                if (generation != detailGeneration) return@launch
                _selectedAudiobook.value = resp.audiobook
                _chapters.value = resp.chapters
                _detailProgress.value = resp.progress
                resp.audiobook?.let { db.audiobookDao().insertAudiobooks(listOf(it.toEntity())) }
                db.audiobookDao().replaceChapters(audiobookId, resp.chapters.map { it.toEntity() })
                DebugLog.i("Audiobooks", "Loaded detail for audiobook $audiobookId: ${resp.chapters.size} chapters")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.e("Audiobooks", "Failed to load audiobook detail", e)
            } finally {
                if (generation == detailGeneration) _detailLoading.value = false
            }
        }
    }

    fun playChapter(audiobook: Audiobook, chapter: AudiobookChapter, resumePositionMs: Long = 0) {
        val chapters = _chapters.value
        val startIndex = chapters.indexOfFirst { it.id == chapter.id }
        if (startIndex < 0) return

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

        com.mvbar.android.social.SocialRealtimeManager.selectLocalLongFormPlayback()
        val resumePositions = if (resumePositionMs > 0) {
            mapOf(allPseudoTracks[startIndex].id to resumePositionMs)
        } else emptyMap()
        val started = playerManager.playTracks(
            allPseudoTracks, startIndex, allStreamUrls, allArtUrls,
            customResumePositions = resumePositions
        )
        if (!started) return
        currentAudiobookId = audiobook.id
        currentChaptersList = chapters

        _playingChapter.value = chapter
        _playingAudiobook.value = audiobook

        startProgressSync(audiobook.id)
    }

    fun continueListening(audiobook: Audiobook) {
        val progress = _detailProgress.value
        val chapters = _chapters.value
        if (progress != null && chapters.isNotEmpty()) {
            val chapter = chapters.find { it.id == progress.chapterId }
            if (chapter != null) playChapter(audiobook, chapter, progress.positionMs)
            else playChapter(audiobook, chapters.first())
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
                        ActivityQueue.enqueueAudiobookProgress(audiobookId, chId, posMs)
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
                ActivityQueue.enqueueAudiobookFinished(audiobookId)
                if (NetworkMonitor.isOnline.value) {
                    loadAudiobooks()
                    loadAudiobookDetail(audiobookId)
                }
            } catch (e: Exception) {
                DebugLog.e("Audiobooks", "Failed to mark finished", e)
            }
        }
    }
}
