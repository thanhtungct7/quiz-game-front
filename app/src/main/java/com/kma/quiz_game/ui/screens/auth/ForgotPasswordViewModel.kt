package com.kma.quiz_game.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    /**
     * True once the request went through -- which says nothing about whether the address has an
     * account. The backend answers the same way either way, and the screen must too.
     */
    val isSent: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank()
}

class ForgotPasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.requestPasswordReset(state.email.trim())
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isSubmitting = false, isSent = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = e.toUserMessage(),
                )
            }
        }
    }

    /** Back to the form, so a mistyped address can be corrected without leaving the screen. */
    fun editEmail() {
        _uiState.value = _uiState.value.copy(isSent = false, errorMessage = null)
    }
}
