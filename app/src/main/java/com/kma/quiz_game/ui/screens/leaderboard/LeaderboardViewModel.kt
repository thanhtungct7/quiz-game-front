package com.kma.quiz_game.ui.screens.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.DuoLeaderboardEntryDto
import com.kma.quiz_game.data.remote.dto.LeaderboardScope
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.AuthRepository
import com.kma.quiz_game.data.repository.DuoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Which board is on screen. The season one resets; the all-time one never does. */
    val scope: String = LeaderboardScope.CURRENT,
    val seasonCode: String? = null,
    val entries: List<DuoLeaderboardEntryDto> = emptyList(),
    /** Null until the player has finished at least one match -- the board only lists those. */
    val myRank: Int? = null,
    val myUserId: String? = null,
    val errorMessage: String? = null,
) {
    val podium: List<DuoLeaderboardEntryDto> get() = entries.take(PODIUM_SIZE)
    val rest: List<DuoLeaderboardEntryDto> get() = entries.drop(PODIUM_SIZE)

    /** True when the player is ranked but fell outside the page, so their row must be pinned. */
    val needsPinnedSelfRow: Boolean
        get() = myRank != null && entries.none { it.userId == myUserId }

    companion object {
        const val PODIUM_SIZE = 3
    }
}

class LeaderboardViewModel(
    private val duoRepository: DuoRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(myUserId = authRepository.currentUserId.first()) }
        }
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    /**
     * Switching board is a fresh load, not a filter: the two are ranked separately on the server,
     * and a player's position in one says nothing about their position in the other.
     */
    fun selectScope(scope: String) {
        if (scope == _uiState.value.scope) return
        _uiState.update { it.copy(scope = scope, entries = emptyList()) }
        load(isRefresh = false)
    }

    private fun load(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !isRefresh, isRefreshing = isRefresh, errorMessage = null) }
            duoRepository.leaderboard(scope = _uiState.value.scope)
                .onSuccess { board ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            entries = board.entries,
                            myRank = board.myRank,
                            seasonCode = board.seasonCode,
                        )
                    }
                }
                .onFailure { cause ->
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, errorMessage = cause.toUserMessage())
                    }
                }
        }
    }
}
