package com.kma.quiz_game.ui.screens.battle

import com.kma.quiz_game.data.remote.dto.LoadoutSlotDto
import com.kma.quiz_game.data.repository.BattleSession

/**
 * What the battle screen draws: the live session plus the little that is presentation only.
 *
 * There is no countdown here any more. The only clock in a fight belongs to the monster, it is
 * published as a deadline rather than a duration, and the arena interpolates it on the GL thread --
 * so nothing on this side has to tick.
 */
data class BattleUiState(
    val session: BattleSession = BattleSession(),
    val showExitDialog: Boolean = false,
    /** The three equipped skills, shared with duo -- one game layer, two modes. */
    val loadout: List<LoadoutSlotDto> = emptyList(),
    /**
     * When each cast skill may be cast again, on the server's clock.
     *
     * Accumulated on the client from `skill.used`, because the server reports a cooldown only for
     * the skill just cast. Purely to grey out a button: the server refuses an early cast whatever
     * this map says.
     */
    val skillReadyAt: Map<String, Long> = emptyMap(),
    /**
     * Set once the player has walked out and the server has not said so.
     *
     * Leaving is normally the server's word -- it answers `battle.leave` with `battle.finished`
     * and the result screen takes over. On a socket that is down there is no answer to wait for,
     * and a player who has confirmed they want out must not be held in the fight by it.
     */
    val hasLeft: Boolean = false,
    /**
     * ORDER questions only: the word tiles placed into the sentence so far, in that order.
     *
     * Local to the screen because a half-built sentence is not an answer -- nothing goes to the
     * server until the last tile lands. Cleared whenever a new question is pushed.
     */
    val placedOptionIds: List<String> = emptyList(),
) {
    /** Whether this skill can be cast right now, at this instant on the server's clock. */
    fun canCast(code: String, manaCost: Int, serverNowMs: Long): Boolean =
        session.isFighting &&
            session.mana >= manaCost &&
            serverNowMs >= (skillReadyAt[code] ?: 0L)

    fun rechargingCodes(serverNowMs: Long): Set<String> =
        skillReadyAt.filterValues { it > serverNowMs }.keys
}
