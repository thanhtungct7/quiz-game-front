package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.dto.AnswerResultDto
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeOptionDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import com.kma.quiz_game.data.remote.dto.ConnectedDto
import com.kma.quiz_game.data.remote.dto.DuoActiveEffectDto
import com.kma.quiz_game.data.remote.dto.DuoBlowDto
import com.kma.quiz_game.data.remote.dto.DuoErrorCode
import com.kma.quiz_game.data.remote.dto.DuoEvent
import com.kma.quiz_game.data.remote.dto.DuoMatchEndReason
import com.kma.quiz_game.data.remote.dto.DuoMatchMode
import com.kma.quiz_game.data.remote.dto.DuoPlayerDto
import com.kma.quiz_game.data.remote.dto.DuoSettingsDto
import com.kma.quiz_game.data.remote.dto.DuoSkillPrivateDto
import com.kma.quiz_game.data.remote.dto.DuoSkillUsedDto
import com.kma.quiz_game.data.remote.dto.DuoStateTickDto
import com.kma.quiz_game.data.remote.dto.ErrorDto
import com.kma.quiz_game.data.remote.dto.MatchFinishedDto
import com.kma.quiz_game.data.remote.dto.MatchFoundDto
import com.kma.quiz_game.data.remote.dto.MatchOutcome
import com.kma.quiz_game.data.remote.dto.MatchStartedDto
import com.kma.quiz_game.data.remote.dto.OpponentAnsweredDto
import com.kma.quiz_game.data.remote.dto.QuestionPushDto
import com.kma.quiz_game.data.remote.dto.RatingChangeDto
import com.kma.quiz_game.data.repository.DuoRepository.Companion.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The combat half of the duo reducer: health, mana, combo, stuns and casts.
 *
 * Kept apart from `DuoReducerTest`, which covers matchmaking and phase transitions. These are the
 * paths that only exist because a duo match is a fight, and every one of them has a way of being
 * quietly wrong -- health taken from the frame that cannot know it, an incoming blow animated
 * twice, a reveal leaking into the next question.
 */
class DuoCombatReducerTest {

    private val me = DuoPlayerDto(id = "me", username = "Me", rating = 1000, tier = "SILVER", level = 4)
    private val them = DuoPlayerDto(id = "them", username = "Them", rating = 1050, tier = "GOLD", level = 6)
    private val settings = DuoSettingsDto(questionCount = 3, timePerQuestion = 15)

    private fun question(id: String = "q1") = ChallengeDto(
        id = id,
        lessonId = "lesson",
        type = ChallengeTypeDto.SELECT,
        question = "2 + 2?",
        difficulty = "EASY",
        orderIndex = 1,
        options = listOf(
            ChallengeOptionDto(id = "a", text = "3", orderIndex = 1),
            ChallengeOptionDto(id = "b", text = "4", orderIndex = 2),
            ChallengeOptionDto(id = "c", text = "5", orderIndex = 3),
        ),
    )

    /** A match on its first question, with whatever health the two classes bring. */
    private fun opening(
        yourHp: Int = 130,
        opponentHp: Int = 80,
        yourMana: Int = 10,
    ): DuoSession {
        var state = reduce(DuoSession(), DuoEvent.Connected(ConnectedDto(user = me, activeMatchId = null)))
        state = reduce(
            state,
            DuoEvent.MatchFound(
                MatchFoundDto(
                    matchId = "m1",
                    mode = DuoMatchMode.RANDOM,
                    opponent = them,
                    settings = settings,
                    hostId = "them",
                    autoStart = true,
                ),
            ),
        )
        state = reduce(
            state,
            DuoEvent.MatchStarted(
                MatchStartedDto(
                    matchId = "m1",
                    deckSize = 3,
                    speedReferenceSeconds = 15,
                    serverTimeMs = 0,
                    deadlineAt = 45_000,
                    yourHp = yourHp,
                    yourMaxHp = yourHp,
                    yourMana = yourMana,
                    opponentHp = opponentHp,
                    opponentMaxHp = opponentHp,
                ),
                receivedAtMs = 0,
            ),
        )
        return reduce(
            state,
            DuoEvent.QuestionPush(
                QuestionPushDto(token = "t0", question = question(), deckRemaining = 3),
            ),
        )
    }

