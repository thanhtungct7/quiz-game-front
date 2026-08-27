package com.kma.quiz_game.ui.screens.duo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.repository.DuoPhase
import com.kma.quiz_game.data.repository.DuoRepository
import com.kma.quiz_game.data.repository.DuoSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DuoMatchUiState(
    val session: DuoSession = DuoSession(),
    /** Whole seconds left in the current round, ticked locally. */
    val secondsLeft: Int = 0,
    val showExitDialog: Boolean = false,
    val showChat: Boolean = false,
) {
    val timerFraction: Float
        get() {
            val limit = session.settings.timePerQuestion
            return if (limit <= 0) 0f else secondsLeft.toFloat() / limit.toFloat()
        }
}

/**
 * Drives the live match screen off [DuoRepository.session].
 *
 * It owns no match state of its own -- the repository does, so this screen can be recreated (or
 * navigated away from and back) mid-match without losing the round.
 *
 * The round clock is the one thing counted here rather than in the reducer: the server sends the
 * limit once per round and measures the real answer time itself, so the countdown is presentation
 * only and is deliberately kept out of the pure, testable reducer.
 */
class DuoMatchViewModel(private val duoRepository: DuoRepository) : ViewModel() {

    private val local = MutableStateFlow(DuoMatchUiState())
    private var tickJob: Job? = null

    val uiState: StateFlow<DuoMatchUiState> =
        combine(duoRepository.session, local) { session, state -> state.copy(session = session) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuoMatchUiState())

    init {
        viewModelScope.launch {
            var lastRoundKey: Pair<Int, DuoPhase>? = null
            duoRepository.session.collect { session ->
                val key = session.roundIndex to session.phase
                if (key == lastRoundKey) return@collect
                lastRoundKey = key
                if (session.phase == DuoPhase.IN_ROUND) {
                    startCountdown(session.roundSecondsRemaining)
                } else {
                    stopCountdown()
                }
            }
        }
    }

    /** Restarts on every new round, and on a resumed round starts from whatever is left of it. */
    private fun startCountdown(fromSeconds: Int) {
        tickJob?.cancel()
        local.update { it.copy(secondsLeft = fromSeconds) }
        tickJob = viewModelScope.launch {
            var remaining = fromSeconds
            while (remaining > 0) {
                delay(1_000)
                remaining--
                local.update { it.copy(secondsLeft = remaining) }
            }
        }
    }

    private fun stopCountdown() {
        tickJob?.cancel()
        tickJob = null
    }

    fun selectOption(optionId: String) =
        duoRepository.submitAnswer(uiState.value.session.roundIndex, optionId)

    fun setExitDialogVisible(visible: Boolean) = local.update { it.copy(showExitDialog = visible) }

    fun setChatVisible(visible: Boolean) = local.update { it.copy(showChat = visible) }

    /** The server broadcasts the message back to the sender too, so nothing is added locally. */
    fun sendChat(message: String) = duoRepository.sendChat(message)

    /** Walking out is always scored as a loss, whatever the score board says. */
    fun forfeit() {
        setExitDialogVisible(false)
        duoRepository.leaveMatch()
    }

    override fun onCleared() {
        stopCountdown()
        super.onCleared()
    }
}
