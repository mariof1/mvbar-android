package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.model.ShareTarget
import com.mvbar.android.data.model.SocialSearchUser
import com.mvbar.android.data.model.SocialSummary
import com.mvbar.android.data.model.TrackShare
import com.mvbar.android.data.repository.SocialRepository
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.social.SocialRealtimeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class SocialUiState(
    val summary: SocialSummary = SocialSummary(),
    val shares: List<TrackShare> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SocialSearchUser> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSearching: Boolean = false,
    val busyKeys: Set<String> = emptySet(),
    val error: String? = null
) {
    val badgeCount: Int get() = summary.incoming.size + summary.unreadShares
}

data class ShareDialogState(
    val trackId: Int? = null,
    val targets: List<ShareTarget> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
)

class SocialViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SocialRepository()
    private val _state = MutableStateFlow(SocialUiState())
    val state: StateFlow<SocialUiState> = _state.asStateFlow()

    private val _shareDialog = MutableStateFlow(ShareDialogState())
    val shareDialog: StateFlow<ShareDialogState> = _shareDialog.asStateFlow()

    private var searchJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            SocialRealtimeManager.revision.drop(1).collect { refresh(silent = true) }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(
                // A realtime/background refresh must not clear an initial or
                // user-requested loading indicator that is already in progress.
                isLoading = if (silent) current.isLoading else current.summary.ok.not(),
                isRefreshing = if (silent) current.isRefreshing else current.summary.ok,
                error = null
            )
            try {
                val summary = repository.getSummary()
                val shares = repository.getShares()
                _state.value = _state.value.copy(
                    summary = summary,
                    shares = shares.shares,
                    isLoading = false,
                    isRefreshing = false,
                    error = null
                )
            } catch (e: Exception) {
                DebugLog.e("Social", "Failed to load friends and shares", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = errorMessage(e)
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.value = _state.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.value = _state.value.copy(isSearching = true)
            try {
                val response = repository.searchUsers(query)
                if (_state.value.searchQuery == query) {
                    _state.value = _state.value.copy(
                        searchResults = response.users,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                if (_state.value.searchQuery == query) {
                    _state.value = _state.value.copy(isSearching = false, error = errorMessage(e))
                }
            }
        }
    }

    fun sendFriendRequest(userId: String) = runAction("user:$userId") {
        repository.sendFriendRequest(userId)
    }

    fun acceptFriendRequest(relationshipId: Int) = runAction("request:$relationshipId") {
        repository.acceptFriendRequest(relationshipId)
    }

    fun removeFriendRequest(relationshipId: Int) = runAction("request:$relationshipId") {
        repository.removeFriendRequest(relationshipId)
    }

    fun removeFriend(userId: String) = runAction("user:$userId") {
        repository.removeFriend(userId)
    }

    fun markShareRead(shareId: Int) = runAction("share:$shareId", refreshSearch = false) {
        repository.markShareRead(shareId)
    }

    fun markAllSharesRead() = runAction("shares:all", refreshSearch = false) {
        repository.markAllSharesRead()
    }

    fun deleteShare(shareId: Int) = runAction("share:$shareId", refreshSearch = false) {
        repository.deleteShare(shareId)
    }

    fun openShareDialog(trackId: Int) {
        _shareDialog.value = ShareDialogState(trackId = trackId, isLoading = true)
        viewModelScope.launch {
            try {
                val response = repository.getShareTargets(trackId)
                if (_shareDialog.value.trackId == trackId) {
                    _shareDialog.value = _shareDialog.value.copy(
                        targets = response.friends,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _shareDialog.value = _shareDialog.value.copy(
                    isLoading = false,
                    error = errorMessage(e)
                )
            }
        }
    }

    fun closeShareDialog() {
        _shareDialog.value = ShareDialogState()
    }

    fun shareTrack(
        recipientIds: List<String>,
        message: String?,
        onShared: (Int) -> Unit
    ) {
        val trackId = _shareDialog.value.trackId ?: return
        if (recipientIds.isEmpty()) return
        viewModelScope.launch {
            _shareDialog.value = _shareDialog.value.copy(isSending = true, error = null)
            try {
                val response = repository.shareTrack(trackId, recipientIds, message)
                _shareDialog.value = ShareDialogState()
                onShared(response.shared)
            } catch (e: Exception) {
                _shareDialog.value = _shareDialog.value.copy(
                    isSending = false,
                    error = errorMessage(e)
                )
            }
        }
    }

    private fun runAction(
        key: String,
        refreshSearch: Boolean = true,
        action: suspend () -> Unit
    ) {
        if (key in _state.value.busyKeys) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                busyKeys = _state.value.busyKeys + key,
                error = null
            )
            try {
                action()
                refresh(silent = true)
                if (refreshSearch && _state.value.searchQuery.trim().length >= 2) {
                    val query = _state.value.searchQuery
                    val response = repository.searchUsers(query)
                    if (_state.value.searchQuery == query) {
                        _state.value = _state.value.copy(searchResults = response.users)
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = errorMessage(e))
            } finally {
                _state.value = _state.value.copy(busyKeys = _state.value.busyKeys - key)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun errorMessage(error: Exception): String {
        if (error is HttpException) {
            val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            return when {
                body.contains("already_friends") -> "You are already friends"
                body.contains("request_pending") || body.contains("request_exists") -> "Friend request already sent"
                body.contains("incoming_request") -> "This user has already sent you a request"
                body.contains("recipient_unavailable") -> "One of these friends cannot access this song"
                error.code() == 401 -> "Your session has expired. Please sign in again."
                else -> "Server error (${error.code()})"
            }
        }
        return error.message ?: "Something went wrong"
    }
}
