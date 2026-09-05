package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.dto.BattleActiveEffectDto
import com.kma.quiz_game.data.remote.dto.BattleAnswerResultDto
import com.kma.quiz_game.data.remote.dto.BattleBlowDto
import com.kma.quiz_game.data.remote.dto.BattleConnectedDto
import com.kma.quiz_game.data.remote.dto.BattleEndReasonDto
import com.kma.quiz_game.data.remote.dto.BattleErrorCode
import com.kma.quiz_game.data.remote.dto.BattleErrorDto
import com.kma.quiz_game.data.remote.dto.BattleEvent
import com.kma.quiz_game.data.remote.dto.BattleExpChangeDto
import com.kma.quiz_game.data.remote.dto.BattleFinishedDto
import com.kma.quiz_game.data.remote.dto.BattleGoldChangeDto
import com.kma.quiz_game.data.remote.dto.BattleLessonProgressDto
import com.kma.quiz_game.data.remote.dto.BattlePongDto
import com.kma.quiz_game.data.remote.dto.BattleQuestionPushDto
import com.kma.quiz_game.data.remote.dto.BattleSkillPrivateDto
import com.kma.quiz_game.data.remote.dto.BattleSkillUsedDto
import com.kma.quiz_game.data.remote.dto.BattleStartedDto
import com.kma.quiz_game.data.remote.dto.BattleStateTickDto
import com.kma.quiz_game.data.remote.dto.BattleStatusDto
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeOptionDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import com.kma.quiz_game.data.remote.dto.LessonProgressStatusDto
import com.kma.quiz_game.data.remote.dto.MonsterDto
import com.kma.quiz_game.data.remote.dto.MonsterSwingDto
import com.kma.quiz_game.data.repository.BattleRepository.Companion.reduce
import com.kma.quiz_game.data.repository.BattleRepository.Companion.swingText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The battle reducer is the only place in the feature with logic that can be quietly wrong --
 * phase transitions, which frame owns which number, a skill's reveal outliving its question -- and
 * it is a pure function, so it is tested here with no socket, no server and no Android framework.
 *
 * The realtime rewrite made one rule sharper than it was: exactly one frame owns each number.
 * `state.tick` owns the bars, `answer.result` owns mana, combo and the monster's health, and
 * `monster.swing` owns the player's. Several tests below exist only to hold that line, because the
 * failure it prevents -- a stale frame undoing a blow that already landed -- is invisible until a
 * fight goes wrong.
 */
class BattleReducerTest {

    private val monster = MonsterDto(
        code = "SLIME",
        name = "Slime",
        tier = 1,
        maxHp = 60,
        attackDamage = 8,
        isBoss = false,
        artCode = "SLIME",
        castIntervalMs = 5_200,
    )

    private fun question(id: String = "q1") = ChallengeDto(
        id = id,
        lessonId = "lesson",
        type = ChallengeTypeDto.SELECT,
        question = "She ___ to school.",
        difficulty = "EASY",
        orderIndex = 1,
        options = listOf(
            ChallengeOptionDto(id = "a", text = "goes", orderIndex = 1),
            ChallengeOptionDto(id = "b", text = "go", orderIndex = 2),
            ChallengeOptionDto(id = "c", text = "going", orderIndex = 3),
        ),
    )

    private fun started() = BattleEvent.Started(
        BattleStartedDto(
            battleId = "battle-1",
            lessonId = "lesson",
            lessonTitle = "Thì hiện tại đơn",
            questionsInPool = 3,
            monster = monster,
            monsterHp = 60,
            yourHp = 100,
            yourMaxHp = 100,
            yourMana = 10,
            tickHz = 15,
            snapshotHz = 10,
            serverTimeMs = 1_000,
        ),
    )

    private fun tick(
        t: Long = 1_000,
        receivedAt: Long = 900,
        hp: Int = 100,
        monsterHp: Int = 60,
        mana: Int = 10,
        combo: Int = 0,
        castEndsAt: Long = 6_200,
        nextSwing: String = "ATTACK",
        lockoutEndsAt: Long = 0,
        effects: List<BattleActiveEffectDto> = emptyList(),
    ) = BattleEvent.StateTick(
        BattleStateTickDto(
            t = t,
            yourHp = hp,
            yourMaxHp = 100,
            yourMana = mana,
            combo = combo,
            monsterHp = monsterHp,
            monsterMaxHp = 60,
            castEndsAt = castEndsAt,
            nextSwing = nextSwing,
            nextSwingDamage = 8,
            lockoutEndsAt = lockoutEndsAt,
            effects = effects,
        ),
        receivedAtMs = receivedAt,
    )

