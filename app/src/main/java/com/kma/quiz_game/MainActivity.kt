package com.kma.quiz_game

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.kma.quiz_game.data.push.PushRoute
import com.kma.quiz_game.ui.navigation.RootNavHost
import com.kma.quiz_game.ui.theme.Quiz_gameTheme

private const val RESET_PASSWORD_SCHEME = "quizgame"
private const val RESET_PASSWORD_HOST = "reset-password"

/**
 * A `FragmentActivity` rather than a `ComponentActivity` for one reason: the battle arena runs on
 * libGDX, whose only supported way to live inside an Activity it does not own is an
 * `AndroidFragmentApplication`, which is a Fragment and needs a fragment manager to be committed
 * into. Nothing else in the app uses fragments, and the whole UI is still Compose.
 *
 * [AndroidFragmentApplication.Callbacks] is not optional: the backend looks for it on the host
 * activity when the arena attaches and throws if it is missing.
 */
class MainActivity : FragmentActivity(), AndroidFragmentApplication.Callbacks {
    /**
     * Token from a `quizgame://reset-password?token=...` link, held here rather than routed as a
     * navigation deep link: [RootNavHost] swaps between two nav graphs with their own
     * NavControllers, and a deep link cannot reliably reach across that swap.
     */
    private val resetToken = mutableStateOf<String?>(null)

    /** Where a tapped push notification asked to go. Held and handed down like [resetToken]. */
    private val pushRoute = mutableStateOf<PushRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetToken.value = intent?.resetTokenOrNull()
        // A recreated activity still carries the intent it was launched with; the tap it came
        // from has already been followed.
        if (savedInstanceState == null) pushRoute.value = intent?.pushRouteOrNull()
        enableEdgeToEdge()
        setContent {
            Quiz_gameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val token by resetToken
                    val route by pushRoute
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
                        pushRoute = route,
                        onPushRouteConsumed = { pushRoute.value = null },
                    )
                }
            }
        }
    }

    /**
     * The arena asking to be shut down -- from a GL initialisation failure, in practice.
     *
     * Ignored on purpose. In a libGDX game this ends the process, but here the arena is a backdrop
     * to a screen that is perfectly playable without it, and killing the app over a graphics
     * problem would lose the fight the player is in the middle of.
     */
    override fun exit() = Unit

    /** The activity is `singleTask`, so a link or notification tapped while the app is already
     * open lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.resetTokenOrNull()?.let { resetToken.value = it }
        intent.pushRouteOrNull()?.let { pushRoute.value = it }
    }
}

private fun Intent.resetTokenOrNull(): String? = data
    ?.takeIf { it.scheme == RESET_PASSWORD_SCHEME && it.host == RESET_PASSWORD_HOST }
    ?.getQueryParameter("token")
    ?.takeIf { it.isNotBlank() }

private fun Intent.pushRouteOrNull(): PushRoute? = PushRoute.fromWire(extras?.getString(PushRoute.EXTRA_KEY))
