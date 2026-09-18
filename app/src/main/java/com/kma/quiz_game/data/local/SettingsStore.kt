package com.kma.quiz_game.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Its own file rather than a key in `auth_tokens`: logging out clears that store wholesale, and a
// player who signs out should not have their choice of palette reset with their session.
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Which palette the app draws in, and whether the phone gets to decide. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /**
     * Resolved against what the phone is set to right now.
     *
     * [SYSTEM] is the default because it is the only one that keeps following the phone after the
     * player stops thinking about it -- the other two are a deliberate override.
     */
    fun isDark(systemInDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDark
        LIGHT -> false
        DARK -> true
    }

    val label: String
        get() = when (this) {
            SYSTEM -> "Theo hệ thống"
            LIGHT -> "Sáng"
            DARK -> "Tối"
        }

    companion object {
        /** An unknown or absent stored value reads as [SYSTEM] rather than throwing: the store
         * outlives any one build, and a renamed entry must not make the app unlaunchable. */
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Preferences that belong to the install rather than to the account.
 *
 * Same shape as `TokenStore`: one DataStore, one key object, a `Flow` per value. Nothing here is
 * ever sent to the server -- the theme is a property of this phone, and syncing it would mean a
 * player on two devices could not have a dark tablet and a light phone.
 */
class SettingsStore(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LEARN_INTRO_SEEN = booleanPreferencesKey("learn_intro_seen")
        val CONVERSATION_VOICE_ON = booleanPreferencesKey("conversation_voice_on")
    }

    val themeMode: Flow<ThemeMode> =
        context.settingsDataStore.data.map { ThemeMode.fromStored(it[Keys.THEME_MODE]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }

    /**
     * Whether the learner has already seen the one-off explanation of the header strip.
     *
     * Absent reads as false, so an account that upgrades into this build is shown it once. Kept
     * here rather than on the server because it describes this install: the same person on a
     * second phone has not seen it *there*, and that is when it is worth showing.
     */
    val hasSeenLearnIntro: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.LEARN_INTRO_SEEN] == true }

    suspend fun markLearnIntroSeen() {
        context.settingsDataStore.edit { prefs -> prefs[Keys.LEARN_INTRO_SEEN] = true }
    }

    /**
     * Whether the AI reads its lines aloud in a practice conversation. On unless the learner has
     * muted it -- somewhere quiet, say -- and it stays muted until they turn it back on.
     */
    val conversationVoiceOn: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.CONVERSATION_VOICE_ON] != false }

    suspend fun setConversationVoiceOn(on: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.CONVERSATION_VOICE_ON] = on }
    }
}