    private fun blow(damage: Int = 18, critical: Boolean = false, stuns: Boolean = false) = DuoBlowDto(
        damage = damage,
        strike = "QUICK",
        comboCount = 1,
        comboMultiplier = 1f,
        isCritical = critical,
        stunsOpponent = stuns,
    )

    @Test
    fun `the classes each bring their own bars`() {
        val state = opening()

        assertEquals(130, state.myHp)
        assertEquals(130, state.myMaxHp)
        assertEquals(80, state.opponentHp)
        assertEquals(80, state.opponentMaxHp)
        assertEquals(10, state.mana)
        assertEquals(1f, state.myHpFraction, 0.001f)
    }

    @Test
    fun `a correct answer takes health off the opponent at once`() {
        val state = reduce(
            opening(),
            DuoEvent.AnswerResult(
                AnswerResultDto(
                    token = "t0",
                    correct = true,
                    optionId = "b",
                    correctOptionIds = listOf("b"),
                    points = 940,
                    blow = blow(damage = 22),
                    yourScore = 940,
                    yourMana = 35,
                    yourCombo = 1,
                    yourDeckRemaining = 2,
                    opponentHp = 58,
                ),
            ),
        )

        assertEquals(58, state.opponentHp)
        assertEquals(22, state.answerResult?.blow?.damage)
        assertEquals(1, state.combo)
        assertEquals(35, state.mana)
        // Our own health is untouched: an answer cannot cost health, and a frame that does not
        // know it must not be allowed to guess.
        assertEquals(130, state.myHp)
    }

    @Test
    fun `a wrong answer lands nothing at all`() {
        val state = reduce(
            opening(),
            DuoEvent.AnswerResult(
                AnswerResultDto(
                    token = "t0",
                    correct = false,
                    optionId = "a",
                    correctOptionIds = listOf("b"),
                    blow = null,
                    yourCombo = 0,
                    yourDeckRemaining = 3,
                    opponentHp = 80,
                ),
            ),
        )

        assertNull(state.answerResult?.blow)
        assertEquals(80, state.opponentHp)
        assertEquals(130, state.myHp)
        assertEquals(0, state.combo)
    }

    @Test
    fun `the opponent's blow lands the moment it is reported`() {
        val state = reduce(
            opening(),
            DuoEvent.OpponentAnswered(
                OpponentAnsweredDto(
                    correct = true,
                    damage = 19,
                    isCritical = true,
                    yourHp = 111,
                    opponentScore = 900,
                    opponentCombo = 2,
                    opponentDeckRemaining = 2,
                ),
            ),
        )

        assertEquals(111, state.myHp)
        assertEquals(900, state.opponentScore)
        assertEquals(2, state.opponentCombo)
        assertEquals(2, state.opponentDeckRemaining)
        assertEquals(19, state.lastIncoming?.damage)
    }

    @Test
    fun `two identical blows in a row are two events, not one`() {
        val once = reduce(
            opening(),
            DuoEvent.OpponentAnswered(OpponentAnsweredDto(correct = true, damage = 12, yourHp = 118)),
        )
        val twice = reduce(
            once,
            DuoEvent.OpponentAnswered(OpponentAnsweredDto(correct = true, damage = 12, yourHp = 106)),
        )

        // The payloads differ only in health here, but the counter is what the arena diffs on --
        // two blows that happened to be identical still have to flash twice.
        assertEquals(once.incomingSeq + 1, twice.incomingSeq)
    }

