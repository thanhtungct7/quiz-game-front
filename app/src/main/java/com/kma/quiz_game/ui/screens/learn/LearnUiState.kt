package com.kma.quiz_game.ui.screens.learn

import com.kma.quiz_game.data.GameConstants
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
    val hearts: Int = GameConstants.MAX_HEARTS,
    val points: Int = 0,
    val isPro: Boolean = false,
    /** From the aggregated profile. Null until it arrives, so the header can leave the chip out
     * rather than draw a placeholder level that visibly changes a moment later. */
    val level: Int? = null,
    val cefr: String = "",
    val dayStreak: Int = 0,
    val errorMessage: String? = null,
)
