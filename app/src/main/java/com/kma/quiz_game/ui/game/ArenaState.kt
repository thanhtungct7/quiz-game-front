package com.kma.quiz_game.ui.game

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * What the arena draws, as one immutable value.
 *
 * Sides rather than roles. The stage holds a left fighter and a right fighter, and what stands on
 * the right depends on the mode: a monster in a lesson battle, a second knight in a duo match. The
 * renderer knows only that distinction ([rightIsHero]); everything else -- who is winning, whose
 * turn it might be -- belongs to the screen above it.
 *
 * Deadlines rather than progress: [castEndsAt] is an instant on the server's clock and
 * [serverOffsetMs] converts a local `elapsedRealtime` reading to it, so the GL thread works out how
 * full the wind-up is on every frame instead of stepping ten times a second with the snapshots.
 * A duo match has no wind-up at all -- neither side attacks on a clock -- so it leaves those unset
 * and no bar is drawn.
 */
data class ArenaState(
    /** The monster on the right. Ignored when [rightIsHero]. */
    val artCode: String = "",
    val isBoss: Boolean = false,
    /**
     * Which school each side fights as, and the skin they wear, straight off the server's player
     * card. The right-hand pair is read only when [rightIsHero]: a monster has art of its own.
     *
     * Empty means "not known yet", which draws the default school rather than nothing -- a fight
     * that starts a frame before the profile arrives must still have someone standing in it.
     */
    val leftClassCode: String = "",
    val leftSkinCode: String = "",
    val rightClassCode: String = "",
    val rightSkinCode: String = "",
    /**
     * True in a duo match: the right-hand fighter is a second player, drawn from their own
     * school's page, flipped to face the left one and tinted so the two never read as one -- two
     * players of the same school would otherwise be the same picture twice.
     */
    val rightIsHero: Boolean = false,
    val castEndsAt: Long = 0,
    val castIntervalMs: Int = 0,
    val serverOffsetMs: Long = 0,
    val enragedNext: Boolean = false,
    val leftCombo: Int = 0,
    val rightCombo: Int = 0,
    val finished: Boolean = false,
) {
    /** How full the wind-up is, 0..1, at this local instant. Zero when nothing is winding up. */
    fun castFraction(localNowMs: Long): Float {
        if (castIntervalMs <= 0 || castEndsAt <= 0) return 0f
        val remaining = (castEndsAt - (localNowMs + serverOffsetMs)).coerceAtLeast(0L)
        return (1f - remaining.toFloat() / castIntervalMs).coerceIn(0f, 1f)
    }
}

/** Something that happened once and should be seen once: a hit, a blow taken, a cast. */
sealed interface ArenaEvent {
    /** The left fighter's blow landed on the right one. */
    data class LeftHit(val damage: Int, val critical: Boolean = false) : ArenaEvent

    /**
     * The right fighter's blow landed on the left one.
     *
     * [heavy] shakes the camera twice as hard: an enraged monster in PvE, a critical in duo.
     */
    data class RightHit(val damage: Int, val heavy: Boolean = false) : ArenaEvent

    data class SkillCast(val effect: String, val onLeft: Boolean = true) : ArenaEvent

    /** A wrong answer: no blow lands, just a beat of stagger on whoever gave it. */
    data class Miss(val onLeft: Boolean = true) : ArenaEvent

    /** Someone hit zero. Everything in flight is dropped. */
    data object Down : ArenaEvent
}

/**
 * The only thing the UI thread and the GL thread share.
 *
 * State is a single reference the writer replaces whole and the reader reads whole, so a frame can
 * never be drawn from half of one snapshot and half of the next. Events go through a queue because
 * they must not be missed: a hit that lands between two frames still has to flash.
 *
 * Nothing here touches Compose, a ViewModel or a coroutine. The GL thread is not the main thread,
 * and reaching back into either from it is the classic way to make a renderer crash on a device
 * that never crashed in a preview.
 */
class ArenaBridge {
    private val current = AtomicReference(ArenaState())
    private val events = ConcurrentLinkedQueue<ArenaEvent>()

    fun publish(state: ArenaState) {
        current.set(state)
    }

    fun emit(event: ArenaEvent) {
        // Bounded by hand: a renderer that is paused (the screen is off, the app is backgrounded)
        // stops draining, and an unbounded queue would grow for as long as the fight runs.
        while (events.size >= MAX_PENDING_EVENTS) events.poll()
        events.add(event)
    }

    fun state(): ArenaState = current.get()

    fun drainEvents(): List<ArenaEvent> {
        if (events.isEmpty()) return emptyList()
        val drained = ArrayList<ArenaEvent>(events.size)
        while (true) drained.add(events.poll() ?: break)
        return drained
    }

    private companion object {
        const val MAX_PENDING_EVENTS = 32
    }
}
