package com.kma.quiz_game.ui.screens.benchmark

import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import java.time.OffsetDateTime

/**
 * The Bài Thi Sát Hạch standing between a learner and the next CEFR band.
 *
 * The paper, the pass mark and the grade all live on the server now -- `POST
 * game/benchmark-exam/attempts` draws it, every answer is graded there without the verdict coming
 * back, and handing in returns the result. What is left here is presentation: which band a cap
 * opens, and how long a sitting runs.
 */
object BenchmarkExam {

    /** The chốt chặn năng lực, mirroring `cefr.LEVEL_CAPS` on the backend. Each sits one level
     * below a band floor, so clearing one is what opens A2 / B1 / B2 / C1 / C2. */
    val LEVEL_CAPS = listOf(10, 25, 50, 75, 92)

    /**
     * How long the paper is and the bar it is graded against, for the banner that offers the exam
     * to read out before a sitting exists. Display only: mirrors `benchmark_exam.QUESTION_COUNT` and
     * `PASS_PERCENT` on the backend, and a sitting's own figures come back from the server.
     */
    const val QUESTION_COUNT = 30
    const val PASS_PERCENT = 80

    /** Used only if the server's timestamps cannot be read; mirrors `benchmark_exam.TIME_LIMIT`. */
    const val FALLBACK_DURATION_MILLIS = 45 * 60 * 1000L

    /** The band that clearing [capLevel] opens, for the learner to read. Null for a level that
     * is not a cap at all. */
    fun bandUnlockedBy(capLevel: Int): String? = when (capLevel) {
        10 -> "A2"
        25 -> "B1"
        50 -> "B2"
        75 -> "C1"
        92 -> "C2"
        else -> null
    }

    /**
     * How long the sitting runs, from the two server instants that bound it.
     *
     * Only the gap is used: the local deadline is "now plus this", so a device clock that is minutes
     * off still counts down the right amount of time. The server keeps a short grace past the
     * deadline for the round trip of a last-second answer.
     */
    fun durationMillis(startedAt: String, expiresAt: String): Long =
        runCatching {
            OffsetDateTime.parse(expiresAt).toInstant().toEpochMilli() -
                OffsetDateTime.parse(startedAt).toInstant().toEpochMilli()
        }.getOrNull()?.takeIf { it > 0 } ?: FALLBACK_DURATION_MILLIS

    /** `mm:ss`, for the clock in the header. */
    fun formatClock(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }
}

/** The grade, exactly as the server returned it. */
data class BenchmarkResult(
    val correctCount: Int,
    val total: Int,
    val percent: Int,
    val passPercent: Int,
    val passed: Boolean,
)

data class BenchmarkExamUiState(
    val isLoading: Boolean = true,
    /** The cap being sat for. */
    val capLevel: Int = 0,
    val attemptId: String? = null,
    val questions: List<ChallengeDto> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOptionId: String? = null,
    /** ORDER questions only: the word tiles laid down so far, in order. */
    val placedOptionIds: List<String> = emptyList(),
    /** Questions the server has taken an answer for. Whether they were right is not known here. */
    val answeredIds: Set<String> = emptySet(),
    /** The local instant the sitting closes, and the instant the clock was last ticked at. */
    val deadlineMillis: Long? = null,
    val nowMillis: Long = 0,
    /** True while an answer is being sent, or while the paper is being handed in. */
    val isSubmitting: Boolean = false,
    /** The paper has been handed in -- the last answer was taken, or time ran out. */
    val handedIn: Boolean = false,
    /** Why handing in failed. The paper is still on the server; retrying the hand-in is enough. */
    val submitErrorMessage: String? = null,
    val result: BenchmarkResult? = null,
    val showExitDialog: Boolean = false,
    val errorMessage: String? = null,
) {
    val band: String? get() = BenchmarkExam.bandUnlockedBy(capLevel)

    val currentQuestion: ChallengeDto? get() = questions.getOrNull(currentIndex)

    val progressPercent: Float
        get() = if (questions.isEmpty()) 0f else currentIndex / questions.size.toFloat()

    val remainingSeconds: Long?
        get() = deadlineMillis?.let { ((it - nowMillis + 999) / 1000).coerceAtLeast(0) }

    val isTimeUp: Boolean get() = remainingSeconds == 0L

    /**
     * An exam shows no per-question feedback, so this is the only thing gating the button: an
     * ORDER question needs every tile placed, anything else needs a selection.
     */
    val hasAnswer: Boolean
        get() {
            val question = currentQuestion ?: return false
            return if (question.type == ChallengeTypeDto.ORDER) {
                question.options.isNotEmpty() && placedOptionIds.size == question.options.size
            } else {
                selectedOptionId != null
            }
        }

    val isLastQuestion: Boolean get() = currentIndex >= questions.size - 1
}
