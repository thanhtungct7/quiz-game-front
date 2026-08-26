package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CourseDto(
    val id: String,
    val title: String,
    val imageSrc: String,
)

@Serializable
data class UnitDto(
    val id: String,
    val courseId: String,
    val title: String,
    val description: String,
    val orderIndex: Int,
)

@Serializable
data class LessonDto(
    val id: String,
    val unitId: String,
    val title: String,
    val orderIndex: Int,
)

/**
 * The whole learn path in one payload, served by `GET courses/{id}/tree` with an ETag.
 *
 * Replaces walking `/courses` -> `/courses/{id}/units` -> `/units/{id}/lessons`, which cost one
 * request per unit on every app launch. Bank lessons (the imported question overflow) are not
 * part of it.
 */
@Serializable
data class CourseTreeDto(
    val id: String,
    val title: String,
    val imageSrc: String,
    val units: List<UnitTreeDto>,
)

@Serializable
data class UnitTreeDto(
    val id: String,
    val title: String,
    val description: String,
    val orderIndex: Int,
    val lessons: List<LessonTreeDto>,
)

@Serializable
data class LessonTreeDto(
    val id: String,
    val title: String,
    val orderIndex: Int,
    val challengeCount: Int,
)

@Serializable
data class PassageDto(
    val id: String,
    val content: String,
    val levelGrade: String? = null,
)

/** Learner-facing option -- the backend never sends which option is correct. */
@Serializable
data class ChallengeOptionDto(
    val id: String,
    val text: String,
    val orderIndex: Int,
    val imageSrc: String? = null,
    val audioSrc: String? = null,
)

enum class ChallengeTypeDto { SELECT, ASSIST }

/** Learner-facing challenge -- correctness is only ever revealed via ProgressApi.checkAnswer. */
@Serializable
data class ChallengeDto(
    val id: String,
    val lessonId: String,
    val type: ChallengeTypeDto,
    val question: String,
    val difficulty: String,
    val topicId: String? = null,
    val orderIndex: Int,
    val passage: PassageDto? = null,
    val options: List<ChallengeOptionDto>,
)
