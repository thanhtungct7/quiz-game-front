package com.kma.quiz_game.ui.widget

import com.kma.quiz_game.data.local.StreakSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The widget's only real logic: what a stored snapshot means at a given moment. Pure, so every
 * branch is tested here rather than by installing a launcher and waiting for 20:00.
 */
class StreakWidgetStateTest {

    /** A wall-clock moment in the streak timezone, which is the only clock this decision uses. */
    private fun at(date: String, hour: Int): Long =
        LocalDate.parse(date).atTime(hour, 0).toInstant(ZoneOffset.ofHours(7)).toEpochMilli()

    private fun snapshot(
        signedIn: Boolean = true,
        dayStreak: Int = 0,
        lastActiveDate: String? = null,
        level: Int = 0,
        cefr: String = "",
        levelFraction: Float = 0f,
    ) = StreakSnapshot(
        signedIn = signedIn,
        dayStreak = dayStreak,
        bestDayStreak = dayStreak,
        level = level,
        cefr = cefr,
        levelFraction = levelFraction,
        lastActiveDate = lastActiveDate,
    )

    @Test
    fun `signed out asks for a sign-in and shows no streak`() {
        val ui = streakWidgetUi(snapshot(signedIn = false, dayStreak = 30), at("2026-09-18", 10))

        assertEquals(StreakMood.SIGNED_OUT, ui.mood)
        assertFalse(ui.showStreak)
    }

    @Test
    fun `no streak yet invites a first day`() {
        val ui = streakWidgetUi(snapshot(dayStreak = 0), at("2026-09-18", 10))

        assertEquals(StreakMood.FRESH_START, ui.mood)
        assertEquals("Bắt đầu chuỗi hôm nay!", ui.message)
        assertFalse(ui.showStreak)
    }

    @Test
    fun `studied today is done for the day`() {
        val ui = streakWidgetUi(
            snapshot(dayStreak = 85, lastActiveDate = "2026-09-18"),
            at("2026-09-18", 9),
        )

        assertEquals(StreakMood.DONE_TODAY, ui.mood)
        assertEquals("Hẹn gặp lại nhé!", ui.message)
        assertEquals(85, ui.streak)
        assertTrue(ui.showStreak)
    }

    @Test
    fun `morning with the day still open`() {
        val ui = streakWidgetUi(
            snapshot(dayStreak = 86, lastActiveDate = "2026-09-17"),
            at("2026-09-18", 7),
        )

        assertEquals(StreakMood.MORNING, ui.mood)
        assertEquals("Học sớm nhé?", ui.message)
        assertEquals(86, ui.streak)
    }

    @Test
    fun `afternoon names the streak it is protecting`() {
        val ui = streakWidgetUi(
            snapshot(dayStreak = 86, lastActiveDate = "2026-09-17"),
            at("2026-09-18", 15),
        )

        assertEquals(StreakMood.DAYTIME, ui.mood)
        assertEquals("Giữ chuỗi 86 ngày nhé!", ui.message)
    }

    @Test
    fun `noon and the reminder hour are the two boundaries`() {
        val stale = snapshot(dayStreak = 5, lastActiveDate = "2026-09-17")

        assertEquals(StreakMood.MORNING, streakWidgetUi(stale, at("2026-09-18", 11)).mood)
        assertEquals(StreakMood.DAYTIME, streakWidgetUi(stale, at("2026-09-18", 12)).mood)
        assertEquals(StreakMood.DAYTIME, streakWidgetUi(stale, at("2026-09-18", 19)).mood)
        assertEquals(StreakMood.AT_RISK, streakWidgetUi(stale, at("2026-09-18", 20)).mood)
    }

    @Test
    fun `after the evening reminder the card is urgent`() {
        val ui = streakWidgetUi(
            snapshot(dayStreak = 86, lastActiveDate = "2026-09-17"),
            at("2026-09-18", 22),
        )

        assertEquals(StreakMood.AT_RISK, ui.mood)
        assertEquals("Sắp mất chuỗi rồi!", ui.message)
        assertEquals(86, ui.streak)
    }

    /** The number outlives the streak it described. Showing 86 two days later would be a figure
     * the app has to take back the moment a lesson restarts the count at 1. */
    @Test
    fun `a streak whose day has passed is shown as gone`() {
        val ui = streakWidgetUi(
            snapshot(dayStreak = 86, lastActiveDate = "2026-09-15"),
            at("2026-09-18", 21),
        )

        assertEquals(StreakMood.FRESH_START, ui.mood)
        assertFalse(ui.showStreak)
    }

    /** A phone whose clock was moved backwards. An unknown gap is not evidence of a live streak,
     * and it must not be read as "studied tomorrow". */
    @Test
    fun `a last active day in the future does not keep the streak alive`() {
        val ui = streakWidgetUi(
            snapshot(dayStreak = 86, lastActiveDate = "2026-09-20"),
            at("2026-09-18", 10),
        )

        assertEquals(StreakMood.FRESH_START, ui.mood)
    }

    /** A fresh install signed into an account with a streak: the number is the server's, the day
     * is unknown, and nobody is told their streak is at risk on a day they may have finished. */
    @Test
    fun `an unknown last active day shows the streak without a warning`() {
        val ui = streakWidgetUi(
            snapshot(dayStreak = 86, lastActiveDate = null),
            at("2026-09-18", 22),
        )

        assertEquals(StreakMood.UNVERIFIED, ui.mood)
        assertEquals("Vào học tiếp nhé!", ui.message)
        assertEquals(86, ui.streak)
    }

    @Test
    fun `a stored date this build cannot parse reads as unknown`() {
        val ui = streakWidgetUi(
            snapshot(dayStreak = 86, lastActiveDate = "18-09-2026"),
            at("2026-09-18", 10),
        )

        assertEquals(StreakMood.UNVERIFIED, ui.mood)
    }

    @Test
    fun `level and band are carried through for the wide layout`() {
        val ui = streakWidgetUi(
            snapshot(dayStreak = 3, lastActiveDate = "2026-09-18", level = 34, cefr = "B1", levelFraction = 0.4f),
            at("2026-09-18", 10),
        )

        assertEquals(34, ui.level)
        assertEquals("B1", ui.cefr)
        assertEquals(0.4f, ui.levelFraction, 0.001f)
    }
}
