package com.kma.quiz_game.ui.screens.profile

import com.kma.quiz_game.data.remote.dto.LearningStatsDto
import com.kma.quiz_game.data.remote.dto.PublicProfileDto
import com.kma.quiz_game.data.remote.dto.PvpStatsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seed-then-replace rule that lets the sheet open with content instead of a spinner.
 *
 * Getting this wrong is not a crash, it is a modal that flashes empty for a moment on every tap,
 * which is exactly the impression a sub-100ms endpoint was meant to avoid.
 */
class PublicProfileUiStateTest {

    private fun card(id: String, rating: Int = 1000, level: Int = 1) = PublicProfileDto(
        id = id,
        username = "Player",
        level = level,
        pvp = PvpStatsDto(rating = rating, tier = "BRONZE"),
        learning = LearningStatsDto(),
    )

    @Test
    fun `the seed is drawn while the full card is still in flight`() {
        val state = PublicProfileUiState(seed = card("them", rating = 1420), isLoading = true)

        assertEquals(1420, state.visible?.pvp?.rating)
    }

    @Test
    fun `the full card replaces the seed once it lands`() {
        val state = PublicProfileUiState(
            seed = card("them", rating = 1420, level = 1),
            profile = card("them", rating = 1420, level = 34),
            isLoading = false,
        )

        assertEquals(34, state.visible?.level)
    }

    @Test
    fun `nothing is drawn when there is no seed and no card yet`() {
        assertNull(PublicProfileUiState(isLoading = true).visible)
    }

    @Test
    fun `a failure with nothing on screen shows the error`() {
        val state = PublicProfileUiState(isLoading = false, errorMessage = "Mất mạng")

        assertTrue(state.showsError)
    }

    /**
     * A failure *over a seed* is not worth an error banner: the player is looking at a real name,
     * avatar and rating, and the card is merely incomplete rather than broken.
     */
    @Test
    fun `a failure over a seed keeps drawing the seed`() {
        val state = PublicProfileUiState(
            seed = card("them", rating = 1420),
            isLoading = false,
            errorMessage = "Mất mạng",
        )

        assertFalse(state.showsError)
        assertEquals(1420, state.visible?.pvp?.rating)
    }
}