    @Test
    fun `a stun arrives with the blow that caused it`() {
        val state = reduce(
            opening(),
            DuoEvent.OpponentAnswered(
                OpponentAnsweredDto(
                    correct = true,
                    damage = 40,
                    isCritical = true,
                    yourHp = 90,
                    yourStunnedUntil = 8_000,
                ),
            ),
        )

        assertEquals(8_000, state.stunnedUntil)
        assertTrue(state.isStunned(localNowMs = 5_000))
        assertFalse(state.isStunned(localNowMs = 9_000))
        assertFalse(state.canCastSkill(localNowMs = 5_000))
        assertTrue(state.canCastSkill(localNowMs = 9_000))
    }

    @Test
    fun `a cast is recorded for both sides and told apart by who fired it`() {
        val mine = reduce(
            opening(),
            DuoEvent.SkillUsed(
                DuoSkillUsedDto(
                    userId = "me",
                    skillCode = "SHIELD",
                    skillName = "Khiên",
                    effect = "DAMAGE_REDUCTION",
                    magnitude = 500,
                    manaSpent = 30,
                    yourHp = 130,
                    opponentHp = 80,
                    yourMana = 5,
                    readyAgainAt = 12_000,
                ),
            ),
        )

        assertEquals("SHIELD", mine.lastSkill?.skillCode)
        assertEquals("me", mine.lastSkill?.userId)
        assertEquals(5, mine.mana)
        assertEquals(12_000L, mine.lastSkill?.readyAgainAt)

        val theirs = reduce(
            mine,
            DuoEvent.SkillUsed(
                DuoSkillUsedDto(
                    userId = "them",
                    skillCode = "DRAIN",
                    skillName = "Hút mana",
                    manaSpent = 20,
                    yourHp = 130,
                    opponentHp = 80,
                    yourMana = 5,
                    // Zero for the side that did not cast it -- a recharge is the caster's
                    // business, and greying our own slot on theirs would be wrong.
                    readyAgainAt = 0,
                ),
            ),
        )
        assertEquals("them", theirs.lastSkill?.userId)
        assertEquals(mine.skillSeq + 1, theirs.skillSeq)
    }

    @Test
    fun `a time penalty pushes our own lockout out at once`() {
        val state = reduce(
            opening(),
            DuoEvent.SkillUsed(
                DuoSkillUsedDto(
                    userId = "them",
                    skillCode = "FREEZE",
                    skillName = "Đóng băng",
                    effect = "TIME_PENALTY",
                    yourHp = 130,
                    opponentHp = 80,
                    yourMana = 10,
                    lockoutEndsAt = 9_000,
                ),
            ),
        )

        // Waiting up to a tenth of a second for the next snapshot to say so would leave the screen
        // claiming a question is about to arrive when it is not.
        assertEquals(9_000, state.lockoutEndsAt)
        assertTrue(state.inLockout(localNowMs = 5_000))
    }

    @Test
    fun `a reveal only hides options for the question it was cast on`() {
        val revealed = reduce(
            opening(),
            DuoEvent.SkillUsed(
                DuoSkillUsedDto(
                    userId = "me",
                    skillCode = "REVEAL",
                    skillName = "Loại đáp án",
                    effect = "REMOVE_OPTIONS",
                    yourHp = 130,
                    opponentHp = 80,
                    yourMana = 0,
                    private = DuoSkillPrivateDto(removedOptionIds = listOf("a")),
                ),
            ),
        )
        assertEquals(listOf("a"), revealed.removedOptionIds)

        val next = reduce(
            revealed,
            DuoEvent.QuestionPush(
                QuestionPushDto(token = "t1", question = question("q2"), deckRemaining = 2),
            ),
        )
        assertTrue(next.removedOptionIds.isEmpty())
    }

    @Test
    fun `the opponent's cast never carries their private reveal`() {
        val state = reduce(
            opening(),
            DuoEvent.SkillUsed(
                DuoSkillUsedDto(
                    userId = "them",
                    skillCode = "REVEAL",
                    skillName = "Loại đáp án",
                    effect = "REMOVE_OPTIONS",
                    yourHp = 130,
                    opponentHp = 80,
                    yourMana = 10,
                    private = null,
                ),
            ),
        )

        assertTrue(state.removedOptionIds.isEmpty())
    }

