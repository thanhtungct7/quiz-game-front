package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.local.dao.ChallengeDao
import com.kma.quiz_game.data.local.dao.ChallengeProgressDao
import com.kma.quiz_game.data.local.dao.CourseDao
import com.kma.quiz_game.data.local.dao.LessonDao
import com.kma.quiz_game.data.local.dao.UnitDao
import com.kma.quiz_game.data.local.entities.CourseEntity
import com.kma.quiz_game.data.local.entities.LessonEntity
import com.kma.quiz_game.data.local.entities.UnitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class LessonNode(val lesson: LessonEntity, val challengeIds: List<Long>)
data class UnitNode(val unit: UnitEntity, val lessons: List<LessonNode>)
data class CourseTree(val course: CourseEntity, val units: List<UnitNode>)

class LearnRepository(
    private val courseDao: CourseDao,
    private val unitDao: UnitDao,
    private val lessonDao: LessonDao,
    private val challengeDao: ChallengeDao,
) {
    fun observeCourses(): Flow<List<CourseEntity>> = courseDao.getAll()

    /** Content is static after seeding, so this is a one-shot load rather than a reactive Flow. */
    suspend fun loadCourseTree(courseId: Long): CourseTree? {
        val course = courseDao.getById(courseId).first() ?: return null
        val units = unitDao.getByCourse(courseId).first().map { unit ->
            val lessons = lessonDao.getByUnit(unit.id).first().map { lesson ->
                val challengeIds = challengeDao.getByLesson(lesson.id).first().map { it.id }
                LessonNode(lesson, challengeIds)
            }
            UnitNode(unit, lessons)
        }
        return CourseTree(course, units)
    }
}

class ChallengeProgressRepository(
    private val progressDao: ChallengeProgressDao,
) {
    fun observeCompletedChallengeIds(userId: String): Flow<List<Long>> =
        progressDao.getCompletedChallengeIds(userId)
}
