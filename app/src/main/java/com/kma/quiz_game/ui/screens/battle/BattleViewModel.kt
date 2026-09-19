package com.kma.quiz_game.ui.screens.battle

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.LoadoutSlotDto
import com.kma.quiz_game.data.repository.BattlePhase
import com.kma.quiz_game.data.repository.BattleRepository
import com.kma.quiz_game.data.repository.BattleSession
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
 * How long [BattleViewModel.leave] waits for the server to confirm the walk-out.
 *
 * Long enough for a healthy socket to answer with `battle.finished`, which is the better exit --
 * it carries the result screen. Short enough that a dead one does not trap the player.
 */
private const val LEAVE_GRACE_MS = 2_000L

/**
 * Drives the battle screen off [BattleRepository.session], and the arena off the same flow.
 *
 * It owns no fight state of its own -- the repository does -- so the screen can be recreated by a
 * configuration change without dropping the fight. Created per lesson id, because the fight it
 * starts is the fight for that gate.
 *
 * The one thing it does own is [arena]: the bridge to the GL thread. Every session update is
 * republished to it whole, and the handful of things that happen *once* -- a blow landed, a blow
 * taken, a skill cast -- are diffed out of consecutive sessions and emitted as events, because a
 * snapshot cannot express "this just happened" and a flash that is missed is a flash that never
 * happened.
 */
