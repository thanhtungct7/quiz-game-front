package com.kma.quiz_game.ui.screens.duo

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.DuoErrorCode
import com.kma.quiz_game.data.remote.dto.LoadoutSlotDto
import com.kma.quiz_game.data.repository.DuoPhase
import com.kma.quiz_game.data.repository.DuoRepository
import com.kma.quiz_game.data.repository.DuoSession
import com.kma.quiz_game.data.repository.GameRepository
import com.kma.quiz_game.ui.game.ArenaBridge
import com.kma.quiz_game.ui.game.ArenaEvent
import com.kma.quiz_game.ui.game.ArenaState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How often the round trip is measured. The arena needs no ping; a warning banner does. */
private const val PING_INTERVAL_MS = 5_000L

/**
 * What the duo match screen draws: the live session plus the little that is presentation only.
 *
 * There is no countdown here any more. The only clock in a match is the deadline it is decided on
 * if nobody falls, it is published as an instant rather than a duration, and the header
 * interpolates it -- so nothing on this side has to tick a round down.
 */
data class DuoMatchUiState(
    val session: DuoSession = DuoSession(),
    val showExitDialog: Boolean = false,
    val showChat: Boolean = false,
    /** The three equipped skills. Empty until `/game/loadout` answers, or if none are equipped. */
    val loadout: List<LoadoutSlotDto> = emptyList(),
    /**
     * When each cast skill may be cast again, on the server's clock.
     *
     * Accumulated on the client from `skill.used`, because the server reports a recharge only for
     * the skill just cast. Purely to grey out a button: the server refuses an early cast whatever
     * this map says.
     */
    val skillReadyAt: Map<String, Long> = emptyMap(),
    /** The skill whose slot should flash, and a counter so two refusals in a row flash twice. */
    val rejectedSkillCode: String? = null,
    val rejectedSeq: Int = 0,
    /** One line under the dock explaining the refusal, cleared on the next question. */
    val castNudge: String? = null,
) {
    /** Whether this skill can be cast right now, at this instant on the server's clock. */
    fun canCast(code: String, manaCost: Int, serverNowMs: Long): Boolean =
        session.phase == DuoPhase.FIGHTING &&
            serverNowMs >= session.stunnedUntil &&
            session.mana >= manaCost &&
            serverNowMs >= (skillReadyAt[code] ?: 0L)

    fun rechargingCodes(serverNowMs: Long): Set<String> =
        skillReadyAt.filterValues { it > serverNowMs }.keys
}

/**
 * Drives the live match screen off [DuoRepository.session], and the arena off the same flow.
 *
 * It owns no match state of its own -- the repository does, so this screen can be recreated (or
 * navigated away from and back) mid-match without losing the fight.
 *
 * The one thing it does own is [arena]: the bridge to the GL thread. Every session update is
 * republished to it whole, and the handful of things that happen *once* -- a blow landed, a blow
 * taken, a skill cast -- are diffed out of consecutive sessions and emitted as events, because a
 * snapshot cannot express "this just happened" and a flash that is missed is a flash that never
 * happened.
 */
