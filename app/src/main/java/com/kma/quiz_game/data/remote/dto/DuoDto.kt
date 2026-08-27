package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for 1v1 PvP -- both the REST endpoints under `/duo` and the WebSocket payloads.
 *
 * Field names are plain camelCase: [com.kma.quiz_game.data.remote.NetworkModule.json] maps them
 * to the backend's snake_case, so no `@SerialName` is needed anywhere here.
 *
 * The question carried by `round.start` is a `ChallengePublicRead`, which is exactly the existing
 * [ChallengeDto] -- it is reused rather than redeclared.
 */

// --- Shared value types ---------------------------------------------------

enum class DuoMatchMode { RANDOM, FRIEND }

enum class DuoMatchStatus { WAITING, IN_PROGRESS, FINISHED, ABANDONED, CANCELLED }

enum class DuoMatchEndReason { COMPLETED, OPPONENT_LEFT, OPPONENT_TIMEOUT, CANCELLED }

enum class MatchOutcome { WIN, LOSE, DRAW }

enum class DuoDifficulty { EASY, MEDIUM, HARD }

@Serializable
data class DuoPlayerDto(
    val id: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val rating: Int,
)

/**
 * Match settings. The bounds mirror `DuoSettingsRequest` on the backend; the client enforces them
 * too so a slider can never produce an `INVALID_PAYLOAD`.
 *
 * Random matchmaking only pairs players whose settings are *identical*, so changing anything here
 * shrinks the pool you can be matched against.
 */
@Serializable
data class DuoSettingsDto(
    val questionCount: Int = DEFAULT_QUESTION_COUNT,
    val timePerQuestion: Int = DEFAULT_TIME_PER_QUESTION,
    val topicIds: List<String>? = null,
    val difficulty: DuoDifficulty? = null,
) {
    companion object {
        const val MIN_QUESTION_COUNT = 3
        const val MAX_QUESTION_COUNT = 20
        const val DEFAULT_QUESTION_COUNT = 10
        const val MIN_TIME_PER_QUESTION = 5
        const val MAX_TIME_PER_QUESTION = 60
        const val DEFAULT_TIME_PER_QUESTION = 15
    }

    fun coerced(): DuoSettingsDto = copy(
        questionCount = questionCount.coerceIn(MIN_QUESTION_COUNT, MAX_QUESTION_COUNT),
        timePerQuestion = timePerQuestion.coerceIn(MIN_TIME_PER_QUESTION, MAX_TIME_PER_QUESTION),
    )
}

// --- REST: /duo/* ---------------------------------------------------------

/** One row of match history, already told from the requesting player's side. */
@Serializable
data class DuoMatchSummaryDto(
    val matchId: String,
    val mode: DuoMatchMode,
    val status: DuoMatchStatus,
    val endReason: DuoMatchEndReason? = null,
    val outcome: MatchOutcome? = null,
    val opponent: DuoPlayerDto? = null,
    val myScore: Int,
    val opponentScore: Int,
    val myCorrect: Int,
    val opponentCorrect: Int,
    val questionCount: Int,
    val durationSeconds: Int? = null,
    val finishedAt: String? = null,
    val createdAt: String,
)

@Serializable
data class DuoRoundDto(
    val roundIndex: Int,
    val challengeId: String? = null,
    val question: String? = null,
    val myOptionId: String? = null,
    val opponentOptionId: String? = null,
    val myCorrect: Boolean,
    val opponentCorrect: Boolean,
    val myElapsedMs: Int? = null,
    val opponentElapsedMs: Int? = null,
    val myPoints: Int,
    val opponentPoints: Int,
)

@Serializable
data class DuoMatchDetailDto(
    val matchId: String,
    val mode: DuoMatchMode,
    val status: DuoMatchStatus,
    val endReason: DuoMatchEndReason? = null,
    val outcome: MatchOutcome? = null,
    val opponent: DuoPlayerDto? = null,
    val myScore: Int,
    val opponentScore: Int,
    val myCorrect: Int,
    val opponentCorrect: Int,
    val questionCount: Int,
    val durationSeconds: Int? = null,
    val finishedAt: String? = null,
    val createdAt: String,
    val rounds: List<DuoRoundDto> = emptyList(),
)

/** All-zero with `rating = 1000` for a player who has never finished a match. */
@Serializable
data class DuoStatsDto(
    val rating: Int,
    val matchesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val winRate: Double,
    val currentStreak: Int,
    val bestStreak: Int,
)

@Serializable
data class DuoLeaderboardEntryDto(
    val rank: Int,
    val userId: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val rating: Int,
    val matchesPlayed: Int,
    val wins: Int,
)

