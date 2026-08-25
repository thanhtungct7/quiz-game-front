package com.kma.quiz_game.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val imageSrc: String,
)

@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val courseId: Long,
    val order: Int,
)

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val unitId: Long,
    val order: Int,
)

enum class ChallengeType { SELECT, ASSIST }

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val type: ChallengeType,
    val question: String,
    val order: Int,
)

@Entity(tableName = "challenge_options")
data class ChallengeOptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val challengeId: Long,
    val text: String,
    val correct: Boolean,
    val imageSrc: String? = null,
    val audioSrc: String? = null,
)

@Entity(tableName = "challenge_progress", primaryKeys = ["userId", "challengeId"])
data class ChallengeProgressEntity(
    val userId: String,
    val challengeId: Long,
    val completed: Boolean = false,
)

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val userId: String,
    val userName: String = "User",
    val activeCourseId: Long? = null,
    val hearts: Int,
    val points: Int = 0,
    val isPro: Boolean = false,
)
