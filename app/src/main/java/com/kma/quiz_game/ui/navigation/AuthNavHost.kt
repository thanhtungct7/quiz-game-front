package com.kma.quiz_game.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kma.quiz_game.ui.screens.auth.ForgotPasswordScreen
import com.kma.quiz_game.ui.screens.auth.LoginScreen
import com.kma.quiz_game.ui.screens.auth.RegisterScreen
import com.kma.quiz_game.ui.screens.auth.ResetPasswordScreen

/**
 * Login, Register and the password reset flow. Swapped out for [DuoNavHost] by [RootNavHost] once
 * a session exists.
 *
 * [resetToken] is set when the app was opened by a `quizgame://reset-password?token=...` link; the
 * flow then starts on the reset screen instead of Login. It is reported back through
 * [onResetTokenConsumed] once the graph has been built with it, so a rotation or a later sign-in
 * does not drop the user back onto the reset screen.
 */
@Composable
fun AuthNavHost(resetToken: String? = null, onResetTokenConsumed: () -> Unit = {}) {
    val navController = rememberNavController()
    var loginNotice by remember { mutableStateOf<String?>(null) }

    // NavHost reads startDestination once, so pinning it here keeps navigation inside the graph
    // working after the token is cleared below.
    val startDestination = remember {
        if (resetToken != null) Destination.ResetPassword(resetToken) else Destination.Login
    }
    LaunchedEffect(Unit) { onResetTokenConsumed() }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable<Destination.Login> {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Destination.Register) },
                onNavigateToForgotPassword = { navController.navigate(Destination.ForgotPassword) },
                notice = loginNotice,
            )
        }
        composable<Destination.Register> {
            RegisterScreen(onNavigateToLogin = { navController.popBackStack() })
        }
        composable<Destination.ForgotPassword> {
            ForgotPasswordScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onEnterCodeManually = {
                    navController.navigate(Destination.ResetPassword(token = ""))
                },
            )
        }
        composable<Destination.ResetPassword> { entry ->
            val route = entry.toRoute<Destination.ResetPassword>()
            ResetPasswordScreen(
                token = route.token,
                onResetComplete = {
                    loginNotice = "Đã đổi mật khẩu. Hãy đăng nhập bằng mật khẩu mới."
                    navController.backToLogin()
                },
                onRequestNewLink = { navController.navigate(Destination.ForgotPassword) },
            )
        }
    }
}

/**
 * A consumed reset token must not be reachable with Back, and the deep link may have made the
 * reset screen the start destination -- so clear the whole stack rather than popping.
 */
private fun NavController.backToLogin() {
    navigate(Destination.Login) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
