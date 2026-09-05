package com.kma.quiz_game.ui.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.GameClassDto
import com.kma.quiz_game.data.remote.dto.GameProfileDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Changing class after the first pick costs this much gold and clears the equipped bar. */
const val CLASS_CHANGE_COST = 500

data class ClassPickerUiState(
    val isLoading: Boolean = true,
    val classes: List<GameClassDto> = emptyList(),
    val profile: GameProfileDto? = null,
    val pending: GameClassDto? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    /** The first class is free. Anything after it is a paid change. */
    val hasClass: Boolean get() = profile?.classCode != null

    val canAfford: Boolean get() = !hasClass || (profile?.gold ?: 0) >= CLASS_CHANGE_COST
}

class ClassPickerViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val local = MutableStateFlow(ClassPickerUiState())

    val uiState: StateFlow<ClassPickerUiState> =
        combine(local, gameRepository.profile) { state, profile -> state.copy(profile = profile) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClassPickerUiState())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            local.update { it.copy(isLoading = true, errorMessage = null) }
            gameRepository.refreshProfile()
            gameRepository.classes()
                .onSuccess { classes -> local.update { it.copy(isLoading = false, classes = classes) } }
                .onFailure { cause ->
                    local.update { it.copy(isLoading = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    /** Never applied straight from a tap: a paid change also wipes the bar, so it is confirmed. */
    fun select(gameClass: GameClassDto) = local.update { it.copy(pending = gameClass) }

    fun dismissConfirm() = local.update { it.copy(pending = null) }

    fun confirm(onDone: () -> Unit) {
        val target = local.value.pending ?: return
        viewModelScope.launch {
            local.update { it.copy(isSaving = true) }
            gameRepository.chooseClass(target.code)
                .onSuccess {
                    local.update { it.copy(isSaving = false, pending = null) }
                    load()
                    onDone()
                }
                .onFailure { cause ->
                    local.update {
                        it.copy(isSaving = false, pending = null, errorMessage = cause.toUserMessage())
                    }
                }
        }
    }

    fun dismissError() = local.update { it.copy(errorMessage = null) }
}