    private fun push(token: String = "t1", poolPass: Int = 0) = BattleEvent.QuestionPush(
        BattleQuestionPushDto(
            token = token,
            question = question(token),
            pushedAt = 1_000,
            poolPass = poolPass,
        ),
    )

    private fun hit(token: String = "t1", damage: Int = 17, monsterHp: Int = 43) =
        BattleEvent.AnswerResult(
            BattleAnswerResultDto(
                token = token,
                correct = true,
                optionId = "a",
                elapsedMs = 4_200,
                correctOptionIds = listOf("a"),
                explanation = "vì chủ ngữ số ít",
                blow = BattleBlowDto(finalDamage = damage, strike = "NORMAL", comboCount = 1),
                yourMana = 42,
                combo = 1,
                monsterHp = monsterHp,
                lockoutEndsAt = 1_600,
            ),
        )

    private fun miss(token: String = "t1") = BattleEvent.AnswerResult(
        BattleAnswerResultDto(
            token = token,
            correct = false,
            optionId = "b",
            elapsedMs = 9_100,
            correctOptionIds = listOf("a"),
            explanation = null,
            blow = null,
            yourMana = 15,
            combo = 0,
            monsterHp = 60,
            lockoutEndsAt = 2_500,
        ),
    )

    private fun swing(damage: Int = 8, hp: Int = 92, index: Int = 1) = BattleEvent.MonsterSwing(
        MonsterSwingDto(damage = damage, enraged = false, yourHp = hp, swingIndex = index, castEndsAt = 11_400),
    )

    /** Walks a session up to a question being on screen, the way a real fight does. */
    private fun fighting(): BattleSession {
        var state = BattleSession()
        state = reduce(state, BattleEvent.SocketOpen)
        state = reduce(state, BattleEvent.Connected(BattleConnectedDto(userId = "me")))
        state = reduce(state, started())
        state = reduce(state, tick())
        return reduce(state, push())
    }

    @Test
    fun `opening a battle carries the monster, both health bars and the pool size`() {
        val state = reduce(BattleSession(), started())

        assertEquals(BattlePhase.STARTING, state.phase)
        assertEquals("SLIME", state.monster?.code)
        assertEquals(60, state.monsterHp)
        assertEquals(60, state.monsterMaxHp)
        assertEquals(100, state.hp)
        // Not a length. The pool goes round again rather than ending the fight.
        assertEquals(3, state.questionsInPool)
        assertEquals(0, state.poolPass)
    }

    @Test
    fun `a snapshot yields the clock offset, and the offset converts a deadline`() {
        // The server said 1000 at an instant this device read as 900, so it runs 100ms behind.
        val state = reduce(BattleSession(), tick(t = 1_000, receivedAt = 900, castEndsAt = 6_200))

        assertEquals(100L, state.serverOffsetMs)
        assertEquals(1_200L, state.serverNowMs(1_100))
        // 6200 on the server's clock, from a local reading of 1100, is five seconds out.
        assertEquals(5_000L, state.castRemainingMs(1_100))
    }

    @Test
    fun `the wind-up fills smoothly between two snapshots`() {
        val state = reduce(BattleSession(), started()).let { reduce(it, tick(castEndsAt = 6_200)) }

        // Half an interval out from the deadline is a half-full bar, computed at any instant the
        // caller likes -- which is what lets the arena draw it at sixty frames a second.
        assertEquals(0f, state.castFraction(6_200 - 5_200 - 100), 0.01f)
        assertEquals(0.5f, state.castFraction(6_200 - 2_600 - 100), 0.01f)
        assertEquals(1f, state.castFraction(6_200 - 100), 0.01f)
    }

