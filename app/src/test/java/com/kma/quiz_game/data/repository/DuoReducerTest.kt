package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.dto.AnswerResultDto
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeOptionDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import com.kma.quiz_game.data.remote.dto.ChatMessageDto
import com.kma.quiz_game.data.remote.dto.ConnectedDto
import com.kma.quiz_game.data.remote.dto.DuoErrorCode
import com.kma.quiz_game.data.remote.dto.DuoEvent
import com.kma.quiz_game.data.remote.dto.DuoMatchEndReason
import com.kma.quiz_game.data.remote.dto.DuoMatchMode
import com.kma.quiz_game.data.remote.dto.DuoPlayerDto
import com.kma.quiz_game.data.remote.dto.DuoSettingsDto
import com.kma.quiz_game.data.remote.dto.DuoStateTickDto
import com.kma.quiz_game.data.remote.dto.ErrorDto
import com.kma.quiz_game.data.remote.dto.MatchFinishedDto
import com.kma.quiz_game.data.remote.dto.MatchFoundDto
import com.kma.quiz_game.data.remote.dto.MatchOutcome
import com.kma.quiz_game.data.remote.dto.MatchResumeDto
import com.kma.quiz_game.data.remote.dto.MatchStartedDto
import com.kma.quiz_game.data.remote.dto.OpponentDisconnectedDto
import com.kma.quiz_game.data.remote.dto.QuestionPushDto
import com.kma.quiz_game.data.remote.dto.QueueWaitingDto
import com.kma.quiz_game.data.remote.dto.RatingChangeDto
import com.kma.quiz_game.data.remote.dto.RoomCreatedDto
import com.kma.quiz_game.data.repository.DuoRepository.Companion.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duo reducer is the only place in the feature with logic that can be quietly wrong -- phase
 * transitions, deck bookkeeping, reconnect handling -- and it is a pure function, so it is tested
 * here with no socket, no server and no Android framework.
 */
class DuoReducerTest {

