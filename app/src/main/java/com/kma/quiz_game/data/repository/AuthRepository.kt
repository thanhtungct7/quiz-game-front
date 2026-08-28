package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.TokenStore
import com.kma.quiz_game.data.remote.api.AuthApi
import com.kma.quiz_game.data.remote.api.UsersApi
import com.kma.quiz_game.data.remote.dto.ForgotPasswordRequest
import com.kma.quiz_game.data.remote.dto.GoogleLoginRequest
import com.kma.quiz_game.data.remote.dto.LoginRequest
import com.kma.quiz_game.data.remote.dto.RefreshTokenRequest
import com.kma.quiz_game.data.remote.dto.RegisterRequest
import com.kma.quiz_game.data.remote.dto.ResetPasswordRequest
import com.kma.quiz_game.data.remote.dto.TokenResponse
import com.kma.quiz_game.data.remote.throwIfUnsuccessful
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val authApi: AuthApi,
    private val usersApi: UsersApi,
    private val tokenStore: TokenStore,
) {
    val currentUserId: Flow<String?> = tokenStore.currentUserId
    val isLoggedIn: Flow<Boolean> = currentUserId.map { it != null }

    suspend fun register(email: String, password: String, username: String?): Result<Unit> =
        runCatching {
            authApi.register(RegisterRequest(email, password, username))
            // Backend doesn't auto-login on register -- log in with the same credentials.
            establishSession { authApi.login(LoginRequest(email, password)) }
        }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        establishSession { authApi.login(LoginRequest(email, password)) }
    }

    /** [idToken] is a Google ID token obtained via Google Sign-In, audience-scoped to the
     * backend's configured web client id (see BuildConfig.GOOGLE_WEB_CLIENT_ID). */
    suspend fun loginWithGoogle(idToken: String): Result<Unit> = runCatching {
        establishSession { authApi.google(GoogleLoginRequest(idToken)) }
    }

    /**
     * Asks for a reset link. Succeeds even for an address with no account -- the backend answers
     * 202 either way so that this endpoint can't be used to probe which emails are registered,
     * and the UI has to keep that promise by showing the same confirmation.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit> = runCatching {
        authApi.forgotPassword(ForgotPasswordRequest(email)).throwIfUnsuccessful()
    }

    /**
     * Consumes the one-time token from the reset email. No session is established: the backend
     * revokes every refresh token as part of the reset, so the user signs in again by hand.
     */
    suspend fun resetPassword(token: String, newPassword: String): Result<Unit> = runCatching {
        authApi.resetPassword(ResetPasswordRequest(token, newPassword)).throwIfUnsuccessful()
    }

    private suspend fun establishSession(obtainTokens: suspend () -> TokenResponse) {
        val tokens = obtainTokens()
        // TokenResponse has no user id -- save tokens first so the authenticated call below
        // (routed through AuthInterceptor) can actually authenticate.
        tokenStore.saveTokens(tokens)
        val user = usersApi.getCurrentUser()
        tokenStore.saveSession(tokens, user.id)
    }

    suspend fun logout() {
        val refreshToken = tokenStore.refreshToken.first()
        if (refreshToken != null) {
            runCatching { authApi.logout(RefreshTokenRequest(refreshToken)) }
        }
        tokenStore.clear()
    }
}
