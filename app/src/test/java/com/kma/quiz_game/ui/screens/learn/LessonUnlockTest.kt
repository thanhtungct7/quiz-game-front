package com.kma.quiz_game.ui.screens.learn

import com.kma.quiz_game.data.remote.dto.LessonProgressDto
import com.kma.quiz_game.data.remote.dto.LessonProgressStatusDto
import com.kma.quiz_game.data.repository.CourseTree
import com.kma.quiz_game.data.repository.LessonNode
import com.kma.quiz_game.data.repository.UnitNode
import com.kma.quiz_game.ui.components.LessonNodeStatus
import com.kma.quiz_game.ui.components.LessonNodeStatus.ACTIVE
import com.kma.quiz_game.ui.components.LessonNodeStatus.COMPLETE
import com.kma.quiz_game.ui.components.LessonNodeStatus.LOCKED
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonUnlockTest {
    /** Two units of three lessons: a1 a2 a3 | b1 b2 b3. */
    private val tree = CourseTree(
        courseId = "course",
        courseTitle = "Course",
        units = listOf("a", "b").map { unit ->
            UnitNode(
                id = unit,
                title = unit,
                description = "",
                lessons = (1..3).map { LessonNode(id = "$unit$it", title = "$unit$it", challengeCount = 10) },
            )
        },
    )

    private fun statuses(vararg completed: String): List<LessonNodeStatus> {
        val progress = completed.associateWith {
            LessonProgressDto(it, LessonProgressStatusDto.COMPLETED, 10, 10)
        }
        return toUnitUi(tree, progress, emptyMap()).flatMap { unit -> unit.lessons.map { it.status } }
    }

    @Test
    fun `a new learner can only open the first lesson`() {
        assertEquals(listOf(ACTIVE, LOCKED, LOCKED, LOCKED, LOCKED, LOCKED), statuses())
    }

    @Test
    fun `unbroken progress opens exactly the next lesson, across a unit boundary too`() {
        assertEquals(
            listOf(COMPLETE, COMPLETE, COMPLETE, ACTIVE, LOCKED, LOCKED),
            statuses("a1", "a2", "a3"),
        )
    }

    @Test
    fun `lessons woven in behind the learner open without locking what they already finished`() {
        // a2 and a3 arrived after the learner had cleared a1 and the whole of unit b.
        assertEquals(
            listOf(COMPLETE, ACTIVE, LOCKED, COMPLETE, COMPLETE, COMPLETE),
            statuses("a1", "b1", "b2", "b3"),
        )
    }

    @Test
    fun `a finished course is complete throughout`() {
        assertEquals(List(6) { COMPLETE }, statuses("a1", "a2", "a3", "b1", "b2", "b3"))
    }
}