    @Test
    fun `standing effects follow the snapshot`() {
        val state = reduce(
            opening(),
            DuoEvent.StateTick(
                DuoStateTickDto(
                    t = 5_000,
                    yourHp = 130,
                    yourMaxHp = 130,
                    opponentHp = 80,
                    opponentMaxHp = 80,
                    effects = listOf(
                        DuoActiveEffectDto(
                            code = "SHIELD",
                            effect = "DAMAGE_REDUCTION",
                            magnitude = 500,
                            expiresAt = 11_000,
                        ),
                    ),
                ),
                receivedAtMs = 5_000,
            ),
        )

        assertEquals(listOf("SHIELD"), state.effects.map { it.code })
    }

    @Test
    fun `a knockout still reaches the result screen`() {
        val downed = reduce(
            opening(),
            DuoEvent.OpponentAnswered(OpponentAnsweredDto(correct = true, damage = 130, yourHp = 0)),
        )
        assertEquals(0, downed.myHp)

        val state = reduce(
            downed,
            DuoEvent.MatchFinished(
                MatchFinishedDto(
                    matchId = "m1",
                    result = MatchOutcome.LOSE,
                    endReason = DuoMatchEndReason.KNOCKOUT,
                    yourScore = 900,
                    opponentScore = 2100,
                    yourCorrect = 1,
                    opponentCorrect = 3,
                    deckSize = 3,
                    yourDeckCleared = false,
                    opponentDeckCleared = false,
                    durationSeconds = 44,
                    rating = RatingChangeDto(before = 1000, after = 984, delta = -16),
                    yourHpLeft = 0,
                    opponentHpLeft = 22,
                ),
            ),
        )

        assertEquals(DuoPhase.FINISHED, state.phase)
        assertEquals(0, state.myHp)
        assertEquals(22, state.opponentHp)
        assertEquals(0, state.stunnedUntil)
    }

    @Test
    fun `clearing the deck is reported as its own ending`() {
        val state = reduce(
            opening(),
            DuoEvent.MatchFinished(
                MatchFinishedDto(
                    matchId = "m1",
                    result = MatchOutcome.WIN,
                    endReason = DuoMatchEndReason.DECK_CLEARED,
                    yourScore = 2800,
                    opponentScore = 900,
                    yourCorrect = 3,
                    opponentCorrect = 1,
                    deckSize = 3,
                    yourDeckCleared = true,
                    opponentDeckCleared = false,
                    durationSeconds = 31,
                    rating = RatingChangeDto(before = 1000, after = 1016, delta = 16),
                    yourHpLeft = 40,
                    opponentHpLeft = 62,
                ),
            ),
        )

        assertEquals(DuoMatchEndReason.DECK_CLEARED, state.finished?.endReason)
        assertTrue(state.finished?.yourDeckCleared == true)
        // Won the race while behind on health, which the old engine could not express at all.
        assertTrue(state.myHp < state.opponentHp)
    }

    @Test
    fun `running out of energy drops the player out of the queue`() {
        val queued = reduce(DuoSession(), DuoEvent.QueueWaiting(com.kma.quiz_game.data.remote.dto.QueueWaitingDto(1, 0)))
        val state = reduce(queued, DuoEvent.Failed(ErrorDto(code = "NOT_ENOUGH_ENERGY")))

        assertEquals(DuoPhase.IDLE, state.phase)
        assertEquals(DuoErrorCode.NOT_ENOUGH_ENERGY, state.lastError)
    }

    @Test
    fun `the same refusal twice is two events, not one`() {
        val once = reduce(opening(), DuoEvent.Failed(ErrorDto(code = "NOT_ENOUGH_MANA")))
        val twice = reduce(once, DuoEvent.Failed(ErrorDto(code = "NOT_ENOUGH_MANA")))

        assertEquals(DuoErrorCode.NOT_ENOUGH_MANA, twice.lastError)
        assertEquals(once.errorSeq + 1, twice.errorSeq)
    }
}
