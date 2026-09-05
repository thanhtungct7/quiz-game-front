package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.BattleHistoryDto
import com.kma.quiz_game.data.remote.dto.CourseMonstersDto
import com.kma.quiz_game.data.remote.dto.MonsterCatalogDto
import com.kma.quiz_game.data.remote.dto.MonsterPreviewDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The REST half of PvE -- the catalog, the map and history. Fighting happens over
 * [com.kma.quiz_game.data.remote.BattleSocket] instead. Every endpoint needs a bearer token.
 */
interface BattleApi {
    /** Every active monster, for drawing art the map and the battle screen share. */
    @GET("battles/monsters")
    suspend fun listMonsters(): List<MonsterCatalogDto>

    /** Who guards one lesson, and whether you have cleared it. 404 for a bank or missing lesson. */
    @GET("battles/lessons/{lessonId}")
    suspend fun previewLesson(@Path("lessonId") lessonId: String): MonsterPreviewDto

    /** Every gate of a course in one request -- the learn map asks for hundreds at once. */
    @GET("battles/courses/{courseId}/monsters")
    suspend fun courseMonsters(@Path("courseId") courseId: String): CourseMonstersDto

    /** Your own battles, newest first. */
    @GET("battles/history")
    suspend fun listBattles(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): List<BattleHistoryDto>
}
