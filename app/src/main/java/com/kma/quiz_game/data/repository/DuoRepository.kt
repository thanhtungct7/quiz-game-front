package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.DuoSocket
import com.kma.quiz_game.data.remote.api.DuoApi
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChatMessageDto
import com.kma.quiz_game.data.remote.dto.DuoClientEvent
import com.kma.quiz_game.data.remote.dto.DuoErrorCode
import com.kma.quiz_game.data.remote.dto.DuoEvent
import com.kma.quiz_game.data.remote.dto.DuoLeaderboardDto
import com.kma.quiz_game.data.remote.dto.DuoMatchDetailDto
import com.kma.quiz_game.data.remote.dto.DuoMatchSummaryDto
import com.kma.quiz_game.data.remote.dto.DuoPlayerDto
import com.kma.quiz_game.data.remote.dto.DuoRoomPreviewDto
import com.kma.quiz_game.data.remote.dto.DuoSettingsDto
import com.kma.quiz_game.data.remote.dto.DuoStatsDto
import com.kma.quiz_game.data.remote.dto.MatchFinishedDto
import com.kma.quiz_game.data.remote.dto.RoundResultDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

/** Where a duo session currently is. Flat on purpose: the reducer stays readable and testable. */
enum class DuoPhase { IDLE, QUEUEING, ROOM_WAITING, MATCHED, IN_ROUND, ROUND_REVEAL, FINISHED }

/**
 * The whole client-side view of a duo session.
 *
 * Deliberately a single flat immutable snapshot rather than per-screen state: matchmaking, the
 * match itself and the result screen are three destinations reading one live session, and the
 * session has to survive navigating between them.
 */
data class DuoSession(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val phase: DuoPhase = DuoPhase.IDLE,
    val me: DuoPlayerDto? = null,
    val opponent: DuoPlayerDto? = null,
    val settings: DuoSettingsDto = DuoSettingsDto(),
    val matchId: String? = null,
    val roomCode: String? = null,
    val hostId: String? = null,
    /** False in a friend room, where the host still has to send `match.start`. */
    val autoStart: Boolean = true,
    val queuePosition: Int = 0,
    val queueWaitedSeconds: Int = 0,
    val queueTimedOut: Boolean = false,
    val roundIndex: Int = 0,
    val totalRounds: Int = 0,
    val question: ChallengeDto? = null,
    /** Seconds the current round started with -- the full limit normally, or whatever was left
     * when a `match.resume` rebuilt the round after a reconnect. */
    val roundSecondsRemaining: Int = 0,
    val myOptionId: String? = null,
    val opponentAnswered: Boolean = false,
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val roundResult: RoundResultDto? = null,
    val opponentConnected: Boolean = true,
    val opponentGraceSeconds: Int = 0,
    val finished: MatchFinishedDto? = null,
    val chat: List<ChatMessageDto> = emptyList(),
    val lastError: DuoErrorCode? = null,
) {
    val isHost: Boolean get() = me != null && me.id == hostId

    /** True once an answer is locked in for this round, or the round has already been revealed. */
    val hasAnswered: Boolean get() = myOptionId != null || phase == DuoPhase.ROUND_REVEAL

    val isInMatch: Boolean
        get() = phase == DuoPhase.MATCHED || phase == DuoPhase.IN_ROUND ||
            phase == DuoPhase.ROUND_REVEAL
}

/**
 * Owns the duo WebSocket and the session state it produces, plus thin wrappers over the duo REST
 * endpoints.
 *
 * Application-scoped: the socket outlives any single ViewModel, so leaving the match screen (or
 * having it recreated by a configuration change) never drops a match in progress.
 */
