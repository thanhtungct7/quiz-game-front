package com.kma.quiz_game.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable
    data object Learn : Destination

    @Serializable
    data object Leaderboard : Destination

    /** PvP lobby: rating, matchmaking, friend rooms and the way into match history. */
    @Serializable
    data object Duo : Destination

    /** The live match. Full screen -- the bottom bar is hidden here. */
    @Serializable
    data object DuoMatch : Destination

    @Serializable
    data object DuoResult : Destination

    @Serializable
    data object DuoHistory : Destination

    @Serializable
    data class DuoMatchDetail(val matchId: String) : Destination

    @Serializable
    data object Shop : Destination

    @Serializable
    data class Lesson(val lessonId: String) : Destination

    @Serializable
    data object Login : Destination

    @Serializable
    data object Register : Destination
}

data class BottomNavItem(
    val destination: Destination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/** Also decides where the bottom bar shows: any route outside this list is full-screen. */
val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(Destination.Learn, "Learn", Icons.Filled.School, Icons.Outlined.School),
    BottomNavItem(Destination.Duo, "Duo", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
    BottomNavItem(Destination.Leaderboard, "Ranking", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
    BottomNavItem(Destination.Shop, "Shop", Icons.Filled.Storefront, Icons.Outlined.Storefront),
)
