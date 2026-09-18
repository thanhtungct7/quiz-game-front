package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.DuoSocket
import com.kma.quiz_game.data.remote.api.DuoApi
import com.kma.quiz_game.data.remote.dto.AnswerResultDto
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChatMessageDto
import com.kma.quiz_game.data.remote.dto.DuoActiveEffectDto
import com.kma.quiz_game.data.remote.dto.DuoClientEvent
import com.kma.quiz_game.data.remote.dto.DuoErrorCode
import com.kma.quiz_game.data.remote.dto.DuoEvent
import com.kma.quiz_game.data.remote.dto.DuoLeaderboardDto
import com.kma.quiz_game.data.remote.dto.DuoMatchDetailDto
import com.kma.quiz_game.data.remote.dto.DuoMatchSummaryDto
import com.kma.quiz_game.data.remote.dto.DuoPlayerDto
import com.kma.quiz_game.data.remote.dto.DuoRoomPreviewDto
import com.kma.quiz_game.data.remote.dto.DuoSettingsDto
import com.kma.quiz_game.data.remote.dto.DuoSkillUsedDto
import com.kma.quiz_game.data.remote.dto.DuoStatsDto
import com.kma.quiz_game.data.remote.dto.LeaderboardScope
import com.kma.quiz_game.data.remote.dto.MatchFinishedDto
import com.kma.quiz_game.data.remote.dto.OpponentAnsweredDto
import com.kma.quiz_game.data.widget.WidgetSync
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

/**
 * The mana ceiling, from `app/services/game/combat.py`.
 *
 * Duplicated here only to draw a bar as a fraction: every mana number that matters is computed on
 * the server and arrives on the wire, and nothing the client does is gated on this value.
 */
const val MAX_MANA = 100

/**
 * Where a duo session currently is.
 *
 * Five states, not seven: the old IN_ROUND / ROUND_REVEAL pair described whose turn it was, and
 * nobody takes turns any more. Whether a question is on screen is [DuoSession.hasQuestion], and it
 * flips many times inside one FIGHTING phase, independently of what the opponent is doing.
 */
enum class DuoPhase { IDLE, QUEUEING, ROOM_WAITING, MATCHED, FIGHTING, FINISHED }

