package com.kma.quiz_game.ui.screens.auth

/**
 * A loose check, only to catch a mistyped address before it costs a round trip and a 422: the
 * backend's EmailStr is the real judge. Not `android.util.Patterns`, which is null in unit tests.
 */
internal fun looksLikeEmail(value: String): Boolean = EMAIL_SHAPE.matches(value.trim())

internal const val INVALID_EMAIL_MESSAGE = "Email chưa đúng định dạng."

private val EMAIL_SHAPE = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
