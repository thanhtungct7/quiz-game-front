package com.kma.quiz_game.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Person
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

    /** Equipment lives behind the Shop tab: chests are where items come from, not a store. */
    @Serializable
    data object Shop : Destination

    /** The class picker. Full screen -- it is a comparison, and the bottom bar steals a row. */
    @Serializable
    data object GameClass : Destination

    @Serializable
    data object GameSkills : Destination

    /**
     * The equipped bar. Reachable from the skill tree, the lobby, *and* from an empty skill dock
     * mid-match, which is the moment a player discovers they need it.
     */
    @Serializable
    data object GameLoadout : Destination

    @Serializable
    data object Profile : Destination

    /** Full screen: it owns the keyboard, and the bottom bar would sit on top of it. */
    @Serializable
    data class Lesson(val lessonId: String) : Destination

    /**
     * The lesson fought instead of studied. Full screen -- the bottom bar would sit on the
     * health bars.
     */
    @Serializable
    data class Battle(val lessonId: String) : Destination

    @Serializable
    data class BattleResult(val lessonId: String) : Destination

    @Serializable
    data object Login : Destination

    @Serializable
    data object Register : Destination

    @Serializable
    data object ForgotPassword : Destination

    /** [token] comes from the emailed `quizgame://reset-password?token=...` link, and is empty
     * when the user opened the screen to type the code in by hand. */
    @Serializable
    data class ResetPassword(val token: String) : Destination
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
    BottomNavItem(Destination.Shop, "Trang bị", Icons.Filled.Storefront, Icons.Outlined.Storefront),
    BottomNavItem(Destination.Profile, "Hồ sơ", Icons.Filled.Person, Icons.Outlined.Person),
)