class DuoMatchViewModel(
    private val duoRepository: DuoRepository,
    private val gameRepository: GameRepository,
) : ViewModel() {

    val arena = ArenaBridge()

    private val local = MutableStateFlow(DuoMatchUiState())

    val uiState: StateFlow<DuoMatchUiState> =
        combine(duoRepository.session, local, gameRepository.loadout) { session, state, loadout ->
            state.copy(session = session, loadout = loadout?.slots.orEmpty())
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Seeded from the live session, never a blank one: the blank's phase is IDLE, and this
            // screen reads IDLE as "the match is over" and leaves. Entering mid-match would then
            // bounce straight back to the lobby before the combine had emitted even once.
            DuoMatchUiState(session = duoRepository.session.value),
        )

    init {
        // Only an equipped skill can be cast, so the bar has to be known before the first question.
        // Cached in the repository, so re-entering the screen mid-match costs nothing.
        viewModelScope.launch { gameRepository.refreshLoadout() }

        viewModelScope.launch {
            var previous: DuoSession? = null
            duoRepository.session.collect { session ->
                emitArenaEvents(previous, session)
                trackCooldown(previous, session)
                clearNudgeOnNewQuestion(previous, session)
                arena.publish(session.toArenaState())
                previous = session
            }
        }

        // A cast the server refused: flash the slot that was tapped rather than raising a dialog.
        // These are ordinary play -- an empty mana bar, a skill recharging -- and a match has many.
        viewModelScope.launch {
            var lastSeq = 0
            duoRepository.session.collect { session ->
                val code = session.lastError ?: return@collect
                if (session.errorSeq == lastSeq) return@collect
                lastSeq = session.errorSeq
                if (code.isInMatchNudge) nudge(lastCastCode, code)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                duoRepository.ping(SystemClock.elapsedRealtime())
                delay(PING_INTERVAL_MS)
            }
        }
    }

    /**
     * The things that happen rather than hold.
     *
     * Each is recognised by an identity that only moves forward -- the answered token, the
     * incoming counter, the cast counter -- so a snapshot re-delivered for any other reason cannot
     * replay a flash that already played. Ten snapshots a second makes that guarantee necessary
     * rather than tidy.
     */
    private fun emitArenaEvents(previous: DuoSession?, session: DuoSession) {
        val result = session.answerResult
        if (result != null && result.token != previous?.answerResult?.token) {
            val blow = result.blow
            if (blow != null) {
                arena.emit(ArenaEvent.LeftHit(blow.damage, blow.isCritical))
            } else {
                arena.emit(ArenaEvent.Miss(onLeft = true))
            }
        }

        val incoming = session.lastIncoming
        if (incoming != null && session.incomingSeq != previous?.incomingSeq) {
            if (incoming.damage > 0) {
                arena.emit(ArenaEvent.RightHit(incoming.damage, heavy = incoming.isCritical))
            } else if (!incoming.correct) {
                arena.emit(ArenaEvent.Miss(onLeft = false))
            }
        }

        val skill = session.lastSkill
        if (skill != null && session.skillSeq != previous?.skillSeq) {
            arena.emit(ArenaEvent.SkillCast(skill.skillName, onLeft = skill.userId == session.me?.id))
        }

        val fell = (session.opponentHp <= 0 && (previous?.opponentHp ?: 1) > 0) ||
            (session.myHp <= 0 && (previous?.myHp ?: 1) > 0)
        if (fell && session.deckSize > 0) arena.emit(ArenaEvent.Down)
    }

    private fun trackCooldown(previous: DuoSession?, session: DuoSession) {
        val skill = session.lastSkill ?: return
        if (session.skillSeq == previous?.skillSeq) return
        // Only our own cast carries a recharge stamp; the opponent's arrives as zero.
        if (skill.readyAgainAt <= 0) return
        local.update { it.copy(skillReadyAt = it.skillReadyAt + (skill.skillCode to skill.readyAgainAt)) }
    }

    /** A refusal belongs to the question it happened on. */
    private fun clearNudgeOnNewQuestion(previous: DuoSession?, session: DuoSession) {
        val token = session.questionToken ?: return
        if (token == previous?.questionToken) return
        local.update { it.copy(castNudge = null, rejectedSkillCode = null) }
    }

    /** Answers the question on screen. The token is the repository's, so a stale tap cannot land. */
    fun selectOption(optionId: String) = duoRepository.submitAnswer(optionId)

    private var lastCastCode: String? = null

    /**
     * Casts a skill, or explains on the spot why it cannot be cast.
     *
     * The slot stays tappable in every state deliberately: a tap is how a player asks "why not",
     * and answering that locally is instant, where a round trip to be refused is not.
     */
    fun castSkill(slot: LoadoutSlotDto) {
        val state = uiState.value
        val session = state.session
        val serverNow = session.serverNowMs(SystemClock.elapsedRealtime())
        lastCastCode = slot.code

        val refusal = when {
            serverNow < session.stunnedUntil -> DuoErrorCode.STUNNED
            session.phase != DuoPhase.FIGHTING -> DuoErrorCode.NOT_IN_MATCH
            serverNow < (state.skillReadyAt[slot.code] ?: 0L) -> DuoErrorCode.SKILL_ON_COOLDOWN
            session.mana < slot.manaCost -> DuoErrorCode.NOT_ENOUGH_MANA
            else -> null
        }
        if (refusal != null) {
            nudge(slot.code, refusal)
            return
        }
        duoRepository.useSkill(slot.code)
    }

    private fun nudge(code: String?, error: DuoErrorCode) {
        local.update {
            it.copy(
                rejectedSkillCode = code,
                rejectedSeq = it.rejectedSeq + 1,
                castNudge = error.nudgeText(),
            )
        }
    }

    fun setExitDialogVisible(visible: Boolean) = local.update { it.copy(showExitDialog = visible) }

    fun setChatVisible(visible: Boolean) = local.update { it.copy(showChat = visible) }

    /** The server broadcasts the message back to the sender too, so nothing is added locally. */
    fun sendChat(message: String) = duoRepository.sendChat(message)

    /** Walking out is always scored as a loss, whatever the score board says. */
    fun forfeit() {
        setExitDialogVisible(false)
        duoRepository.leaveMatch()
    }
}

/** The match as the renderer needs it: no DTOs, no nulls, nothing the GL thread has to unwrap. */
private fun DuoSession.toArenaState(): ArenaState = ArenaState(
    // Two knights, not a knight and a monster. Nothing winds up on a clock here, so the cast bar
    // is left unset and never drawn.
    rightIsHero = true,
    serverOffsetMs = serverOffsetMs,
    leftCombo = combo,
    rightCombo = opponentCombo,
    finished = finished != null,
)

/** Terse on purpose: this appears mid-match, under a dock, while the opponent is still answering. */
private fun DuoErrorCode.nudgeText(): String = when (this) {
    DuoErrorCode.STUNNED -> "Đang choáng — chờ tỉnh lại"
    DuoErrorCode.NOT_ENOUGH_MANA -> "Không đủ mana"
    DuoErrorCode.SKILL_ON_COOLDOWN -> "Kỹ năng đang hồi"
    DuoErrorCode.SKILL_NOT_EQUIPPED -> "Kỹ năng chưa được trang bị"
    DuoErrorCode.QUESTION_CLOSED -> "Chưa có câu hỏi nào đang mở"
    else -> "Không dùng được lúc này"
}
