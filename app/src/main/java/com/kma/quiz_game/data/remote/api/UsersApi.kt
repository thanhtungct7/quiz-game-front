package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.UserRead
import retrofit2.http.GET

interface UsersApi {
    @GET("users/me")
    suspend fun getCurrentUser(): UserRead
}
