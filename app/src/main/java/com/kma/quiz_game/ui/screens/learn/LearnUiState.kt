package com.kma.quiz_game.ui.screens.learn

import com.kma.quiz_game.data.GameConstants
import com.kma.quiz_game.ui.components.LessonPathItem

data class UnitUi(
    val id: Long,
    val title: String,
    val description: String,
    val lessons: List<LessonPathItem>,
)

data class LearnUiState(
    val isLoading: Boolean = true,
    val units: List<UnitUi> = emptyList(),
    val hearts: Int = GameConstants.MAX_HEARTS,
    val points: Int = 0,
    val isPro: Boolean = false,
)
