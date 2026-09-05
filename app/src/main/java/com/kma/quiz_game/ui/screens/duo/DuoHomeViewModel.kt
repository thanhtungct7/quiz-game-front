package com.kma.quiz_game.ui.screens.duo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.DuoRoomPreviewDto
import com.kma.quiz_game.data.remote.dto.DuoSettingsDto
import com.kma.quiz_game.data.remote.dto.DuoStatsDto
import com.kma.quiz_game.data.remote.dto.GameProfileDto
import com.kma.quiz_game.data.remote.dto.SeasonDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.DuoRepository
import com.kma.quiz_game.data.repository.DuoRepository.Companion.normalizeRoomCode
import com.kma.quiz_game.data.repository.DuoSession
import com.kma.quiz_game.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DuoHomeUiState(
    val session: DuoSession = DuoSession(),
    val stats: DuoStatsDto? = null,
    val profile: GameProfileDto? = null,
    val season: SeasonDto? = null,
    val showOutOfEnergy: Boolean = false,
    val isLoadingStats: Boolean = true,
    /** The draft settings the next match will be opened with. */
    val settings: DuoSettingsDto = DuoSettingsDto(),
    val showSettingsSheet: Boolean = false,
    val showJoinDialog: Boolean = false,
    val roomCodeInput: String = "",
    val roomPreview: DuoRoomPreviewDto? = null,
    val isPreviewingRoom: Boolean = false,
    val joinErrorMessage: String? = null,
    val errorMessage: String? = null,
) {
    val canJoinRoom: Boolean
        get() = roomCodeInput.length == DuoRepository.ROOM_CODE_LENGTH && !isPreviewingRoom

    /**
     * False only when the profile has actually loaded and says the bar is empty.
     *
     * Erring towards "let them try" is deliberate: energy is taken server-side after the question
     * draw succeeds, so a client that guesses wrong costs nothing, while a client that blocks on a
     * profile it failed to load would lock a player out of a match they can afford.
     */
    val hasEnergy: Boolean get() = profile?.energy?.isEmpty != true
}

class DuoHomeViewModel(
    private val duoRepository: DuoRepository,
    private val gameRepository: GameRepository,
) : ViewModel() {

    private val local = MutableStateFlow(DuoHomeUiState())

    val uiState: StateFlow<DuoHomeUiState> =
        combine(duoRepository.session, local, gameRepository.profile) { session, state, profile ->
            state.copy(session = session, profile = profile)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Same reason as DuoMatchViewModel: a blank seed reports IDLE for one frame, which
            // re-keys the lobby's phase effect and fires `onMatchStarting` a second time.
            DuoHomeUiState(
                session = duoRepository.session.value,
                profile = gameRepository.profile.value,
            ),
        )

    init {
        // Opening the socket up front means the lobby already knows who we are, and picks up an
        // `active_match_id` if a match from a previous run of the app is still waiting for us.
        duoRepository.connect()
        refreshStats()
    }

    /** Re-read on every visit: a match just played moves energy, level, gold and the ladder. */
    fun refreshProfile() {
        viewModelScope.launch {
            gameRepository.refreshProfile()
            gameRepository.currentSeason()
                .onSuccess { season -> local.update { it.copy(season = season) } }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            local.update { it.copy(isLoadingStats = true) }
            duoRepository.stats()
                .onSuccess { stats -> local.update { it.copy(stats = stats, isLoadingStats = false) } }
                .onFailure { cause ->
                    local.update { it.copy(isLoadingStats = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    /** Manual retry after the socket has exhausted its own reconnect attempts. */
    fun reconnect() = duoRepository.connect()

    /**
     * Queueing costs one energy out of five.
     *
     * Refused here rather than by the server so the answer is instant and, more importantly, so it
     * can carry the way *out* of an empty bar: finishing a lesson refills two, which is the loop
     * the whole energy system exists to push.
     */
    fun findMatch() {
        if (!uiState.value.hasEnergy) {
            local.update { it.copy(showOutOfEnergy = true) }
            return
        }
        duoRepository.joinQueue(uiState.value.settings)
    }

    fun createRoomChecked() {
        if (!uiState.value.hasEnergy) {
            local.update { it.copy(showOutOfEnergy = true) }
            return
        }
        createRoom()
    }

    fun dismissOutOfEnergy() = local.update { it.copy(showOutOfEnergy = false) }

    fun cancelQueue() = duoRepository.leaveQueue()

    fun createRoom() = duoRepository.createRoom(uiState.value.settings)

    fun startMatch() = duoRepository.startMatch()

    /** Leaves a friend room the same way as any other match -- the room is a WAITING match. */
    fun leaveRoom() = duoRepository.leaveMatch()

    // --- Settings sheet ----------------------------------------------------

    fun openSettings() = local.update { it.copy(showSettingsSheet = true) }

    fun closeSettings() = local.update { it.copy(showSettingsSheet = false) }

    fun updateSettings(settings: DuoSettingsDto) =
        local.update { it.copy(settings = settings.coerced()) }

    // --- Join by room code -------------------------------------------------

    fun openJoinDialog() =
        local.update { it.copy(showJoinDialog = true, roomCodeInput = "", roomPreview = null, joinErrorMessage = null) }

    fun closeJoinDialog() = local.update { it.copy(showJoinDialog = false) }

    fun updateRoomCode(raw: String) {
        val code = raw.normalizeRoomCode()
        local.update { it.copy(roomCodeInput = code, roomPreview = null, joinErrorMessage = null) }
        if (code.length == DuoRepository.ROOM_CODE_LENGTH) previewRoom(code)
    }

    /** Shows who is hosting and on what settings before committing to the room. */
    private fun previewRoom(code: String) {
        viewModelScope.launch {
            local.update { it.copy(isPreviewingRoom = true) }
            duoRepository.previewRoom(code)
                .onSuccess { preview ->
                    local.update { it.copy(isPreviewingRoom = false, roomPreview = preview) }
                }
                .onFailure { cause ->
                    local.update {
                        it.copy(isPreviewingRoom = false, roomPreview = null, joinErrorMessage = cause.toUserMessage())
                    }
                }
        }
    }

    fun confirmJoinRoom() {
        val code = uiState.value.roomCodeInput
        if (code.length != DuoRepository.ROOM_CODE_LENGTH) return
        duoRepository.joinRoom(code)
        local.update { it.copy(showJoinDialog = false) }
    }

    fun dismissError() {
        duoRepository.clearError()
        local.update { it.copy(errorMessage = null) }
    }
}
