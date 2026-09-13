package com.kma.quiz_game.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.ui.screens.battle.BattleResultScreen
import com.kma.quiz_game.ui.screens.battle.BattleScreen
import com.kma.quiz_game.ui.screens.duo.DuoHistoryScreen
import com.kma.quiz_game.ui.screens.duo.DuoHomeScreen
import com.kma.quiz_game.ui.screens.duo.DuoMatchDetailScreen
import com.kma.quiz_game.ui.screens.duo.DuoMatchScreen
import com.kma.quiz_game.ui.screens.duo.DuoResultScreen
import com.kma.quiz_game.ui.screens.game.CharacterHubScreen
import com.kma.quiz_game.ui.screens.game.ClassPickerScreen
import com.kma.quiz_game.ui.screens.game.LoadoutScreen
import com.kma.quiz_game.ui.screens.game.SkillTreeScreen
import com.kma.quiz_game.ui.screens.leaderboard.LeaderboardScreen
import com.kma.quiz_game.ui.screens.learn.LearnScreen
import com.kma.quiz_game.ui.screens.lesson.LessonScreen
import com.kma.quiz_game.ui.screens.profile.MyProfileScreen
import kotlinx.coroutines.launch

@Composable
fun DuoNavHost() {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = BOTTOM_NAV_ITEMS.any { item ->
        currentDestination?.hierarchy?.any { it.hasRoute(item.destination::class) } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BOTTOM_NAV_ITEMS.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(item.destination::class)
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.destination) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Learn,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<Destination.Learn> {
                LearnScreen(
                    // A gate on the path is a monster now. The plain lesson screen stays wired
                    // below as the way back if the battle route ever has to be switched off.
                    onLessonClick = { lessonId -> navController.navigate(Destination.Battle(lessonId)) },
                )
            }
            composable<Destination.Leaderboard> {
                LeaderboardScreen(onPlayDuo = { navController.navigate(Destination.Duo) })
            }
            composable<Destination.Duo> {
                DuoHomeScreen(
                    // The socket decides when a match exists, so the lobby only signals it here.
                    onMatchStarting = { navController.navigateToMatch() },
                    onOpenHistory = { navController.navigate(Destination.DuoHistory) },
                    onOpenClasses = { navController.navigate(Destination.GameClass) },
                    onOpenSkills = { navController.navigate(Destination.GameSkills) },
                    onOpenLoadout = { navController.navigate(Destination.GameLoadout) },
                    onOpenInventory = { navController.navigate(Destination.Shop) },
                )
            }
            composable<Destination.DuoMatch> {
                DuoMatchScreen(
                    onFinished = { navController.replaceMatchWith(Destination.DuoResult) },
                    onLeft = { navController.replaceMatchWith(Destination.Duo) },
                    // An empty skill dock is the one place a player learns the bar exists at all,
                    // so it opens the loadout *over* the match rather than leaving it.
                    onOpenLoadout = { navController.navigate(Destination.GameLoadout) },
                )
            }
            composable<Destination.DuoResult> {
                DuoResultScreen(
                    onPlayAgain = { navController.replaceResultWith(Destination.Duo) },
                    onBackToLobby = { navController.replaceResultWith(Destination.Duo) },
                )
            }
            composable<Destination.DuoHistory> {
                DuoHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMatch = { matchId -> navController.navigate(Destination.DuoMatchDetail(matchId)) },
                )
            }
            composable<Destination.DuoMatchDetail> { entry ->
                val route = entry.toRoute<Destination.DuoMatchDetail>()
                DuoMatchDetailScreen(
                    matchId = route.matchId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Destination.Profile> {
                // Editing is a sheet inside this screen now, not a route: see [EditProfileSheet].
                MyProfileScreen(onLogout = { coroutineScope.launch { app.logout() } })
            }
            composable<Destination.Shop> {
                CharacterHubScreen()
            }
            composable<Destination.GameClass> {
                ClassPickerScreen(onBack = { navController.popBackStack() })
            }
            composable<Destination.GameSkills> {
                SkillTreeScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLoadout = { navController.navigate(Destination.GameLoadout) },
                )
            }
            composable<Destination.GameLoadout> {
                LoadoutScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSkillTree = { navController.navigate(Destination.GameSkills) },
                )
            }
            composable<Destination.Lesson> { entry ->
                val lesson = entry.toRoute<Destination.Lesson>()
                LessonScreen(
                    lessonId = lesson.lessonId,
                    onExit = { navController.popBackStack() },
                )
            }
            composable<Destination.Battle> { entry ->
                val route = entry.toRoute<Destination.Battle>()
                BattleScreen(
                    lessonId = route.lessonId,
                    onFinished = {
                        navController.replaceBattleWith(Destination.BattleResult(route.lessonId))
                    },
                    onLeft = { navController.popBackStack() },
                )
            }
            composable<Destination.BattleResult> { entry ->
                val route = entry.toRoute<Destination.BattleResult>()
                BattleResultScreen(
                    lessonId = route.lessonId,
                    onBackToPath = { navController.replaceBattleResultWith(Destination.Learn) },
                    onFightAgain = { lessonId ->
                        navController.navigate(Destination.Battle(lessonId)) {
                            popUpTo(Destination.BattleResult(lessonId)) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

/** The lobby can fire this more than once as the phase settles; [launchSingleTop] keeps it to one. */
private fun NavController.navigateToMatch() {
    navigate(Destination.DuoMatch) { launchSingleTop = true }
}

/** A finished or abandoned match must not be reachable with Back. */
private fun NavController.replaceMatchWith(destination: Destination) {
    navigate(destination) {
        popUpTo(Destination.DuoMatch) { inclusive = true }
        launchSingleTop = true
    }
}

/** A finished battle must not be reachable with Back. */
private fun NavController.replaceBattleWith(destination: Destination) {
    navigate(destination) {
        popUpTo<Destination.Battle> { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavController.replaceBattleResultWith(destination: Destination) {
    navigate(destination) {
        popUpTo<Destination.BattleResult> { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavController.replaceResultWith(destination: Destination) {
    navigate(destination) {
        popUpTo(Destination.DuoResult) { inclusive = true }
        launchSingleTop = true
    }
}
