package com.kma.quiz_game.ui.screens.lesson

import com.kma.quiz_game.data.GameConstants
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto

enum class AnswerStatus { NONE, CORRECT, WRONG }

data class LessonUiState(
    val isLoading: Boolean = true,
    val challenges: List<ChallengeDto> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOptionId: String? = null,
    /**
     * ORDER challenges only: the word tiles the learner has placed, in the order they placed
     * them. Everything not in here is still in the word bank.
     */
    val placedOptionIds: List<String> = emptyList(),
    val answerStatus: AnswerStatus = AnswerStatus.NONE,
    val isChecking: Boolean = false,
    val correctOptionIds: List<String> = emptyList(),
    /** Sent by `POST /challenges/{id}/check`; worth showing when the answer was wrong. */
    val explanation: String? = null,
    val hearts: Int = GameConstants.MAX_HEARTS,
    val points: Int = 0,
    val isPro: Boolean = false,
    val isComplete: Boolean = false,
    val earnedPoints: Int = 0,
    val showHeartsDialog: Boolean = false,
    val showExitDialog: Boolean = false,
    val errorMessage: String? = null,
) {
    val currentChallenge: ChallengeDto?
        get() = challenges.getOrNull(currentIndex)

    val progressPercent: Float
        get() = if (challenges.isEmpty()) 0f else currentIndex / challenges.size.toFloat()

    /**
     * An ORDER challenge is only answerable once every tile is placed: a partial sentence is not
     * a sentence, and the backend rejects it as wrong rather than as incomplete. Single-choice
     * challenges just need a selection.
     */
    val hasAnswer: Boolean
        get() {
            val challenge = currentChallenge ?: return false
            return if (challenge.type == ChallengeTypeDto.ORDER) {
                placedOptionIds.size == challenge.options.size && challenge.options.isNotEmpty()
            } else {
                selectedOptionId != null
            }
        }
}
