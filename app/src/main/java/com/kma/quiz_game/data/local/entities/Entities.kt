package com.kma.quiz_game.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local-only gamification state (hearts/points/pro), keyed by the real backend user id. The
 * backend has no concept of this -- learning progress and challenge content come from the API
 * instead (see data/remote and data/repository).
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

/**
 * Cached copy of the learn path from `GET courses/{id}/tree`.
 *
 * The path is the same for every user and changes only when an admin edits content, so the app
 * renders it straight from these tables on launch and revalidates in the background. Only the
 * path's shape is cached -- challenges are still fetched when a lesson is opened, and progress is
 * per-user and always comes from the network.
 */
@Entity(tableName = "cached_course")
data class CachedCourseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val imageSrc: String,
    /** ETag of the payload these rows were built from; replayed as `If-None-Match`. */
    val etag: String?,
    val fetchedAtMillis: Long,
)

@Entity(tableName = "cached_unit", indices = [Index("courseId")])
data class CachedUnitEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val title: String,
    val description: String,
    val orderIndex: Int,
)

@Entity(tableName = "cached_lesson", indices = [Index("unitId")])
data class CachedLessonEntity(
    @PrimaryKey val id: String,
    val unitId: String,
    val title: String,
    val orderIndex: Int,
    val challengeCount: Int,
)
