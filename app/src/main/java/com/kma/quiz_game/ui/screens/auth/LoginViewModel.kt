package com.kma.quiz_game.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.isNotBlank()
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun login() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.login(state.email.trim(), state.password)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isSubmitting = false)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = e.toUserMessage())
            }
        }
    }

    /** [idToken] comes from Google Sign-In -- fetching it needs an Activity Context, so that
     * part happens in the composable; this only handles exchanging it with the backend. */
    fun loginWithGoogle(idToken: String) {
        // isSubmitting is already true here -- set by onGoogleSignInStarted() when the button
        // was tapped, before the account picker even opened.
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.loginWithGoogle(idToken)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isSubmitting = false)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = e.toUserMessage())
            }
        }
    }

    /** Surfaces a Credential Manager failure (e.g. no Google account on device) without going
     * through a network call. */
    fun onGoogleSignInFailed(message: String) {
        _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = message)
    }

    /** User dismissed the account picker -- not an error worth showing. */
    fun onGoogleSignInCancelled() {
        _uiState.value = _uiState.value.copy(isSubmitting = false)
    }

    fun onGoogleSignInStarted() {
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
    }
}
