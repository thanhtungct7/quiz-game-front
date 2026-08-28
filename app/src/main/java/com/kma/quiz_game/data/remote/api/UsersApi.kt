package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.UpdateProfileRequest
import com.kma.quiz_game.data.remote.dto.UserRead
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part

interface UsersApi {
    @GET("users/me")
    suspend fun getCurrentUser(): UserRead

    @PATCH("users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): UserRead

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): UserRead

    @DELETE("users/me/avatar")
    suspend fun deleteAvatar(): UserRead
}
