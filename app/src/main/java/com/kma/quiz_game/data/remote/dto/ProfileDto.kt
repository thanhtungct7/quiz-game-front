package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for the `profile` endpoints -- the whole of a player in one call.
 *
 * This is the aggregate the server assembles out of `users`, `user_game_profiles`, `duo_ratings`
 * and the study tables. The client used to have to fetch three endpoints and stitch them
 * together; it does not any more, and it must not go back to doing so: a card that half-loads is
 * how two screens end up disagreeing about the same player.
 *
 * Two shapes, and the difference is deliberate. [SelfProfileDto] is what `GET /profile/me`
 * answers and carries the private half; [PublicProfileDto] is what `GET /profile/{id}` answers
 * about anyone else and has no email, gold or energy in it at all.
 *
 * [PublicProfileDto.cefr] and [PvpStatsDto.tier] stay raw strings for the same reason as in
 * [DuoPlayerDto]: they are catalog-shaped, the server may grow a value, and a new band must not
 * be able to break decoding for every screen that draws a card.
 */

@Serializable
data class PvpStatsDto(
    val rating: Int,
    val tier: String = "",
    val matchesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    /** Already a percentage, rounded server-side to one decimal. */
    val winRate: Double = 0.0,
) {
    /** True before the first finished match, when every number here is a default rather than a
     * measurement -- worth saying out loud instead of drawing a 0% win rate. */
    val hasPlayed: Boolean get() = matchesPlayed > 0
}

/**
 * The study side.
 *
 * [accuracy] is answers that landed over answers given. It is honest about what the server
 * stores rather than precise: progress is kept as the best-ever outcome per question, not as a
 * log of answers, so re-drilling something already mastered drags this down. It never overstates.
 */
@Serializable
data class LearningStatsDto(
    val challengesAttempted: Int = 0,
    val challengesMastered: Int = 0,
    val totalAttempts: Int = 0,
    val accuracy: Double = 0.0,
) {
    val hasAnswered: Boolean get() = totalAttempts > 0
}

/**
 * The four numbers a player brings into a fight, already resolved server-side.
 *
 * Class, equipment and the daily streak are added up by `services/game/combat_stats.py`, so these
 * are the figures a match will really use rather than a base the client would have to assemble --
 * which is the whole reason the card can draw them as bars and be honest about it.
 *
 * [atk] is the damage a correct answer deals, not the multiplier behind it: [damagePermille]
 * carries the exact figure, but only [atk] can be held against [hp] and compared.
 */
@Serializable
data class CombatStatsDto(
    val hp: Int = 0,
    val atk: Int = 0,
    val defence: Int = 0,
    val mana: Int = 0,
    val damagePermille: Int = 0,
)

/**
 * One line of "where did this number come from", from `GET /profile/me/combat`.
 *
 * Every figure is a delta, and the lines add up to [CombatBreakdownDto.total] exactly -- including
 * the case where the equipment ceiling clipped the total, which the server attributes greedily
 * rather than leaving unaccounted. A breakdown that did not add up would be worse than none.
 */
@Serializable
data class StatSourceDto(
    /** CLASS / EQUIPMENT / STREAK. Raw for the catalog reason above. */
    val kind: String = "",
    val code: String = "",
    val label: String = "",
    val hp: Int = 0,
    val atk: Int = 0,
    val defence: Int = 0,
    val mana: Int = 0,
) {
    /** Whether this line moved anything at all -- a class contributes no defence, and a row of
     * zeroes in the modal is noise rather than information. */
    val isEmpty: Boolean get() = hp == 0 && atk == 0 && defence == 0 && mana == 0
}

object StatSourceKind {
    const val CLASS = "CLASS"
    const val EQUIPMENT = "EQUIPMENT"
    const val STREAK = "STREAK"
}

@Serializable
data class CombatBreakdownDto(
    val total: CombatStatsDto = CombatStatsDto(),
    val sources: List<StatSourceDto> = emptyList(),
)

/**
 * One unlocked achievement, as a card shows it.
 *
 * [iconCode] is resolved to artwork client-side the way a monster's `artCode` is: the catalog can
 * grow an achievement before anyone draws an icon for it, so an unknown code has to fall back
 * rather than blank the badge out.
 */
@Serializable
data class AchievementDto(
    val code: String,
    val name: String = "",
    val description: String = "",
    /** PROGRESSION / LEARNING / PVP / PVE. */
    val category: String = "",
    val iconCode: String = "",
    val unlockedAt: String? = null,
)

