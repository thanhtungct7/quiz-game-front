package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for PvE -- the REST endpoints under `/battles` and the WebSocket payloads of a lesson
 * battle.
 *
 * Field names are plain camelCase: [com.kma.quiz_game.data.remote.NetworkModule.json] maps them to
 * the backend's snake_case, so no `@SerialName` is needed here.
 *
 * Every name carries a `Battle`/`Monster` prefix even where duo already has the same concept: the
 * two features share one `dto` package but not one protocol, and a shared `RoundStartDto` would
 * quietly couple them again.
 *
 * The question carried by `question.push` is a `ChallengePublicRead`, which is exactly the existing
 * [ChallengeDto] -- reused rather than redeclared.
 *
 * There are no rounds here. A battle is a clock: `question.push` opens a question, `answer.result`
 * closes it, `state.tick` reports the whole fight ten times a second, and `monster.swing` announces
 * a blow the player did nothing to cause.
 */

// --- Shared value types ---------------------------------------------------

enum class BattleStatusDto { IN_PROGRESS, WON, LOST, ABANDONED }

enum class BattleEndReasonDto { MONSTER_DOWN, PLAYER_DOWN, OUT_OF_QUESTIONS, LEFT, CANCELLED }

/** The monster as the fight needs it; the catalog view carries the prose. */
@Serializable
data class MonsterDto(
    val code: String,
    val name: String,
    val tier: Int,
    val maxHp: Int,
    val attackDamage: Int,
    val isBoss: Boolean,
    val artCode: String,
    /** How long one wind-up takes. The arena draws the ring against this. */
    val castIntervalMs: Int = 0,
)

// --- REST: /battles/* -----------------------------------------------------

@Serializable
data class MonsterCatalogDto(
    val code: String,
    val name: String,
    val description: String,
    val tier: Int,
    val maxHp: Int,
    val attackDamage: Int,
    val isBoss: Boolean,
    val artCode: String,
)

/** Who guards one lesson, and how this player has fared against them. */
@Serializable
data class MonsterPreviewDto(
    val lessonId: String,
    val lessonTitle: String,
    val monster: MonsterCatalogDto,
    val cleared: Boolean,
    val bestHpLeft: Int? = null,
    val lastPlayedAt: String? = null,
)

/** One gate on the course map. */
@Serializable
data class CourseMonsterDto(
    val unitId: String,
    val lessonId: String,
    val monsterCode: String,
    val isBoss: Boolean,
    val artCode: String,
    val cleared: Boolean,
)

/** Every gate of a course in one payload -- the map draws hundreds at once. */
@Serializable
data class CourseMonstersDto(
    val courseId: String,
    val lessons: List<CourseMonsterDto> = emptyList(),
)

@Serializable
data class BattleHistoryDto(
    val id: String,
    val lessonId: String,
    val monsterCode: String,
    val status: BattleStatusDto,
    val endReason: BattleEndReasonDto? = null,
    val playerHpLeft: Int,
    val monsterHpLeft: Int,
    val roundsPlayed: Int,
    val correctCount: Int,
    val bestCombo: Int,
    val expAwarded: Int,
    val goldAwarded: Int,
    val firstClear: Boolean,
    val startedAt: String,
    val finishedAt: String? = null,
)

// --- WebSocket payloads ---------------------------------------------------

@Serializable
data class BattleConnectedDto(
    val userId: String,
    val activeBattleId: String? = null,
    val serverTimeMs: Long = 0,
)

@Serializable
data class BattleStartedDto(
    val battleId: String,
    val lessonId: String,
    val lessonTitle: String,
    /** How many questions the lesson holds. Not a length: the pool goes round again. */
    val questionsInPool: Int,
    val monster: MonsterDto,
    val monsterHp: Int,
    val yourHp: Int,
    val yourMaxHp: Int,
    val yourMana: Int,
    val tickHz: Int = 0,
    val snapshotHz: Int = 0,
    val serverTimeMs: Long = 0,
)

/** One effect standing on the player, as the HUD needs to draw it. */
@Serializable
data class BattleActiveEffectDto(
    val code: String = "",
    /** The effect name from the server's catalog; shown, never branched on. */
    val effect: String = "",
    val magnitude: Int = 0,
    val expiresAt: Long = 0,
)

/**
 * The whole fight, ten times a second.
 *
 * [t] is the server's own clock at the moment the snapshot was made, and every other instant here
 * is on that same scale. Pairing them is what lets the client work out its offset from one frame
 * and animate a 60 fps cast ring off a 10 Hz feed, with no clock synchronisation of its own.
 *
 * [nextSwing] is a raw string rather than an enum on purpose: a value the server adds later would
 * make kotlinx throw, and dropping *this* frame would freeze the arena mid-fight.
 */
@Serializable
data class BattleStateTickDto(
    val t: Long,
    val yourHp: Int,
    val yourMaxHp: Int,
    val yourMana: Int,
    val combo: Int,
    val monsterHp: Int,
    val monsterMaxHp: Int,
    val castEndsAt: Long,
    val nextSwing: String = "",
    val nextSwingDamage: Int = 0,
    /** Zero while no question is on screen and none is pending. */
    val lockoutEndsAt: Long = 0,
    val effects: List<BattleActiveEffectDto> = emptyList(),
)

/**
 * A question, opened by the token that answers it.
 *
 * A token rather than an index because the pool is recycled: the same question can legitimately
 * come round twice, and an index would no longer say which showing an answer belongs to.
 * [poolPass] above zero means the player has seen everything once and repeats no longer move
 * lesson progress -- worth saying on screen rather than letting it look like a bug.
 */
