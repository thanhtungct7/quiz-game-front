package com.kma.quiz_game.ui.screens.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.GameConstants
import com.kma.quiz_game.data.remote.dto.LessonProgressDto
import com.kma.quiz_game.data.remote.dto.LessonProgressStatusDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.AuthRepository
import com.kma.quiz_game.data.repository.CourseTree
import com.kma.quiz_game.data.repository.LearnRepository
import com.kma.quiz_game.data.repository.UserProgressRepository
import com.kma.quiz_game.ui.components.LessonNodeStatus
import com.kma.quiz_game.ui.components.LessonPathItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LearnViewModel(
    private val learnRepository: LearnRepository,
    private val userProgressRepository: UserProgressRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** Cache-first: whatever was stored on the last run is on screen before any request goes out. */
    private val tree: StateFlow<CourseTree?> = learnRepository.observeTree()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val progressByLesson = MutableStateFlow<Map<String, LessonProgressDto>>(emptyMap())
    private val isSyncing = MutableStateFlow(true)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val userProgress = authRepository.currentUserId
        .filterNotNull()
        .flatMapLatest { userProgressRepository.observe(it) }

    val uiState: StateFlow<LearnUiState> = combine(
        tree,
        progressByLesson,
        userProgress,
        isSyncing,
        errorMessage,
    ) { courseTree, progress, gamification, syncing, error ->
        LearnUiState(
            isLoading = courseTree == null && syncing,
            isSyncing = syncing,
            units = courseTree?.let { toUnitUi(it, progress) }.orEmpty(),
            hearts = gamification?.hearts ?: GameConstants.MAX_HEARTS,
            points = gamification?.points ?: 0,
            isPro = gamification?.isPro ?: false,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnUiState())

    init {
        viewModelScope.launch {
            userProgressRepository.getOrCreate(authRepository.currentUserId.filterNotNull().first())
        }
    }

    /**
     * Revalidate the cached path and reload progress.
     *
     * Called every time the screen enters composition -- including on the way back from a lesson,
     * which is what keeps the completed/active markers current. Cheap: an unchanged path costs a
     * single 304, and progress is one request covering the whole course.
     */
    fun sync() {
        viewModelScope.launch {
            isSyncing.value = true
            val result = learnRepository.refreshTree()
            errorMessage.value = result.exceptionOrNull()?.toUserMessage()
            // Even a failed refresh leaves the cached tree, which still names the course.
            val courseId = result.getOrNull() ?: tree.value?.courseId
            if (courseId != null) {
                progressByLesson.value = learnRepository.loadProgress(courseId)
            }
            isSyncing.value = false
        }
    }
}

/**
 * The first lesson that is not COMPLETED -- across the whole course, not per unit -- is ACTIVE;
 * everything after it is LOCKED, everything before it keeps its real status.
 */
private fun toUnitUi(tree: CourseTree, progress: Map<String, LessonProgressDto>): List<UnitUi> {
    val flatLessonIds = tree.units.flatMap { unit -> unit.lessons.map { it.id } }
    val firstIncompleteIndex = flatLessonIds.indexOfFirst {
        progress[it]?.status != LessonProgressStatusDto.COMPLETED
    }

    var index = 0
    return tree.units.map { unit ->
        val lessonItems = unit.lessons.map { lesson ->
            val status = when {
                firstIncompleteIndex == -1 || index < firstIncompleteIndex ->
                    LessonNodeStatus.COMPLETE
                index == firstIncompleteIndex -> LessonNodeStatus.ACTIVE
                else -> LessonNodeStatus.LOCKED
            }
            index++
            LessonPathItem(lesson.id, lesson.title, status)
        }
        UnitUi(unit.id, unit.title, unit.description, lessonItems)
    }
}
