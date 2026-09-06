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
    // The character and achievement modules do not exist yet. The server declares these and
    // answers null/empty until they do, so this card does not have to be rewritten when they land.
    val title: String? = null,
    val companionCharacter: String? = null,
    val skinCode: String? = null,
    val achievements: List<String> = emptyList(),
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
    val title: String? = null,
    val companionCharacter: String? = null,
    val skinCode: String? = null,
    val achievements: List<String> = emptyList(),

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
        title = title,
        companionCharacter = companionCharacter,
        skinCode = skinCode,
        achievements = achievements,
    )
}
