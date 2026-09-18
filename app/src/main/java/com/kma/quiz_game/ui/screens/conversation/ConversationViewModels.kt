package com.kma.quiz_game.ui.screens.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.ConversationSummaryDto
import com.kma.quiz_game.data.remote.dto.ScenarioDto
import com.kma.quiz_game.data.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The scenarios to practise, and opening a conversation on one. */
class ConversationTopicsViewModel(private val repository: ConversationRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationTopicsUiState())
    val uiState: StateFlow<ConversationTopicsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.scenarios()
                .onSuccess { scenarios -> _uiState.update { it.copy(isLoading = false, scenarios = scenarios) } }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = cause.toConversationMessage()) }
                }
        }
    }

    fun select(scenario: ScenarioDto) =
        _uiState.update { it.copy(selected = scenario, startErrorMessage = null) }

    fun dismissSelection() = _uiState.update {
        if (it.isStarting) it else it.copy(selected = null, startErrorMessage = null)
    }

    /** Every start counts against the learner's daily conversations, hence the brief in between. */
    fun start() {
        val scenario = _uiState.value.selected ?: return
        if (_uiState.value.isStarting) return
        _uiState.update { it.copy(isStarting = true, startErrorMessage = null) }
        viewModelScope.launch {
            repository.start(scenario.code)
                .onSuccess { detail ->
                    _uiState.update { it.copy(isStarting = false, selected = null, startedSessionId = detail.id) }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isStarting = false, startErrorMessage = cause.toConversationMessage()) }
                }
        }
    }

    fun consumeStarted() = _uiState.update { it.copy(startedSessionId = null) }
}

/** The feedback on one finished conversation, and starting the same scenario again. */
class ConversationFeedbackViewModel(
    private val sessionId: String,
    private val repository: ConversationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationFeedbackUiState())
    val uiState: StateFlow<ConversationFeedbackUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /**
     * Feedback is stored with the conversation, so reading it back is free. A conversation without
     * any yet is finished here instead -- that is safe to repeat, and the only way this screen is
     * reached without feedback is a finish whose response was lost.
     */
    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val detail = repository.get(sessionId).mapCatching { detail ->
                if (detail.feedback != null) detail else repository.finish(sessionId).getOrThrow()
            }
            detail
                .onSuccess { loaded -> _uiState.update { it.copy(isLoading = false, detail = loaded) } }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = cause.toConversationMessage()) }
                }
        }
    }

    fun toggleTranscript() = _uiState.update { it.copy(showTranscript = !it.showTranscript) }

    fun practiceAgain() {
        val scenario = _uiState.value.detail?.scenario ?: return
        if (_uiState.value.isStartingAgain) return
        _uiState.update { it.copy(isStartingAgain = true, errorMessage = null) }
        viewModelScope.launch {
            repository.start(scenario.code)
                .onSuccess { detail ->
                    _uiState.update { it.copy(isStartingAgain = false, startedSessionId = detail.id) }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isStartingAgain = false, errorMessage = cause.toConversationMessage()) }
                }
        }
    }

    fun consumeStarted() = _uiState.update { it.copy(startedSessionId = null) }
}

/** Past conversations, newest first, a page at a time. */
class ConversationHistoryViewModel(private val repository: ConversationRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationHistoryUiState())
    val uiState: StateFlow<ConversationHistoryUiState> = _uiState.asStateFlow()

    /** Called whenever the screen is shown, so a conversation just finished shows its score. */
    fun refresh() {
        _uiState.update { it.copy(isLoading = it.items.isEmpty(), errorMessage = null) }
        viewModelScope.launch {
            repository.history(HISTORY_PAGE_SIZE)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(isLoading = false, items = page, endReached = page.size < HISTORY_PAGE_SIZE)
                    }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = cause.toConversationMessage()) }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val last = state.items.lastOrNull() ?: return
        if (state.isLoading || state.isLoadingMore || state.endReached) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            repository.history(HISTORY_PAGE_SIZE, before = last.startedAt)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            items = it.items + page.filterNot { row -> it.items.any { old -> old.id == row.id } },
                            endReached = page.size < HISTORY_PAGE_SIZE,
                        )
                    }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoadingMore = false, errorMessage = cause.toConversationMessage()) }
                }
        }
    }

    fun askDelete(item: ConversationSummaryDto) = _uiState.update { it.copy(pendingDelete = item) }

    fun dismissDelete() = _uiState.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val item = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            repository.delete(item.id)
                .onSuccess { _uiState.update { it.copy(items = it.items.filterNot { row -> row.id == item.id }) } }
                .onFailure { cause -> _uiState.update { it.copy(errorMessage = cause.toConversationMessage()) } }
        }
    }
}
