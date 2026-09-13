package com.kma.quiz_game.ui.screens.benchmark

import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeOptionDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the exam the client still owns: the button gate, the clock, and what a cap is
 * called. The pass mark is not here any more -- the server grades the paper and says whether it
 * passed, so there is no arithmetic on this side to get wrong.
 */
class BenchmarkExamUiStateTest {

    private fun question(id: String, type: ChallengeTypeDto = ChallengeTypeDto.SELECT, options: Int = 4) =
        ChallengeDto(
            id = id,
            lessonId = "lesson-1",
            type = type,
            question = "Choose the best word",
            difficulty = "EASY",
            orderIndex = 1,
            options = (1..options).map { ChallengeOptionDto(id = "$id-o$it", text = "w$it", orderIndex = it) },
        )

    private fun state(total: Int, currentIndex: Int = 0, capLevel: Int = 25) = BenchmarkExamUiState(
        isLoading = false,
        capLevel = capLevel,
        questions = (1..total).map { question("c$it") },
        currentIndex = currentIndex,
    )

    // --- The button gate ---------------------------------------------------

    @Test
    fun `a select question is answerable once an option is picked`() {
        val base = state(total = 5)

        assertFalse(base.hasAnswer)
        assertTrue(base.copy(selectedOptionId = "c1-o2").hasAnswer)
    }

    @Test
    fun `an order question needs every tile placed`() {
        val base = BenchmarkExamUiState(
            isLoading = false,
            questions = listOf(question("c1", ChallengeTypeDto.ORDER, options = 4)),
        )

        assertFalse(base.copy(placedOptionIds = listOf("c1-o1", "c1-o2", "c1-o3")).hasAnswer)
        assertTrue(base.copy(placedOptionIds = listOf("c1-o1", "c1-o2", "c1-o3", "c1-o4")).hasAnswer)
    }

    @Test
    fun `an order question ignores a stray single selection`() {
        val base = BenchmarkExamUiState(
            isLoading = false,
            questions = listOf(question("c1", ChallengeTypeDto.ORDER, options = 3)),
        )

        assertFalse(base.copy(selectedOptionId = "c1-o1").hasAnswer)
    }

    // --- Position in the paper --------------------------------------------

    @Test
    fun `the last question is the one that hands the paper in`() {
        assertFalse(state(total = 30, currentIndex = 28).isLastQuestion)
        assertTrue(state(total = 30, currentIndex = 29).isLastQuestion)
    }

    @Test
    fun `progress runs from nothing to nearly full across the paper`() {
        assertEquals(0f, state(total = 30, currentIndex = 0).progressPercent, 0.001f)
        assertEquals(0.5f, state(total = 30, currentIndex = 15).progressPercent, 0.001f)
    }

    // --- The clock ---------------------------------------------------------

    @Test
    fun `the clock counts whole seconds up and stops at zero`() {
        val base = state(total = 5).copy(deadlineMillis = 10_000)

        assertEquals(10L, base.copy(nowMillis = 0).remainingSeconds)
        assertEquals(1L, base.copy(nowMillis = 9_001).remainingSeconds)
        assertFalse(base.copy(nowMillis = 9_999).isTimeUp)
        assertTrue(base.copy(nowMillis = 10_000).isTimeUp)
        assertEquals(0L, base.copy(nowMillis = 60_000).remainingSeconds)
    }

    @Test
    fun `no deadline means no clock and never time up`() {
        assertNull(state(total = 5).remainingSeconds)
        assertFalse(state(total = 5).isTimeUp)
    }

    @Test
    fun `the clock reads as minutes and seconds`() {
        assertEquals("45:00", BenchmarkExam.formatClock(45 * 60))
        assertEquals("00:59", BenchmarkExam.formatClock(59))
        assertEquals("00:00", BenchmarkExam.formatClock(-3))
    }

    /** Only the gap between the server's instants matters, whatever offset it is written in. */
    @Test
    fun `the sitting length is read off the server's two instants`() {
        assertEquals(
            45 * 60 * 1000L,
            BenchmarkExam.durationMillis("2026-09-13T12:00:00.123456Z", "2026-09-13T12:45:00.123456Z"),
        )
        assertEquals(
            45 * 60 * 1000L,
            BenchmarkExam.durationMillis("2026-09-13T19:00:00+07:00", "2026-09-13T12:45:00Z"),
        )
    }

    @Test
    fun `an unreadable timestamp falls back to the default length`() {
        assertEquals(BenchmarkExam.FALLBACK_DURATION_MILLIS, BenchmarkExam.durationMillis("nope", "2026-09-13T12:45:00Z"))
        assertEquals(
            BenchmarkExam.FALLBACK_DURATION_MILLIS,
            BenchmarkExam.durationMillis("2026-09-13T12:45:00Z", "2026-09-13T12:00:00Z"),
        )
    }

    // --- What the learner is sitting for ----------------------------------

    @Test
    fun `each cap names the band it opens`() {
        assertEquals("A2", BenchmarkExam.bandUnlockedBy(10))
        assertEquals("B1", BenchmarkExam.bandUnlockedBy(25))
        assertEquals("B2", BenchmarkExam.bandUnlockedBy(50))
        assertEquals("C1", BenchmarkExam.bandUnlockedBy(75))
        assertEquals("C2", BenchmarkExam.bandUnlockedBy(92))
    }

    /** Every cap the backend knows about must have a band to show, or the banner goes blank on
     * the one screen that has to explain itself. */
    @Test
    fun `every level cap has a band`() {
        BenchmarkExam.LEVEL_CAPS.forEach { cap ->
            assertEquals("cap $cap", true, BenchmarkExam.bandUnlockedBy(cap) != null)
        }
    }

    @Test
    fun `a level that is not a cap names no band`() {
        assertNull(BenchmarkExam.bandUnlockedBy(11))
        assertNull(BenchmarkExam.bandUnlockedBy(100))
    }
}
