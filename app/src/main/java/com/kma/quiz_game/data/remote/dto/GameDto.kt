package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for the `game` endpoints -- the player's standing outside any single match: level, gold,
 * energy, class, skill tree, loadout, inventory and the season ladder.
 *
 * This is the layer both game modes read from. A duo match and a lesson battle both fight with
 * the class stats, the equipped skills and the equipment bonuses resolved here, so neither screen
 * can draw a skill bar without it.
 *
 * Catalog-shaped values (`effect`, `rarity`, `kind`, `slot`, `tier`, `unlockKind`,
 * `lockedReason`) stay raw strings for the same reason as in [DuoPlayerDto]: the server may add a
 * row to any of those tables, and a new row must not be able to break decoding.
 */

// --- Profile --------------------------------------------------------------

/**
 * The energy bar. Energy regenerates lazily -- nothing runs in the background, the stored value is
 * read against how long it has been sitting -- so [nextRegenAt] is null exactly when the bar is
 * full and nothing is accruing.
 */
@Serializable
data class EnergyDto(
    val current: Int,
    val maximum: Int,
    val nextRegenAt: String? = null,
) {
    val isEmpty: Boolean get() = current <= 0

    val fraction: Float get() = if (maximum <= 0) 0f else current.toFloat() / maximum
}

@Serializable
data class GameProfileDto(
    val userId: String,
    val level: Int,
    val totalExp: Int,
    val expForCurrentLevel: Int,
    val expForNextLevel: Int,
    val expToNextLevel: Int,
    val gold: Int,
    val classCode: String? = null,
    val className: String? = null,
    val energy: EnergyDto,
    val dayStreak: Int,
    val bestDayStreak: Int,
) {
    /** How far through the current level the player is, for the bar under their name. */
    val levelFraction: Float
        get() {
            val span = expForNextLevel - expForCurrentLevel
            return if (span <= 0) 0f else ((totalExp - expForCurrentLevel).toFloat() / span).coerceIn(0f, 1f)
        }
}

// --- Classes --------------------------------------------------------------

/** [damagePermille] is thousandths: 900 is x0.9, 1250 is x1.25. */
@Serializable
data class GameClassDto(
    val code: String,
    val name: String,
    val description: String,
    val maxHp: Int,
    val damagePermille: Int,
    val startingMana: Int,
    val isCurrent: Boolean,
)

@Serializable
data class ChooseClassRequest(val classCode: String)

// --- Skills ---------------------------------------------------------------

/**
 * One node of the tree. [parentCode] is the edge: a node opens once its parent is owned, the level
 * is high enough and the gold is there.
 *
 * [lockedReason] is null exactly when the node can be unlocked now. `NEEDS_UNIT` is the
 * interesting one -- an ultimate bought with study rather than gold, which opens when every lesson
 * of [unlockUnitId] is complete.
 */
@Serializable
data class SkillNodeDto(
    val id: String,
    val code: String,
    val name: String,
    val description: String,
    val effect: String = "",
    val classCode: String? = null,
    val tier: Int = 1,
    val parentCode: String? = null,
    val manaCost: Int = 0,
    val magnitude: Int = 0,
    val durationRounds: Int = 0,
    val unlockKind: String = "",
    val unlockLevel: Int = 1,
    val goldPrice: Int = 0,
    val unlockUnitId: String? = null,
    val owned: Boolean = false,
    val unlockable: Boolean = false,
    val lockedReason: String? = null,
    /** 0..2 when this skill sits on the bar, null when it is owned but not equipped. */
    val equippedSlot: Int? = null,
)

@Serializable
data class SkillTreeDto(
    val level: Int,
    val gold: Int,
    val classCode: String? = null,
    val loadoutSlots: Int = LOADOUT_SLOTS,
    val skills: List<SkillNodeDto> = emptyList(),
)

/** Why a node cannot be unlocked yet. Absent means it can be. */
object SkillLockReason {
    const val WRONG_CLASS = "WRONG_CLASS"
    const val NEEDS_PARENT = "NEEDS_PARENT"
    const val NEEDS_LEVEL = "NEEDS_LEVEL"
    const val NEEDS_GOLD = "NEEDS_GOLD"
    const val NEEDS_UNIT = "NEEDS_UNIT"
}

object SkillUnlockKind {
    const val STARTER = "STARTER"
    const val LEVEL_GOLD = "LEVEL_GOLD"
    const val UNIT_COMPLETION = "UNIT_COMPLETION"
}

/** What a skill does. The catalog holds the numbers; this picks the icon and the wording. */
object SkillEffect {
    const val DOUBLE_DAMAGE = "DOUBLE_DAMAGE"
    const val DAMAGE_REDUCTION = "DAMAGE_REDUCTION"
    const val HEAL = "HEAL"
    const val TIME_PENALTY = "TIME_PENALTY"
    const val REMOVE_OPTIONS = "REMOVE_OPTIONS"
    const val MANA_BURN = "MANA_BURN"
    const val COMBO_KEEP = "COMBO_KEEP"
    const val EXECUTE = "EXECUTE"
}

/** Three at a time, and only an equipped skill can be cast. */
const val LOADOUT_SLOTS = 3

@Serializable
data class LoadoutSlotDto(
    val slotIndex: Int,
    val skillId: String,
    val code: String,
    val name: String,
    val effect: String = "",
    val manaCost: Int = 0,
)

@Serializable
data class LoadoutDto(val slots: List<LoadoutSlotDto> = emptyList())

/** Fewer than three is allowed; the empty list clears the bar. */
@Serializable
data class LoadoutRequest(val skillIds: List<String> = emptyList())

// --- Items ----------------------------------------------------------------

@Serializable
data class ItemDto(
    val id: String,
    val code: String,
    val name: String,
    /** EQUIPMENT / SKIN / CARD. Only EQUIPMENT carries bonuses. */
    val kind: String = "",
    /** WEAPON / ARMOR / TRINKET, null for anything that is not equipment. */
    val slot: String? = null,
    val rarity: String = "",
    val bonusMaxHp: Int = 0,
    val bonusDamagePermille: Int = 0,
    val bonusStartingMana: Int = 0,
    val quantity: Int = 1,
    val equipped: Boolean = false,
)

/** The bonuses are already capped by the server, so this is exactly what a match will use. */
@Serializable
data class InventoryDto(
    val items: List<ItemDto> = emptyList(),
    val bonusMaxHp: Int = 0,
    val bonusDamagePermille: Int = 0,
    val bonusStartingMana: Int = 0,
)

@Serializable
data class EquipmentRequest(
    val weaponId: String? = null,
    val armorId: String? = null,
    val trinketId: String? = null,
)

object EquipmentSlot {
    const val WEAPON = "WEAPON"
    const val ARMOR = "ARMOR"
    const val TRINKET = "TRINKET"
}

object ItemKind {
    const val EQUIPMENT = "EQUIPMENT"
    const val SKIN = "SKIN"
    const val CARD = "CARD"
}

// --- Season ---------------------------------------------------------------

/**
 * The season ladder. `duo_ratings` is the all-time rating and is never reset; this is the
 * projection that does, opening each new season at 70% of the previous one.
 */
@Serializable
data class SeasonDto(
    val code: String,
    val name: String,
    val startsAt: String,
    val endsAt: String,
    val rating: Int,
    val peakRating: Int,
    val tier: String = "",
    val nextTier: String? = null,
    val ratingToNextTier: Int? = null,
    val matchesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
)