    @Test
    fun `a question opens with its token and no answer of its own`() {
        val state = fighting()

        assertEquals(BattlePhase.FIGHTING, state.phase)
        assertTrue(state.hasQuestion)
        assertEquals("t1", state.questionToken)
        assertNull(state.myOptionId)
        assertNull(state.answerResult)
    }

    @Test
    fun `a correct answer takes health off the monster and none off the player`() {
        val state = reduce(fighting(), hit(monsterHp = 43))

        assertEquals(43, state.monsterHp)
        assertEquals(100, state.hp)
        assertEquals(1, state.combo)
        assertNotNull(state.answerResult?.blow)
        // The question closed the moment the result landed, so a second tap cannot land.
        assertFalse(state.hasQuestion)
    }

    @Test
    fun `a wrong answer costs the chain and the tempo, never health`() {
        val state = reduce(fighting(), miss())

        assertEquals(100, state.hp)
        assertEquals(60, state.monsterHp)
        assertEquals(0, state.combo)
        assertNull(state.answerResult?.blow)
        // A longer lockout is the whole punishment: the monster keeps winding up through it.
        assertEquals(2_500L, state.lockoutEndsAt)
        assertTrue(state.inLockout(2_000))
        assertFalse(state.inLockout(2_500))
    }

    @Test
    fun `the monster's blow is the only thing that takes the player's health`() {
        val answered = reduce(fighting(), hit())
        val state = reduce(answered, swing(damage = 8, hp = 92))

        assertEquals(92, state.hp)
        // Re-armed from the swing itself, so the ring restarts without waiting for a snapshot.
        assertEquals(11_400L, state.castEndsAt)
        assertEquals(1, state.lastSwing?.swingIndex)
        // A blow taken does not break the chain -- being hit is not the player's mistake.
        assertEquals(1, state.combo)
    }

    @Test
    fun `the next question clears the previous answer and its reveal`() {
        var state = reduce(fighting(), hit())
        state = reduce(state, push(token = "t2"))

        assertEquals("t2", state.questionToken)
        assertNull(state.answerResult)
        assertNull(state.myOptionId)
        assertTrue(state.removedOptionIds.isEmpty())
    }

    @Test
    fun `going round the pool again is reported rather than hidden`() {
        val state = reduce(fighting(), push(token = "t4", poolPass = 1))

        assertEquals(1, state.poolPass)
    }

    @Test
    fun `a skill's hidden options do not survive into the next question`() {
        val cast = BattleEvent.SkillUsed(
            BattleSkillUsedDto(
                skillCode = "REVEAL",
                skillName = "Soi",
                effect = "REMOVE_OPTIONS",
                magnitude = 1,
                manaSpent = 20,
                yourHp = 100,
                yourMana = 22,
                monsterHp = 60,
                castEndsAt = 9_000,
                readyAgainAt = 5_000,
                private = BattleSkillPrivateDto(removedOptionIds = listOf("c")),
            ),
        )
        var state = reduce(fighting(), cast)
        assertEquals(listOf("c"), state.removedOptionIds)
        // A clock-stealing skill moves the deadline, and the ring must follow at once.
        assertEquals(9_000L, state.castEndsAt)
        assertEquals("REVEAL", state.lastSkill?.skillCode)

        state = reduce(state, push(token = "t2"))
        assertTrue(state.removedOptionIds.isEmpty())
    }

    @Test
    fun `finishing carries the payout and where the lesson now stands`() {
        val finish = BattleEvent.Finished(
            BattleFinishedDto(
                battleId = "battle-1",
                outcome = BattleStatusDto.WON,
                endReason = BattleEndReasonDto.MONSTER_DOWN,
                yourHpLeft = 92,
                monsterHpLeft = 0,
                answersGiven = 3,
                correctCount = 3,
                bestCombo = 3,
                durationMs = 14_500,
                monsterSwings = 1,
                firstClear = true,
                exp = BattleExpChangeDto(0, 30, 30, 1, 1, false),
                gold = BattleGoldChangeDto(0, 12, 12),
                lessonProgress = BattleLessonProgressDto(
                    status = LessonProgressStatusDto.COMPLETED,
                    correct = 3,
                    total = 3,
                ),
            ),
        )
        val state = reduce(fighting(), finish)

        assertEquals(BattlePhase.FINISHED, state.phase)
        assertEquals(0, state.monsterHp)
        assertNull(state.question)
        assertFalse(state.hasQuestion)
        assertEquals(30, state.finished?.exp?.delta)
        assertEquals(1, state.finished?.monsterSwings)
    }

