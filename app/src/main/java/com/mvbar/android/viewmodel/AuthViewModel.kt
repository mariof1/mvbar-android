package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.repository.AuthRepository
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val googleEnabled: Boolean = false,
    val googleClientId: String? = null,
    val checkingGoogle: Boolean = false
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository(app)
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = repo.restoreSession()
            DebugLog.i("Auth", "Session restore: $restored")
            _state.value = AuthState(isLoggedIn = restored, isLoading = false)
        }
    }

    fun checkGoogleAuth(serverUrl: String) {
        if (serverUrl.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(checkingGoogle = true)
            try {
                val info = repo.checkGoogleAuth(serverUrl)
                _state.value = _state.value.copy(
                    googleEnabled = info.enabled,
                    googleClientId = info.clientId,
                    checkingGoogle = false
                )
                DebugLog.d("Auth", "Google OAuth enabled: ${info.enabled}, clientId present: ${info.clientId != null}")
            } catch (e: Exception) {
                _state.value = _state.value.copy(googleEnabled = false, googleClientId = null, checkingGoogle = false)
            }
        }
    }

    fun login(serverUrl: String, email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            DebugLog.i("Auth", "Login attempt to $serverUrl as $email")
            val result = repo.login(serverUrl, email, password)
            _state.value = if (result.isSuccess) {
                DebugLog.i("Auth", "Login successful")
                _state.value.copy(isLoggedIn = true, isLoading = false)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Login failed"
                DebugLog.e("Auth", "Login failed: $msg")
                result.exceptionOrNull()?.let { DebugLog.e("Auth", "Login exception", it) }
                _state.value.copy(isLoading = false, error = msg)
            }
        }
    }

    fun googleSignIn(serverUrl: String, idToken: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            DebugLog.i("Auth", "Google sign-in attempt to $serverUrl")
            val result = repo.googleSignIn(serverUrl, idToken)
            _state.value = if (result.isSuccess) {
                DebugLog.i("Auth", "Google sign-in successful")
                _state.value.copy(isLoggedIn = true, isLoading = false)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Google sign-in failed"
                DebugLog.e("Auth", "Google sign-in failed: $msg")
                _state.value.copy(isLoading = false, error = msg)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            DebugLog.i("Auth", "Logout")
            repo.logout()
            _state.value = AuthState(isLoggedIn = false, isLoading = false)
        }
    }
}
