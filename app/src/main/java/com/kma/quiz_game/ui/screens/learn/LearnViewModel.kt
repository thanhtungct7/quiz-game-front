package com.kma.quiz_game.ui.screens.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.GameConstants
import com.kma.quiz_game.data.remote.dto.LessonProgressStatusDto
import com.kma.quiz_game.data.repository.AuthRepository
import com.kma.quiz_game.data.repository.LearnRepository
import com.kma.quiz_game.data.repository.UserProgressRepository
import com.kma.quiz_game.ui.components.LessonNodeStatus
import com.kma.quiz_game.ui.components.LessonPathItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LearnViewModel(
    private val learnRepository: LearnRepository,
    private val userProgressRepository: UserProgressRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearnUiState())
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = authRepository.currentUserId.filterNotNull().first()
            userProgressRepository.getOrCreate(userId)

            val tree = learnRepository.loadFirstCourseTree()
            if (tree == null) {
                _uiState.value = LearnUiState(isLoading = false)
                return@launch
            }

            authRepository.currentUserId.filterNotNull().flatMapLatest { uid ->
                userProgressRepository.observe(uid)
            }.collect { userProgress ->
                // First non-COMPLETED lesson across the whole course (not per-unit) is ACTIVE;
                // everything after it is LOCKED; everything up to it keeps its real status.
                // Mirrors the old client-side "first incomplete challenge" logic, now derived
                // from server progress instead of a locally tracked completed-challenge set.
                val flatLessons = tree.units.flatMap { it.lessons }
                val firstIncompleteIndex = flatLessons.indexOfFirst {
                    it.progress?.status != LessonProgressStatusDto.COMPLETED
                }

                var index = 0
                val unitsUi = tree.units.map { unitNode ->
                    val lessonItems = unitNode.lessons.map { node ->
                        val status = when {
                            firstIncompleteIndex == -1 -> LessonNodeStatus.COMPLETE
                            index < firstIncompleteIndex -> LessonNodeStatus.COMPLETE
                            index == firstIncompleteIndex -> LessonNodeStatus.ACTIVE
                            else -> LessonNodeStatus.LOCKED
                        }
                        index++
                        LessonPathItem(node.lesson.id, node.lesson.title, status)
                    }
                    UnitUi(unitNode.unit.id, unitNode.unit.title, unitNode.unit.description, lessonItems)
                }

                _uiState.value = LearnUiState(
                    isLoading = false,
                    units = unitsUi,
                    hearts = userProgress?.hearts ?: GameConstants.MAX_HEARTS,
                    points = userProgress?.points ?: 0,
                    isPro = userProgress?.isPro ?: false,
                )
            }
        }
    }
}
