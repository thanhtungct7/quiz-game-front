package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.api.ProfileApi
import com.kma.quiz_game.data.remote.api.UsersApi
import com.kma.quiz_game.data.remote.dto.AchievementListDto
import com.kma.quiz_game.data.remote.dto.CombatBreakdownDto
import com.kma.quiz_game.data.remote.dto.PublicProfileDto
import com.kma.quiz_game.data.remote.dto.SelfProfileDto
import com.kma.quiz_game.data.remote.dto.UpdateProfileRequest
import com.kma.quiz_game.data.remote.dto.UserRead
import com.kma.quiz_game.data.widget.WidgetSync
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
    /** Where the home-screen widget's snapshot is written from. The card is the only request that
     * carries the streak, level and band the widget draws, so every refresh of it is also the
     * widget's chance to stop being stale. */
    private val widgetSync: WidgetSync,
) {

    private val _profile = MutableStateFlow<UserRead?>(null)
    val profile: StateFlow<UserRead?> = _profile.asStateFlow()

    private val _selfProfile = MutableStateFlow<SelfProfileDto?>(null)
    val selfProfile: StateFlow<SelfProfileDto?> = _selfProfile.asStateFlow()

    suspend fun refresh(): Result<UserRead> = runCatching {
        usersApi.getCurrentUser().also { _profile.value = it }
    }

    suspend fun refreshSelfProfile(): Result<SelfProfileDto> = runCatching {
        profileApi.getMyProfile().also {
            _selfProfile.value = it
            widgetSync.onCard(it)
        }
    }

    /**
     * The itemised build behind the four combat numbers, fetched on demand.
     *
     * Not cached and not fetched with the card: it is what a player sees after tapping a bar to
     * ask where a number came from, and it is the same request either way -- caching it would only
     * add a way to show a breakdown that no longer adds up to the total beside it.
     */
    suspend fun combatBreakdown(): Result<CombatBreakdownDto> =
        runCatching { profileApi.getMyCombatBreakdown() }

    /**
     * The whole achievement shelf.
     *
     * This request syncs server-side, which is why it is worth making even when the card already
     * carries three featured badges: an account that passed a threshold before the achievement
     * existed unlocks it here.
     */
    suspend fun achievements(): Result<AchievementListDto> =
        runCatching { profileApi.getMyAchievements() }

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
