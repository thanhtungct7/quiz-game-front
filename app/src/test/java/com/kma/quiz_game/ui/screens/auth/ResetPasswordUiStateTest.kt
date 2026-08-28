package com.kma.quiz_game.ui.screens.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The submit rules mirror the backend's ResetPasswordRequest (token >= 32 chars, password 8-128).
 * Getting them wrong here costs a round trip and a 422 the user cannot act on.
 */
class ResetPasswordUiStateTest {
    private val validToken = "t".repeat(43) // what secrets.token_urlsafe(32) produces

    private fun state(
        token: String = validToken,
        password: String = "brand-new-password",
        confirmPassword: String = "brand-new-password",
        isSubmitting: Boolean = false,
    ) = ResetPasswordUiState(
        token = token,
        password = password,
        confirmPassword = confirmPassword,
        isSubmitting = isSubmitting,
    )

    @Test
    fun `a matching password of the right length can be submitted`() {
        assertTrue(state().canSubmit)
    }

    @Test
    fun `a password shorter than eight characters cannot be submitted`() {
        assertFalse(state(password = "short12", confirmPassword = "short12").canSubmit)
    }

    @Test
    fun `a password longer than the backend allows cannot be submitted`() {
        val tooLong = "p".repeat(129)
        assertFalse(state(password = tooLong, confirmPassword = tooLong).canSubmit)
    }

    @Test
    fun `mismatched passwords cannot be submitted and are flagged`() {
        val mismatched = state(confirmPassword = "something-else")

        assertFalse(mismatched.canSubmit)
        assertTrue(mismatched.passwordsMismatch)
    }

    @Test
    fun `an empty confirmation is not yet a mismatch`() {
        assertFalse(state(confirmPassword = "").passwordsMismatch)
    }

    @Test
    fun `a token too short for the backend cannot be submitted`() {
        assertFalse(state(token = "abc").canSubmit)
    }

    @Test
    fun `a missing token asks the user to paste one`() {
        assertTrue(state(token = "").needsToken)
        assertFalse(state().needsToken)
    }

    @Test
    fun `a request already in flight cannot be submitted again`() {
        assertFalse(state(isSubmitting = true).canSubmit)
    }
}
