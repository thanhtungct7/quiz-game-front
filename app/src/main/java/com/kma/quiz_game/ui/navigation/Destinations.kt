package com.kma.quiz_game.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable
    data object Learn : Destination

    @Serializable
    data object Leaderboard : Destination

    @Serializable
    data object Quests : Destination

    @Serializable
    data object Shop : Destination

    @Serializable
    data class Lesson(val lessonId: Long) : Destination
}

data class BottomNavItem(
    val destination: Destination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(Destination.Learn, "Learn", Icons.Filled.School, Icons.Outlined.School),
    BottomNavItem(Destination.Leaderboard, "Ranking", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
    BottomNavItem(Destination.Quests, "Quests", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    BottomNavItem(Destination.Shop, "Shop", Icons.Filled.Storefront, Icons.Outlined.Storefront),
)
