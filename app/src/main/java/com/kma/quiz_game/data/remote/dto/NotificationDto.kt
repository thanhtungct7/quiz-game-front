package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

/** The backend's `DeviceType`. Only Android exists on this side. */
const val DEVICE_TYPE_ANDROID = "ANDROID"

/**
 * No defaults on purpose: the app's JSON config does not encode default values, so a defaulted
 * field would silently vanish from the request body.
 */
@Serializable
data class DeviceRegistrationRequest(
    val fcmToken: String,
    val deviceType: String,
)

@Serializable
data class DeviceUnregistrationRequest(
    val fcmToken: String,
)
