package com.kma.quiz_game.ui.components.battle

/**
 * A glyph for a monster, keyed by the catalog's `art_code`.
 *
 * For the places a monster is *named* rather than fought: the gate on the learn path, and the
 * result screen after the fight. Inside the fight the monster is drawn by
 * [com.kma.quiz_game.ui.game.BattleArena] on the GL surface instead -- a glyph cannot squash when
 * it is hit.
 *
 * Glyphs rather than drawables: the catalog can grow a monster at any time, and a code with no
 * artwork should still draw *something* rather than an empty gate. Swapping these for real assets
 * later is a change to this map alone.
 */
private val ART_BY_CODE = mapOf(
    "SLIME" to "🟢",
    "SLIME_KING" to "👑",
    "GOBLIN" to "👺",
    "GOBLIN_CHIEF" to "🗿",
    "DIRE_WOLF" to "🐺",
    "ALPHA_WOLF" to "🐺",
    "GOLEM" to "🪨",
    "STONE_TITAN" to "🗿",
    "WRAITH" to "👻",
    "LICH" to "💀",
    "DRAKE" to "🐉",
    "DRAGON" to "🐲",
)

private const val FALLBACK_ART = "👾"

fun monsterArt(artCode: String): String = ART_BY_CODE[artCode] ?: FALLBACK_ART
