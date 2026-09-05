package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.api.ContentApi
import com.kma.quiz_game.data.remote.api.ProgressApi
import com.kma.quiz_game.data.remote.dto.AnswerCheckRequest
import com.kma.quiz_game.data.remote.dto.AnswerCheckResult
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.LessonProgressStatusDto

class ChallengeRepository(
    private val contentApi: ContentApi,
    private val progressApi: ProgressApi,
) {
    suspend fun loadChallenges(lessonId: String): List<ChallengeDto> =
        contentApi.getChallenges(lessonId)

    /** Server-authoritative: the backend never sends the correct option to the client, so
     * correctness can only be learned by asking it. */
    suspend fun checkAnswer(challengeId: String, selectedOptionId: String): AnswerCheckResult =
        progressApi.checkAnswer(
            challengeId,
            AnswerCheckRequest(selectedOptionId = selectedOptionId),
        )

    /**
     * Check an ORDER challenge: [placedOptionIds] is every word tile in the order the learner
     * arranged them. Grading the sequence is the whole point of the type -- all of its options
     * belong in the sentence, so there is no single option that could be "the" answer.
     */
    suspend fun checkOrderedAnswer(
        challengeId: String,
        placedOptionIds: List<String>,
    ): AnswerCheckResult =
        progressApi.checkAnswer(
            challengeId,
            AnswerCheckRequest(selectedOptionIds = placedOptionIds),
        )

    /** Whether this lesson was already fully completed before this session -- used to decide
     * "practice mode" hearts behavior. The backend doesn't expose per-challenge completion to
     * the client, so this is lesson-granularity rather than per-challenge like before. */
    suspend fun wasLessonAlreadyCompleted(lessonId: String): Boolean =
        runCatching { progressApi.getLessonProgress(lessonId) }
            .getOrNull()
            ?.status == LessonProgressStatusDto.COMPLETED
}