    private val me = DuoPlayerDto(id = "me", username = "Me", rating = 1000)
    private val them = DuoPlayerDto(id = "them", username = "Them", rating = 1050)
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
        ),
    )

    private fun connected(): DuoSession =
        reduce(DuoSession(), DuoEvent.Connected(ConnectedDto(user = me, activeMatchId = null)))

    private fun matched(autoStart: Boolean = true, hostId: String = "them"): DuoSession = reduce(
        connected(),
        DuoEvent.MatchFound(
            MatchFoundDto(
                matchId = "m1",
                mode = DuoMatchMode.RANDOM,
                opponent = them,
                settings = settings,
                hostId = hostId,
                autoStart = autoStart,
            ),
        ),
    )

    /** Walks a session up to the first question, the way a real random match does. */
    private fun inMatch(): DuoSession {
        val started = reduce(
            matched(),
            DuoEvent.MatchStarted(
                MatchStartedDto(
                    matchId = "m1",
                    deckSize = 3,
                    speedReferenceSeconds = 15,
                    serverTimeMs = 10_000,
                    deadlineAt = 55_000,
                    yourHp = 100,
                    yourMaxHp = 100,
                    yourMana = 0,
                    opponentHp = 100,
                    opponentMaxHp = 100,
                ),
                receivedAtMs = 1_000,
            ),
        )
        return reduce(
            started,
            DuoEvent.QuestionPush(
                QuestionPushDto(token = "t0", question = question(), deckRemaining = 3),
            ),
        )
    }

    @Test
    fun `connected identifies the player`() {
        val state = reduce(DuoSession(), DuoEvent.Connected(ConnectedDto(user = me, activeMatchId = "m9")))

        assertEquals(ConnectionState.CONNECTED, state.connection)
        assertEquals(me, state.me)
        // An active match id is the signal that a `match.resume` is about to arrive.
        assertEquals("m9", state.matchId)
    }

    @Test
    fun `queue waiting reports its position`() {
        val state = reduce(DuoSession(), DuoEvent.QueueWaiting(QueueWaitingDto(position = 2, waitedSeconds = 7)))

        assertEquals(DuoPhase.QUEUEING, state.phase)
        assertEquals(2, state.queuePosition)
        assertEquals(7, state.queueWaitedSeconds)
    }

    @Test
    fun `queue timeout returns to idle and flags itself`() {
        val queued = reduce(DuoSession(), DuoEvent.QueueWaiting(QueueWaitingDto(1, 300)))
        val state = reduce(queued, DuoEvent.QueueTimeout)

        assertEquals(DuoPhase.IDLE, state.phase)
        assertTrue(state.queueTimedOut)
    }

    @Test
    fun `creating a room makes the creator the host`() {
        val state = reduce(
            connected(),
            DuoEvent.RoomCreated(RoomCreatedDto(matchId = "m1", roomCode = "ABC234", settings = settings)),
        )

        assertEquals(DuoPhase.ROOM_WAITING, state.phase)
        assertEquals("ABC234", state.roomCode)
        assertTrue(state.isHost)
    }

    @Test
    fun `a joiner is not the host`() {
        val state = inMatch()

        assertEquals("them", state.hostId)
        assertFalse(state.isHost)
    }

    @Test
    fun `a friend room stays a lobby until the host starts it`() {
        val hosted = reduce(
            connected(),
            DuoEvent.RoomCreated(RoomCreatedDto(matchId = "m1", roomCode = "ABC234", settings = settings)),
        )
        val joined = reduce(
            hosted,
            DuoEvent.MatchFound(
                MatchFoundDto(
                    matchId = "m1",
                    roomCode = "ABC234",
                    mode = DuoMatchMode.FRIEND,
                    opponent = them,
                    settings = settings,
                    hostId = "me",
                    autoStart = false,
                ),
            ),
        )

        // The host still has to press start, so this must not look like a live match yet.
        assertEquals(DuoPhase.ROOM_WAITING, joined.phase)
        assertFalse(joined.isInMatch)
        assertEquals(them, joined.opponent)
        assertTrue(joined.isHost)
    }

    @Test
    fun `a random match goes straight into play`() {
        val state = matched()

        assertEquals(DuoPhase.MATCHED, state.phase)
        assertTrue(state.isInMatch)
    }

    @Test
    fun `a room closed by the server does not strand the player in the lobby`() {
        val hosted = reduce(
            connected(),
            DuoEvent.RoomCreated(RoomCreatedDto(matchId = "m1", roomCode = "ABC234", settings = settings)),
        )

        val state = reduce(hosted, DuoEvent.Failed(ErrorDto(code = "ROOM_NOT_FOUND")))

        assertEquals(DuoPhase.IDLE, state.phase)
        assertNull(state.roomCode)
        assertEquals(DuoErrorCode.ROOM_NOT_FOUND, state.lastError)
    }

    @Test
    fun `the start frame opens both decks and fixes the clock offset`() {
        val state = inMatch()

        assertEquals(DuoPhase.FIGHTING, state.phase)
        assertEquals(3, state.deckSize)
        assertEquals(3, state.myDeckRemaining)
        assertEquals(3, state.opponentDeckRemaining)
        assertEquals(100, state.myMaxHp)
        assertEquals(100, state.opponentMaxHp)
        // The server said 10_000 at the instant this device read 1_000.
        assertEquals(9_000, state.serverOffsetMs)
        assertEquals(55_000, state.deadlineAt)
        assertEquals(45_000, state.matchRemainingMs(1_000))
    }

    @Test
    fun `a question arrives with no answer key and clears the last result`() {
        val answered = reduce(
            inMatch(),
            DuoEvent.AnswerResult(
                AnswerResultDto(
                    token = "t0",
                    correct = true,
                    optionId = "b",
                    correctOptionIds = listOf("b"),
                    yourScore = 940,
                    yourDeckRemaining = 2,
                ),
            ),
        )

        val next = reduce(
            answered,
            DuoEvent.QuestionPush(
                QuestionPushDto(token = "t1", question = question("q2"), deckRemaining = 2),
            ),
        )

        assertEquals("t1", next.questionToken)
        assertTrue(next.hasQuestion)
        assertNull(next.myOptionId)
        assertNull(next.answerResult)
        assertFalse(next.hasAnswered)
        // The score is cumulative and must survive the new question.
        assertEquals(940, next.myScore)
    }

    @Test
    fun `a wrong answer leaves the deck no shorter and the question comes back`() {
        val wrong = reduce(
            inMatch(),
            DuoEvent.AnswerResult(
                AnswerResultDto(
                    token = "t0",
                    correct = false,
                    optionId = "a",
                    correctOptionIds = listOf("b"),
                    explanation = "Vì 2 + 2 = 4",
                    blow = null,
                    yourDeckRemaining = 3,
                ),
            ),
        )

        assertNull(wrong.questionToken)
        assertFalse(wrong.hasQuestion)
        assertEquals(3, wrong.myDeckRemaining)
        assertEquals("Vì 2 + 2 = 4", wrong.answerResult?.explanation)

        val again = reduce(
            wrong,
            DuoEvent.QuestionPush(
                QuestionPushDto(token = "t9", question = question(), deckRemaining = 1, retry = true),
            ),
        )
        assertTrue(again.questionRetry)
    }

    @Test
    fun `a snapshot is authoritative and re-fixes the clock offset`() {
        val state = reduce(
            inMatch(),
            DuoEvent.StateTick(
                DuoStateTickDto(
                    t = 20_000,
                    yourHp = 80,
                    yourMaxHp = 100,
                    yourMana = 35,
                    yourCombo = 2,
                    yourScore = 1800,
                    yourDeckRemaining = 1,
                    opponentHp = 60,
                    opponentMaxHp = 100,
                    opponentCombo = 1,
                    opponentScore = 900,
                    opponentDeckRemaining = 2,
                    deadlineAt = 55_000,
                    lockoutEndsAt = 20_500,
                ),
                receivedAtMs = 4_000,
            ),
        )

        assertEquals(16_000, state.serverOffsetMs)
        assertEquals(80, state.myHp)
        assertEquals(60, state.opponentHp)
        assertEquals(1, state.myDeckRemaining)
        assertEquals(2, state.opponentDeckRemaining)
        assertEquals(2, state.myCleared)
        assertEquals(1, state.opponentCleared)
        assertTrue(state.inLockout(4_000))
        assertFalse(state.inLockout(5_000))
    }

    @Test
    fun `resuming hands back the question that was open`() {
        val dropped = reduce(inMatch(), DuoEvent.SocketClosed(willRetry = true))
        assertEquals(ConnectionState.RECONNECTING, dropped.connection)

        val state = reduce(
            dropped,
            DuoEvent.MatchResume(
                MatchResumeDto(
                    matchId = "m1",
                    opponent = them,
                    settings = settings,
                    deckSize = 3,
                    serverTimeMs = 30_000,
                    deadlineAt = 55_000,
                    token = "t7",
                    question = question("q3"),
                    yourDeckRemaining = 2,
                    opponentDeckRemaining = 1,
                    yourScore = 1500,
                    opponentScore = 1200,
                    yourHp = 70,
                    yourMaxHp = 100,
                    opponentHp = 90,
                    opponentMaxHp = 100,
                ),
                receivedAtMs = 2_000,
            ),
        )

        assertEquals(ConnectionState.CONNECTED, state.connection)
        assertEquals(DuoPhase.FIGHTING, state.phase)
        assertEquals("t7", state.questionToken)
        assertEquals(1500, state.myScore)
        assertEquals(2, state.myDeckRemaining)
        assertEquals(1, state.opponentDeckRemaining)
        assertEquals(28_000, state.serverOffsetMs)
        // A question handed back still open is one this player has yet to answer.
        assertNull(state.myOptionId)
        assertFalse(state.hasAnswered)
    }

    @Test
    fun `resuming between two questions shows no stale question`() {
        val state = reduce(
            inMatch(),
            DuoEvent.MatchResume(
                MatchResumeDto(
                    matchId = "m1",
                    opponent = them,
                    settings = settings,
                    deckSize = 3,
                    token = null,
                    question = null,
                    yourDeckRemaining = 1,
                    opponentDeckRemaining = 1,
                ),
                receivedAtMs = 0,
            ),
        )

        assertEquals(DuoPhase.FIGHTING, state.phase)
        assertNull(state.question)
        assertFalse(state.hasQuestion)
    }

    @Test
    fun `opponent disconnect and return flip the flag both ways`() {
        val gone = reduce(inMatch(), DuoEvent.OpponentDisconnected(OpponentDisconnectedDto(graceSeconds = 30)))
        assertFalse(gone.opponentConnected)
        assertEquals(30, gone.opponentGraceSeconds)

        val back = reduce(gone, DuoEvent.OpponentReconnected)
        assertTrue(back.opponentConnected)
        assertEquals(0, back.opponentGraceSeconds)
    }

    @Test
    fun `finishing keeps the result until it is acknowledged`() {
        val finished = MatchFinishedDto(
            matchId = "m1",
            result = MatchOutcome.WIN,
            endReason = DuoMatchEndReason.DECK_CLEARED,
            yourScore = 2400,
            opponentScore = 900,
            yourCorrect = 3,
            opponentCorrect = 1,
            deckSize = 3,
            yourDeckCleared = true,
            durationSeconds = 62,
            rating = RatingChangeDto(before = 1000, after = 1016, delta = 16),
        )
        val state = reduce(inMatch(), DuoEvent.MatchFinished(finished))

        assertEquals(DuoPhase.FINISHED, state.phase)
        assertEquals(finished, state.finished)
        assertEquals(2400, state.myScore)
        assertFalse(state.isInMatch)
        assertNull(state.questionToken)
    }

    @Test
    fun `a snapshot arriving after the result does not reopen the match`() {
        val finished = reduce(
            inMatch(),
            DuoEvent.MatchFinished(
                MatchFinishedDto(
                    matchId = "m1",
                    result = MatchOutcome.LOSE,
                    endReason = DuoMatchEndReason.KNOCKOUT,
                    yourScore = 0,
                    opponentScore = 900,
                    yourCorrect = 0,
                    opponentCorrect = 3,
                    deckSize = 3,
                    durationSeconds = 40,
                    rating = RatingChangeDto(before = 1000, after = 984, delta = -16),
                ),
            ),
        )

        val late = reduce(
            finished,
            DuoEvent.StateTick(DuoStateTickDto(t = 40_000, yourHp = 0), receivedAtMs = 20_000),
        )

        assertEquals(DuoPhase.FINISHED, late.phase)
    }

    @Test
    fun `tapping a question that has already closed releases the option instead of erroring`() {
        val tapped = reduce(inMatch(), DuoEvent.Failed(ErrorDto(code = "QUESTION_CLOSED")))

        assertNull(tapped.lastError)
        assertNull(tapped.myOptionId)
        assertEquals(
            DuoErrorCode.NOT_HOST,
            reduce(inMatch(), DuoEvent.Failed(ErrorDto(code = "NOT_HOST"))).lastError,
        )
    }

    @Test
    fun `an unrecognised error code degrades instead of throwing`() {
        val state = reduce(DuoSession(), DuoEvent.Failed(ErrorDto(code = "SOMETHING_NEW_FROM_THE_SERVER")))

        assertEquals(DuoErrorCode.UNKNOWN, state.lastError)
    }

    @Test
    fun `chat appends in arrival order`() {
        var state = inMatch()
        state = reduce(state, DuoEvent.Chat(ChatMessageDto("me", "gl", "2026-08-27T10:00:00Z")))
        state = reduce(state, DuoEvent.Chat(ChatMessageDto("them", "hf", "2026-08-27T10:00:01Z")))

        assertEquals(listOf("gl", "hf"), state.chat.map { it.message })
    }

    @Test
    fun `room codes are upper-cased and stripped of ambiguous characters`() {
        // The server's alphabet has no I, O, 0 or 1, so those can only be typos.
        assertEquals("ABC234", DuoRepository.run { "abc-234".normalizeRoomCode() })
        assertEquals("ABC234", DuoRepository.run { "AB1C2O34".normalizeRoomCode() })
    }

    @Test
    fun `settings are clamped to what the server will accept`() {
        val tooBig = DuoSettingsDto(questionCount = 99, timePerQuestion = 999).coerced()

        assertEquals(DuoSettingsDto.MAX_QUESTION_COUNT, tooBig.questionCount)
        assertEquals(DuoSettingsDto.MAX_TIME_PER_QUESTION, tooBig.timePerQuestion)
    }
}
