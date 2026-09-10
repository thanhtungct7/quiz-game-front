package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.BattleSocket
import com.kma.quiz_game.data.remote.api.BattleApi
import com.kma.quiz_game.data.remote.dto.BattleClientEvent
import com.kma.quiz_game.data.remote.dto.BattleErrorCode
import com.kma.quiz_game.data.remote.dto.BattleEvent
import com.kma.quiz_game.data.remote.dto.BattleFinishedDto
import com.kma.quiz_game.data.remote.dto.BattleHistoryDto
import com.kma.quiz_game.data.remote.dto.BattleSkillUsedDto
import com.kma.quiz_game.data.remote.dto.BattleActiveEffectDto
import com.kma.quiz_game.data.remote.dto.BattleAnswerResultDto
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import com.kma.quiz_game.data.remote.dto.CourseMonstersDto
import com.kma.quiz_game.data.remote.dto.MonsterCatalogDto
import com.kma.quiz_game.data.remote.dto.MonsterDto
import com.kma.quiz_game.data.remote.dto.MonsterPreviewDto
import com.kma.quiz_game.data.remote.dto.MonsterSwingDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Where a lesson battle currently is.
 *
 * Four states, not six: the old IN_ROUND / ROUND_REVEAL pair described whose turn it was, and
 * nobody takes turns any more. Whether a question is on screen is [BattleSession.hasQuestion], and
 * it can flip several times inside one FIGHTING phase.
 */
enum class BattlePhase { IDLE, STARTING, FIGHTING, FINISHED }

/**
 * The whole client-side view of one lesson battle.
 *
 * A single flat immutable snapshot, like [DuoSession]: the battle screen and the result screen are
 * two destinations reading one live fight, and the fight has to survive navigating between them.
 *
 * Every instant here -- [castEndsAt], [lockoutEndsAt] -- is on the *server's* clock, and
 * [serverOffsetMs] is what converts a local reading to it. Storing deadlines rather than progress
 * is what lets the arena draw a smooth ring at 60 fps from a feed that arrives ten times a second.
 */
data class BattleSession(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val phase: BattlePhase = BattlePhase.IDLE,
    val battleId: String? = null,
    val lessonId: String? = null,
    val lessonTitle: String = "",
    val monster: MonsterDto? = null,
    val monsterHp: Int = 0,
    val monsterMaxHp: Int = 0,
    val hp: Int = 0,
    val maxHp: Int = 0,
    val mana: Int = 0,
    val combo: Int = 0,
    /** How many questions the lesson holds -- not how many the fight will ask. */
    val questionsInPool: Int = 0,
    /** Above zero once the pool has been round; repeats no longer move lesson progress. */
    val poolPass: Int = 0,
    /** The token that answers the question on screen. Null between two questions. */
    val questionToken: String? = null,
    val question: ChallengeDto? = null,
    /** What this player has answered: one option, or an ORDER question's tiles in the order they
     * were laid down. Empty until an answer is locked in. */
    val myOptionIds: List<String> = emptyList(),
    val answerResult: BattleAnswerResultDto? = null,
    /** Wrong options a REMOVE_OPTIONS skill hid. Cleared on every new question. */
    val removedOptionIds: List<String> = emptyList(),
    val effects: List<BattleActiveEffectDto> = emptyList(),

    // --- the clock ---------------------------------------------------------
    /** Add to a local `elapsedRealtime` reading to get the server's clock. */
    val serverOffsetMs: Long = 0,
    val castEndsAt: Long = 0,
    val nextSwing: String = "",
    val nextSwingDamage: Int = 0,
    val lockoutEndsAt: Long = 0,
    /** The last blow the monster landed, for the arena to react to. */
    val lastSwing: MonsterSwingDto? = null,
    /** The last skill cast, for the arena to react to and the dock to start a cooldown from. */
    val lastSkill: BattleSkillUsedDto? = null,
    val roundTripMs: Long = 0,

    val finished: BattleFinishedDto? = null,
    val lastError: BattleErrorCode? = null,
) {
    val hasQuestion: Boolean get() = questionToken != null

    /** True once an answer is locked in, or while the result of the last one is on screen. */
    val hasAnswered: Boolean get() = myOptionIds.isNotEmpty()

    val isFighting: Boolean
        get() = phase == BattlePhase.STARTING || phase == BattlePhase.FIGHTING

    val hpFraction: Float get() = if (maxHp <= 0) 0f else hp.toFloat() / maxHp
    val monsterHpFraction: Float
        get() = if (monsterMaxHp <= 0) 0f else monsterHp.toFloat() / monsterMaxHp

    val enragedNext: Boolean get() = nextSwing == "ENRAGED_ATTACK"

    /** The server's clock, from a local `elapsedRealtime` reading. */
    fun serverNowMs(localNowMs: Long): Long = localNowMs + serverOffsetMs

    /** Milliseconds until the monster's blow lands. Zero once it is due. */
    fun castRemainingMs(localNowMs: Long): Long =
        (castEndsAt - serverNowMs(localNowMs)).coerceAtLeast(0)

    /**
     * How full the wind-up is, 0..1.
     *
     * Interpolated locally against the monster's own interval, so it climbs smoothly between two
     * snapshots instead of stepping ten times a second.
     */
    fun castFraction(localNowMs: Long): Float {
        val interval = monster?.castIntervalMs ?: 0
        if (interval <= 0 || castEndsAt <= 0) return 0f
        val remaining = castRemainingMs(localNowMs).toFloat()
        return (1f - remaining / interval).coerceIn(0f, 1f)
    }

    /** True while the player is reading the last result and no question is up yet. */
    fun inLockout(localNowMs: Long): Boolean =
        lockoutEndsAt > 0 && serverNowMs(localNowMs) < lockoutEndsAt
}

