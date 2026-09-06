package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.api.ProfileApi
import com.kma.quiz_game.data.remote.api.UsersApi
import com.kma.quiz_game.data.remote.dto.PublicProfileDto
import com.kma.quiz_game.data.remote.dto.SelfProfileDto
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
 * Two views of the same person are cached here, and they are not redundant:
 *
 * - [profile] is the **account** -- name, bio, avatar, email. It is what the edit screen writes
 *   to, and every mutation below returns the server's new copy and republishes it, so a rename
 *   or a new avatar reaches anything observing it without a refetch.
 * - [selfProfile] is the **aggregated card** -- that identity plus level, CEFR band, PvP record
 *   and study totals, assembled server-side in one round trip.
 *
 * They live together rather than in two repositories because the card embeds the identity: a
 * rename that refreshed only [profile] would leave the RPG card showing the old name until the
 * next cold start. Every mutation therefore refreshes both.
 */
class ProfileRepository(
    private val usersApi: UsersApi,
    private val profileApi: ProfileApi,
) {

    private val _profile = MutableStateFlow<UserRead?>(null)
    val profile: StateFlow<UserRead?> = _profile.asStateFlow()

    private val _selfProfile = MutableStateFlow<SelfProfileDto?>(null)
    val selfProfile: StateFlow<SelfProfileDto?> = _selfProfile.asStateFlow()

    suspend fun refresh(): Result<UserRead> = runCatching {
        usersApi.getCurrentUser().also { _profile.value = it }
    }

    suspend fun refreshSelfProfile(): Result<SelfProfileDto> = runCatching {
        profileApi.getMyProfile().also { _selfProfile.value = it }
    }

    /**
     * Another player's card, fetched fresh every time and deliberately not cached.
     *
     * It is opened from a leaderboard row or a lobby, where the point is what that player looks
     * like *now*; a cache would show a stale rating to the one person most likely to check it
     * again right after a match against them.
     */
    suspend fun publicProfile(userId: String): Result<PublicProfileDto> =
        runCatching { profileApi.getPublicProfile(userId) }

    /**
     * [bio] must be `""` to clear it, not null: null fields are dropped from the request body,
     * which the backend reads as "leave this field alone".
     */
    suspend fun updateProfile(username: String?, bio: String?): Result<UserRead> = runCatching {
        usersApi.updateProfile(UpdateProfileRequest(username = username, bio = bio))
            .also { _profile.value = it; refreshSelfProfile() }
    }

    suspend fun uploadAvatar(content: ByteArray, mimeType: String): Result<UserRead> = runCatching {
        // The part name has to be "file" -- it is the name of the endpoint's parameter.
        val part = MultipartBody.Part.createFormData(
            "file",
            "avatar.jpg",
            content.toRequestBody(mimeType.toMediaType()),
        )
        usersApi.uploadAvatar(part).also { _profile.value = it; refreshSelfProfile() }
    }

    suspend fun removeAvatar(): Result<UserRead> = runCatching {
        usersApi.deleteAvatar().also { _profile.value = it; refreshSelfProfile() }
    }

    fun clear() {
        _profile.value = null
        _selfProfile.value = null
    }
}