class DuoRepository(
    private val duoApi: DuoApi,
    private val socket: DuoSocket,
    private val json: Json,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _session = MutableStateFlow(DuoSession())
    val session: StateFlow<DuoSession> = _session.asStateFlow()

    init {
        scope.launch {
            socket.events.collect { event -> _session.update { reduce(it, event) } }
        }
    }

    // --- Socket lifecycle --------------------------------------------------

    fun connect() {
        if (_session.value.connection == ConnectionState.CONNECTED) return
        _session.update {
            it.copy(
                connection = if (it.isInMatch) ConnectionState.RECONNECTING
                else ConnectionState.CONNECTING,
            )
        }
        socket.connect()
    }

    /** Full teardown -- used on logout, where the token the socket authenticated with is revoked. */
    fun disconnect() {
        socket.close()
        _session.value = DuoSession()
    }

    // --- Actions -----------------------------------------------------------

    fun joinQueue(settings: DuoSettingsDto) {
        val safe = settings.coerced()
        _session.update {
            it.copy(
                phase = DuoPhase.QUEUEING,
                settings = safe,
                queuePosition = 0,
                queueWaitedSeconds = 0,
                queueTimedOut = false,
                lastError = null,
            )
        }
        socket.send(DuoClientEvent.QUEUE_JOIN, safe.toJsonObject())
    }

    fun leaveQueue() {
        socket.send(DuoClientEvent.QUEUE_LEAVE)
    }

    fun createRoom(settings: DuoSettingsDto) {
        val safe = settings.coerced()
        _session.update { it.copy(settings = safe, lastError = null) }
        socket.send(DuoClientEvent.ROOM_CREATE, safe.toJsonObject())
    }

    fun joinRoom(roomCode: String) {
        _session.update { it.copy(lastError = null) }
        socket.send(DuoClientEvent.ROOM_JOIN, buildJsonObject { put("room_code", roomCode.normalizeRoomCode()) })
    }

    /** Host only; anyone else gets a `NOT_HOST` error frame back. */
    fun startMatch() {
        socket.send(DuoClientEvent.MATCH_START)
    }

    /**
     * Answers are timed by the server from the moment it sent `round.start`, so this fires on the
     * tap itself -- there is no confirm step to spend the speed bonus on.
     */
    fun submitAnswer(roundIndex: Int, optionId: String) {
        val current = _session.value
        if (current.phase != DuoPhase.IN_ROUND || current.myOptionId != null) return
        _session.update { it.copy(myOptionId = optionId) }
        socket.send(
            DuoClientEvent.ANSWER_SUBMIT,
            buildJsonObject {
                put("round_index", roundIndex)
                put("option_id", optionId)
            },
        )
    }

    /**
     * Leaves a room, or forfeits a match in progress -- which the server always scores as a loss,
     * whatever the score was at the time.
     *
     * Cancelling a *waiting* room is silent on the wire: the server tears the room down without
     * broadcasting anything, so the lobby has to put itself back to idle here.
     */
    fun leaveMatch() {
        socket.send(DuoClientEvent.MATCH_LEAVE)
        _session.update {
            if (it.phase == DuoPhase.ROOM_WAITING) {
                it.copy(phase = DuoPhase.IDLE, roomCode = null, opponent = null, matchId = null)
            } else {
                it
            }
        }
    }

    fun sendChat(message: String) {
        val trimmed = message.trim().take(MAX_CHAT_LENGTH)
        if (trimmed.isEmpty()) return
        socket.send(DuoClientEvent.CHAT_SEND, buildJsonObject { put("message", trimmed) })
    }

    /** Called once the result screen has been seen, returning the session to the lobby. */
    fun acknowledgeResult() {
        _session.update {
            DuoSession(
                connection = it.connection,
                me = it.me,
                settings = it.settings,
            )
        }
    }

    fun clearError() {
        _session.update { it.copy(lastError = null, queueTimedOut = false) }
    }

    // --- REST --------------------------------------------------------------

    suspend fun stats(): Result<DuoStatsDto> = runCatching { duoApi.getMyStats() }

    suspend fun leaderboard(limit: Int = DEFAULT_LEADERBOARD_LIMIT): Result<DuoLeaderboardDto> =
        runCatching { duoApi.getLeaderboard(limit) }

    suspend fun matches(limit: Int = DEFAULT_HISTORY_PAGE, offset: Int = 0): Result<List<DuoMatchSummaryDto>> =
        runCatching { duoApi.listMatches(limit, offset) }

    suspend fun match(matchId: String): Result<DuoMatchDetailDto> =
        runCatching { duoApi.getMatch(matchId) }

    suspend fun previewRoom(roomCode: String): Result<DuoRoomPreviewDto> =
        runCatching { duoApi.previewRoom(roomCode.normalizeRoomCode()) }

    private fun DuoSettingsDto.toJsonObject(): JsonObject =
        json.encodeToJsonElement(DuoSettingsDto.serializer(), this).jsonObject

    companion object {
        const val MAX_CHAT_LENGTH = 200
        const val DEFAULT_LEADERBOARD_LIMIT = 50
        const val DEFAULT_HISTORY_PAGE = 20

        /** Room codes are 6 characters from an alphabet with no I/O/0/1, and the server upper-cases
         * whatever it receives -- so do the same here and drop anything that cannot be in a code. */
        const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val ROOM_CODE_LENGTH = 6

        fun String.normalizeRoomCode(): String =
            uppercase().filter { it in ROOM_CODE_ALPHABET }.take(ROOM_CODE_LENGTH)

        /**
         * The one piece of real logic in this file, kept pure so it can be unit-tested on the JVM
         * with no socket, no server and no Android framework.
         */
        fun reduce(state: DuoSession, event: DuoEvent): DuoSession = when (event) {
            DuoEvent.SocketOpen -> state.copy(connection = ConnectionState.CONNECTED)

            is DuoEvent.SocketClosed -> state.copy(
                connection = if (event.willRetry) ConnectionState.RECONNECTING
                else ConnectionState.DISCONNECTED,
            )

            // Carries `active_match_id` when the server still holds a match for us; the
            // `match.resume` that rebuilds it follows on its own.
            is DuoEvent.Connected -> state.copy(
                connection = ConnectionState.CONNECTED,
                me = event.data.user,
                matchId = event.data.activeMatchId ?: state.matchId,
            )

            is DuoEvent.QueueWaiting -> state.copy(
                phase = DuoPhase.QUEUEING,
                queuePosition = event.data.position,
                queueWaitedSeconds = event.data.waitedSeconds,
            )

            DuoEvent.QueueLeft -> state.copy(phase = DuoPhase.IDLE, queuePosition = 0, queueWaitedSeconds = 0)

            DuoEvent.QueueTimeout -> state.copy(phase = DuoPhase.IDLE, queueTimedOut = true)

            is DuoEvent.RoomCreated -> state.copy(
                phase = DuoPhase.ROOM_WAITING,
                matchId = event.data.matchId,
                roomCode = event.data.roomCode,
                settings = event.data.settings,
                hostId = state.me?.id,
            )

            // A random-queue match is already running, so it goes straight to the match screen.
            // A friend room is not: both players stay in the lobby until the host starts it, or
            // the host would be shown a match screen with no way to begin.
            is DuoEvent.MatchFound -> state.copy(
                phase = if (event.data.autoStart) DuoPhase.MATCHED else DuoPhase.ROOM_WAITING,
                autoStart = event.data.autoStart,
                matchId = event.data.matchId,
                roomCode = event.data.roomCode ?: state.roomCode,
                opponent = event.data.opponent,
                settings = event.data.settings,
                hostId = event.data.hostId,
                totalRounds = event.data.settings.questionCount,
                myScore = 0,
                opponentScore = 0,
                roundIndex = 0,
                opponentConnected = true,
                finished = null,
                chat = emptyList(),
            )

            is DuoEvent.MatchStarted -> state.copy(
                phase = DuoPhase.MATCHED,
                matchId = event.data.matchId,
                totalRounds = event.data.totalRounds,
            )

            is DuoEvent.RoundStart -> state.copy(
                phase = DuoPhase.IN_ROUND,
                roundIndex = event.data.roundIndex,
                totalRounds = event.data.totalRounds,
                question = event.data.question,
                roundSecondsRemaining = event.data.timeLimitSeconds,
                myOptionId = null,
                opponentAnswered = false,
                roundResult = null,
            )

            is DuoEvent.OpponentAnswered ->
                if (event.data.roundIndex == state.roundIndex) state.copy(opponentAnswered = true)
                else state

            is DuoEvent.RoundResult -> state.copy(
                phase = DuoPhase.ROUND_REVEAL,
                roundIndex = event.data.roundIndex,
                roundResult = event.data,
                myScore = event.data.yourScore,
                opponentScore = event.data.opponentScore,
                myOptionId = event.data.you.optionId ?: state.myOptionId,
            )

            // Reconnected mid-match: rebuild the screen from the server's version of the truth,
            // including how much of the round's clock is actually left.
            is DuoEvent.MatchResume -> state.copy(
                connection = ConnectionState.CONNECTED,
                phase = if (event.data.question != null) DuoPhase.IN_ROUND else DuoPhase.MATCHED,
                matchId = event.data.matchId,
                opponent = event.data.opponent,
                settings = event.data.settings,
                roundIndex = event.data.roundIndex,
                totalRounds = event.data.totalRounds,
                myScore = event.data.yourScore,
                opponentScore = event.data.opponentScore,
                question = event.data.question,
                roundSecondsRemaining = event.data.secondsRemaining ?: 0,
                // A resumed round we already answered must stay locked; the option id itself is
                // not resent, so a non-null placeholder is what keeps the buttons disabled.
                myOptionId = if (event.data.alreadyAnswered) RESUMED_ANSWER else null,
                opponentAnswered = false,
                roundResult = null,
                finished = null,
            )

            is DuoEvent.OpponentDisconnected -> state.copy(
                opponentConnected = false,
                opponentGraceSeconds = event.data.graceSeconds,
            )

            DuoEvent.OpponentReconnected -> state.copy(opponentConnected = true, opponentGraceSeconds = 0)

            is DuoEvent.MatchFinished -> state.copy(
                phase = DuoPhase.FINISHED,
                finished = event.data,
                myScore = event.data.yourScore,
                opponentScore = event.data.opponentScore,
            )

            is DuoEvent.Chat -> state.copy(chat = state.chat + event.data)

            DuoEvent.Pong -> state

            // Losing a race to answer is routine, not something to interrupt the player over.
            is DuoEvent.Failed -> when (val code = event.data.errorCode) {
                DuoErrorCode.ROUND_CLOSED, DuoErrorCode.ALREADY_ANSWERED -> state

                // The server closes an idle room with this error and then drops it, so sitting in
                // the lobby afterwards would be waiting for a room that no longer exists.
                DuoErrorCode.ROOM_NOT_FOUND -> if (state.phase == DuoPhase.ROOM_WAITING) {
                    state.copy(phase = DuoPhase.IDLE, roomCode = null, opponent = null, lastError = code)
                } else {
                    state.copy(lastError = code)
                }

                else -> state.copy(lastError = code)
            }
        }

        /** Stands in for "an answer is already locked in" when resuming, where the server tells us
         * that we answered but not what we picked. */
        const val RESUMED_ANSWER = "__resumed__"
    }
}
