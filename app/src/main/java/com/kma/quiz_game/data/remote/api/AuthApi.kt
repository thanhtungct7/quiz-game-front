package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.ForgotPasswordRequest
import com.kma.quiz_game.data.remote.dto.GoogleLoginRequest
import com.kma.quiz_game.data.remote.dto.LoginRequest
import com.kma.quiz_game.data.remote.dto.RefreshTokenRequest
import com.kma.quiz_game.data.remote.dto.RegisterRequest
import com.kma.quiz_game.data.remote.dto.ResetPasswordRequest
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

    /** Always 202, whether or not the address has an account -- the backend refuses to say
     * which, so the app must not either. */
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<Unit>

    /** 204 on success; 400 with a `detail` message when the token is unknown, used or expired. */
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<Unit>
}
