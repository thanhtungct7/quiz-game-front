package com.kma.quiz_game.ui.screens.lesson

import com.kma.quiz_game.data.GameConstants
import com.kma.quiz_game.data.repository.ChallengeWithOptions

enum class AnswerStatus { NONE, CORRECT, WRONG }

data class LessonUiState(
    val isLoading: Boolean = true,
    val challenges: List<ChallengeWithOptions> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOptionId: Long? = null,
    val answerStatus: AnswerStatus = AnswerStatus.NONE,
    val hearts: Int = GameConstants.MAX_HEARTS,
    val points: Int = 0,
    val isPro: Boolean = false,
    val isComplete: Boolean = false,
    val earnedPoints: Int = 0,
    val showHeartsDialog: Boolean = false,
    val showExitDialog: Boolean = false,
) {
    val currentChallenge: ChallengeWithOptions?
        get() = challenges.getOrNull(currentIndex)

    val progressPercent: Float
        get() = if (challenges.isEmpty()) 0f else currentIndex / challenges.size.toFloat()
}
