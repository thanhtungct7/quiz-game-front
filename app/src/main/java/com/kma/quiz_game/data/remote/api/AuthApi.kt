package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.GoogleLoginRequest
import com.kma.quiz_game.data.remote.dto.LoginRequest
import com.kma.quiz_game.data.remote.dto.RefreshTokenRequest
import com.kma.quiz_game.data.remote.dto.RegisterRequest
import com.kma.quiz_game.data.remote.dto.TokenResponse
import com.kma.quiz_game.data.remote.dto.UserRead
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Unauthenticated endpoints -- served from a Retrofit instance with no AuthInterceptor, so
 * calling [refresh] here never recurses into another refresh attempt. */
interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): UserRead

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshTokenRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshTokenRequest): Response<Unit>

    @POST("auth/google")
    suspend fun google(@Body body: GoogleLoginRequest): TokenResponse
}