/** The same, plus how far off it is -- what `GET /profile/me/achievements` returns for the ones
 * still to earn. [current] is clamped server-side, so a finished bar cannot read as 130%. */
@Serializable
data class AchievementProgressDto(
    val code: String,
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val iconCode: String = "",
    val unlockedAt: String? = null,
    val threshold: Int = 0,
    val current: Int = 0,
    val unlocked: Boolean = false,
) {
    val fraction: Float
        get() = if (threshold <= 0) 0f else (current.toFloat() / threshold).coerceIn(0f, 1f)
}

@Serializable
data class AchievementListDto(
    val unlockedCount: Int = 0,
    val total: Int = 0,
    val items: List<AchievementProgressDto> = emptyList(),
) {
    /** Which tab of the shelf a badge belongs under, in the order the server sorted them. */
    fun byCategory(category: String): List<AchievementProgressDto> =
        items.filter { it.category == category }
}

object AchievementCategory {
    const val PROGRESSION = "PROGRESSION"
    const val LEARNING = "LEARNING"
    const val PVP = "PVP"
    const val PVE = "PVE"
}

@Serializable
data class PublicProfileDto(
    val id: String,
    val username: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val joinedAt: String? = null,
    val level: Int = 1,
    /** CEFR band: "A1".."C2". */
    val cefr: String = "",
    val toeicEstimate: Int = 0,
    val classCode: String? = null,
    val dayStreak: Int = 0,
    val bestDayStreak: Int = 0,
    val pvp: PvpStatsDto,
    val learning: LearningStatsDto,
    val combat: CombatStatsDto = CombatStatsDto(),
    // The character module does not exist yet. The server declares these and answers null until
    // it does, so this card does not have to be rewritten when it lands -- and every composable
    // that draws them has to treat absent as the normal case rather than as an error.
    val title: String? = null,
    val companionCharacter: String? = null,
    val skinCode: String? = null,
    /** The three most recently unlocked, which is what the overview tab draws. Not the whole
     * shelf: this payload is fetched for every leaderboard row, and the full list is its own
     * endpoint. */
    val featuredAchievements: List<AchievementDto> = emptyList(),
    val totalAchievementsUnlocked: Int = 0,
)

@Serializable
data class SelfProfileDto(
    val id: String,
    val username: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val joinedAt: String? = null,
    val level: Int = 1,
    val cefr: String = "",
    val toeicEstimate: Int = 0,
    val classCode: String? = null,
    val dayStreak: Int = 0,
    val bestDayStreak: Int = 0,
    val pvp: PvpStatsDto,
    val learning: LearningStatsDto,
    val combat: CombatStatsDto = CombatStatsDto(),
    val title: String? = null,
    val companionCharacter: String? = null,
    val skinCode: String? = null,
    val featuredAchievements: List<AchievementDto> = emptyList(),
    val totalAchievementsUnlocked: Int = 0,

    // --- the private half, never present on another player's card ---
    val email: String = "",
    val hasUploadedAvatar: Boolean = false,
    val gold: Int = 0,
    val energy: EnergyDto? = null,
    val totalExp: Int = 0,
    val expForCurrentLevel: Int = 0,
    val expForNextLevel: Int = 0,
    val expToNextLevel: Int = 0,
    /** Null once the top band is reached -- there is nothing above C2. */
    val nextCefr: String? = null,
    val nextCefrAtLevel: Int? = null,
) {
    /** How far through the current level, for the bar under the name. Mirrors
     * [GameProfileDto.levelFraction]; the two read the same numbers. */
    val levelFraction: Float
        get() {
            val span = expForNextLevel - expForCurrentLevel
            return if (span <= 0) 0f else ((totalExp - expForCurrentLevel).toFloat() / span).coerceIn(0f, 1f)
        }

    /** The same fields the public card carries, for drawing both with one composable. */
    fun asPublic(): PublicProfileDto = PublicProfileDto(
        id = id,
        username = username,
        bio = bio,
        avatarUrl = avatarUrl,
        joinedAt = joinedAt,
        level = level,
        cefr = cefr,
        toeicEstimate = toeicEstimate,
        classCode = classCode,
        dayStreak = dayStreak,
        bestDayStreak = bestDayStreak,
        pvp = pvp,
        learning = learning,
        combat = combat,
        title = title,
        companionCharacter = companionCharacter,
        skinCode = skinCode,
        featuredAchievements = featuredAchievements,
        totalAchievementsUnlocked = totalAchievementsUnlocked,
    )
}
