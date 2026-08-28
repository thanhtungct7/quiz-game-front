package com.kma.quiz_game.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.UserRead
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_USERNAME_LENGTH = 50
const val MAX_BIO_LENGTH = 300

data class ProfileUiState(
    val userId: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val hasUploadedAvatar: Boolean = false,
    val isLoading: Boolean = true,
    val isUploadingAvatar: Boolean = false,
    val errorMessage: String? = null,
    /** What is stored, kept apart from the draft so the edit screen knows what changed. */
    val username: String = "",
    val bio: String = "",
    val draftUsername: String = "",
    val draftBio: String = "",
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    val hasAvatar: Boolean get() = avatarUrl != null

    /** Only an uploaded avatar can be removed -- see [ProfileUiState.hasUploadedAvatar]. */
    val canRemoveAvatar: Boolean get() = hasUploadedAvatar && !isUploadingAvatar

    /**
     * Mirrors the backend's own limits: username at most 50, bio at most 300. Blanking a
     * display name that is already set is rejected here because the backend rejects it too --
     * catching it in the UI is the difference between a message and a silent no-op.
     */
    val isDraftValid: Boolean
        get() {
            val name = draftUsername.trim()
            val nameIsValid = name.length <= MAX_USERNAME_LENGTH &&
                (name.isNotEmpty() || username.isEmpty())
            return nameIsValid && draftBio.trim().length <= MAX_BIO_LENGTH
        }

    val hasChanges: Boolean
        get() = draftUsername.trim() != username || draftBio.trim() != bio

    val canSubmit: Boolean get() = !isSaving && isDraftValid && hasChanges
}

/**
 * Backed by [ProfileRepository]'s cached profile rather than by its own copy: the profile and
 * the edit screens each get their own instance of this ViewModel, and collecting the shared
 * flow is what keeps them from drifting apart after a save.
 */
class ProfileViewModel(private val profileRepository: ProfileRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.profile.filterNotNull().collect { user ->
                _uiState.update { it.applyProfile(user) }
            }
        }
        load()
    }

    fun load() {
        // Only a first, uncached load is worth a spinner; a revalidation should leave whatever
        // is already on screen alone.
        _uiState.update {
            it.copy(isLoading = profileRepository.profile.value == null, errorMessage = null)
        }
        viewModelScope.launch {
            profileRepository.refresh().onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(draftUsername = value, errorMessage = null, isSaved = false) }
    }

    fun onBioChange(value: String) {
        _uiState.update { it.copy(draftBio = value, errorMessage = null, isSaved = false) }
    }

    /** Throws away an abandoned edit so reopening the screen starts from what is stored. */
    fun discardDraft() {
        _uiState.update {
            it.copy(
                draftUsername = it.username,
                draftBio = it.bio,
                errorMessage = null,
                isSaved = false,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null, isSaved = false) }
        viewModelScope.launch {
            val username = state.draftUsername.trim()
            profileRepository.updateProfile(
                // Only send what changed: an omitted field means "leave it alone" to the
                // backend, which is one less way to clobber the other one.
                username = username.takeIf { it != state.username && it.isNotEmpty() },
                // "" clears the bio; null would be dropped from the body and change nothing.
                bio = state.draftBio.trim().takeIf { it != state.bio },
            )
                .onSuccess { _uiState.update { it.copy(isSaved = true) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = error.toUserMessage())
                    }
                }
        }
    }

    fun uploadAvatar(content: ByteArray, mimeType: String) {
        _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
        viewModelScope.launch {
            profileRepository.uploadAvatar(content, mimeType).onFailure { error ->
                _uiState.update {
                    it.copy(isUploadingAvatar = false, errorMessage = error.toUserMessage())
                }
            }
        }
    }

    fun removeAvatar() {
        _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
        viewModelScope.launch {
            profileRepository.removeAvatar().onFailure { error ->
                _uiState.update {
                    it.copy(isUploadingAvatar = false, errorMessage = error.toUserMessage())
                }
            }
        }
    }

    /** Surfaces a failure that happened before any request -- an unreadable picked image. */
    fun onAvatarReadFailed() {
        _uiState.update {
            it.copy(
                isUploadingAvatar = false,
                errorMessage = "Không đọc được ảnh đã chọn. Hãy thử ảnh khác.",
            )
        }
    }

    private fun ProfileUiState.applyProfile(user: UserRead) = copy(
        userId = user.id,
        email = user.email,
        avatarUrl = user.avatarUrl,
        hasUploadedAvatar = user.hasUploadedAvatar,
        username = user.username.orEmpty(),
        bio = user.bio.orEmpty(),
        draftUsername = user.username.orEmpty(),
        draftBio = user.bio.orEmpty(),
        isLoading = false,
        isUploadingAvatar = false,
        isSaving = false,
        errorMessage = null,
    )
}
