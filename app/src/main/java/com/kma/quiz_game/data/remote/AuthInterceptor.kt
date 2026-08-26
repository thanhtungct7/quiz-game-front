package com.kma.quiz_game.data.remote

import com.kma.quiz_game.data.remote.api.AuthApi
import com.kma.quiz_game.data.remote.dto.RefreshTokenRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the current access token to every request and, on a 401, transparently refreshes it
 * via [AuthApi.refresh] and retries once. [authApiProvider] must point at an unauthenticated
 * Retrofit instance so the refresh call itself never recurses back into this interceptor.
 *
 * Runs on OkHttp's background dispatcher (never the UI thread), so blocking on the token Flows
 * here is the standard, accepted pattern -- not a main-thread-blocking concern.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val authApiProvider: () -> AuthApi,
) : Interceptor {
    private val refreshMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val accessToken = runBlocking { tokenStore.accessToken.first() }
        val authorizedRequest = if (accessToken != null) {
            originalRequest.newBuilder().header("Authorization", "Bearer $accessToken").build()
        } else {
            originalRequest
        }

        val response = chain.proceed(authorizedRequest)
        if (response.code != 401 || accessToken == null) return response
        response.close()

        val newAccessToken = runBlocking { refreshAccessToken(accessToken) }
            ?: return response // refresh failed/no refresh token -- caller sees the original 401

        val retryRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $newAccessToken")
            .build()
        return chain.proceed(retryRequest)
    }

    /** Single-flight refresh: concurrent 401s all wait on the same mutex, and every waiter after
     * the first sees the token already changed and reuses it instead of refreshing again. */
    private suspend fun refreshAccessToken(staleAccessToken: String): String? =
        refreshMutex.withLock {
            val current = tokenStore.accessToken.first()
            if (current != staleAccessToken) return@withLock current

            val refreshToken = tokenStore.refreshToken.first() ?: return@withLock null
            try {
                val tokens = authApiProvider().refresh(RefreshTokenRequest(refreshToken))
                tokenStore.saveTokens(tokens)
                tokens.accessToken
            } catch (e: Exception) {
                tokenStore.clear()
                null
            }
        }
}
