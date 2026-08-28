package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "bearer",
    val expiresIn: Int,
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
)

@Serializable
data class GoogleLoginRequest(
    val idToken: String,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

/** [token] is the one-time token from the emailed `quizgame://reset-password?token=...` link. */
@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String,
)

@Serializable
data class UserRead(
    val id: String,
    val email: String,
    val username: String? = null,
    val bio: String? = null,
    /** Either an absolute googleusercontent link (Google sign-in) or a path on this API
     * (uploaded avatar). Run it through `toAbsoluteMediaUrl()` before loading it. */
    val avatarUrl: String? = null,
    /** Whether [avatarUrl] is an upload this app can delete. A Google account's picture
     * comes with the account and is not removable here. */
    val hasUploadedAvatar: Boolean = false,
    val isActive: Boolean,
    val createdAt: String,
)

/** PATCH body. A field left `null` is omitted by `explicitNulls = false`, which is exactly
 * the "leave it alone" the backend expects -- so clearing the bio needs `""`, not null. */
@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val bio: String? = null,
)
