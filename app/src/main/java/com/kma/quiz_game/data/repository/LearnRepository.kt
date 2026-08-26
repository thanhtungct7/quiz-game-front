package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.local.dao.CourseContentDao
import com.kma.quiz_game.data.local.entities.CachedCourseEntity
import com.kma.quiz_game.data.local.entities.CachedLessonEntity
import com.kma.quiz_game.data.local.entities.CachedUnitEntity
import com.kma.quiz_game.data.remote.api.ContentApi
import com.kma.quiz_game.data.remote.api.ProgressApi
import com.kma.quiz_game.data.remote.dto.CourseTreeDto
import com.kma.quiz_game.data.remote.dto.LessonProgressDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LessonNode(val id: String, val title: String, val challengeCount: Int)

data class UnitNode(
    val id: String,
    val title: String,
    val description: String,
    val lessons: List<LessonNode>,
)

data class CourseTree(val courseId: String, val courseTitle: String, val units: List<UnitNode>)

private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_NOT_FOUND = 404

/**
 * The learn path, cached on disk and revalidated in the background.
 *
 * The path is identical for every user and only changes when content is edited, so it is read
 * from Room -- the screen draws immediately on launch, offline included -- while [refreshTree]
 * checks the server with the stored ETag. An unchanged course answers 304 and nothing is
 * rewritten. Progress is per-user and deliberately not cached: [loadProgress] always asks the
 * server, in one request covering the whole course.
 */
class LearnRepository(
    private val contentApi: ContentApi,
    private val progressApi: ProgressApi,
    private val dao: CourseContentDao,
) {
    /** The cached path, or null until the first successful [refreshTree]. */
    fun observeTree(): Flow<CourseTree?> =
        combine(
            dao.observeCourse(),
            dao.observeUnits(),
            dao.observeLessons(),
        ) { course, units, lessons ->
            if (course == null) return@combine null
            val lessonsByUnit = lessons.groupBy { it.unitId }
            CourseTree(
                courseId = course.id,
                courseTitle = course.title,
                units = units.map { unit ->
                    UnitNode(
                        id = unit.id,
                        title = unit.title,
                        description = unit.description,
                        lessons = lessonsByUnit[unit.id].orEmpty().map {
                            LessonNode(it.id, it.title, it.challengeCount)
                        },
                    )
                },
            )
        }

    /**
     * Revalidate the cached path against the server, replacing it if it changed. Returns the
     * course id (null only when the server has no course at all).
     *
     * Failure is returned rather than thrown: an unreachable network is not an error when a
     * usable cached path is already on screen.
     */
    suspend fun refreshTree(): Result<String?> = runCatching {
        val cached = dao.getCourse()
        var response = cached?.let { contentApi.getCourseTree(it.id, it.etag) }
        if (response?.code() == HTTP_NOT_MODIFIED) return@runCatching cached!!.id

        // No cache yet, or the cached course is gone (a re-import gives the course a new id, and
        // without this the app would keep asking for the old one forever). Rediscover it: only
        // one course is ever seeded server-side, so the first one is the course.
        if (response == null || response.code() == HTTP_NOT_FOUND) {
            val courseId = contentApi.getCourses().firstOrNull()?.id ?: return@runCatching null
            response = contentApi.getCourseTree(courseId, null)
        }

        val tree = response.body() ?: error("Empty course tree response (${response.code()})")
        dao.replaceTree(
            course = CachedCourseEntity(
                id = tree.id,
                title = tree.title,
                imageSrc = tree.imageSrc,
                etag = response.headers()["ETag"],
                fetchedAtMillis = System.currentTimeMillis(),
            ),
            units = tree.toUnitEntities(),
            lessons = tree.toLessonEntities(),
        )
        tree.id
    }

    /** Lesson progress for the whole course, keyed by lesson id. Empty if the call fails. */
    suspend fun loadProgress(courseId: String): Map<String, LessonProgressDto> =
        runCatching { progressApi.getCourseProgress(courseId) }
            .getOrNull()
            ?.lessons
            ?.associateBy { it.lessonId }
            .orEmpty()
}

private fun CourseTreeDto.toUnitEntities(): List<CachedUnitEntity> = units.map { unit ->
    CachedUnitEntity(
        id = unit.id,
        courseId = id,
        title = unit.title,
        description = unit.description,
        orderIndex = unit.orderIndex,
    )
}

private fun CourseTreeDto.toLessonEntities(): List<CachedLessonEntity> = units.flatMap { unit ->
    unit.lessons.map { lesson ->
        CachedLessonEntity(
            id = lesson.id,
            unitId = unit.id,
            title = lesson.title,
            orderIndex = lesson.orderIndex,
            challengeCount = lesson.challengeCount,
        )
    }
}
