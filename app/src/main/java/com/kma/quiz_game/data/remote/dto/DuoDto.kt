package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for the 1v1 PvP REST endpoints under `/duo`.
 *
 * Field names are plain camelCase: [com.kma.quiz_game.data.remote.NetworkModule.json] maps them
 * to the backend's snake_case, so no `@SerialName` is needed anywhere here.
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
