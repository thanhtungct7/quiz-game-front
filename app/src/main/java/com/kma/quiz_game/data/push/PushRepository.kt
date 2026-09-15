package com.kma.quiz_game.data.push

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.kma.quiz_game.data.remote.api.NotificationsApi
import com.kma.quiz_game.data.remote.dto.DEVICE_TYPE_ANDROID
import com.kma.quiz_game.data.remote.dto.DeviceRegistrationRequest
import com.kma.quiz_game.data.remote.dto.DeviceUnregistrationRequest
import com.kma.quiz_game.data.remote.throwIfUnsuccessful
import com.kma.quiz_game.data.repository.AuthRepository
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine

private val Context.pushDataStore by preferencesDataStore(name = "push")

/**
 * This install's push registration with the backend.
 *
 * Every call is best effort and does nothing in a build without a Firebase project (no
 * google-services.json): push is an extra, and nothing else in the app waits on it.
 */
class PushRepository(
    private val context: Context,
    private val notificationsApi: NotificationsApi,
    private val authRepository: AuthRepository,
) {
    private object Keys {
        val ASKED_FOR_PERMISSION = booleanPreferencesKey("asked_for_notification_permission")
    }

    /** False when the build carried no google-services.json, so Firebase never initialised. */
    val isAvailable: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    val hasAskedForPermission: Flow<Boolean> =
        context.pushDataStore.data.map { it[Keys.ASKED_FOR_PERMISSION] == true }

    suspend fun markPermissionAsked() {
        context.pushDataStore.edit { it[Keys.ASKED_FOR_PERMISSION] = true }
    }

    /** Send this install's current token to the server. Called each time the signed-in app starts. */
    suspend fun syncToken(): Result<Unit> = runCatching {
        if (isAvailable) register(FirebaseMessaging.getInstance().token.await())
    }

    /** A token Firebase just rotated, which only matters while an account is signed in. */
    suspend fun registerIfSignedIn(token: String): Result<Unit> = runCatching {
        if (isAvailable && authRepository.isLoggedIn.first()) register(token)
    }

    /**
     * Stop the server pushing to this install. Called on sign-out, while the session still works.
     *
     * The token itself is kept rather than deleted: deleting makes Firebase mint a new one at once,
     * and [registerIfSignedIn] could hand it to the account that is still signing out. The next
     * sign-in registers the same token to whoever it belongs to by then.
     */
    suspend fun unregister(): Result<Unit> = runCatching {
        if (!isAvailable) return@runCatching
        val token = FirebaseMessaging.getInstance().token.await()
        notificationsApi.unregisterDevice(DeviceUnregistrationRequest(token)).throwIfUnsuccessful()
    }

    private suspend fun register(token: String) {
        notificationsApi
            .registerDevice(DeviceRegistrationRequest(fcmToken = token, deviceType = DEVICE_TYPE_ANDROID))
            .throwIfUnsuccessful()
    }
}

/** Play services' callback-style [Task] as a suspend call. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> continuation.resumeWithException(error)
            task.isCanceled -> continuation.cancel()
            else -> continuation.resume(task.result)
        }
    }
}
