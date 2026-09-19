package com.kma.quiz_game.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushRouteTest {

    @Test
    fun `the routes the backend sends are recognised`() {
        // The strings in duo-game-back/app/services/notification/messages.py.
        assertEquals(PushRoute.LEARN, PushRoute.fromWire("learn"))
        assertEquals(PushRoute.LEADERBOARD, PushRoute.fromWire("leaderboard"))
        assertEquals(PushRoute.QUESTS, PushRoute.fromWire("quests"))
    }

    @Test
    fun `a missing or unknown route opens nothing in particular`() {
        assertNull(PushRoute.fromWire(null))
        assertNull(PushRoute.fromWire(""))
        assertNull(PushRoute.fromWire("inbox"))
    }

    @Test
    fun `the extra key matches the data field the backend fills`() {
        assertEquals("route", PushRoute.EXTRA_KEY)
    }

    @Test
    fun `channel ids match the backend`() {
        assertEquals("streak", NotificationChannels.STREAK)
        assertEquals("season", NotificationChannels.SEASON)
        assertEquals("quest", NotificationChannels.QUEST)
    }
}
