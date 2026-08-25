package com.kma.quiz_game.ui.screens.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.GameConstants
import com.kma.quiz_game.data.local.AppDatabase
import com.kma.quiz_game.data.local.entities.UserProgressEntity
import com.kma.quiz_game.data.repository.ChallengeRepository
import com.kma.quiz_game.data.repository.ReduceHeartResult
import com.kma.quiz_game.data.repository.UserProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val USER_ID = AppDatabase.LOCAL_USER_ID

class LessonViewModel(
    private val lessonId: Long,
    private val challengeRepository: ChallengeRepository,
    private val userProgressRepository: UserProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    /** Snapshot, per challenge id, of whether it was already completed before this session started. */
    private var practiceFlags: Map<Long, Boolean> = emptyMap()
    private var latestUserProgress: UserProgressEntity? = null

    init {
        viewModelScope.launch {
            val challenges = challengeRepository.loadChallenges(lessonId)
            practiceFlags = challenges.associate { c ->
                c.challenge.id to challengeRepository.wasAlreadyCompleted(USER_ID, c.challenge.id)
            }
            val progress = userProgressRepository.observe().first()
            latestUserProgress = progress
            _uiState.value = LessonUiState(
                isLoading = false,
                challenges = challenges,
                hearts = progress?.hearts ?: GameConstants.MAX_HEARTS,
                points = progress?.points ?: 0,
                isPro = progress?.isPro ?: false,
            )
        }
    }

    fun selectOption(optionId: Long) {
        _uiState.update { state ->
            if (state.answerStatus != AnswerStatus.NONE) return
            state.copy(selectedOptionId = optionId)
        }
    }

    fun onCheck() {
        val state = _uiState.value
        val challenge = state.currentChallenge ?: return
        val selectedId = state.selectedOptionId ?: return
        val selectedOption = challenge.options.firstOrNull { it.id == selectedId } ?: return
        val isPractice = practiceFlags[challenge.challenge.id] == true

        viewModelScope.launch {
            if (selectedOption.correct) {
                challengeRepository.markCompleted(USER_ID, challenge.challenge.id)
                val current = latestUserProgress
                if (current != null) {
                    userProgressRepository.addPoints(current)
                    if (isPractice) {
                        userProgressRepository.regenHeartFromPractice(current)
                    }
                }
                refreshUserProgress()
                _uiState.update { it.copy(answerStatus = AnswerStatus.CORRECT) }
            } else {
                val current = latestUserProgress
                var showHearts = false
                if (current != null) {
                    when (userProgressRepository.reduceHeart(current, isPractice)) {
                        ReduceHeartResult.OutOfHearts -> showHearts = true
                        else -> Unit
                    }
                }
                refreshUserProgress()
                _uiState.update {
                    it.copy(answerStatus = AnswerStatus.WRONG, showHeartsDialog = showHearts)
                }
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
                        )
                    }
                }

                AnswerStatus.WRONG -> state.copy(selectedOptionId = null, answerStatus = AnswerStatus.NONE)
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
        val progress = userProgressRepository.observe().first()
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
