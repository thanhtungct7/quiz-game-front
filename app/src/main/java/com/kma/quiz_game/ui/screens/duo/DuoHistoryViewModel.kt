package com.kma.quiz_game.ui.screens.duo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.DuoMatchSummaryDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.DuoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DuoHistoryUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val matches: List<DuoMatchSummaryDto> = emptyList(),
    /** False once a page comes back short, which is the only end-of-list signal the API gives. */
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * Paged match history.
 *
 * Paging is a plain offset counter rather than Paging 3: the list is short, read-only, and reached
 * from a single screen -- a library would be more moving parts than the feature has.
 */
class DuoHistoryViewModel(private val duoRepository: DuoRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DuoHistoryUiState())
    val uiState: StateFlow<DuoHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            duoRepository.matches(offset = 0)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            matches = page,
                            hasMore = page.size == DuoRepository.DEFAULT_HISTORY_PAGE,
                        )
                    }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            duoRepository.matches(offset = state.matches.size)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            matches = it.matches + page,
                            hasMore = page.size == DuoRepository.DEFAULT_HISTORY_PAGE,
                        )
                    }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoadingMore = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }
}
