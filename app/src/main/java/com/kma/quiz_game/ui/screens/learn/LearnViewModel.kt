package com.kma.quiz_game.ui.screens.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.local.SettingsStore
import com.kma.quiz_game.data.remote.dto.DailyQuestsDto
import com.kma.quiz_game.data.remote.dto.CourseMonsterDto
import com.kma.quiz_game.data.remote.dto.LessonProgressDto
import com.kma.quiz_game.data.remote.dto.LessonProgressStatusDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.BattleRepository
import com.kma.quiz_game.data.repository.CourseTree
import com.kma.quiz_game.data.repository.GameRepository
import com.kma.quiz_game.data.repository.LearnRepository
import com.kma.quiz_game.data.repository.ProfileRepository
import com.kma.quiz_game.data.repository.QuestRepository
import com.kma.quiz_game.ui.components.LessonNodeStatus
import com.kma.quiz_game.ui.components.LessonPathItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LearnViewModel(
    private val learnRepository: LearnRepository,
    private val battleRepository: BattleRepository,
    private val profileRepository: ProfileRepository,
    private val gameRepository: GameRepository,
    private val settingsStore: SettingsStore,
    private val questRepository: QuestRepository,
) : ViewModel() {

    /**
     * Today's quests for the banner above the path. Its own flow rather than a field of
     * [uiState]: that `combine` is already at its five-flow limit, and the banner changes on a
     * different beat -- after a claim, not after a sync.
     */
    val quests: StateFlow<DailyQuestsDto?> = questRepository.today

    /** Cache-first: whatever was stored on the last run is on screen before any request goes out. */
    private val tree: StateFlow<CourseTree?> = learnRepository.observeTree()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val progressByLesson = MutableStateFlow<Map<String, LessonProgressDto>>(emptyMap())

    /** Which monster guards each gate, and which gates are already down. One request for the whole
     * course; an empty map simply draws the old path, so a PvE outage never hides the lessons. */
    private val monstersByLesson = MutableStateFlow<Map<String, CourseMonsterDto>>(emptyMap())
    private val isSyncing = MutableStateFlow(true)
    private val errorMessage = MutableStateFlow<String?>(null)

    /** The three flows the path itself is built from, folded first: `combine` only has a typed
     * overload up to five, and the path deserves the readable half of the budget. */
    private val path = combine(tree, progressByLesson, monstersByLesson) { courseTree, progress, monsters ->
        courseTree?.let { toUnitUi(it, progress, monsters) }.orEmpty() to (courseTree != null)
    }

    /**
     * The two standings the header strip reads, folded together for the same budget reason.
     *
     * They are separate endpoints and neither subsumes the other: the aggregated card carries the
     * level and CEFR band on show, while the game profile is the only thing that knows whether
     * that level is being *held* at a cap.
     */
    private val standing = combine(
        profileRepository.selfProfile,
        gameRepository.profile,
    ) { card, game -> card to game }

    val uiState: StateFlow<LearnUiState> = combine(
        path,
        isSyncing,
        errorMessage,
        // The header strip's level and band. Shared with the profile tab through the repositories,
        // so a level gained mid-session shows up here without this screen asking again.
        standing,
        // Fifth and last: `combine`'s typed overload stops at five, and this is the cheapest of
        // them -- one boolean off DataStore, which is also what dismissing the dialog writes.
        settingsStore.hasSeenLearnIntro,
    ) { (units, hasTree), syncing, error, (card, game), introSeen ->
        LearnUiState(
            isLoading = !hasTree && syncing,
            isSyncing = syncing,
            units = units,
            energy = game?.energy,
            level = card?.level,
            cefr = card?.cefr.orEmpty(),
            dayStreak = card?.dayStreak ?: 0,
            pendingBenchmarkLevel = game?.pendingBenchmarkLevel,
            showIntro = !introSeen && hasTree,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnUiState())

    /**
     * The one-off header explanation has been read.
     *
     * Written to the store rather than to local state: the flow above is what clears the dialog,
     * so there is one source of truth and a second screen cannot disagree with it.
     */
    fun dismissIntro() {
        viewModelScope.launch { settingsStore.markLearnIntroSeen() }
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
            // Best effort, like the monster map below: the path draws fine without a level chip.
            profileRepository.refreshSelfProfile()
            // The only source of `pendingBenchmarkLevel`, and it changes the moment an exam is
            // passed -- so the banner clears on the way back from one without a manual reload.
            gameRepository.refreshProfile()
            // Best effort too: a battle just finished may have moved a quest, and the banner is
            // the first place the learner lands afterwards.
            questRepository.refresh()
            val result = learnRepository.refreshTree()
            errorMessage.value = result.exceptionOrNull()?.toUserMessage()
            // Even a failed refresh leaves the cached tree, which still names the course.
            val courseId = result.getOrNull() ?: tree.value?.courseId
            if (courseId != null) {
                progressByLesson.value = learnRepository.loadProgress(courseId)
                // Best effort: the path is drawable without it, so a failure here is not an error
                // the learner has to see.
                battleRepository.courseMonsters(courseId).onSuccess { map ->
                    monstersByLesson.value = map.lessons.associateBy { it.lessonId }
                }
            }
            isSyncing.value = false
        }
    }
}

/**
 * Walks the whole course in path order, not unit by unit. A COMPLETED lesson is COMPLETE; a lesson
 * that is not is ACTIVE when it opens the path or the lesson before it is completed, and LOCKED
 * otherwise.
 *
 * Judging each lesson by its predecessor, rather than locking everything after the first gap, is
 * what lets new units be woven into the path: a learner who is past the point where they land
 * finds their opening lesson ACTIVE, and every lesson they already finished further on stays
 * COMPLETE instead of disappearing behind a padlock.
 */
internal fun toUnitUi(
    tree: CourseTree,
    progress: Map<String, LessonProgressDto>,
    monsters: Map<String, CourseMonsterDto>,
): List<UnitUi> {
    var previousCompleted = true
    return tree.units.map { unit ->
        val lessonItems = unit.lessons.map { lesson ->
            val completed = progress[lesson.id]?.status == LessonProgressStatusDto.COMPLETED
            val status = when {
                completed -> LessonNodeStatus.COMPLETE
                previousCompleted -> LessonNodeStatus.ACTIVE
                else -> LessonNodeStatus.LOCKED
            }
            previousCompleted = completed
            val monster = monsters[lesson.id]
            LessonPathItem(
                id = lesson.id,
                title = lesson.title,
                status = status,
                monsterArtCode = monster?.artCode,
                isBoss = monster?.isBoss == true,
                cleared = monster?.cleared == true,
            )
        }
        UnitUi(unit.id, unit.title, unit.description, lessonItems)
    }
}
