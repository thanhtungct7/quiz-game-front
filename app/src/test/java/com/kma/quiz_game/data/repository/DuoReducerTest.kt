package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.dto.AnswerOutcomeDto
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
import com.kma.quiz_game.data.remote.dto.ErrorDto
import com.kma.quiz_game.data.remote.dto.MatchFinishedDto
import com.kma.quiz_game.data.remote.dto.MatchFoundDto
import com.kma.quiz_game.data.remote.dto.MatchOutcome
import com.kma.quiz_game.data.remote.dto.MatchResumeDto
import com.kma.quiz_game.data.remote.dto.MatchStartedDto
import com.kma.quiz_game.data.remote.dto.OpponentAnsweredDto
import com.kma.quiz_game.data.remote.dto.OpponentDisconnectedDto
import com.kma.quiz_game.data.remote.dto.QueueWaitingDto
import com.kma.quiz_game.data.remote.dto.RatingChangeDto
import com.kma.quiz_game.data.remote.dto.RoomCreatedDto
import com.kma.quiz_game.data.remote.dto.RoundResultDto
import com.kma.quiz_game.data.remote.dto.RoundStartDto
import com.kma.quiz_game.data.repository.DuoRepository.Companion.RESUMED_ANSWER
import com.kma.quiz_game.data.repository.DuoRepository.Companion.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duo reducer is the only place in the feature with logic that can be quietly wrong -- phase
 * transitions, score carry-over, reconnect handling -- and it is a pure function, so it is tested
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

    /** Walks a session up to the start of round 0, the way a real random match does. */
    private fun matchInProgress(): DuoSession {
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
        state = reduce(state, DuoEvent.MatchStarted(MatchStartedDto(matchId = "m1", totalRounds = 3)))
        return reduce(
            state,
            DuoEvent.RoundStart(
                RoundStartDto(roundIndex = 0, totalRounds = 3, question = question(), timeLimitSeconds = 15),
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
        val connected = reduce(DuoSession(), DuoEvent.Connected(ConnectedDto(me, null)))
        val state = reduce(
            connected,
            DuoEvent.RoomCreated(RoomCreatedDto(matchId = "m1", roomCode = "ABC234", settings = settings)),
        )

        assertEquals(DuoPhase.ROOM_WAITING, state.phase)
        assertEquals("ABC234", state.roomCode)
        assertTrue(state.isHost)
    }

    @Test
    fun `a joiner is not the host`() {
        val state = matchInProgress()

        assertEquals("them", state.hostId)
        assertFalse(state.isHost)
    }

    @Test
    fun `a friend room stays a lobby until the host starts it`() {
        val connected = reduce(DuoSession(), DuoEvent.Connected(ConnectedDto(me, null)))
        val hosted = reduce(
            connected,
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

        val started = reduce(joined, DuoEvent.MatchStarted(MatchStartedDto(matchId = "m1", totalRounds = 3)))
        assertEquals(DuoPhase.MATCHED, started.phase)
        assertTrue(started.isInMatch)
    }

    @Test
    fun `a random match goes straight into play`() {
        val state = reduce(
            reduce(DuoSession(), DuoEvent.Connected(ConnectedDto(me, null))),
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

        assertEquals(DuoPhase.MATCHED, state.phase)
        assertTrue(state.isInMatch)
    }

    @Test
    fun `a room closed by the server does not strand the player in the lobby`() {
        val hosted = reduce(
            reduce(DuoSession(), DuoEvent.Connected(ConnectedDto(me, null))),
            DuoEvent.RoomCreated(RoomCreatedDto(matchId = "m1", roomCode = "ABC234", settings = settings)),
        )

        val state = reduce(hosted, DuoEvent.Failed(ErrorDto(code = "ROOM_NOT_FOUND")))

        assertEquals(DuoPhase.IDLE, state.phase)
        assertNull(state.roomCode)
        assertEquals(DuoErrorCode.ROOM_NOT_FOUND, state.lastError)
    }

    @Test
    fun `round start clears the previous round's answer and reveal`() {
        val revealed = reduce(
            matchInProgress(),
            DuoEvent.RoundResult(
                RoundResultDto(
                    roundIndex = 0,
                    correctOptionIds = listOf("b"),
                    you = AnswerOutcomeDto(optionId = "b", correct = true, elapsedMs = 1200, points = 940),
                    opponent = AnswerOutcomeDto(optionId = "a", correct = false, elapsedMs = 3000, points = 0),
                    yourScore = 940,
                    opponentScore = 0,
                ),
            ),
        )

        val next = reduce(
            revealed,
            DuoEvent.RoundStart(
                RoundStartDto(roundIndex = 1, totalRounds = 3, question = question("q2"), timeLimitSeconds = 15),
            ),
        )

        assertEquals(DuoPhase.IN_ROUND, next.phase)
        assertEquals(1, next.roundIndex)
        assertNull(next.myOptionId)
        assertNull(next.roundResult)
        assertFalse(next.opponentAnswered)
        // Scores are cumulative and must survive the new round.
        assertEquals(940, next.myScore)
    }

    @Test
    fun `round result carries the running score`() {
        val state = reduce(
            matchInProgress(),
            DuoEvent.RoundResult(
                RoundResultDto(
                    roundIndex = 0,
                    correctOptionIds = listOf("b"),
                    explanation = "Vì 2 + 2 = 4",
                    you = AnswerOutcomeDto(optionId = "a", correct = false, elapsedMs = 5000, points = 0),
                    opponent = AnswerOutcomeDto(optionId = "b", correct = true, elapsedMs = 900, points = 970),
                    yourScore = 0,
                    opponentScore = 970,
                ),
            ),
        )

        assertEquals(DuoPhase.ROUND_REVEAL, state.phase)
        assertEquals(0, state.myScore)
        assertEquals(970, state.opponentScore)
        assertEquals("Vì 2 + 2 = 4", state.roundResult?.explanation)
        assertTrue(state.hasAnswered)
    }

    @Test
    fun `opponent answered only applies to the current round`() {
        val state = matchInProgress()

        val stale = reduce(state, DuoEvent.OpponentAnswered(OpponentAnsweredDto(roundIndex = 5)))
        assertFalse(stale.opponentAnswered)

        val current = reduce(state, DuoEvent.OpponentAnswered(OpponentAnsweredDto(roundIndex = 0)))
        assertTrue(current.opponentAnswered)
    }

    @Test
    fun `resuming mid-round restores score, clock and answered state`() {
        val dropped = reduce(matchInProgress(), DuoEvent.SocketClosed(willRetry = true))
        assertEquals(ConnectionState.RECONNECTING, dropped.connection)

        val state = reduce(
            dropped,
            DuoEvent.MatchResume(
                MatchResumeDto(
                    matchId = "m1",
                    opponent = them,
                    settings = settings,
                    roundIndex = 2,
                    totalRounds = 3,
                    yourScore = 1500,
                    opponentScore = 1200,
                    question = question("q3"),
                    secondsRemaining = 6,
                    alreadyAnswered = true,
                ),
            ),
        )

        assertEquals(ConnectionState.CONNECTED, state.connection)
        assertEquals(DuoPhase.IN_ROUND, state.phase)
        assertEquals(2, state.roundIndex)
        assertEquals(1500, state.myScore)
        assertEquals(6, state.roundSecondsRemaining)
        // The server says we answered but not what we picked -- the options must stay locked.
        assertEquals(RESUMED_ANSWER, state.myOptionId)
        assertTrue(state.hasAnswered)
    }

    @Test
    fun `resuming between rounds waits instead of showing a stale question`() {
        val state = reduce(
            matchInProgress(),
            DuoEvent.MatchResume(
                MatchResumeDto(
                    matchId = "m1",
                    opponent = them,
                    settings = settings,
                    roundIndex = 1,
                    totalRounds = 3,
                    yourScore = 500,
                    opponentScore = 500,
                    question = null,
                    secondsRemaining = null,
                    alreadyAnswered = false,
                ),
            ),
        )

        assertEquals(DuoPhase.MATCHED, state.phase)
        assertNull(state.question)
    }

    @Test
    fun `opponent disconnect and return flip the flag both ways`() {
        val gone = reduce(matchInProgress(), DuoEvent.OpponentDisconnected(OpponentDisconnectedDto(graceSeconds = 30)))
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
            endReason = DuoMatchEndReason.OPPONENT_LEFT,
            yourScore = 2400,
            opponentScore = 900,
            yourCorrect = 3,
            opponentCorrect = 1,
            totalRounds = 3,
            durationSeconds = 62,
            rating = RatingChangeDto(before = 1000, after = 1016, delta = 16),
        )
        val state = reduce(matchInProgress(), DuoEvent.MatchFinished(finished))

        assertEquals(DuoPhase.FINISHED, state.phase)
        assertEquals(finished, state.finished)
        assertEquals(2400, state.myScore)
        assertFalse(state.isInMatch)
    }

    @Test
    fun `losing a race to answer is not surfaced as an error`() {
        val state = matchInProgress()

        assertNull(reduce(state, DuoEvent.Failed(ErrorDto(code = "ROUND_CLOSED"))).lastError)
        assertNull(reduce(state, DuoEvent.Failed(ErrorDto(code = "ALREADY_ANSWERED"))).lastError)
        assertEquals(
            DuoErrorCode.NOT_HOST,
            reduce(state, DuoEvent.Failed(ErrorDto(code = "NOT_HOST"))).lastError,
        )
    }

    @Test
    fun `an unrecognised error code degrades instead of throwing`() {
        val state = reduce(DuoSession(), DuoEvent.Failed(ErrorDto(code = "SOMETHING_NEW_FROM_THE_SERVER")))

        assertEquals(DuoErrorCode.UNKNOWN, state.lastError)
    }

    @Test
    fun `chat appends in arrival order`() {
        var state = matchInProgress()
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
