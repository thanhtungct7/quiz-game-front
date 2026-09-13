package com.kma.quiz_game.ui.screens.learn

import com.kma.quiz_game.data.remote.dto.EnergyDto
import com.kma.quiz_game.ui.components.LessonPathItem

data class UnitUi(
    val id: String,
    val title: String,
    val description: String,
    val lessons: List<LessonPathItem>,
)

data class LearnUiState(
    /** Only true while there is nothing to draw -- a cached path renders straight away. */
    val isLoading: Boolean = true,
    /** A background refresh is in flight over an already-drawn path. */
    val isSyncing: Boolean = false,
    val units: List<UnitUi> = emptyList(),
    /** The energy bar from the game profile -- the one resource the server actually spends. Null
     * until the profile arrives, so the header leaves the chip out instead of guessing. */
    val energy: EnergyDto? = null,
    /** From the aggregated profile. Null until it arrives, so the header can leave the chip out
     * rather than draw a placeholder level that visibly changes a moment later. */
    val level: Int? = null,
    val cefr: String = "",
    val dayStreak: Int = 0,
    /**
     * The chốt chặn năng lực holding [level] back, or null when nothing is. Non-null is what puts
     * the Benchmark Exam banner above the path -- without it a capped learner sees their
     * experience bar fill and their level never move, with nothing on screen saying why.
     */
    val pendingBenchmarkLevel: Int? = null,
    val errorMessage: String? = null,
)
