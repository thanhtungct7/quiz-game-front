package com.kma.quiz_game.ui.screens.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.BattleFinishedDto
import com.kma.quiz_game.data.repository.BattleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Reads the finished battle off the live session.
 *
 * The repository holds the FINISHED phase until it is acknowledged, so this screen can be entered,
 * rotated and re-entered without the result evaporating underneath it.
 */
class BattleResultViewModel(private val battleRepository: BattleRepository) : ViewModel() {

    val result: StateFlow<BattleFinishedDto?> = battleRepository.session
        .map { it.finished }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val monsterName: StateFlow<String> = battleRepository.session
        .map { it.monster?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val monsterArtCode: StateFlow<String> = battleRepository.session
        .map { it.monster?.artCode.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Returns the session to idle. Call once the result has actually been seen. */
    fun acknowledge() = battleRepository.acknowledgeResult()
}
