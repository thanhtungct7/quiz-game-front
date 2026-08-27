package com.kma.quiz_game.data.remote

import com.kma.quiz_game.data.remote.api.UsersApi
import kotlinx.coroutines.flow.first

/**
 * Hands out an access token that is known to be usable *right now*.
 *
 * A duo socket authenticates once, at the handshake, via the `token` query parameter -- it cannot
 * re-authenticate later the way an HTTP request can. With a 30-minute access token and a match
 * that may be opened long after login, connecting with whatever is in [TokenStore] would sometimes
 * be rejected outright.
 *
 * So make one cheap authenticated call first: [AuthInterceptor] transparently refreshes on a 401
 * and writes the new pair back to the store, which means reading the token *after* that call gives
 * a token the server has just accepted. Reusing that path is also why there is no second refresh
 * implementation to keep in sync.
 */
class FreshTokenProvider(
    private val usersApi: UsersApi,
    private val tokenStore: TokenStore,
) {
    suspend operator fun invoke(): String? {
        runCatching { usersApi.getCurrentUser() }
        return tokenStore.accessToken.first()
    }
}
