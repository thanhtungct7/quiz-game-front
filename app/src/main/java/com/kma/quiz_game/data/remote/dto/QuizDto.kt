package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * One submitted answer, in whichever of the two shapes the challenge takes.
 *
 * SELECT/ASSIST challenges send [selectedOptionId]. An ORDER challenge ("ghép câu") sends
 * [selectedOptionIds] -- every word tile, in the order the learner laid them down. Exactly one of
 * the two is ever set; the other is dropped from the payload by `explicitNulls = false`.
 */
@Serializable
data class AnswerCheckRequest(
    val selectedOptionId: String? = null,
    val selectedOptionIds: List<String>? = null,
)

/**
 * The graded answer.
 *
 * [correctOptionIds] is a set for single-choice challenges but a *sequence* for ORDER ones -- the
 * tiles in the order that spells the right sentence, so a wrong answer can be shown its solution.
 */
@Serializable
data class AnswerCheckResult(
    val challengeId: String,
    val selectedOptionId: String? = null,
    val selectedOptionIds: List<String> = emptyList(),
    val correct: Boolean,
    val correctOptionIds: List<String>,
    val explanation: String? = null,
)