@Serializable
data class DuoLeaderboardDto(
    val entries: List<DuoLeaderboardEntryDto> = emptyList(),
    val myRank: Int? = null,
)

/** Reads the *in-memory* room registry, so it only answers while the room is live and WAITING. */
@Serializable
data class DuoRoomPreviewDto(
    val roomCode: String,
    val host: DuoPlayerDto,
    val settings: DuoSettingsDto,
    val playerCount: Int,
)

// --- WebSocket payloads ---------------------------------------------------

@Serializable
data class ConnectedDto(
    val user: DuoPlayerDto,
    val activeMatchId: String? = null,
)

@Serializable
data class QueueWaitingDto(
    val position: Int,
    val waitedSeconds: Int,
)

@Serializable
data class RoomCreatedDto(
    val matchId: String,
    val roomCode: String,
    val settings: DuoSettingsDto,
)

@Serializable
data class MatchFoundDto(
    val matchId: String,
    val roomCode: String? = null,
    val mode: DuoMatchMode,
    val opponent: DuoPlayerDto,
    val settings: DuoSettingsDto,
    val hostId: String,
    /** True for the random queue (the match loop is already running); false in a friend room,
     * where the host must send `match.start`. */
    val autoStart: Boolean,
)

@Serializable
data class MatchStartedDto(
    val matchId: String,
    val totalRounds: Int,
)

@Serializable
data class RoundStartDto(
    val roundIndex: Int,
    val totalRounds: Int,
    val question: ChallengeDto,
    val timeLimitSeconds: Int,
    /** Server-side deadline. The countdown runs off [timeLimitSeconds] instead, so a skewed
     * device clock cannot shorten or stretch the round on screen. */
    val deadlineAt: String? = null,
)

@Serializable
data class OpponentAnsweredDto(val roundIndex: Int)

@Serializable
data class AnswerOutcomeDto(
    val optionId: String? = null,
    val correct: Boolean,
    /** Measured on the server from the round start -- the client never reports its own timing. */
    val elapsedMs: Int? = null,
    val points: Int,
)

@Serializable
data class RoundResultDto(
    val roundIndex: Int,
    val correctOptionIds: List<String> = emptyList(),
    val explanation: String? = null,
    val you: AnswerOutcomeDto,
    val opponent: AnswerOutcomeDto,
    val yourScore: Int,
    val opponentScore: Int,
)

/** Everything needed to rebuild the match screen after reconnecting. */
@Serializable
data class MatchResumeDto(
    val matchId: String,
    val opponent: DuoPlayerDto,
    val settings: DuoSettingsDto,
    val roundIndex: Int,
    val totalRounds: Int,
    val yourScore: Int,
    val opponentScore: Int,
    val question: ChallengeDto? = null,
    val secondsRemaining: Int? = null,
    val alreadyAnswered: Boolean = false,
)

@Serializable
data class OpponentDisconnectedDto(val graceSeconds: Int)

@Serializable
data class RatingChangeDto(
    val before: Int,
    val after: Int,
    val delta: Int,
)

@Serializable
data class MatchFinishedDto(
    val matchId: String,
    val result: MatchOutcome,
    val endReason: DuoMatchEndReason,
    val yourScore: Int,
    val opponentScore: Int,
    val yourCorrect: Int,
    val opponentCorrect: Int,
    val totalRounds: Int,
    val durationSeconds: Int,
    val rating: RatingChangeDto,
)

/** Broadcast to both players, including whoever sent it. */
@Serializable
data class ChatMessageDto(
    val userId: String,
    val message: String,
    val sentAt: String,
)

/** [code] stays a raw string on the wire so an error code added on the server later degrades to
 * [DuoErrorCode.UNKNOWN] instead of crashing the decoder. */
@Serializable
data class ErrorDto(
    val code: String = "",
    val message: String = "",
) {
    val errorCode: DuoErrorCode
        get() = DuoErrorCode.entries.firstOrNull { it.name == code } ?: DuoErrorCode.UNKNOWN
}

/** Stable error codes from `app/schemas/duo/events.py`, plus a client-side catch-all. */
enum class DuoErrorCode {
    INVALID_PAYLOAD,
    UNKNOWN_EVENT,
    ALREADY_IN_MATCH,
    ALREADY_IN_QUEUE,
    NOT_IN_QUEUE,
    ROOM_NOT_FOUND,
    ROOM_FULL,
    NOT_HOST,
    NOT_ENOUGH_PLAYERS,
    MATCH_ALREADY_STARTED,
    NOT_IN_MATCH,
    ROUND_CLOSED,
    ALREADY_ANSWERED,
    INVALID_OPTION,
    NO_QUESTIONS_AVAILABLE,
    UNKNOWN,
}
