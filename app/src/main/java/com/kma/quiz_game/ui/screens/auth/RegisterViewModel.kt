package com.kma.quiz_game.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Mirrors backend RegisterRequest constraints: password 8-128 chars, username optional 1-50. */
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.length in 8..128 &&
            username.length <= 50
}

class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun register() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.register(
                email = state.email.trim(),
                password = state.password,
                username = state.username.trim().ifBlank { null },
            )
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isSubmitting = false)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = e.toUserMessage())
            }
        }
    }
}
