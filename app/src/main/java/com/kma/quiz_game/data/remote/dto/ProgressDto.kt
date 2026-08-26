package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

enum class LessonProgressStatusDto { NOT_STARTED, IN_PROGRESS, COMPLETED }

@Serializable
data class LessonProgressDto(
    val lessonId: String,
    val status: LessonProgressStatusDto,
    val correctChallengeCount: Int,
    val totalChallengeCount: Int,
    val completedAt: String? = null,
)

@Serializable
data class UnitProgressDto(
    val unitId: String,
    val lessons: List<LessonProgressDto>,
)
