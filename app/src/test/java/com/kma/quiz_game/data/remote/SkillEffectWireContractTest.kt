package com.kma.quiz_game.data.remote

import com.kma.quiz_game.data.remote.dto.PVP_LIFELINE_EFFECTS
import com.kma.quiz_game.data.remote.dto.SkillEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the effect codes against the strings the server actually sends.
 *
 * These are wire values, not an app-side vocabulary: a loadout slot arrives carrying `effect` as
 * a string and the dock decides what to allow by comparing it. Nothing throws when the app spells
 * one differently -- the comparison just never matches, and a real lifeline shows up in the PvP
 * dock locked as "chỉ PvE" with nothing in any log to say why. That is exactly what happened when
 * this set named the add-time lifeline `ADD_TIME` while the backend called it `TIME_BONUS`.
 *
 * The literals below are copied from `duo-game-back/app/models/game/skill.py`. Renaming one there
 * must break this test.
 */
class SkillEffectWireContractTest {

    @Test
    fun `the three knowledge lifelines carry the server's own codes`() {
        assertEquals(
            setOf("REMOVE_OPTIONS", "COMBO_KEEP", "TIME_BONUS"),
            PVP_LIFELINE_EFFECTS,
        )
    }

    @Test
    fun `combat effects stay out of the duel`() {
        val combatOnly = listOf(
            SkillEffect.DOUBLE_DAMAGE,
            SkillEffect.DAMAGE_REDUCTION,
            SkillEffect.HEAL,
            SkillEffect.MANA_BURN,
            SkillEffect.EXECUTE,
            SkillEffect.TIME_PENALTY,
        )

        combatOnly.forEach { effect ->
            assertTrue("$effect must not be castable in PvP", effect !in PVP_LIFELINE_EFFECTS)
        }
    }
}
