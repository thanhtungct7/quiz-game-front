package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AnswerCheckRequest(
    val selectedOptionId: String,
)

@Serializable
data class AnswerCheckResult(
    val challengeId: String,
    val selectedOptionId: String,
    val correct: Boolean,
    val correctOptionIds: List<String>,
    val explanation: String? = null,
)
