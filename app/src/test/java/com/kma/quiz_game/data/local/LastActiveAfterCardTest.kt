package com.kma.quiz_game.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The widget's one inference: a profile card carries a streak but not the day it was earned, and
 * what this function decides is the difference between "Hẹn gặp lại nhé!" and a reminder.
 */
class LastActiveAfterCardTest {

    private val today = LocalDate.parse("2026-09-18")

    /** A fresh install, or the first card after a logout. The streak is the server's; whether
     * today counts towards it is not something this device can know yet. */
    @Test
    fun `nothing stored claims nothing`() {
        assertNull(lastActiveAfterCard(null, incomingStreak = 86, storedDate = null, today = today))
    }

    @Test
    fun `a streak the server has raised means the learner studied since this device looked`() {
        val result = lastActiveAfterCard(
            previousStreak = 85,
            incomingStreak = 86,
            storedDate = "2026-09-17",
            today = today,
        )

        assertEquals("2026-09-18", result)
    }

    /** Otherwise every refresh of the learn screen would read as a lesson. */
    @Test
    fun `an unchanged streak leaves the stored day alone`() {
        val result = lastActiveAfterCard(
            previousStreak = 86,
            incomingStreak = 86,
            storedDate = "2026-09-17",
            today = today,
        )

        assertEquals("2026-09-17", result)
    }

    /** A streak that broke and restarted elsewhere. Under-claiming is the safe direction: the
     * widget keeps asking for a lesson until this device settles one. */
    @Test
    fun `a lower streak claims nothing new`() {
        val result = lastActiveAfterCard(
            previousStreak = 86,
            incomingStreak = 1,
            storedDate = "2026-09-15",
            today = today,
        )

        assertEquals("2026-09-15", result)
    }
}
