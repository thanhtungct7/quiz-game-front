package com.kma.quiz_game.ui.screens.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.local.AppDatabase
import com.kma.quiz_game.data.repository.ChallengeProgressRepository
import com.kma.quiz_game.data.repository.LearnRepository
import com.kma.quiz_game.data.repository.UserProgressRepository
import com.kma.quiz_game.ui.components.LessonNodeStatus
import com.kma.quiz_game.ui.components.LessonPathItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LearnViewModel(
    private val learnRepository: LearnRepository,
    private val challengeProgressRepository: ChallengeProgressRepository,
    private val userProgressRepository: UserProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearnUiState())
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val course = learnRepository.observeCourses().first().firstOrNull()
            val tree = course?.let { learnRepository.loadCourseTree(it.id) }
            if (tree == null) {
                _uiState.value = LearnUiState(isLoading = false)
                return@launch
            }

            combine(
                challengeProgressRepository.observeCompletedChallengeIds(AppDatabase.LOCAL_USER_ID),
                userProgressRepository.observe(),
            ) { completedIds, userProgress ->
                val completed = completedIds.toSet()
                val flatLessons = tree.units.flatMap { it.lessons }
                val firstIncompleteIndex = flatLessons.indexOfFirst { node ->
                    node.challengeIds.isEmpty() || !node.challengeIds.all { it in completed }
                }

                var index = 0
                val unitsUi = tree.units.map { unitNode ->
                    val lessonItems = unitNode.lessons.map { lessonNode ->
                        val status = when {
                            firstIncompleteIndex == -1 -> LessonNodeStatus.COMPLETE
                            index < firstIncompleteIndex -> LessonNodeStatus.COMPLETE
                            index == firstIncompleteIndex -> LessonNodeStatus.ACTIVE
                            else -> LessonNodeStatus.LOCKED
                        }
                        index++
                        LessonPathItem(lessonNode.lesson.id, lessonNode.lesson.title, status)
                    }
                    UnitUi(unitNode.unit.id, unitNode.unit.title, unitNode.unit.description, lessonItems)
                }

                LearnUiState(
                    isLoading = false,
                    units = unitsUi,
                    hearts = userProgress?.hearts ?: com.kma.quiz_game.data.GameConstants.MAX_HEARTS,
                    points = userProgress?.points ?: 0,
                    isPro = userProgress?.isPro ?: false,
                )
            }.collect { state -> _uiState.value = state }
        }
    }
}
