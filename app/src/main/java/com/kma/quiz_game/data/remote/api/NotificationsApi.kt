package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.DeviceRegistrationRequest
import com.kma.quiz_game.data.remote.dto.DeviceUnregistrationRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Authenticated: where the server may send this install's push notifications. */
interface NotificationsApi {
    /** 204. Safe to repeat: the server upserts by token. */
    @POST("notifications/register-device")
    suspend fun registerDevice(@Body body: DeviceRegistrationRequest): Response<Unit>

    /** 204, also for a token the server does not know. */
    @POST("notifications/unregister-device")
    suspend fun unregisterDevice(@Body body: DeviceUnregistrationRequest): Response<Unit>
}
