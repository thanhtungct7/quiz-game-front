package com.kma.quiz_game.ui.game

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidFragmentApplication

/**
 * The libGDX surface, embedded in the Compose tree.
 *
 * libGDX offers exactly one way to run inside an Activity it does not own -- an
 * [AndroidFragmentApplication] -- so this hosts a [FragmentContainerView] and commits one into it.
 * That is why [com.kma.quiz_game.MainActivity] is a `FragmentActivity`, and why it implements
 * [AndroidFragmentApplication.Callbacks].
 *
 * The surface is transparent and sits *behind* the Compose HUD: the arena draws the fight, and the
 * question, the bars and the buttons are drawn over it by Compose.
 */
@Composable
fun ArenaSurface(bridge: ArenaBridge, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    // Without a FragmentActivity there is nowhere to put the surface. The HUD is a complete,
    // playable screen on its own, so the fight goes on without the arena rather than crashing.
    if (activity == null) return

    val containerId = remember { View.generateViewId() }
    val tag = remember { "$FRAGMENT_TAG-$containerId" }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            FragmentContainerView(viewContext).apply { id = containerId }
        },
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activity, containerId, lifecycleOwner, bridge) {
        val manager = activity.supportFragmentManager

        fun attach() {
            if (manager.isStateSaved || manager.findFragmentByTag(tag) != null) return
            ArenaFragment.pending = bridge
            // `commitNow` rather than `commit`: the container has just been added to the tree, and
            // a posted transaction can land after the composable has already left it.
            manager.commitNow { add(containerId, ArenaFragment(), tag) }
            ArenaFragment.pending = null
        }

        fun detach() {
            val existing = manager.findFragmentByTag(tag) ?: return
            if (activity.isFinishing) return
            manager.commitNow(allowStateLoss = true) { remove(existing) }
        }

        /**
         * Tied to the screen's lifecycle rather than only to disposal, and that is a fix, not a
         * refinement.
         *
         * libGDX pauses by queueing an event onto the GL thread and waiting for it; if it has not
         * been answered in four seconds it assumes a deadlock and calls `Process.killProcess` --
         * the app simply vanishes. `GLSurfaceView` shuts that thread down the moment its view
         * leaves the window, so removing this fragment *after* Compose has detached the container
         * means the answer can never come. Compose detaches the view before it runs `onDispose`,
         * so navigating away from a fight used to be a coin flip on whether the process survived.
         *
         * ON_PAUSE fires as the exit transition starts, while the surface is still attached, which
         * is exactly the moment libGDX can still pause itself properly. The guard keeps this to
         * navigation: when the whole activity is pausing, the fragment pauses on its own with the
         * view still in place, and committing a transaction inside that dispatch would throw.
         */
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> attach()
                Lifecycle.Event.ON_PAUSE ->
                    if (activity.lifecycle.currentState == Lifecycle.State.RESUMED) detach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // A screen that is only STARTED -- mid entry transition -- still gets its arena now rather
        // than a third of a second late.
        attach()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ArenaFragment.pending = null
            detach()
        }
    }
}

/**
 * The libGDX application, as a Fragment.
 *
 * [AndroidFragmentApplication] *is* a Fragment -- it owns the GL surface, the render thread and the
 * lifecycle callbacks that pause them -- so this subclasses it rather than wrapping it. Wrapping
 * would leave the render thread with no lifecycle to follow and keep it spinning after the screen
 * had gone.
 *
 * The bridge arrives through [pending] rather than through a constructor argument because the
 * framework builds fragments itself and demands a no-argument constructor. It is set immediately
 * before the transaction that creates this fragment and cleared when it goes, so the window in
 * which it is non-null is a single synchronous `commitNow`.
 */
class ArenaFragment : AndroidFragmentApplication() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val bridge = pending ?: return null
        val config = AndroidApplicationConfiguration().apply {
            // The arena is a backdrop, not a game world: no sensors, and an alpha channel so the
            // Compose surface behind it still shows through.
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            r = 8
            g = 8
            b = 8
            a = 8
        }
        return initializeForView(BattleArena(bridge), config)
    }

    companion object {
        var pending: ArenaBridge? = null
    }
}

private const val FRAGMENT_TAG = "battle-arena"

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
