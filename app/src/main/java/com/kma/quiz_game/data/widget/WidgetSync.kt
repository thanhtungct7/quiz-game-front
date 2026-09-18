package com.kma.quiz_game.data.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.kma.quiz_game.data.local.WidgetStateStore
import com.kma.quiz_game.data.remote.dto.SelfProfileDto
import com.kma.quiz_game.ui.widget.StreakWidget
import kotlin.coroutines.cancellation.CancellationException

/**
 * The one place the app tells the home screen what it has learned.
 *
 * Everything that can move the widget goes through here -- a profile card, a settlement, a logout
 * -- so the snapshot and the redraw can never drift apart: a write without a redraw leaves a stale
 * card on the home screen until the next half-hourly pass, and a redraw without a write draws the
 * same thing again.
 *
 * It reaches up into `ui.widget` for [StreakWidget], which is the wrong direction for a data-layer
 * class and is deliberate: `updateAll` is Glance's only supported way to ask for a redraw, and it
 * takes the widget instance. The alternative -- broadcasting `APPWIDGET_UPDATE` by hand -- would
 * trade one import for a second, untested update path.
 */
class WidgetSync(
    private val context: Context,
    private val store: WidgetStateStore,
) {

    /** `GET /profile/me` came back. Carries the level and band the wide layout draws, and a streak
     * that may be news from another device -- see [WidgetStateStore.writeCard]. */
    suspend fun onCard(card: SelfProfileDto) = bestEffort {
        val now = System.currentTimeMillis()
        store.writeCard(
            dayStreak = card.dayStreak,
            bestDayStreak = card.bestDayStreak,
            level = card.level,
            cefr = card.cefr,
            levelFraction = card.levelFraction,
            today = StreakClock.today(now),
            nowMillis = now,
        )
        redraw()
    }

    /**
     * A lesson battle or a duo match just settled.
     *
     * This is the only first-hand evidence the app gets that today has been studied, and it is why
     * the widget can say "Hẹn gặp lại nhé!" the moment a fight ends instead of waiting for the
     * learn screen to refresh the card.
     */
    suspend fun onSettlement(dayStreak: Int, bestDayStreak: Int) = bestEffort {
        val now = System.currentTimeMillis()
        store.writeStreak(
            dayStreak = dayStreak,
            bestDayStreak = bestDayStreak,
            today = StreakClock.today(now),
            nowMillis = now,
        )
        redraw()
    }

    suspend fun onSignedOut() = bestEffort {
        store.clear()
        redraw()
    }

    private suspend fun redraw() {
        StreakWidget().updateAll(context)
    }

    /**
     * Swallows everything, and that is the point: these are called from inside a profile refresh
     * and from inside the socket collectors that drive a fight. A DataStore write that failed on a
     * full disk, or an `updateAll` during a launcher restore, must not turn a good refresh into an
     * error on screen -- and must certainly not cancel the collector that is delivering a match.
     *
     * Cancellation is the one thing that still propagates: swallowing it would leave work running
     * inside a scope that has already been torn down, which is what a logout does to these.
     */
    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Deliberately dropped.
        }
    }
}
