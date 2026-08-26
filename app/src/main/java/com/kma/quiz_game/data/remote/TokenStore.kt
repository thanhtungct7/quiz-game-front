package com.kma.quiz_game.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kma.quiz_game.data.remote.dto.TokenResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth_tokens")

/** Persists the current session's tokens + user id across app restarts. */
class TokenStore(private val context: Context) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
    }

    val accessToken: Flow<String?> = context.authDataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.authDataStore.data.map { it[Keys.REFRESH_TOKEN] }
    val currentUserId: Flow<String?> = context.authDataStore.data.map { it[Keys.USER_ID] }

    /** Full login/register session: tokens plus the user they belong to. */
    suspend fun saveSession(tokens: TokenResponse, userId: String) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = tokens.accessToken
            prefs[Keys.REFRESH_TOKEN] = tokens.refreshToken
            prefs[Keys.USER_ID] = userId
        }
    }

    /** Called by [AuthInterceptor] after a silent refresh -- leaves the user id untouched. */
    suspend fun saveTokens(tokens: TokenResponse) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = tokens.accessToken
            prefs[Keys.REFRESH_TOKEN] = tokens.refreshToken
        }
    }

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
    }
}
