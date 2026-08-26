package com.kma.quiz_game.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local-only gamification state (hearts/points/pro), keyed by the real backend user id. The
 * backend has no concept of this -- course/unit/lesson/challenge content and learning progress
 * all come from the API instead (see data/remote and data/repository).
 */
@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val userId: String,
    val userName: String = "User",
    val activeCourseId: String? = null,
    val hearts: Int,
    val points: Int = 0,
    val isPro: Boolean = false,
)
