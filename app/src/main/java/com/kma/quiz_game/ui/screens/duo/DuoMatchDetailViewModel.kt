package com.kma.quiz_game.ui.screens.duo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.DuoMatchDetailDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.DuoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DuoMatchDetailUiState(
    val isLoading: Boolean = true,
    val match: DuoMatchDetailDto? = null,
    val errorMessage: String? = null,
)

/** One match, round by round. The server answers 403 for a match you did not play in. */
class DuoMatchDetailViewModel(
    private val matchId: String,
    private val duoRepository: DuoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuoMatchDetailUiState())
    val uiState: StateFlow<DuoMatchDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            duoRepository.match(matchId)
                .onSuccess { detail -> _uiState.update { it.copy(isLoading = false, match = detail) } }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }
}
