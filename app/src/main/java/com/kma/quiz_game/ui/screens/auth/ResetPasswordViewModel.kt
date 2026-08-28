package com.kma.quiz_game.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResetPasswordUiState(
    val token: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val isReset: Boolean = false,
    val errorMessage: String? = null,
) {
    /** True when the screen was opened by hand rather than from the emailed link. */
    val needsToken: Boolean
        get() = token.isBlank()

    /** Mirrors the backend's ResetPasswordRequest: token >= 32 chars, password 8-128. */
    val canSubmit: Boolean
        get() = !isSubmitting && token.length >= MIN_TOKEN_LENGTH &&
            password.length in 8..128 && password == confirmPassword

    val passwordsMismatch: Boolean
        get() = confirmPassword.isNotEmpty() && password != confirmPassword

    private companion object {
        const val MIN_TOKEN_LENGTH = 32
    }
}

class ResetPasswordViewModel(
    private val authRepository: AuthRepository,
    /** Empty when the user reached the screen without the emailed link. */
    token: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResetPasswordUiState(token = token))
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun onTokenChange(value: String) {
        _uiState.value = _uiState.value.copy(token = value.trim(), errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null)
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.resetPassword(state.token, state.password)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isSubmitting = false, isReset = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = e.toUserMessage(),
                )
            }
        }
    }
}