/**
 * The whole client-side view of a duo session.
 *
 * Deliberately a single flat immutable snapshot rather than per-screen state: matchmaking, the
 * match itself and the result screen are three destinations reading one live session, and the
 * session has to survive navigating between them.
 *
 * Every instant here -- [deadlineAt], [lockoutEndsAt], [stunnedUntil] -- is on the *server's*
 * clock, and [serverOffsetMs] is what converts a local `elapsedRealtime` reading to it. Storing
 * deadlines rather than countdowns is what lets the arena draw at 60 fps from a feed that arrives
 * ten times a second.
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

    // --- the deck ----------------------------------------------------------
    /** How many questions the match was drawn with. Both decks start this long. */
    val deckSize: Int = 0,
    /** How many this player still has to get right, the one on screen included. */
    val myDeckRemaining: Int = 0,
    val opponentDeckRemaining: Int = 0,
    /** The token that answers the question on screen. Null between two questions. */
    val questionToken: String? = null,
    val question: ChallengeDto? = null,
    /** True when this question is coming round again after a wrong answer. */
    val questionRetry: Boolean = false,
    val myOptionId: String? = null,
    val answerResult: AnswerResultDto? = null,

    // --- combat ------------------------------------------------------------
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val myHp: Int = 0,
    val opponentHp: Int = 0,
    val myMaxHp: Int = 0,
    val opponentMaxHp: Int = 0,
    val mana: Int = 0,
    val combo: Int = 0,
    val opponentCombo: Int = 0,
    /**
     * The last blow the opponent landed on us, with a counter beside it.
     *
     * The counter is what makes it an event rather than a state: two identical blows in a row are
     * two things to animate, and comparing the payloads alone would miss the second.
     */
    val lastIncoming: OpponentAnsweredDto? = null,
    val incomingSeq: Int = 0,
    /** The last cast either side made, and the same counter trick. */
    val lastSkill: DuoSkillUsedDto? = null,
    val skillSeq: Int = 0,
    /** Wrong options a REMOVE_OPTIONS skill hid from us. Cleared on every new question. */
    val removedOptionIds: List<String> = emptyList(),
    val effects: List<DuoActiveEffectDto> = emptyList(),

    // --- the clock ---------------------------------------------------------
    /** Add to a local `elapsedRealtime` reading to get the server's clock. */
    val serverOffsetMs: Long = 0,
    /** When the match is decided on health if nobody has fallen or cleared their deck. */
    val deadlineAt: Long = 0,
    /** While this is in the future the player is reading the last result. */
    val lockoutEndsAt: Long = 0,
    /** While this is in the future the player cannot answer or cast. */
    val stunnedUntil: Long = 0,
    val roundTripMs: Long = 0,

    val opponentConnected: Boolean = true,
    val opponentGraceSeconds: Int = 0,
    val finished: MatchFinishedDto? = null,
    val chat: List<ChatMessageDto> = emptyList(),
    val lastError: DuoErrorCode? = null,
    /**
     * Bumped on every error frame.
     *
     * The same code twice in a row -- two taps on a skill with no mana -- is two events the screen
     * has to flash twice, but [lastError] alone does not change, so a state comparison would miss
     * the second one. Kept pure (a counter, not a clock) so the reducer stays testable.
     */
    val errorSeq: Int = 0,
) {
    val isHost: Boolean get() = me != null && me.id == hostId

    val hasQuestion: Boolean get() = questionToken != null

    /** True once an answer is locked in, or while the result of the last one is on screen. */
    val hasAnswered: Boolean get() = myOptionId != null

    val isInMatch: Boolean
        get() = phase == DuoPhase.MATCHED || phase == DuoPhase.FIGHTING

    val myHpFraction: Float get() = if (myMaxHp <= 0) 0f else myHp.toFloat() / myMaxHp

    val opponentHpFraction: Float
        get() = if (opponentMaxHp <= 0) 0f else opponentHp.toFloat() / opponentMaxHp

    val manaFraction: Float get() = (mana.toFloat() / MAX_MANA).coerceIn(0f, 1f)

    /** How many of this player's questions are already behind them. */
    val myCleared: Int get() = (deckSize - myDeckRemaining).coerceAtLeast(0)

    val opponentCleared: Int get() = (deckSize - opponentDeckRemaining).coerceAtLeast(0)

    /** The server's clock, from a local `elapsedRealtime` reading. */
    fun serverNowMs(localNowMs: Long): Long = localNowMs + serverOffsetMs

    /** True while the player is reading the last result and no question is up yet. */
    fun inLockout(localNowMs: Long): Boolean =
        lockoutEndsAt > 0 && serverNowMs(localNowMs) < lockoutEndsAt

    fun isStunned(localNowMs: Long): Boolean =
        stunnedUntil > 0 && serverNowMs(localNowMs) < stunnedUntil

    /** Milliseconds left on the match clock. Zero once it is up, or while it is unknown. */
    fun matchRemainingMs(localNowMs: Long): Long =
        if (deadlineAt <= 0) 0 else (deadlineAt - serverNowMs(localNowMs)).coerceAtLeast(0)

    /** True while a skill may be cast at all -- a running match we are not sitting out. */
    fun canCastSkill(localNowMs: Long): Boolean =
        phase == DuoPhase.FIGHTING && !isStunned(localNowMs)
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
    private val widgetSync: WidgetSync,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _session = MutableStateFlow(DuoSession())
    val session: StateFlow<DuoSession> = _session.asStateFlow()

    init {
        scope.launch {
            socket.events.collect { event ->
                _session.update { reduce(it, event) }
                // Both modes settle through the same daily streak, so a match counts for the
                // home-screen widget exactly as a lesson battle does.
                if (event is DuoEvent.MatchFinished) {
                    event.data.streak?.let { widgetSync.onSettlement(it.dayStreak, it.bestDayStreak) }
                }
            }
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
     * Answers the question on screen.
     *
     * Fires on the tap itself: the server times the answer from the moment it pushed *this player*
     * the question and a faster answer hits harder, so a confirm step would spend the speed bonus
     * on a second thought. The token is the session's own -- a caller cannot answer a question
     * that has already closed.
     */
    fun submitAnswer(optionId: String) {
        val current = _session.value
        val token = current.questionToken ?: return
        if (current.myOptionId != null) return
        _session.update { it.copy(myOptionId = optionId) }
        socket.send(
            DuoClientEvent.ANSWER_SUBMIT,
            buildJsonObject {
                put("token", token)
                put("option_id", optionId)
            },
        )
    }

    /**
     * Casts an equipped skill.
     *
     * Unlike answering, this is not tied to a question: a shield or a heal is worth casting while
     * the player is reading the last explanation and the opponent is still swinging.
     */
    fun useSkill(skillCode: String) {
        if (_session.value.phase != DuoPhase.FIGHTING) return
        socket.send(
            DuoClientEvent.SKILL_USE,
            buildJsonObject { put("skill_code", skillCode) },
        )
    }

    /** Measures the round trip and keeps the clock offset honest between snapshots. */
    fun ping(nowMs: Long) {
        socket.send(
            DuoClientEvent.PING,
            buildJsonObject { put("client_time_ms", nowMs) },
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

    /**
     * Called once the result screen has been seen, returning the session to the lobby.
     *
     * Rebuilding from a fresh [DuoSession] rather than copying is what clears the combat state:
     * health, mana, combo, the deck and the standing effects all belong to the match that just
     * ended, and carrying any of them into the next lobby would draw a half-empty health bar over
     * a match that has not started.
     */
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

    suspend fun leaderboard(
        limit: Int = DEFAULT_LEADERBOARD_LIMIT,
        scope: String = LeaderboardScope.CURRENT,
    ): Result<DuoLeaderboardDto> = runCatching { duoApi.getLeaderboard(limit, scope) }

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
                myScore = 0,
                opponentScore = 0,
                opponentConnected = true,
                finished = null,
                chat = emptyList(),
                // Everything the last match left behind. A stale pair of bars drawn over a new
                // opponent's face is worse than an empty one.
                deckSize = 0,
                myDeckRemaining = 0,
                opponentDeckRemaining = 0,
                questionToken = null,
                question = null,
                questionRetry = false,
                myOptionId = null,
                answerResult = null,
                myHp = 0,
                opponentHp = 0,
                myMaxHp = 0,
                opponentMaxHp = 0,
                mana = 0,
                combo = 0,
                opponentCombo = 0,
                lastIncoming = null,
                lastSkill = null,
                removedOptionIds = emptyList(),
                effects = emptyList(),
                deadlineAt = 0,
                lockoutEndsAt = 0,
                stunnedUntil = 0,
            )

            // The one frame that states both maximums outright, and the first that carries the
            // server's clock -- the arena cannot draw a deadline before it knows the offset.
            is DuoEvent.MatchStarted -> state.copy(
                phase = DuoPhase.FIGHTING,
                matchId = event.data.matchId,
                serverOffsetMs = event.data.serverTimeMs - event.receivedAtMs,
                deckSize = event.data.deckSize,
                myDeckRemaining = event.data.deckSize,
                opponentDeckRemaining = event.data.deckSize,
                deadlineAt = event.data.deadlineAt,
                myHp = event.data.yourHp,
                myMaxHp = event.data.yourMaxHp,
                opponentHp = event.data.opponentHp,
                opponentMaxHp = event.data.opponentMaxHp,
                mana = event.data.yourMana,
                combo = 0,
                opponentCombo = 0,
                lockoutEndsAt = 0,
                stunnedUntil = 0,
            )

            is DuoEvent.QuestionPush -> state.copy(
                phase = DuoPhase.FIGHTING,
                questionToken = event.data.token,
                question = event.data.question,
                questionRetry = event.data.retry,
                myDeckRemaining = event.data.deckRemaining,
                myOptionId = null,
                answerResult = null,
                // A skill's reveal only applies to the question it was cast on.
                removedOptionIds = emptyList(),
            )

            /**
             * Closes the question the moment the result lands. Note what is not copied: our own
             * health. An answer cannot cost health any more, and taking it from here would let a
             * stale frame undo a blow the opponent has already landed.
             */
            is DuoEvent.AnswerResult -> state.copy(
                questionToken = null,
                answerResult = event.data,
                myOptionId = event.data.optionId,
                myScore = event.data.yourScore,
                mana = event.data.yourMana,
                combo = event.data.yourCombo,
                myDeckRemaining = event.data.yourDeckRemaining,
                opponentHp = event.data.opponentHp,
                lockoutEndsAt = event.data.lockoutEndsAt,
            )

            // The blow has already landed on the server; this is not a prediction.
            is DuoEvent.OpponentAnswered -> state.copy(
                myHp = event.data.yourHp,
                opponentScore = event.data.opponentScore,
                opponentCombo = event.data.opponentCombo,
                opponentDeckRemaining = event.data.opponentDeckRemaining,
                stunnedUntil = maxOf(state.stunnedUntil, event.data.yourStunnedUntil),
                lastIncoming = event.data,
                incomingSeq = state.incomingSeq + 1,
            )

            /**
             * The snapshot is authoritative for every bar on screen, and it is also where the
             * clock offset comes from -- recomputed on every frame rather than negotiated once, so
             * a device that sleeps mid-match corrects itself within a tenth of a second.
             */
            is DuoEvent.StateTick -> state.copy(
                phase = if (state.phase == DuoPhase.FINISHED) state.phase else DuoPhase.FIGHTING,
                serverOffsetMs = event.data.t - event.receivedAtMs,
                myHp = event.data.yourHp,
                myMaxHp = event.data.yourMaxHp,
                mana = event.data.yourMana,
                combo = event.data.yourCombo,
                myScore = event.data.yourScore,
                myDeckRemaining = event.data.yourDeckRemaining,
                opponentHp = event.data.opponentHp,
                opponentMaxHp = event.data.opponentMaxHp,
                opponentCombo = event.data.opponentCombo,
                opponentScore = event.data.opponentScore,
                opponentDeckRemaining = event.data.opponentDeckRemaining,
                deadlineAt = event.data.deadlineAt,
                lockoutEndsAt = event.data.lockoutEndsAt,
                stunnedUntil = event.data.stunnedUntil,
                effects = event.data.effects,
            )

            // Reconnected mid-match: rebuild the screen from the server's version of the truth,
            // the question that was on screen included.
            is DuoEvent.MatchResume -> state.copy(
                connection = ConnectionState.CONNECTED,
                phase = DuoPhase.FIGHTING,
                matchId = event.data.matchId,
                opponent = event.data.opponent,
                settings = event.data.settings,
                serverOffsetMs = event.data.serverTimeMs - event.receivedAtMs,
                deckSize = event.data.deckSize,
                myDeckRemaining = event.data.yourDeckRemaining,
                opponentDeckRemaining = event.data.opponentDeckRemaining,
                deadlineAt = event.data.deadlineAt,
                questionToken = event.data.token,
                question = event.data.question,
                questionRetry = false,
                // The option is not resent, so a question handed back still open is one this
                // player has yet to answer.
                myOptionId = null,
                answerResult = null,
                myScore = event.data.yourScore,
                opponentScore = event.data.opponentScore,
                myHp = event.data.yourHp,
                myMaxHp = event.data.yourMaxHp,
                opponentHp = event.data.opponentHp,
                opponentMaxHp = event.data.opponentMaxHp,
                mana = event.data.yourMana,
                combo = event.data.yourCombo,
                opponentCombo = event.data.opponentCombo,
                lockoutEndsAt = event.data.lockoutEndsAt,
                stunnedUntil = event.data.stunnedUntil,
                effects = event.data.effects,
                removedOptionIds = emptyList(),
                lastIncoming = null,
                lastSkill = null,
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
                myHp = event.data.yourHpLeft,
                opponentHp = event.data.opponentHpLeft,
                questionToken = null,
                stunnedUntil = 0,
            )

            // Sent to both players. `private` arrives only for the caster, which is what carries
            // the options a reveal hid from us.
            is DuoEvent.SkillUsed -> state.copy(
                myHp = event.data.yourHp,
                opponentHp = event.data.opponentHp,
                mana = event.data.yourMana,
                // A TIME_PENALTY landing on us pushes this out, and the screen must say so at once
                // rather than wait up to a tenth of a second for the next snapshot.
                lockoutEndsAt = if (event.data.lockoutEndsAt > 0) event.data.lockoutEndsAt
                else state.lockoutEndsAt,
                lastSkill = event.data,
                skillSeq = state.skillSeq + 1,
                removedOptionIds = event.data.private?.removedOptionIds
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { state.removedOptionIds + it }
                    ?: state.removedOptionIds,
            )

            is DuoEvent.Chat -> state.copy(chat = state.chat + event.data)

            is DuoEvent.Pong -> state.copy(
                roundTripMs = (event.receivedAtMs - event.data.clientTimeMs).coerceAtLeast(0),
            )

            is DuoEvent.Failed -> when (val code = event.data.errorCode) {
                // Tapping an option a beat after the question closed is routine, not something to
                // interrupt the player over -- but the tap must be released, or the screen sits on
                // a selected answer nothing will ever resolve.
                DuoErrorCode.QUESTION_CLOSED, DuoErrorCode.INVALID_OPTION ->
                    state.copy(myOptionId = null)

                // The server closes an idle room with this error and then drops it, so sitting in
                // the lobby afterwards would be waiting for a room that no longer exists.
                DuoErrorCode.ROOM_NOT_FOUND -> if (state.phase == DuoPhase.ROOM_WAITING) {
                    state.copy(
                        phase = DuoPhase.IDLE,
                        roomCode = null,
                        opponent = null,
                        lastError = code,
                        errorSeq = state.errorSeq + 1,
                    )
                } else {
                    state.copy(lastError = code, errorSeq = state.errorSeq + 1)
                }

                // Energy is checked when we queue, so this refusal means the match never opened.
                // Staying in QUEUEING would be waiting on a queue entry the server has dropped.
                DuoErrorCode.NOT_ENOUGH_ENERGY -> state.copy(
                    phase = DuoPhase.IDLE,
                    lastError = code,
                    errorSeq = state.errorSeq + 1,
                )

                else -> state.copy(lastError = code, errorSeq = state.errorSeq + 1)
            }
        }
    }
}
