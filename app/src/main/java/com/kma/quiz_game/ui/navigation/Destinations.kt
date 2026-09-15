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

    /**
     * The Character Hub: wardrobe, shop and class behind one tab, three sub-tabs -- see
     * `duo-game-back/android.md` §3B / §6.2. Kept named `Shop` rather than renamed to avoid
     * touching every existing navigation call site for a route whose destination screen already
     * changed to [com.kma.quiz_game.ui.screens.game.CharacterHubScreen].
     */
    @Serializable
    data object Shop : Destination

    /**
     * Deep link into the class sub-tab from outside the hub (e.g. the Duo lobby). The picker's
     * layout is the same compact row used inside the hub -- see §6.2 -- so this is a shortcut to
     * that content, not a second design.
     */
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

    /**
     * The lesson fought instead of studied. Full screen -- the bottom bar would sit on the
     * health bars.
     */
    @Serializable
    data class Battle(val lessonId: String) : Destination

    @Serializable
    data class BattleResult(val lessonId: String) : Destination

    /**
     * The Bài Thi Sát Hạch for one chốt chặn năng lực -- see `duo-game-back/android.md` §1.6.
     * Full screen: it is an exam, and a bottom bar offering four ways out of it is not.
     *
     * [capLevel] is the cap being sat for (10, 25, 50, 75 or 92), carried on the route rather
     * than re-derived so the screen cannot end up reporting a pass against a different one than
     * the banner offered.
     */
    @Serializable
    data class BenchmarkExam(val capLevel: Int) : Destination

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
    BottomNavItem(Destination.Learn, "Học", Icons.Filled.School, Icons.Outlined.School),
    BottomNavItem(Destination.Duo, "Đấu", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
    BottomNavItem(Destination.Leaderboard, "Xếp hạng", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
    BottomNavItem(Destination.Shop, "Nhân vật", Icons.Filled.Storefront, Icons.Outlined.Storefront),
    BottomNavItem(Destination.Profile, "Hồ sơ", Icons.Filled.Person, Icons.Outlined.Person),
)
