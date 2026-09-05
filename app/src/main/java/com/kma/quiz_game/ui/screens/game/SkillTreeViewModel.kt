package com.kma.quiz_game.ui.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.SkillNodeDto
import com.kma.quiz_game.data.remote.dto.SkillTreeDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillTreeUiState(
    val isLoading: Boolean = true,
    val tree: SkillTreeDto? = null,
    val unlocking: String? = null,
    val errorMessage: String? = null,
    val justUnlocked: String? = null,
) {
    /**
     * Nodes banded by tier.
     *
     * A tier is exactly how far down the tree a node sits, so banding by it lays the tree out
     * top-to-bottom without needing to lay out edges: the parent of anything in band N is in band
     * N-1, and each node names its own parent anyway.
     */
    val tiers: List<Pair<Int, List<SkillNodeDto>>>
        get() = tree?.skills.orEmpty()
            .groupBy { it.tier }
            .toSortedMap()
            .map { (tier, nodes) -> tier to nodes.sortedBy { it.name } }
}

class SkillTreeViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillTreeUiState())
    val uiState: StateFlow<SkillTreeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            gameRepository.skillTree()
                .onSuccess { tree -> _uiState.update { it.copy(isLoading = false, tree = tree) } }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    /**
     * Unlocking spends gold, so the whole tree is re-read afterwards rather than patched locally:
     * one purchase can open several children at once, and guessing which ones would drift.
     */
    fun unlock(node: SkillNodeDto) {
        if (!node.unlockable || node.owned) return
        viewModelScope.launch {
            _uiState.update { it.copy(unlocking = node.id) }
            gameRepository.unlockSkill(node.id)
                .onSuccess {
                    _uiState.update { it.copy(unlocking = null, justUnlocked = node.name) }
                    load()
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(unlocking = null, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null, justUnlocked = null) }
}
