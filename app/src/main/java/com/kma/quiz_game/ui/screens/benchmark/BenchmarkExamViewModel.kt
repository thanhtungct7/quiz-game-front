package com.kma.quiz_game.ui.screens.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.GameRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Sitting the Bài Thi Sát Hạch for one chốt chặn năng lực.
 *
 * The server owns the paper. It draws the questions, takes each answer and grades it without
 * saying how it went, and only reveals the result when the paper is handed in -- so there is
 * nothing on this side a learner could tamper with to be let through. A wrong answer costs the
 * question and nothing else, and the whole paper is what passes or fails.
 *
 * Retaking is free and uncapped, and each sitting is a fresh draw. Starting one abandons any
 * sitting still open on the server.
 */
class BenchmarkExamViewModel(
    private val capLevel: Int,
    private val gameRepository: GameRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BenchmarkExamUiState(capLevel = capLevel))
    val uiState: StateFlow<BenchmarkExamUiState> = _uiState.asStateFlow()

    private var ticker: Job? = null

    init {
        draw()
    }

    /** Open a fresh sitting. Also the retake path, so it resets everything shown so far. */
    fun draw() {
        ticker?.cancel()
        viewModelScope.launch {
            _uiState.value = BenchmarkExamUiState(isLoading = true, capLevel = capLevel)
            gameRepository.startBenchmark(capLevel)
                .onSuccess { paper ->
                    val now = clock()
                    _uiState.value = BenchmarkExamUiState(
                        isLoading = false,
                        capLevel = capLevel,
                        attemptId = paper.attemptId,
                        questions = paper.questions,
                        deadlineMillis = now + BenchmarkExam.durationMillis(paper.startedAt, paper.expiresAt),
                        nowMillis = now,
                    )
                    startTicker()
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    fun selectOption(optionId: String) = _uiState.update { state ->
        if (state.isSubmitting) state else state.copy(selectedOptionId = optionId)
    }

    /** ORDER questions: append a word tile from the bank to the sentence being built. */
    fun placeOption(optionId: String) = _uiState.update { state ->
        if (state.isSubmitting || optionId in state.placedOptionIds) {
            state
        } else {
            state.copy(placedOptionIds = state.placedOptionIds + optionId)
        }
    }

    /** ORDER questions: take a word back out, leaving the rest in the order they were placed. */
    fun removePlacedOption(optionId: String) = _uiState.update { state ->
        if (state.isSubmitting) state else state.copy(placedOptionIds = state.placedOptionIds - optionId)
    }

    /**
     * Send the current answer and move on -- or, on the last question, hand the paper in.
     *
     * A failure to reach the server leaves the question on screen to send again: skipping it would
     * silently count it wrong. A 409 is different -- the server will not take an answer for this
     * question any more (already answered, or the sitting has closed), so the only way forward is
     * on.
     */
    fun submitAnswer() {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        val attemptId = state.attemptId ?: return
        if (!state.hasAnswer || state.isSubmitting || state.handedIn) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val isOrder = question.type == ChallengeTypeDto.ORDER
            val outcome = gameRepository.answerBenchmark(
                attemptId = attemptId,
                challengeId = question.id,
                selectedOptionId = if (isOrder) null else state.selectedOptionId,
                selectedOptionIds = if (isOrder) state.placedOptionIds else null,
            )
            val failure = outcome.exceptionOrNull()
            if (failure != null && !(failure is HttpException && failure.code() == 409)) {
                _uiState.update { it.copy(isSubmitting = false, errorMessage = failure.toUserMessage()) }
                return@launch
            }

            val answered = _uiState.updateAndGet {
                it.copy(
                    isSubmitting = false,
                    answeredIds = it.answeredIds + question.id,
                    selectedOptionId = null,
                    placedOptionIds = emptyList(),
                )
            }
            if (answered.isLastQuestion) {
                handIn()
            } else {
                _uiState.update { it.copy(currentIndex = it.currentIndex + 1) }
            }
        }
    }

    /** Hand the paper in again after the first attempt failed to reach the server. */
    fun retrySubmitResult() {
        val state = _uiState.value
        if (state.handedIn && state.result == null && !state.isSubmitting) handIn()
    }

    fun setExitDialogVisible(visible: Boolean) =
        _uiState.update { it.copy(showExitDialog = visible) }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    /**
     * Hand the paper in. Unanswered questions count as wrong on the server. Safe to call more than
     * once: a sitting that has been graded answers with the same grade.
     */
    private fun handIn() {
        val attemptId = _uiState.value.attemptId ?: return
        ticker?.cancel()
        _uiState.update { it.copy(handedIn = true, isSubmitting = true, submitErrorMessage = null) }
        viewModelScope.launch {
            gameRepository.submitBenchmark(attemptId)
                .onSuccess { graded ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            result = BenchmarkResult(
                                correctCount = graded.correctCount,
                                total = graded.total,
                                percent = graded.percent,
                                passPercent = graded.passPercent,
                                passed = graded.passed,
                            ),
                        )
                    }
                }
                .onFailure { cause ->
                    _uiState.update {
                        it.copy(isSubmitting = false, submitErrorMessage = cause.toUserMessage())
                    }
                }
        }
    }

    /** Tick the clock once a second, and hand the paper in the moment it runs out. */
    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.updateAndGet { it.copy(nowMillis = clock()) }
                if (state.isTimeUp && !state.handedIn) {
                    handIn()
                    return@launch
                }
                delay(1_000)
            }
        }
    }
}
