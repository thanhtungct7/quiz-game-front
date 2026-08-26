package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.AnswerCheckRequest
import com.kma.quiz_game.data.remote.dto.AnswerCheckResult
import com.kma.quiz_game.data.remote.dto.LessonProgressDto
import com.kma.quiz_game.data.remote.dto.UnitProgressDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Authenticated: answer checking and the progress it drives. */
interface ProgressApi {
    @POST("challenges/{challengeId}/check")
    suspend fun checkAnswer(
        @Path("challengeId") challengeId: String,
        @Body body: AnswerCheckRequest,
    ): AnswerCheckResult

    @GET("progress/lessons/{lessonId}")
    suspend fun getLessonProgress(@Path("lessonId") lessonId: String): LessonProgressDto

    @GET("progress/units/{unitId}")
    suspend fun getUnitProgress(@Path("unitId") unitId: String): UnitProgressDto
}