@Serializable
data class BattleQuestionPushDto(
    val token: String,
    val question: ChallengeDto,
    val pushedAt: Long = 0,
    val poolPass: Int = 0,
)

/** The player's blow. Null for an answer that missed. */
@Serializable
data class BattleBlowDto(
    val finalDamage: Int,
    /** QUICK / NORMAL / HEAVY. A raw string so a new strike kind cannot break the decoder. */
    val strike: String = "",
    val comboCount: Int = 0,
    val comboMultiplier: Float = 1f,
    val isCritical: Boolean = false,
    val elementMultiplier: Float = 1f,
)

/**
 * What one answer did.
 *
 * Note what is absent: the player's health. An answer cannot cost health any more -- being wrong
 * costs the combo and the tempo of [lockoutEndsAt], and the monster's damage arrives on its own
 * clock through [MonsterSwingDto].
 */
@Serializable
data class BattleAnswerResultDto(
    val token: String,
    val correct: Boolean,
    val optionId: String,
    /** Measured on the server from the moment it pushed the question. */
    val elapsedMs: Int = 0,
    val correctOptionIds: List<String> = emptyList(),
    val explanation: String? = null,
    val blow: BattleBlowDto? = null,
    val yourMana: Int,
    val combo: Int,
    val monsterHp: Int,
    val lockoutEndsAt: Long = 0,
)

/** A blow the player did nothing to earn -- the cast simply finished. */
@Serializable
data class MonsterSwingDto(
    val damage: Int,
    val enraged: Boolean = false,
    val yourHp: Int,
    val swingIndex: Int = 0,
    /** Where the next wind-up now ends, so the ring re-arms without waiting for a snapshot. */
    val castEndsAt: Long = 0,
)

@Serializable
data class BattleSkillUsedDto(
    val skillCode: String,
    val skillName: String,
    /** The effect name from the server's catalog; shown, never branched on. */
    val effect: String = "",
    val magnitude: Int = 0,
    val manaSpent: Int = 0,
    val yourHp: Int,
    val yourMana: Int,
    val monsterHp: Int,
    /** A clock-stealing skill moves this, which is the whole reason it is worth casting. */
    val castEndsAt: Long = 0,
    val readyAgainAt: Long = 0,
    /** Caster-only extras, such as `removed_option_ids` from a REMOVE_OPTIONS skill. */
    val private: BattleSkillPrivateDto? = null,
)

@Serializable
data class BattleSkillPrivateDto(
    val removedOptionIds: List<String> = emptyList(),
)

@Serializable
data class BattleExpChangeDto(
    val before: Int,
    val after: Int,
    val delta: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val leveledUp: Boolean,
)

@Serializable
data class BattleGoldChangeDto(
    val before: Int,
    val after: Int,
    val delta: Int,
)

@Serializable
data class BattleLootDto(
    val code: String,
    val name: String,
    /** COMMON / RARE / EPIC / LEGENDARY, kept as a string so a new rarity cannot break decoding. */
    val rarity: String = "",
)

@Serializable
data class BattleStreakDto(
    val dayStreak: Int,
    val bestDayStreak: Int,
    val extended: Boolean,
)

/** Where the lesson stands now that the battle's answers are in -- the proof both layers moved. */
@Serializable
data class BattleLessonProgressDto(
    val status: LessonProgressStatusDto,
    val correct: Int,
    val total: Int,
)

@Serializable
data class BattleFinishedDto(
    val battleId: String,
    val outcome: BattleStatusDto,
    val endReason: BattleEndReasonDto,
    val yourHpLeft: Int,
    val monsterHpLeft: Int,
    val answersGiven: Int,
    val correctCount: Int,
    val bestCombo: Int,
    val durationMs: Long = 0,
    /** How many times the monster got a cast off. Zero is the new perfect run. */
    val monsterSwings: Int = 0,
    val firstClear: Boolean,
    val exp: BattleExpChangeDto,
    val gold: BattleGoldChangeDto,
    val lessonProgress: BattleLessonProgressDto,
    val loot: BattleLootDto? = null,
    val streak: BattleStreakDto? = null,
)

/** Echoed stamp plus the server's own, for a round-trip estimate. */
@Serializable
data class BattlePongDto(
    val clientTimeMs: Long = 0,
    val serverTimeMs: Long = 0,
)

/** [code] stays a raw string on the wire so an error code added on the server later degrades to
 * [BattleErrorCode.UNKNOWN] instead of crashing the decoder. */
@Serializable
data class BattleErrorDto(
    val code: String = "",
    val message: String = "",
) {
    val errorCode: BattleErrorCode
        get() = BattleErrorCode.entries.firstOrNull { it.name == code } ?: BattleErrorCode.UNKNOWN
}

/** Stable error codes from `app/schemas/pve/events.py`, plus a client-side catch-all. */
enum class BattleErrorCode {
    INVALID_PAYLOAD,
    UNKNOWN_EVENT,
    BATTLE_ALREADY_ACTIVE,
    LESSON_NOT_FOUND,
    NO_QUESTIONS_AVAILABLE,
    MONSTER_UNAVAILABLE,
    NOT_IN_BATTLE,
    QUESTION_CLOSED,
    INVALID_OPTION,
    SKILL_NOT_EQUIPPED,
    NOT_ENOUGH_MANA,
    SKILL_ON_COOLDOWN,
    SKILL_NO_TARGET,

    /** Client-side: the socket never came up, so `battle.start` was never answered. Never on
     * the wire -- the server cannot report a connection it does not have. */
    CONNECTION_LOST,
    UNKNOWN,
}
