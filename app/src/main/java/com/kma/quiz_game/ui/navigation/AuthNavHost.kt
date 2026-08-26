package com.kma.quiz_game.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kma.quiz_game.ui.screens.auth.LoginScreen
import com.kma.quiz_game.ui.screens.auth.RegisterScreen

/** Login <-> Register only. Swapped out for [DuoNavHost] by [RootNavHost] once a session exists. */
@Composable
fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Destination.Login) {
        composable<Destination.Login> {
            LoginScreen(onNavigateToRegister = { navController.navigate(Destination.Register) })
        }
        composable<Destination.Register> {
            RegisterScreen(onNavigateToLogin = { navController.popBackStack() })
        }
    }
}
