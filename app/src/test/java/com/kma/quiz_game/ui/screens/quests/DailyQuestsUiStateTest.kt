package com.kma.quiz_game.ui.screens.quests

import com.kma.quiz_game.data.remote.dto.ActivityChestDto
import com.kma.quiz_game.data.remote.dto.DailyQuestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DailyQuestsUiStateTest {

    private fun chest(reached: Boolean, claimed: Boolean) = ActivityChestDto(
        tier = "BRONZE",
        name = "Rương Đồng",
        milestone = 30,
        rewardGold = 15,
        rewardExp = 0,
        reached = reached,
        claimed = claimed,
    )

    private fun quest(progress: Int, target: Int, completed: Boolean, claimed: Boolean) = DailyQuestDto(
        id = "q",
        code = "Q",
        questType = "CORRECT_ANSWERS",
        difficulty = "EASY",
        title = "Trả lời đúng 15 câu",
        progress = progress,
        target = target,
        activityPoints = 20,
        rewardGold = 10,
        rewardExp = 20,
        completed = completed,
        claimed = claimed,
    )

    @Test
    fun `a chest is locked, then ready, then opened`() {
        assertEquals(ChestState.LOCKED, chest(reached = false, claimed = false).state())
        assertEquals(ChestState.READY, chest(reached = true, claimed = false).state())
        assertEquals(ChestState.OPENED, chest(reached = true, claimed = true).state())
    }

    @Test
    fun `only a finished, uncollected quest can be claimed`() {
        assertFalse(quest(3, 15, completed = false, claimed = false).claimable)
        assertTrue(quest(15, 15, completed = true, claimed = false).claimable)
        assertFalse(quest(15, 15, completed = true, claimed = true).claimable)
    }

    @Test
    fun `quest progress never draws past the end of its bar`() {
        assertEquals(0.2f, quest(3, 15, completed = false, claimed = false).fraction, 0.0001f)
        assertEquals(1f, quest(20, 15, completed = true, claimed = false).fraction, 0.0001f)
    }

    @Test
    fun `the countdown runs to local midnight and stops at zero`() {
        val resetsAt = "2026-09-19T17:00:00Z"

        assertEquals("5:12:30", countdown(resetsAt, Instant.parse("2026-09-19T11:47:30Z")))
        assertEquals("0:00:01", countdown(resetsAt, Instant.parse("2026-09-19T16:59:59Z")))
        assertEquals("0:00:00", countdown(resetsAt, Instant.parse("2026-09-19T17:00:05Z")))
        assertEquals("", countdown("not a time", Instant.parse("2026-09-19T11:47:30Z")))
    }

    @Test
    fun `the day has expired from the reset instant on`() {
        val resetsAt = "2026-09-19T17:00:00Z"

        assertFalse(hasExpired(resetsAt, Instant.parse("2026-09-19T16:59:59Z")))
        assertTrue(hasExpired(resetsAt, Instant.parse("2026-09-19T17:00:00Z")))
        assertFalse(hasExpired("garbage", Instant.parse("2026-09-19T17:00:00Z")))
    }

    @Test
    fun `difficulty and chest labels follow the server codes`() {
        assertEquals("Khó", difficultyLabel("HARD"))
        assertEquals("Vừa", difficultyLabel("MEDIUM"))
        assertEquals("Dễ", difficultyLabel("EASY"))
        assertEquals("Dễ", difficultyLabel("SOMETHING_NEW"))
        assertEquals("chests/gold.png", chestArt("GOLD"))
        assertEquals("chests/silver.png", chestArt("silver"))
        assertEquals("chests/bronze.png", chestArt("SOMETHING_NEW"))
        assertEquals("chest-60", chestKey(60))
    }
}
