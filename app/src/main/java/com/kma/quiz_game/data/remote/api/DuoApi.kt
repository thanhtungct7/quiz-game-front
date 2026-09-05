package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.DuoLeaderboardDto
import com.kma.quiz_game.data.remote.dto.DuoMatchDetailDto
import com.kma.quiz_game.data.remote.dto.DuoMatchSummaryDto
import com.kma.quiz_game.data.remote.dto.DuoRoomPreviewDto
import com.kma.quiz_game.data.remote.dto.DuoStatsDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The REST half of 1v1 PvP -- history, stats and the leaderboard. Playing a match happens over
 * [com.kma.quiz_game.data.remote.DuoSocket] instead. Every endpoint here needs a bearer token.
 */
interface DuoApi {
    /** Your own match history, newest first, already told from your side. */
    @GET("duo/matches")
    suspend fun listMatches(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): List<DuoMatchSummaryDto>

    /** 403 if you did not play in the match. */
    @GET("duo/matches/{matchId}")
    suspend fun getMatch(@Path("matchId") matchId: String): DuoMatchDetailDto

    @GET("duo/me/stats")
    suspend fun getMyStats(): DuoStatsDto

    /**
     * Only players with at least one finished match appear, ordered by rating then wins.
     *
     * [season] is `current` (the ladder that resets, the server's default) or `all_time` (the
     * rating that never does). Passing null takes the server's default rather than guessing one.
     */
    @GET("duo/leaderboard")
    suspend fun getLeaderboard(
        @Query("limit") limit: Int? = null,
        @Query("season") season: String? = null,
    ): DuoLeaderboardDto

    /** Reads the in-memory room registry -- 404 once the room is gone or the match has started. */
    @GET("duo/rooms/{roomCode}")
    suspend fun previewRoom(@Path("roomCode") roomCode: String): DuoRoomPreviewDto
}
