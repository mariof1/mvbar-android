package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.repository.AuthRepository
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
            _state.value = AuthState(isLoggedIn = restored, isLoading = false)
        }
    }

    fun login(serverUrl: String, email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = repo.login(serverUrl, email, password)
            _state.value = if (result.isSuccess) {
                _state.value.copy(isLoggedIn = true, isLoading = false)
            } else {
                _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Login failed"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _state.value = AuthState(isLoggedIn = false, isLoading = false)
        }
    }
}
