package com.kma.quiz_game.data.widget

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Which calendar day a moment belongs to, for streak purposes.
 *
 * The offset is not the phone's: the server counts days in UTC+7 (`services/game/streak.py`,
 * `STREAK_TZ`), and a widget that used the device timezone would tell a learner abroad that their
 * streak is safe on a day the server has already closed -- or the reverse, which is worse.
 *
 * Dates are stored and compared as ISO `yyyy-MM-dd` strings, the same shape the backend's
 * `last_active_date` column has, so a snapshot written by one build is still readable by the next.
 */
object StreakClock {

    val ZONE: ZoneOffset = ZoneOffset.ofHours(7)

    fun today(nowMillis: Long): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(ZONE).toLocalDate()

    /** The hour of the learner's day, 0..23 -- what decides which line the widget shows. */
    fun hourOfDay(nowMillis: Long): Int =
        Instant.ofEpochMilli(nowMillis).atZone(ZONE).hour

    /** Null for a missing or malformed stored date rather than throwing: the store outlives any
     * one build, and a bad entry must not make the widget uninflatable. */
    fun parseOrNull(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
