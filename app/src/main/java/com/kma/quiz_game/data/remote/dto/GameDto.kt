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
    /**
     * The chốt chặn năng lực [level] is being held at, or null when nothing is holding it.
     *
     * Experience keeps accruing past a cap but the *level* does not, so a learner who is capped
     * and never told would watch their bar fill and their level sit still with no explanation.
     * Non-null is exactly the condition for offering the Benchmark Exam -- see
     * `duo-game-back/app/schemas/game/game.py`, which says the same thing from the other side.
     */
    val pendingBenchmarkLevel: Int? = null,
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

// --- Benchmark Exam ---------------------------------------------------------

/**
 * Sit the Benchmark Exam bound to one cap. The server draws the paper and grades it; nothing the
 * client counts is ever trusted.
 */
@Serializable
data class BenchmarkAttemptStartRequest(val capLevel: Int)

/**
 * A freshly drawn paper. [questions] carry no answers. [startedAt] and [expiresAt] are server
 * instants; the client only ever uses the *gap* between them, so a device clock that is off does not
 * move the deadline.
 */
@Serializable
data class BenchmarkAttemptDto(
    val attemptId: String,
    val capLevel: Int,
    val questions: List<ChallengeDto>,
    val total: Int,
    val passPercent: Int,
    val startedAt: String,
    val expiresAt: String,
)

/** One answer. Exactly one of the two option fields is set, as with [AnswerCheckRequest]. */
@Serializable
data class BenchmarkAnswerRequest(
    val challengeId: String,
    val selectedOptionId: String? = null,
    val selectedOptionIds: List<String>? = null,
)

/** That an answer was taken -- deliberately without saying whether it was right. */
@Serializable
data class BenchmarkAnswerAckDto(
    val attemptId: String,
    val answeredCount: Int,
    val total: Int,
)

/** The grade, and the profile after it: a pass arrives with the cap already lifted. */
@Serializable
data class BenchmarkResultDto(
    val attemptId: String,
    val capLevel: Int,
    val status: String,
    val correctCount: Int,
    val total: Int,
    val percent: Int,
    val passPercent: Int,
    val passed: Boolean,
    val submittedAt: String? = null,
    val profile: GameProfileDto,
)

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

    /**
     * Adds seconds to the time the caster's next correct answer is scored against, so reading a
     * hard question carefully no longer costs the speed bonus.
     *
     * The name must stay exactly `TIME_BONUS`: these are wire values compared against the `effect`
     * string a loadout slot arrives with, not an app-side vocabulary. Spelling it anything else
     * makes [PVP_LIFELINE_EFFECTS] miss a real lifeline and the PvP dock lock it as "chỉ PvE".
     */
    const val TIME_BONUS = "TIME_BONUS"
}

/**
 * The three "Trợ Lực Tri Thức" (knowledge lifelines) a duel allows: pedagogical help only, no
 * combat effect. Everything else in [SkillEffect] stays available in PvE, where the RPG numbers
 * are still real -- see `duo-game-back/android.md` §1.3 and §6.3 for why PvP is narrower.
 */
val PVP_LIFELINE_EFFECTS = setOf(SkillEffect.REMOVE_OPTIONS, SkillEffect.COMBO_KEEP, SkillEffect.TIME_BONUS)

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
    /** Thousandths on the EXP a match or lesson pays out: 45 is +4.5%. */
    val bonusExpPermille: Int = 0,
    /** Thousandths on the Gold a match or lesson pays out. */
    val bonusGoldPermille: Int = 0,
    val quantity: Int = 1,
    val equipped: Boolean = false,
)

/**
 * Equipment no longer moves a combat number: what it buys is a percentage on top of what a match
 * or lesson *pays*, never on the odds of winning it. The bonuses here are already capped by the
 * server, so this is exactly what the next payout will use.
 */
@Serializable
data class InventoryDto(
    val items: List<ItemDto> = emptyList(),
    val bonusExpPermille: Int = 0,
    val bonusGoldPermille: Int = 0,
    /** The skin on show, or null for the CEFR band's own colour. See `heroGlowFor`. */
    val skinCode: String? = null,
)

/** A null code takes the current skin off; it is not an omitted field. */
@Serializable
data class WearSkinRequest(
    val skinCode: String? = null,
)

/**
 * One row on the shop's shelf.
 *
 * No bonus fields, and that is the point rather than an omission: only zero-bonus cosmetics carry
 * a price server-side, so gold can buy a look and never an edge. Equipment stays chest-only.
 */
@Serializable
data class ShopItemDto(
    val id: String,
    val code: String,
    val name: String,
    /** Always SKIN today; kept so a future cosmetic kind needs no client change. */
    val kind: String = "",
    val rarity: String = "",
    val goldPrice: Int = 0,
    val owned: Boolean = false,
)

/** The shelf and the balance together, so a purchase's response leaves nothing stale to re-fetch. */
@Serializable
data class ShopDto(
    val gold: Int = 0,
    val items: List<ShopItemDto> = emptyList(),
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
