package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.api.ContentApi
import com.kma.quiz_game.data.remote.api.ProgressApi
import com.kma.quiz_game.data.remote.dto.LessonDto
import com.kma.quiz_game.data.remote.dto.LessonProgressDto
import com.kma.quiz_game.data.remote.dto.UnitDto

data class LessonNode(val lesson: LessonDto, val progress: LessonProgressDto?)
data class UnitNode(val unit: UnitDto, val lessons: List<LessonNode>)
data class CourseTree(val courseId: String, val courseTitle: String, val units: List<UnitNode>)

class LearnRepository(
    private val contentApi: ContentApi,
    private val progressApi: ProgressApi,
) {
    /** There is currently only ever one course seeded server-side, so this loads the first one. */
    suspend fun loadFirstCourseTree(): CourseTree? {
        val course = contentApi.getCourses().firstOrNull() ?: return null
        val units = contentApi.getUnits(course.id)
        val unitNodes = units.map { unit -> UnitNode(unit, loadLessons(unit.id)) }
        return CourseTree(course.id, course.title, unitNodes)
    }

    private suspend fun loadLessons(unitId: String): List<LessonNode> {
        val lessons = contentApi.getLessons(unitId)
        val progressByLesson = runCatching { progressApi.getUnitProgress(unitId) }
            .getOrNull()
            ?.lessons
            ?.associateBy { it.lessonId }
            .orEmpty()
        return lessons.map { lesson -> LessonNode(lesson, progressByLesson[lesson.id]) }
    }
}
