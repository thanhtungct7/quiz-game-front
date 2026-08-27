package com.kma.quiz_game.ui.screens.duo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.MatchFinishedDto
import com.kma.quiz_game.data.repository.DuoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Reads the finished match off the live session.
 *
 * The repository holds the FINISHED phase until it is acknowledged, so this screen can be entered,
 * rotated and re-entered without the result evaporating underneath it.
 */
class DuoResultViewModel(private val duoRepository: DuoRepository) : ViewModel() {

    val result: StateFlow<MatchFinishedDto?> = duoRepository.session
        .map { it.finished }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Returns the session to the lobby. Call once the result has actually been seen. */
    fun acknowledge() = duoRepository.acknowledgeResult()

    /** Straight back into the queue on the same settings the finished match used. */
    fun playAgain() {
        val settings = duoRepository.session.value.settings
        duoRepository.acknowledgeResult()
        duoRepository.joinQueue(settings)
    }
}
