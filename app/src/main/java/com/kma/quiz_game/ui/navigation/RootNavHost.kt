package com.kma.quiz_game.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kma.quiz_game.DuoGameApplication

/**
 * Top-level switch between the auth graph and the main app graph, driven by whether a session
 * is persisted in [com.kma.quiz_game.data.remote.TokenStore]. `null` is the brief "haven't read
 * DataStore yet" state, distinct from "logged out", so a persisted session isn't flashed as a
 * Login screen on cold start.
 *
 * [resetToken] is the token from a `quizgame://reset-password?token=...` link. It holds the auth
 * graph open while the session is being torn down (MainActivity signs out on such a link), so the
 * reset screen never flashes past on the way to the lobby.
 *
 * [AuthNavHost] is called from a single place on purpose: two call sites would be two composables
 * to Compose, and moving between them would rebuild the nav graph and lose its state.
 */
@Composable
fun RootNavHost(resetToken: String? = null, onResetTokenConsumed: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val isLoggedIn by app.authRepository.isLoggedIn.collectAsState(initial = null)

    when {
        isLoggedIn == null && resetToken == null -> SplashScreen()
        isLoggedIn == false || resetToken != null ->
            AuthNavHost(resetToken = resetToken, onResetTokenConsumed = onResetTokenConsumed)
        else -> DuoNavHost()
    }
}

@Composable
private fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
