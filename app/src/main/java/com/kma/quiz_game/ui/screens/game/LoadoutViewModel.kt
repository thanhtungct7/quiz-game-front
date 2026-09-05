package com.kma.quiz_game.ui.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.LOADOUT_SLOTS
import com.kma.quiz_game.data.remote.dto.SkillNodeDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoadoutUiState(
    val isLoading: Boolean = true,
    val owned: List<SkillNodeDto> = emptyList(),
    /** The draft bar, in slot order. Saved only when the player asks. */
    val selectedIds: List<String> = emptyList(),
    val savedIds: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val isFull: Boolean get() = selectedIds.size >= LOADOUT_SLOTS

    val isDirty: Boolean get() = selectedIds != savedIds

    fun selected(): List<SkillNodeDto> = selectedIds.mapNotNull { id -> owned.firstOrNull { it.id == id } }

    /** What a full bar would cost to fire in one round -- the only number worth pre-computing. */
    val totalManaCost: Int get() = selected().sumOf { it.manaCost }
}

/**
 * The equipped bar: three skills out of everything owned.
 *
 * Edited as a draft and saved explicitly rather than written on each tap. Swapping a bar is
 * several taps -- take one off, put two on -- and a server round trip per tap would leave a
 * half-built bar live in a match the player may be about to start.
 */
class LoadoutViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoadoutUiState())
    val uiState: StateFlow<LoadoutUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            gameRepository.skillTree()
                .onSuccess { tree ->
                    val owned = tree.skills.filter { it.owned }
                    // The tree already says which slot each skill sits in, so the draft starts
                    // from the server's own ordering rather than from the loadout call.
                    val equipped = owned
                        .filter { it.equippedSlot != null }
                        .sortedBy { it.equippedSlot }
                        .map { it.id }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            owned = owned,
                            selectedIds = equipped,
                            savedIds = equipped,
                        )
                    }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    /** Tapping an equipped skill takes it off; tapping a spare puts it on if there is room. */
    fun toggle(node: SkillNodeDto) {
        _uiState.update { state ->
            val current = state.selectedIds
            when {
                node.id in current -> state.copy(selectedIds = current - node.id)
                current.size >= LOADOUT_SLOTS -> state
                else -> state.copy(selectedIds = current + node.id)
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val ids = _uiState.value.selectedIds
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            gameRepository.setLoadout(ids)
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, savedIds = ids) }
                    onDone()
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
}