class BattleViewModel(
    private val lessonId: String,
    private val battleRepository: BattleRepository,
    private val gameRepository: GameRepository,
) : ViewModel() {

    val arena = ArenaBridge()

    private val local = MutableStateFlow(BattleUiState())

    val uiState: StateFlow<BattleUiState> =
        combine(battleRepository.session, local, gameRepository.loadout) { session, state, loadout ->
            state.copy(session = session, loadout = loadout?.slots.orEmpty())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BattleUiState())

    init {
        battleRepository.connect()
        battleRepository.startBattle(lessonId)
        // The same three equipped skills a duo match fights with -- one game layer, two modes.
        viewModelScope.launch { gameRepository.refreshLoadout() }

        viewModelScope.launch {
            var previous: BattleSession? = null
            battleRepository.session.collect { session ->
                emitArenaEvents(previous, session)
                trackCooldown(previous, session)
                // A new question is a new sentence: whatever was laid out for the last one has to
                // go, or its tiles would be waiting under the next question's word bank.
                val token = session.questionToken
                if (token != null && token != previous?.questionToken) {
                    local.update { it.copy(placedOptionIds = emptyList()) }
                }
                // Read at publish time rather than combined into the flow: the profile is a
                // StateFlow that rarely changes, and folding it in would restart this collector
                // -- and with it the event diffing above -- every time gold ticked over.
                val profile = gameRepository.profile.value
                arena.publish(
                    session.toArenaState(
                        classCode = profile?.classCode.orEmpty(),
                        skinCode = profile?.skinCode.orEmpty(),
                    )
                )
                previous = session
            }
        }

        viewModelScope.launch {
            while (isActive) {
                battleRepository.ping(SystemClock.elapsedRealtime())
                delay(PING_INTERVAL_MS)
            }
        }
    }

    /**
     * The three things that happen rather than hold.
     *
     * Each is recognised by an identity that only moves forward -- the answered token, the swing
     * index, the skill's own recharge stamp -- so a snapshot re-delivered for any other reason
     * cannot replay a flash that already played.
     */
    private fun emitArenaEvents(previous: BattleSession?, session: BattleSession) {
        val result = session.answerResult
        if (result != null && result.token != previous?.answerResult?.token) {
            val blow = result.blow
            if (blow != null) {
                arena.emit(ArenaEvent.LeftHit(blow.finalDamage, blow.isCritical))
            } else if (!result.correct) {
                arena.emit(ArenaEvent.Miss(onLeft = true))
            }
        }

        val swing = session.lastSwing
        if (swing != null && swing.swingIndex != previous?.lastSwing?.swingIndex) {
            arena.emit(ArenaEvent.RightHit(swing.damage, heavy = swing.enraged))
        }

        val skill = session.lastSkill
        if (skill != null && skill.readyAgainAt != previous?.lastSkill?.readyAgainAt) {
            arena.emit(ArenaEvent.SkillCast(skill.skillName, onLeft = true))
        }

        if (session.monsterHp <= 0 && (previous?.monsterHp ?: 0) > 0) {
            arena.emit(ArenaEvent.Down)
        }
    }

    private fun trackCooldown(previous: BattleSession?, session: BattleSession) {
        val skill = session.lastSkill ?: return
        if (skill.readyAgainAt == previous?.lastSkill?.readyAgainAt) return
        local.update { it.copy(skillReadyAt = it.skillReadyAt + (skill.skillCode to skill.readyAgainAt)) }
    }

    /** Answers the question on screen. The token is the repository's, so a stale tap cannot land. */
    fun selectOption(optionId: String) = battleRepository.submitAnswer(listOf(optionId))

    /** ORDER questions: lay one word tile down at the end of the sentence. */
    fun placeWord(optionId: String) {
        val session = battleRepository.session.value
        if (!session.hasQuestion || session.hasAnswered) return
        if (optionId in local.value.placedOptionIds) return
        local.update { it.copy(placedOptionIds = it.placedOptionIds + optionId) }
    }

    /**
     * ORDER questions: take a word back out of the sentence.
     *
     * The tiles after it keep their relative order, so pulling the wrong word out of the middle
     * does not cost the rest of the sentence.
     */
    fun removeWord(optionId: String) {
        val session = battleRepository.session.value
        if (!session.hasQuestion || session.hasAnswered) return
        local.update { it.copy(placedOptionIds = it.placedOptionIds - optionId) }
    }

    /**
     * ORDER questions: answer with the sentence as it stands.
     *
     * Guarded on the sentence being finished for the same reason the button is greyed out until
     * then: a partial sentence is not an answer, and the server refuses one rather than marking it
     * wrong -- so sending it would cost the player a round trip and tell them nothing.
     */
    fun checkSentence() {
        val session = battleRepository.session.value
        val question = session.question ?: return
        val placed = local.value.placedOptionIds
        if (placed.size != question.options.size) return
        battleRepository.submitAnswer(placed)
    }

    /**
     * Casts an equipped skill.
     *
     * Guarded locally on mana and on the cooldown this client has seen, for the same reason duo
     * guards: the refusal would be instant and certain, and a round trip that can only come back as
     * an error is wasted time in a fight measured in seconds. Everything else stays the server's
     * call -- and unlike the old engine, a cast does not need a question on screen: a shield is
     * worth raising while reading the last explanation.
     */
    fun castSkill(slot: LoadoutSlotDto) {
        val state = uiState.value
        val serverNow = state.session.serverNowMs(SystemClock.elapsedRealtime())
        if (!state.canCast(slot.code, slot.manaCost, serverNow)) return
        battleRepository.useSkill(slot.code)
    }

    fun setExitDialogVisible(visible: Boolean) = local.update { it.copy(showExitDialog = visible) }

    /** Walking out is scored as abandoned -- and pays nothing, though the answers already given
     * keep the progress they earned. */
    fun leave() {
        setExitDialogVisible(false)
        battleRepository.leaveBattle()
        viewModelScope.launch {
            delay(LEAVE_GRACE_MS)
            // A server that answered has already moved the session to FINISHED, and the result
            // screen this ViewModel's owner navigated to is showing it. Nothing answered, so the
            // walk-out has to be honoured here.
            if (uiState.value.session.phase != BattlePhase.FINISHED) {
                local.update { it.copy(hasLeft = true) }
            }
        }
    }
}

/** The fight as the renderer needs it: no DTOs, no nulls, nothing the GL thread has to unwrap. */
private fun BattleSession.toArenaState(classCode: String, skinCode: String): ArenaState = ArenaState(
    artCode = monster?.artCode.orEmpty(),
    leftClassCode = classCode,
    leftSkinCode = skinCode,
    isBoss = monster?.isBoss == true,
    castEndsAt = castEndsAt,
    castIntervalMs = monster?.castIntervalMs ?: 0,
    serverOffsetMs = serverOffsetMs,
    enragedNext = enragedNext,
    leftCombo = combo,
    finished = finished != null,
)
