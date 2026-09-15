package com.kma.quiz_game.ui.screens.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailFormatTest {
    @Test
    fun `an ordinary address passes`() {
        assertTrue(looksLikeEmail("uireview@example.com"))
        assertTrue(looksLikeEmail("  ct070358@actvn.edu.vn "))
    }

    @Test
    fun `an address missing its at sign or domain is caught before the request`() {
        assertFalse(looksLikeEmail("uireview"))
        assertFalse(looksLikeEmail("uireview@"))
        assertFalse(looksLikeEmail("uireview@example"))
        assertFalse(looksLikeEmail("ui review@example.com"))
    }
}
