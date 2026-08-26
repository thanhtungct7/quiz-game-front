package com.kma.quiz_game.ui.screens.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.GameConstants
import com.kma.quiz_game.data.local.entities.UserProgressEntity
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.AuthRepository
import com.kma.quiz_game.data.repository.ChallengeRepository
import com.kma.quiz_game.data.repository.ReduceHeartResult
import com.kma.quiz_game.data.repository.UserProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LessonViewModel(
    private val lessonId: String,
    private val challengeRepository: ChallengeRepository,
    private val userProgressRepository: UserProgressRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private lateinit var userId: String

    /** Whole-lesson granularity: the backend doesn't expose per-challenge completion to the
     * client, so every challenge in an already-completed lesson is treated as "practice". */
    private var isPractice: Boolean = false
    private var latestUserProgress: UserProgressEntity? = null

    init {
        viewModelScope.launch {
            userId = authRepository.currentUserId.filterNotNull().first()
            val challenges = challengeRepository.loadChallenges(lessonId)
            isPractice = challengeRepository.wasLessonAlreadyCompleted(lessonId)
            val progress = userProgressRepository.getOrCreate(userId)
            latestUserProgress = progress
            _uiState.value = LessonUiState(
                isLoading = false,
                challenges = challenges,
                hearts = progress.hearts,
                points = progress.points,
                isPro = progress.isPro,
            )
        }
    }

    fun selectOption(optionId: String) {
        _uiState.update { state ->
            if (state.answerStatus != AnswerStatus.NONE) return
            state.copy(selectedOptionId = optionId)
        }
    }

    fun onCheck() {
        val state = _uiState.value
        val challenge = state.currentChallenge ?: return
        val selectedId = state.selectedOptionId ?: return
        if (state.isChecking) return

        _uiState.update { it.copy(isChecking = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { challengeRepository.checkAnswer(challenge.id, selectedId) }
            result.onSuccess { checkResult ->
                if (checkResult.correct) {
                    val current = latestUserProgress
                    if (current != null) {
                        userProgressRepository.addPoints(current)
                        if (isPractice) userProgressRepository.regenHeartFromPractice(current)
                    }
                    refreshUserProgress()
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            answerStatus = AnswerStatus.CORRECT,
                            correctOptionIds = checkResult.correctOptionIds,
                        )
                    }
                } else {
                    val current = latestUserProgress
                    var showHearts = false
                    if (current != null) {
                        if (userProgressRepository.reduceHeart(current, isPractice) == ReduceHeartResult.OutOfHearts) {
                            showHearts = true
                        }
                    }
                    refreshUserProgress()
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            answerStatus = AnswerStatus.WRONG,
                            showHeartsDialog = showHearts,
                            correctOptionIds = checkResult.correctOptionIds,
                        )
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isChecking = false, errorMessage = e.toUserMessage()) }
            }
        }
    }

    fun onContinue() {
        _uiState.update { state ->
            when (state.answerStatus) {
                AnswerStatus.CORRECT -> {
                    val nextIndex = state.currentIndex + 1
                    if (nextIndex >= state.challenges.size) {
                        state.copy(
                            isComplete = true,
                            earnedPoints = state.challenges.size * GameConstants.POINTS_PER_CORRECT,
                        )
                    } else {
                        state.copy(
                            currentIndex = nextIndex,
                            selectedOptionId = null,
                            answerStatus = AnswerStatus.NONE,
                            correctOptionIds = emptyList(),
                        )
                    }
                }

                AnswerStatus.WRONG -> state.copy(
                    selectedOptionId = null,
                    answerStatus = AnswerStatus.NONE,
                    correctOptionIds = emptyList(),
                )

                AnswerStatus.NONE -> state
            }
        }
    }

    fun dismissHeartsDialog() {
        _uiState.update { it.copy(showHeartsDialog = false) }
    }

    fun refillHeartsWithPoints() {
        val current = latestUserProgress ?: return
        viewModelScope.launch {
            userProgressRepository.refillHeartsWithPoints(current)
            refreshUserProgress()
            _uiState.update { it.copy(showHeartsDialog = false) }
        }
    }

    fun setExitDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showExitDialog = visible) }
    }

    private suspend fun refreshUserProgress() {
        val progress = userProgressRepository.observe(userId).first()
        latestUserProgress = progress
        _uiState.update {
            it.copy(
                hearts = progress?.hearts ?: it.hearts,
                points = progress?.points ?: it.points,
                isPro = progress?.isPro ?: it.isPro,
            )
        }
    }
}

private inline fun MutableStateFlow<LessonUiState>.update(block: (LessonUiState) -> LessonUiState) {
    value = block(value)
}
