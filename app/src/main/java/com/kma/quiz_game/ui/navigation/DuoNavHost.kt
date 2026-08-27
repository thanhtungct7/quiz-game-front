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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.ui.screens.duo.DuoHomeScreen
import com.kma.quiz_game.ui.screens.leaderboard.LeaderboardScreen
import com.kma.quiz_game.ui.screens.learn.LearnScreen
import com.kma.quiz_game.ui.screens.lesson.LessonScreen
import com.kma.quiz_game.ui.screens.placeholder.PlaceholderScreen
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
                    onLessonClick = { lessonId -> navController.navigate(Destination.Lesson(lessonId)) },
                    onLogout = { coroutineScope.launch { app.logout() } },
                )
            }
            composable<Destination.Leaderboard> {
                LeaderboardScreen(onPlayDuo = { navController.navigate(Destination.Duo) })
            }
            composable<Destination.Duo> {
                DuoHomeScreen(
                    // The socket decides when a match exists, so the lobby only signals it here.
                    onMatchStarting = {},   // the match screen arrives in the next commit
                    onOpenHistory = {},     // the history screen arrives in a later commit
                )
            }
            composable<Destination.Shop> {
                PlaceholderScreen(title = "Shop", subtitle = "Coming in the next iteration")
            }
            composable<Destination.Lesson> { entry ->
                val lesson = entry.toRoute<Destination.Lesson>()
                LessonScreen(
                    lessonId = lesson.lessonId,
                    onExit = { navController.popBackStack() },
                )
            }
        }
    }
}
