package com.kma.quiz_game.data.remote

import com.kma.quiz_game.data.remote.dto.DEVICE_TYPE_ANDROID
import com.kma.quiz_game.data.remote.dto.DeviceRegistrationRequest
import com.kma.quiz_game.data.remote.dto.DeviceUnregistrationRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The request bodies of `POST /notifications/register-device` and `/unregister-device`, encoded
 * with the app's serializer settings. The backend's `DeviceRegistrationRequest` rejects a body
 * without `fcm_token`, so a key that encodes under another name fails every registration.
 */
class NotificationWireContractTest {

    /** Mirrors `NetworkModule.json`, rebuilt here so the test pulls in no Android classes. */
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `registering sends the token and the device type in snake case`() {
        val body = json.encodeToString(
            DeviceRegistrationRequest.serializer(),
            DeviceRegistrationRequest(fcmToken = "token-1", deviceType = DEVICE_TYPE_ANDROID),
        )

        assertEquals("""{"fcm_token":"token-1","device_type":"ANDROID"}""", body)
    }

    @Test
    fun `unregistering sends only the token`() {
        val body = json.encodeToString(
            DeviceUnregistrationRequest.serializer(),
            DeviceUnregistrationRequest(fcmToken = "token-1"),
        )

        assertEquals("""{"fcm_token":"token-1"}""", body)
    }
}
