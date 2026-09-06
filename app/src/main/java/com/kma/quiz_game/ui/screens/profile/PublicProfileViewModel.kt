package com.kma.quiz_game.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kma.quiz_game.data.remote.dto.PublicProfileDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [seed] is what the caller already knows about this player -- the leaderboard row or lobby entry
 * they tapped. It is drawn immediately so the modal opens with content instead of a spinner, and
 * is replaced the moment the full card arrives.
 *
 * That is the whole reason this state has both a [seed] and a [profile]: the round trip is fast,
 * but it is not instant, and an empty sheet sliding up is the one thing that makes a fast
 * endpoint feel slow.
 */
data class PublicProfileUiState(
    val userId: String = "",
    val seed: PublicProfileDto? = null,
    val profile: PublicProfileDto? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    /** What to draw right now: the full card once it lands, the seed until then. */
    val visible: PublicProfileDto? get() = profile ?: seed

    /** A failure only matters when there is nothing at all to show; with a seed on screen the
     * card is merely incomplete, and an error banner over it would be noise. */
    val showsError: Boolean get() = errorMessage != null && visible == null
}

class PublicProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val userId: String,
    seed: PublicProfileDto? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PublicProfileUiState(userId = userId, seed = seed, isLoading = true),
    )
    val uiState: StateFlow<PublicProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            profileRepository.publicProfile(userId)
                .onSuccess { card ->
                    _uiState.update { it.copy(profile = card, isLoading = false) }
                }
                .onFailure { cause ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = cause.toUserMessage())
                    }
                }
        }
    }

    companion object {
        /**
         * Keyed by the player being looked at, so opening a second card does not reuse the first
         * one's state. Built inline rather than listed in `AppViewModelFactory` for the reason
         * that factory documents: it only builds ViewModels whose dependencies are all
         * application-scoped, and this one takes a route argument.
         */
        fun factory(
            repository: ProfileRepository,
            userId: String,
            seed: PublicProfileDto?,
        ) = viewModelFactory {
            initializer { PublicProfileViewModel(repository, userId, seed) }
        }
    }
}
