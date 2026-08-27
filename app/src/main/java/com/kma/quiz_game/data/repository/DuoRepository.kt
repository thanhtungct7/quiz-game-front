package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.api.DuoApi
import com.kma.quiz_game.data.remote.dto.DuoLeaderboardDto
import com.kma.quiz_game.data.remote.dto.DuoMatchDetailDto
import com.kma.quiz_game.data.remote.dto.DuoMatchSummaryDto
import com.kma.quiz_game.data.remote.dto.DuoRoomPreviewDto
import com.kma.quiz_game.data.remote.dto.DuoStatsDto

/**
 * Thin wrappers over the duo REST endpoints, returning [Result] the way [LearnRepository] does.
 *
 * Application-scoped and the only place that talks to [DuoApi], so that everything about 1v1 --
 * history and stats now, a live match later -- sits behind one door.
 */
class DuoRepository(private val duoApi: DuoApi) {

    suspend fun stats(): Result<DuoStatsDto> = runCatching { duoApi.getMyStats() }

    suspend fun leaderboard(limit: Int = DEFAULT_LEADERBOARD_LIMIT): Result<DuoLeaderboardDto> =
        runCatching { duoApi.getLeaderboard(limit) }

    suspend fun matches(limit: Int = DEFAULT_HISTORY_PAGE, offset: Int = 0): Result<List<DuoMatchSummaryDto>> =
        runCatching { duoApi.listMatches(limit, offset) }

    suspend fun match(matchId: String): Result<DuoMatchDetailDto> =
        runCatching { duoApi.getMatch(matchId) }

    suspend fun previewRoom(roomCode: String): Result<DuoRoomPreviewDto> =
        runCatching { duoApi.previewRoom(roomCode.normalizeRoomCode()) }

    companion object {
        const val DEFAULT_LEADERBOARD_LIMIT = 50
        const val DEFAULT_HISTORY_PAGE = 20

        /** Room codes are 6 characters from an alphabet with no I/O/0/1, and the server upper-cases
         * whatever it receives -- so do the same here and drop anything that cannot be in a code. */
        const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val ROOM_CODE_LENGTH = 6

        fun String.normalizeRoomCode(): String =
            uppercase().filter { it in ROOM_CODE_ALPHABET }.take(ROOM_CODE_LENGTH)
    }
}