    @Test
    fun `a snapshot arriving after the payout cannot restart the fight`() {
        val finished = reduce(fighting(), BattleEvent.Finished(finishedDto()))
        val state = reduce(finished, tick(hp = 5, monsterHp = 12))

        // The bars may still settle, but the phase must not walk backwards into FIGHTING -- the
        // screen has already navigated to the result by then.
        assertEquals(BattlePhase.FINISHED, state.phase)
    }

    @Test
    fun `a refused start returns the session to idle instead of hanging on STARTING`() {
        val starting = reduce(BattleSession(), started())
        val state = reduce(
            starting,
            BattleEvent.Failed(BattleErrorDto(code = "LESSON_NOT_FOUND", message = "nope")),
        )

        assertEquals(BattlePhase.IDLE, state.phase)
        assertEquals(BattleErrorCode.LESSON_NOT_FOUND, state.lastError)
    }

    @Test
    fun `a refused answer releases the option instead of leaving it stuck selected`() {
        var state = fighting()
        state = state.copy(myOptionId = "a")
        state = reduce(
            state,
            BattleEvent.Failed(BattleErrorDto(code = "QUESTION_CLOSED", message = "too late")),
        )

        assertNull(state.myOptionId)
        assertEquals(BattlePhase.FIGHTING, state.phase)
    }

    @Test
    fun `an error mid-fight does not throw the player out`() {
        val state = reduce(
            fighting(),
            BattleEvent.Failed(BattleErrorDto(code = "NOT_ENOUGH_MANA", message = "no")),
        )

        assertEquals(BattlePhase.FIGHTING, state.phase)
        assertEquals(BattleErrorCode.NOT_ENOUGH_MANA, state.lastError)
        assertTrue(state.hasQuestion)
    }

    @Test
    fun `an unknown error code degrades instead of crashing`() {
        val state = reduce(
            fighting(),
            BattleEvent.Failed(BattleErrorDto(code = "SOMETHING_NEW", message = "?")),
        )

        assertEquals(BattleErrorCode.UNKNOWN, state.lastError)
    }

    @Test
    fun `losing the socket is reported but keeps what was on screen`() {
        val state = reduce(fighting(), BattleEvent.SocketClosed(willRetry = true))

        assertEquals(ConnectionState.RECONNECTING, state.connection)
        assertTrue(state.hasQuestion)
    }

    @Test
    fun `a pong measures the round trip`() {
        val state = reduce(
            fighting(),
            BattleEvent.Pong(BattlePongDto(clientTimeMs = 1_000, serverTimeMs = 5_000), receivedAtMs = 1_080),
        )

        assertEquals(80L, state.roundTripMs)
    }

    @Test
    fun `the warning says what the wind-up will cost, and shouts when the monster is enraged`() {
        val calm = reduce(fighting(), tick(nextSwing = "ATTACK"))
        val angry = reduce(fighting(), tick(nextSwing = "ENRAGED_ATTACK"))

        assertTrue(swingText(calm).contains("8"))
        assertFalse(calm.enragedNext)
        assertTrue(angry.enragedNext)
        assertTrue(swingText(angry).contains("nổi giận"))
        assertEquals("", swingText(BattleSession()))
    }

    private fun finishedDto() = BattleFinishedDto(
        battleId = "battle-1",
        outcome = BattleStatusDto.WON,
        endReason = BattleEndReasonDto.MONSTER_DOWN,
        yourHpLeft = 92,
        monsterHpLeft = 0,
        answersGiven = 3,
        correctCount = 3,
        bestCombo = 3,
        durationMs = 14_500,
        monsterSwings = 1,
        firstClear = true,
        exp = BattleExpChangeDto(0, 30, 30, 1, 1, false),
        gold = BattleGoldChangeDto(0, 12, 12),
        lessonProgress = BattleLessonProgressDto(
            status = LessonProgressStatusDto.COMPLETED,
            correct = 3,
            total = 3,
        ),
    )
}