/**
 * Owns the PvE WebSocket and the session it produces, plus thin wrappers over the `/battles` REST
 * endpoints.
 *
 * Application-scoped, like [DuoRepository]: the socket outlives any single ViewModel, so a
 * configuration change cannot drop a fight in progress. It is a *separate* socket from duo's --
 * two protocols, two engines, and nothing shared but the combat maths on the server.
 */
class BattleRepository(
    private val battleApi: BattleApi,
    private val socket: BattleSocket,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _session = MutableStateFlow(BattleSession())
    val session: StateFlow<BattleSession> = _session.asStateFlow()

    /**
     * A `battle.start` asked for before the socket finished opening.
     *
     * The screen navigates to the fight and asks to start it in the same breath, so the request
     * almost always arrives first. Holding it here and flushing it on `SocketOpen` is what makes
     * "tap a gate, land in a fight" work on a cold socket.
     */
    private var pendingStart: String? = null

    /**
     * Gives up on a `battle.start` that was never answered.
     *
     * Without it a socket that cannot come up leaves the screen on STARTING forever -- a spinner
     * with no fight behind it and no way out, which is exactly what a server restart used to do.
     */
    private var startWatchdog: Job? = null

    init {
        scope.launch {
            socket.events.collect { event ->
                if (event is BattleEvent.SocketOpen) flushPendingStart()
                // The fight is open; nothing is waiting to be given up on any more.
                if (event is BattleEvent.Started) cancelStartWatchdog()
                _session.update { reduce(it, event) }
            }
        }
    }

    // --- Socket lifecycle --------------------------------------------------

    /**
     * Asks for the socket, unless it is already up.
     *
     * Guarded on the socket rather than on [BattleSession.connection]: the session field is a copy
     * of what the last event said, and a copy that has drifted out of date -- a socket that died
     * without its close event being seen -- must not be what stops a reconnect.
     */
    fun connect() {
        if (socket.isConnected) return
        _session.update { it.copy(connection = ConnectionState.CONNECTING) }
        socket.connect()
    }

    /** Full teardown -- used on logout, where the token the socket authenticated with is revoked. */
    fun disconnect() {
        pendingStart = null
        cancelStartWatchdog()
        socket.close()
        _session.value = BattleSession()
    }

    // --- Actions -----------------------------------------------------------

    /**
     * Opens a fight against whatever guards this lesson.
     *
     * Sent as soon as the socket is up; until then it is held as [pendingStart]. There is no
     * per-question time limit to ask for any more: the only clock in the fight belongs to the
     * monster.
     */
    fun startBattle(lessonId: String) {
        _session.value = BattleSession(
            connection = _session.value.connection,
            phase = BattlePhase.STARTING,
            lessonId = lessonId,
        )
        pendingStart = lessonId
        if (socket.isConnected) flushPendingStart() else connect()

        cancelStartWatchdog()
        startWatchdog = scope.launch {
            delay(START_TIMEOUT_MS)
            // Anything else already moved the session on -- the fight opened, the server refused
            // it, or the player walked out -- and none of those is this watchdog's business.
            if (_session.value.phase != BattlePhase.STARTING) return@launch
            pendingStart = null
            _session.update {
                it.copy(phase = BattlePhase.IDLE, lastError = BattleErrorCode.CONNECTION_LOST)
            }
        }
    }

    /**
     * Answers the question on screen.
     *
     * [optionIds] is one option for a single-choice question, and every word tile in the order it
     * was laid down for an ORDER one. Which of the two shapes goes on the wire is decided by the
     * question's own type rather than by how many ids came in: a one-word sentence is still a
     * sentence, and the server takes either shape but never both at once.
     *
     * Fires on the tap that completes the answer: the server times it from the moment it pushed
     * the question, so a confirm step would spend the speed bonus on a second thought. The token
     * is the session's own -- a caller cannot answer a question that has already closed.
     */
    fun submitAnswer(optionIds: List<String>) {
        val current = _session.value
        val token = current.questionToken ?: return
        if (optionIds.isEmpty() || current.hasAnswered) return
        _session.update { it.copy(myOptionIds = optionIds) }
        val isOrder = current.question?.type == ChallengeTypeDto.ORDER
        socket.send(
            BattleClientEvent.ANSWER_SUBMIT,
            buildJsonObject {
                put("token", token)
                if (isOrder) {
                    putJsonArray("option_ids") { optionIds.forEach { add(it) } }
                } else {
                    put("option_id", optionIds.first())
                }
            },
        )
    }

    /**
     * Casts an equipped skill.
     *
     * Unlike answering, this is not tied to a question: a shield or a heal is worth casting while
     * the player is reading the last explanation and the monster's cast bar is still filling.
     */
    fun useSkill(skillCode: String) {
        if (!_session.value.isFighting) return
        socket.send(
            BattleClientEvent.SKILL_USE,
            buildJsonObject { put("skill_code", skillCode) },
        )
    }

    /** Measures the round trip. The cast ring does not need it; a connection warning does. */
    fun ping(nowMs: Long) {
        socket.send(
            BattleClientEvent.PING,
            buildJsonObject { put("client_time_ms", nowMs) },
        )
    }

    /**
     * Walks out of a fight in progress.
     *
     * The server marks it ABANDONED and pays nothing -- but every answer already given keeps the
     * progress it earned, which is the thing worth telling the player before they confirm.
     */
    fun leaveBattle() {
        pendingStart = null
        cancelStartWatchdog()
        socket.send(BattleClientEvent.BATTLE_LEAVE)
    }

    /** Called once the result screen has been seen, returning the session to idle. */
    fun acknowledgeResult() {
        cancelStartWatchdog()
        _session.update { BattleSession(connection = it.connection) }
    }

    fun clearError() {
        _session.update { it.copy(lastError = null) }
    }

    private fun cancelStartWatchdog() {
        startWatchdog?.cancel()
        startWatchdog = null
    }

    private fun flushPendingStart() {
        val lessonId = pendingStart ?: return
        pendingStart = null
        socket.send(
            BattleClientEvent.BATTLE_START,
            buildJsonObject { put("lesson_id", lessonId) },
        )
    }

    // --- REST --------------------------------------------------------------

    suspend fun monsters(): Result<List<MonsterCatalogDto>> = runCatching { battleApi.listMonsters() }

    suspend fun previewLesson(lessonId: String): Result<MonsterPreviewDto> =
        runCatching { battleApi.previewLesson(lessonId) }

    suspend fun courseMonsters(courseId: String): Result<CourseMonstersDto> =
        runCatching { battleApi.courseMonsters(courseId) }

    suspend fun history(limit: Int = DEFAULT_HISTORY_PAGE, offset: Int = 0): Result<List<BattleHistoryDto>> =
        runCatching { battleApi.listBattles(limit, offset) }

    companion object {
        const val DEFAULT_HISTORY_PAGE = 20

        /**
         * How long a `battle.start` may go unanswered before the screen is let go.
         *
         * Generous on purpose: the first two rungs of the socket's backoff fit inside it, so a
         * server that blinks is still ridden out rather than reported as a failure.
         */
        const val START_TIMEOUT_MS = 12_000L

        /**
         * The one piece of real logic in this file, kept pure so it can be unit-tested on the JVM
         * with no socket, no server and no Android framework.
         */
        fun reduce(state: BattleSession, event: BattleEvent): BattleSession = when (event) {
            BattleEvent.SocketOpen -> state.copy(connection = ConnectionState.CONNECTED)

            is BattleEvent.SocketClosed -> state.copy(
                connection = if (event.willRetry) ConnectionState.RECONNECTING
                else ConnectionState.DISCONNECTED,
            )

            is BattleEvent.Connected -> state.copy(connection = ConnectionState.CONNECTED)

            is BattleEvent.Started -> state.copy(
                phase = BattlePhase.STARTING,
                battleId = event.data.battleId,
                lessonId = event.data.lessonId,
                lessonTitle = event.data.lessonTitle,
                monster = event.data.monster,
                monsterHp = event.data.monsterHp,
                monsterMaxHp = event.data.monster.maxHp,
                hp = event.data.yourHp,
                maxHp = event.data.yourMaxHp,
                mana = event.data.yourMana,
                combo = 0,
                questionsInPool = event.data.questionsInPool,
                poolPass = 0,
                questionToken = null,
                question = null,
                myOptionIds = emptyList(),
                answerResult = null,
                removedOptionIds = emptyList(),
                effects = emptyList(),
                lastSwing = null,
                lastSkill = null,
                finished = null,
                lastError = null,
            )

            /**
             * The snapshot is authoritative for every bar on screen, and it is also where the
             * clock offset comes from -- recomputed on every frame rather than negotiated once,
             * so a device that sleeps mid-fight corrects itself within a tenth of a second.
             */
            is BattleEvent.StateTick -> state.copy(
                phase = if (state.phase == BattlePhase.FINISHED) state.phase
                else BattlePhase.FIGHTING,
                serverOffsetMs = event.data.t - event.receivedAtMs,
                hp = event.data.yourHp,
                maxHp = event.data.yourMaxHp,
                mana = event.data.yourMana,
                combo = event.data.combo,
                monsterHp = event.data.monsterHp,
                monsterMaxHp = event.data.monsterMaxHp,
                castEndsAt = event.data.castEndsAt,
                nextSwing = event.data.nextSwing,
                nextSwingDamage = event.data.nextSwingDamage,
                lockoutEndsAt = event.data.lockoutEndsAt,
                effects = event.data.effects,
            )

            is BattleEvent.QuestionPush -> state.copy(
                phase = BattlePhase.FIGHTING,
                questionToken = event.data.token,
                question = event.data.question,
                poolPass = event.data.poolPass,
                myOptionIds = emptyList(),
                answerResult = null,
                // A skill's reveal only applies to the question it was cast on.
                removedOptionIds = emptyList(),
            )

            /**
             * Closes the question the moment the result lands. Note what is not copied: health.
             * An answer cannot cost health any more, and taking it from here would let a stale
             * frame undo a swing that has already landed.
             */
            is BattleEvent.AnswerResult -> state.copy(
                questionToken = null,
                answerResult = event.data,
                myOptionIds = event.data.optionIds,
                mana = event.data.yourMana,
                combo = event.data.combo,
                monsterHp = event.data.monsterHp,
                lockoutEndsAt = event.data.lockoutEndsAt,
            )

            is BattleEvent.MonsterSwing -> state.copy(
                hp = event.data.yourHp,
                castEndsAt = event.data.castEndsAt,
                lastSwing = event.data,
            )

            is BattleEvent.SkillUsed -> state.copy(
                hp = event.data.yourHp,
                mana = event.data.yourMana,
                monsterHp = event.data.monsterHp,
                // A clock-stealing skill moves this, and the ring must follow at once rather than
                // wait up to a tenth of a second for the next snapshot to say so.
                castEndsAt = if (event.data.castEndsAt > 0) event.data.castEndsAt
                else state.castEndsAt,
                lastSkill = event.data,
                removedOptionIds = event.data.private?.removedOptionIds
                    ?: state.removedOptionIds,
            )

            is BattleEvent.Finished -> state.copy(
                phase = BattlePhase.FINISHED,
                finished = event.data,
                hp = event.data.yourHpLeft,
                monsterHp = event.data.monsterHpLeft,
                questionToken = null,
                question = null,
            )

            is BattleEvent.Pong -> state.copy(
                roundTripMs = (event.receivedAtMs - event.data.clientTimeMs).coerceAtLeast(0),
            )

            // A refused `battle.start` leaves nothing running, so the session goes back to idle
            // rather than sitting on a STARTING screen that will never receive a question.
            is BattleEvent.Failed -> state.copy(
                phase = if (state.phase == BattlePhase.STARTING) BattlePhase.IDLE else state.phase,
                // A refused answer never landed, so the option must be released or the player is
                // stuck looking at a selected answer nothing will ever resolve.
                myOptionIds = if (event.data.errorCode == BattleErrorCode.INVALID_OPTION ||
                    event.data.errorCode == BattleErrorCode.QUESTION_CLOSED
                ) emptyList() else state.myOptionIds,
                lastError = event.data.errorCode,
            )
        }

        /** Kept out of the composables so the warning text has one home. */
        fun swingText(session: BattleSession): String = when {
            session.nextSwingDamage <= 0 -> ""
            session.enragedNext -> "Đang nổi giận: đòn tới ăn ${session.nextSwingDamage}"
            else -> "Đòn tới ăn ${session.nextSwingDamage}"
        }
    }
}
