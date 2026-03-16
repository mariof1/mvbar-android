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
    val error: String? = null
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

    fun logout() {
        viewModelScope.launch {
            DebugLog.i("Auth", "Logout")
            repo.logout()
            _state.value = AuthState(isLoggedIn = false, isLoading = false)
        }
    }
}
