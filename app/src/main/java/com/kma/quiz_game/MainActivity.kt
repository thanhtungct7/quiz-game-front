package com.kma.quiz_game

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.kma.quiz_game.ui.navigation.RootNavHost
import com.kma.quiz_game.ui.theme.Quiz_gameTheme

private const val RESET_PASSWORD_SCHEME = "quizgame"
private const val RESET_PASSWORD_HOST = "reset-password"

class MainActivity : ComponentActivity() {
    /**
     * Token from a `quizgame://reset-password?token=...` link, held here rather than routed as a
     * navigation deep link: [RootNavHost] swaps between two nav graphs with their own
     * NavControllers, and a deep link cannot reliably reach across that swap.
     */
    private val resetToken = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetToken.value = intent?.resetTokenOrNull()
        enableEdgeToEdge()
        setContent {
            Quiz_gameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val token by resetToken
                    val app = applicationContext as DuoGameApplication

                    // Finishing the reset revokes every refresh token server-side, so the session
                    // on this device is spent either way -- ending it up front keeps the app from
                    // running on credentials that are about to stop working.
                    LaunchedEffect(token) {
                        if (token != null) app.logout()
                    }

                    RootNavHost(
                        resetToken = token,
                        onResetTokenConsumed = { resetToken.value = null },
                    )
                }
            }
        }
    }

    /** The activity is `singleTask`, so a link tapped while the app is already open lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.resetTokenOrNull()?.let { resetToken.value = it }
    }
}

private fun Intent.resetTokenOrNull(): String? = data
    ?.takeIf { it.scheme == RESET_PASSWORD_SCHEME && it.host == RESET_PASSWORD_HOST }
    ?.getQueryParameter("token")
    ?.takeIf { it.isNotBlank() }
