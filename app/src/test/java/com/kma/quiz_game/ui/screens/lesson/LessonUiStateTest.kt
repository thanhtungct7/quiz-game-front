package com.kma.quiz_game.ui.screens.lesson

import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeOptionDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Check button's gate. It differs by challenge type: a single-choice challenge needs one
 * selection, but an ORDER ("ghép câu") one needs the whole sentence -- a half-built sentence sent
 * to the backend comes back wrong, not incomplete, and costs a heart for nothing.
 */
class LessonUiStateTest {
    private fun challenge(type: ChallengeTypeDto, optionCount: Int) = ChallengeDto(
        id = "c0",
        lessonId = "lesson-1",
        type = type,
        question = "Sắp xếp các từ",
        difficulty = "EASY",
        orderIndex = 1,
        options = (1..optionCount).map {
            ChallengeOptionDto(id = "o$it", text = "w$it", orderIndex = it)
        },
    )

    private fun state(
        type: ChallengeTypeDto,
        optionCount: Int = 4,
        selectedOptionId: String? = null,
        placedOptionIds: List<String> = emptyList(),
    ) = LessonUiState(
        isLoading = false,
        challenges = listOf(challenge(type, optionCount)),
        selectedOptionId = selectedOptionId,
        placedOptionIds = placedOptionIds,
    )

    @Test
    fun `a select challenge is answerable as soon as an option is picked`() {
        assertTrue(state(ChallengeTypeDto.SELECT, selectedOptionId = "o1").hasAnswer)
    }

    @Test
    fun `a select challenge with nothing picked is not answerable`() {
        assertFalse(state(ChallengeTypeDto.SELECT).hasAnswer)
    }

    @Test
    fun `an order challenge is answerable only once every word is placed`() {
        assertTrue(
            state(
                ChallengeTypeDto.ORDER,
                placedOptionIds = listOf("o1", "o2", "o3", "o4"),
            ).hasAnswer,
        )
    }

    @Test
    fun `an order challenge with a half-built sentence is not answerable`() {
        assertFalse(state(ChallengeTypeDto.ORDER, placedOptionIds = listOf("o1", "o2")).hasAnswer)
    }

    @Test
    fun `tapping one word does not make an order challenge answerable`() {
        // The bug this type used to have: it was rendered as multiple choice, so a single tap
        // counted as an answer -- and the backend said yes to whichever word was tapped.
        assertFalse(state(ChallengeTypeDto.ORDER, selectedOptionId = "o1").hasAnswer)
    }

    @Test
    fun `an order challenge with no options at all is not answerable`() {
        assertFalse(state(ChallengeTypeDto.ORDER, optionCount = 0).hasAnswer)
    }
}
