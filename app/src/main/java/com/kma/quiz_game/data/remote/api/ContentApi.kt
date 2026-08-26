package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.CourseDto
import com.kma.quiz_game.data.remote.dto.CourseTreeDto
import com.kma.quiz_game.data.remote.dto.LessonDto
import com.kma.quiz_game.data.remote.dto.UnitDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/** Read-only course content, unauthenticated on the backend. */
interface ContentApi {
    @GET("courses")
    suspend fun getCourses(): List<CourseDto>

    /**
     * The whole learn path -- course, units and lessons -- in one call.
     *
     * Pass the ETag stored next to the cached copy as [ifNoneMatch]: an unchanged course comes
     * back as an empty 304, so a launch with a warm cache downloads nothing. Returns a [Response]
     * rather than the body so that 304 stays visible instead of surfacing as a parse failure.
     */
    @GET("courses/{courseId}/tree")
    suspend fun getCourseTree(
        @Path("courseId") courseId: String,
        @Header("If-None-Match") ifNoneMatch: String? = null,
    ): Response<CourseTreeDto>

    @GET("courses/{courseId}")
    suspend fun getCourse(@Path("courseId") courseId: String): CourseDto

    @GET("courses/{courseId}/units")
    suspend fun getUnits(@Path("courseId") courseId: String): List<UnitDto>

    @GET("units/{unitId}/lessons")
    suspend fun getLessons(@Path("unitId") unitId: String): List<LessonDto>

    /** Paged server-side; the default page covers a whole path lesson. */
    @GET("lessons/{lessonId}/challenges")
    suspend fun getChallenges(
        @Path("lessonId") lessonId: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): List<ChallengeDto>

    @GET("challenges/{challengeId}")
    suspend fun getChallenge(@Path("challengeId") challengeId: String): ChallengeDto
}
