package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.api.UsersApi
import com.kma.quiz_game.data.remote.dto.UpdateProfileRequest
import com.kma.quiz_game.data.remote.dto.UserRead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The signed-in user's profile. Separate from [AuthRepository], which owns the session rather
 * than what the account looks like.
 *
 * Every call that changes the profile returns the server's new copy and publishes it on
 * [profile], so a rename or a new avatar reaches anything observing it without a refetch.
 */
class ProfileRepository(private val usersApi: UsersApi) {

    private val _profile = MutableStateFlow<UserRead?>(null)
    val profile: StateFlow<UserRead?> = _profile.asStateFlow()

    suspend fun refresh(): Result<UserRead> = runCatching {
        usersApi.getCurrentUser().also { _profile.value = it }
    }

    /**
     * [bio] must be `""` to clear it, not null: null fields are dropped from the request body,
     * which the backend reads as "leave this field alone".
     */
    suspend fun updateProfile(username: String?, bio: String?): Result<UserRead> = runCatching {
        usersApi.updateProfile(UpdateProfileRequest(username = username, bio = bio))
            .also { _profile.value = it }
    }

    suspend fun uploadAvatar(content: ByteArray, mimeType: String): Result<UserRead> = runCatching {
        // The part name has to be "file" -- it is the name of the endpoint's parameter.
        val part = MultipartBody.Part.createFormData(
            "file",
            "avatar.jpg",
            content.toRequestBody(mimeType.toMediaType()),
        )
        usersApi.uploadAvatar(part).also { _profile.value = it }
    }

    suspend fun removeAvatar(): Result<UserRead> = runCatching {
        usersApi.deleteAvatar().also { _profile.value = it }
    }

    fun clear() {
        _profile.value = null
    }
}
