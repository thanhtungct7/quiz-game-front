package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.CourseDto
import com.kma.quiz_game.data.remote.dto.LessonDto
import com.kma.quiz_game.data.remote.dto.UnitDto
import retrofit2.http.GET
import retrofit2.http.Path

/** Read-only course content, unauthenticated on the backend. */
interface ContentApi {
    @GET("courses")
    suspend fun getCourses(): List<CourseDto>

    @GET("courses/{courseId}")
    suspend fun getCourse(@Path("courseId") courseId: String): CourseDto

    @GET("courses/{courseId}/units")
    suspend fun getUnits(@Path("courseId") courseId: String): List<UnitDto>

    @GET("units/{unitId}/lessons")
    suspend fun getLessons(@Path("unitId") unitId: String): List<LessonDto>

    @GET("lessons/{lessonId}/challenges")
    suspend fun getChallenges(@Path("lessonId") lessonId: String): List<ChallengeDto>

    @GET("challenges/{challengeId}")
    suspend fun getChallenge(@Path("challengeId") challengeId: String): ChallengeDto
}
