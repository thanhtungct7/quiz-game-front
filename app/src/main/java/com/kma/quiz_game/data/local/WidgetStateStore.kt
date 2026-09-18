package com.kma.quiz_game.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

// Its own store rather than a few more keys in `settings`: this one is read from the launcher's
// update pass, where the app may not be running at all, and it is cleared on logout -- neither of
// which is true of the theme.
private val Context.widgetDataStore by preferencesDataStore(name = "widget_state")

/**
 * Everything the home-screen widget draws, as of the last time the app knew.
 *
 * The widget never calls the network: the launcher can ask for a redraw with no session up, no
 * connectivity and the app not running, and a widget that went to the server would either block
 * that pass or show a spinner on the home screen. So the app writes here whenever it learns
 * something -- see `WidgetSync` -- and the widget only ever reads.
 *
 * [lastActiveDate] is the one field with no server equivalent in `GET /profile/me`: it is inferred
 * locally from settlements seen on this device, which is why it can be null on a fresh install
 * that has a streak. [StreakWidgetUi] treats that as "unknown" rather than guessing.
 */
data class StreakSnapshot(
    val signedIn: Boolean = false,
    val dayStreak: Int = 0,
    val bestDayStreak: Int = 0,
    val level: Int = 0,
    val cefr: String = "",
    val levelFraction: Float = 0f,
    /** ISO `yyyy-MM-dd` in UTC+7, or null if this install has never recorded a day of study. */
    val lastActiveDate: String? = null,
    val updatedAtMillis: Long = 0L,
)

/**
 * Which day the card's streak says the learner was last active on -- the one field in a snapshot
 * that is inferred rather than read, because `GET /profile/me` does not carry `last_active_date`.
 *
 * A streak the server reports as *higher* than the one stored here can only mean the learner
 * studied since this device last looked -- on another phone, or in a session this install never
 * saw settle -- so today is recorded. Anything else leaves the stored day alone:
 *
 * - **No stored streak at all** ([previousStreak] null) is a fresh install or the first card after
 *   a logout. The number is the server's and this device has no idea whether today counts towards
 *   it, so nothing is claimed; the widget shows that as [StreakWidgetUi]'s unverified state rather
 *   than telling someone they are done for the day.
 * - **An equal or lower streak** says nothing new. Writing today anyway would turn every profile
 *   refresh into "studied today", and the widget would never ask anyone to come back.
 *
 * The one case it under-claims is a streak restarted at 1 on another device today: the widget goes
 * on showing "start a streak" until this device settles something. Under-claiming is the safer
 * error -- the opposite one tells a learner their streak is safe when it is about to break.
 */
internal fun lastActiveAfterCard(
    previousStreak: Int?,
    incomingStreak: Int,
    storedDate: String?,
    today: LocalDate,
): String? = when {
    previousStreak == null -> storedDate
    incomingStreak > previousStreak -> today.toString()
    else -> storedDate
}

class WidgetStateStore(private val context: Context) {

    private object Keys {
        val SIGNED_IN = booleanPreferencesKey("signed_in")
        val DAY_STREAK = intPreferencesKey("day_streak")
        val BEST_DAY_STREAK = intPreferencesKey("best_day_streak")
        val LEVEL = intPreferencesKey("level")
        val CEFR = stringPreferencesKey("cefr")
        val LEVEL_FRACTION = floatPreferencesKey("level_fraction")
        val LAST_ACTIVE_DATE = stringPreferencesKey("last_active_date")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    val snapshot: Flow<StreakSnapshot> = context.widgetDataStore.data.map { it.toSnapshot() }

    /** A single read, for the widget's update pass -- which is not a place to observe a flow. */
    suspend fun current(): StreakSnapshot = snapshot.first()

    /** The profile card, as `GET /profile/me` just returned it. See [lastActiveAfterCard] for the
     * one part of it that is inferred rather than read. */
    suspend fun writeCard(
        dayStreak: Int,
        bestDayStreak: Int,
        level: Int,
        cefr: String,
        levelFraction: Float,
        today: LocalDate,
        nowMillis: Long,
    ) {
        context.widgetDataStore.edit { prefs ->
            val lastActive = lastActiveAfterCard(
                previousStreak = prefs[Keys.DAY_STREAK],
                incomingStreak = dayStreak,
                storedDate = prefs[Keys.LAST_ACTIVE_DATE],
                today = today,
            )
            prefs[Keys.SIGNED_IN] = true
            prefs[Keys.DAY_STREAK] = dayStreak
            prefs[Keys.BEST_DAY_STREAK] = bestDayStreak
            prefs[Keys.LEVEL] = level
            prefs[Keys.CEFR] = cefr
            prefs[Keys.LEVEL_FRACTION] = levelFraction
            prefs[Keys.UPDATED_AT] = nowMillis
            if (lastActive != null) prefs[Keys.LAST_ACTIVE_DATE] = lastActive
        }
    }

    /**
     * A settlement just paid out, which is the only first-hand evidence this device gets that the
     * learner studied today -- so unlike [writeCard] it always records the day.
     */
    suspend fun writeStreak(dayStreak: Int, bestDayStreak: Int, today: LocalDate, nowMillis: Long) {
        context.widgetDataStore.edit { prefs ->
            prefs[Keys.SIGNED_IN] = true
            prefs[Keys.DAY_STREAK] = dayStreak
            prefs[Keys.BEST_DAY_STREAK] = maxOf(bestDayStreak, dayStreak)
            prefs[Keys.LAST_ACTIVE_DATE] = today.toString()
            prefs[Keys.UPDATED_AT] = nowMillis
        }
    }

    /** Logout. Everything goes, not just the session flag: a streak left on the home screen is
     * the previous account's, and it would keep being shown to whoever signs in next. */
    suspend fun clear() {
        context.widgetDataStore.edit { it.clear() }
    }

    private fun Preferences.toSnapshot() = StreakSnapshot(
        signedIn = this[Keys.SIGNED_IN] == true,
        dayStreak = this[Keys.DAY_STREAK] ?: 0,
        bestDayStreak = this[Keys.BEST_DAY_STREAK] ?: 0,
        level = this[Keys.LEVEL] ?: 0,
        cefr = this[Keys.CEFR].orEmpty(),
        levelFraction = this[Keys.LEVEL_FRACTION] ?: 0f,
        lastActiveDate = this[Keys.LAST_ACTIVE_DATE],
        updatedAtMillis = this[Keys.UPDATED_AT] ?: 0L,
    )
}
