package com.kma.quiz_game.ui.screens.quests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.ActivityChestDto
import com.kma.quiz_game.data.remote.dto.DailyQuestDto
import com.kma.quiz_game.data.remote.dto.QuestClaimDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.QuestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Today's four quests and three chests, and the two claim buttons.
 *
 * The day itself lives in [QuestRepository] -- the banner on the path reads the same flow -- so
 * this only adds what the screen needs on top: which claim is in flight, the popup after one, and
 * an error. A claim's response carries the day as it stands afterwards, so nothing is patched
 * locally.
 */
class DailyQuestsViewModel(private val questRepository: QuestRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyQuestsUiState(day = questRepository.today.value))
    val uiState: StateFlow<DailyQuestsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            questRepository.today.collect { day ->
                _uiState.update { it.copy(day = day, isLoading = it.isLoading && day == null) }
            }
        }
    }

    /** Loaded by the screen on entering composition: quests move whenever a battle ends. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.day == null, errorMessage = null) }
            questRepository.refresh().onFailure { cause ->
                _uiState.update { it.copy(errorMessage = cause.toUserMessage()) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun claimQuest(quest: DailyQuestDto) {
        if (!quest.claimable) return
        claim(key = quest.id, title = quest.title, chestTier = null) {
            questRepository.claimQuest(quest.id)
        }
    }

    fun claimChest(chest: ActivityChestDto) {
        if (!chest.claimable) return
        claim(key = chestKey(chest.milestone), title = chest.name, chestTier = chest.tier) {
            questRepository.claimChest(chest.milestone)
        }
    }

    fun dismissCelebration() {
        _uiState.update { it.copy(celebration = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun claim(
        key: String,
        title: String,
        chestTier: String?,
        request: suspend () -> Result<QuestClaimDto>,
    ) {
        if (_uiState.value.claiming != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(claiming = key, errorMessage = null) }
            request()
                .onSuccess { claim ->
                    _uiState.update {
                        it.copy(
                            claiming = null,
                            celebration = QuestCelebration(title, claim.reward, chestTier),
                        )
                    }
                }
                .onFailure { cause ->
                    _uiState.update {
                        it.copy(claiming = null, errorMessage = cause.toUserMessage(CLAIM_ERRORS))
                    }
                    // Whatever the server disagreed about, its view of the day is the one to show.
                    questRepository.refresh()
                }
        }
    }
}
