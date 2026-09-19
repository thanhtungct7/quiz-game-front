package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for 1v1 PvP -- both the REST endpoints under `/duo` and the WebSocket payloads.
 *
 * Field names are plain camelCase: [com.kma.quiz_game.data.remote.NetworkModule.json] maps them
 * to the backend's snake_case, so no `@SerialName` is needed anywhere here.
 *
 * The question carried by `question.push` is a `ChallengePublicRead`, which is exactly the existing
 * [ChallengeDto] -- it is reused rather than redeclared.
 *
 * There are no rounds on this wire. Each player is pushed their own question, answers it on their
 * own clock, and is told separately what the other side just landed on them. Every instant is an
 * absolute millisecond stamp on the server's own clock, so the client can interpolate at 60 fps
 * from a feed that arrives ten times a second.
 *
 * Anything the server may add a value to later -- a rank tier, a strike kind, a skill effect, an
 * item rarity -- stays a raw [String] here rather than an enum. kotlinx throws on an unknown enum
 * constant, and one new balance row on the server must never be able to make a live match
 * undecodable. Enums are used only where the set is closed by the protocol itself.
 */

// --- Shared value types ---------------------------------------------------

enum class DuoMatchMode { RANDOM, FRIEND }

enum class DuoMatchStatus { WAITING, IN_PROGRESS, FINISHED, ABANDONED, CANCELLED }

/**
 * How a match ended.
 *
 * `KNOCKOUT` is a blow taking someone to 0 HP, `DECK_CLEARED` is a player answering every question
 * in their deck correctly, and `TIME_UP` is the match clock running out with neither. `COMPLETED`
 * belongs to the lock-step engine and only appears on old rows in history.
 */
enum class DuoMatchEndReason {
    COMPLETED,
    OPPONENT_LEFT,
    OPPONENT_TIMEOUT,
    CANCELLED,
    KNOCKOUT,
    DECK_CLEARED,
    TIME_UP,
}

enum class MatchOutcome { WIN, LOSE, DRAW }

enum class DuoDifficulty { EASY, MEDIUM, HARD }

/**
 * The public face of a player, sent identically everywhere somebody is drawn: the lobby, a live
 * match, a history row, a room preview.
 *
 * [tier] is derived on the server from [rating], so the two can never disagree; a player with no
 * game profile yet still gets a card, at level 1 with no class.
 */
@Serializable
data class DuoPlayerDto(
    val id: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val rating: Int,
    /** BRONZE / SILVER / GOLD / PLATINUM / DIAMOND / MASTER. */
    val tier: String = "",
    val level: Int = 1,
    /** The class code, not its name -- names come from `GET /game/classes`. */
    val classCode: String? = null,
    val dayStreak: Int = 0,
)

/**
 * Match settings. The bounds mirror `DuoSettingsRequest` on the backend; the client enforces them
 * too so a slider can never produce an `INVALID_PAYLOAD`.
 *
 * Random matchmaking only pairs players whose settings are *identical*, so changing anything here
 * shrinks the pool you can be matched against.
 */
@Serializable
data class DuoSettingsDto(
    val questionCount: Int = DEFAULT_QUESTION_COUNT,
    val timePerQuestion: Int = DEFAULT_TIME_PER_QUESTION,
    val topicIds: List<String>? = null,
    val difficulty: DuoDifficulty? = null,
) {
    companion object {
        const val MIN_QUESTION_COUNT = 3
        const val MAX_QUESTION_COUNT = 20
        const val DEFAULT_QUESTION_COUNT = 10
        const val MIN_TIME_PER_QUESTION = 5
        const val MAX_TIME_PER_QUESTION = 60
        const val DEFAULT_TIME_PER_QUESTION = 15
    }

    fun coerced(): DuoSettingsDto = copy(
        questionCount = questionCount.coerceIn(MIN_QUESTION_COUNT, MAX_QUESTION_COUNT),
        timePerQuestion = timePerQuestion.coerceIn(MIN_TIME_PER_QUESTION, MAX_TIME_PER_QUESTION),
    )
}

// --- REST: /duo/* ---------------------------------------------------------

/** One row of match history, already told from the requesting player's side. */
@Serializable
data class DuoMatchSummaryDto(
    val matchId: String,
    val mode: DuoMatchMode,
    val status: DuoMatchStatus,
    val endReason: DuoMatchEndReason? = null,
    val outcome: MatchOutcome? = null,
    val opponent: DuoPlayerDto? = null,
    val myScore: Int,
    val opponentScore: Int,
    val myCorrect: Int,
    val opponentCorrect: Int,
    val questionCount: Int,
    val durationSeconds: Int? = null,
    val finishedAt: String? = null,
    val createdAt: String,
)

/**
 * One cast, as the history reports it. [mine] separates my casts from the opponent's.
 *
 * [roundIndex] keeps its name from the lock-step engine and means what it always did -- how far
 * into the match this was -- which with no rounds left to count is the caster's answer count.
 */
@Serializable
data class DuoSkillUseDto(
    val roundIndex: Int,
    val skillCode: String,
    val manaSpent: Int,
    val mine: Boolean,
)

@Serializable
data class DuoMatchDetailDto(
    val matchId: String,
    val mode: DuoMatchMode,
    val status: DuoMatchStatus,
    val endReason: DuoMatchEndReason? = null,
    val outcome: MatchOutcome? = null,
    val opponent: DuoPlayerDto? = null,
    val myScore: Int,
    val opponentScore: Int,
    val myCorrect: Int,
    val opponentCorrect: Int,
    val questionCount: Int,
    val durationSeconds: Int? = null,
    val finishedAt: String? = null,
    val createdAt: String,
    val myHpLeft: Int = 0,
    val opponentHpLeft: Int = 0,
    val skillUses: List<DuoSkillUseDto> = emptyList(),
)

/** All-zero with `rating = 1000` for a player who has never finished a match. */
@Serializable
data class DuoStatsDto(
    val rating: Int,
    val matchesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val winRate: Double,
    val currentStreak: Int,
    val bestStreak: Int,
)

@Serializable
data class DuoLeaderboardEntryDto(
    val rank: Int,
    val userId: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val rating: Int,
    val matchesPlayed: Int,
    val wins: Int,
    val tier: String = "",
)

/**
 * [scope] says which board came back: the season resets, the all-time one never does, and a
 * player whose rating just soft-reset needs to be told which of the two they are looking at.
 */
@Serializable
data class DuoLeaderboardDto(
    val entries: List<DuoLeaderboardEntryDto> = emptyList(),
    val myRank: Int? = null,
    val scope: String = LeaderboardScope.ALL_TIME,
    val seasonCode: String? = null,
)

/** The two values `GET /duo/leaderboard?season=` accepts. */
object LeaderboardScope {
    const val CURRENT = "current"
    const val ALL_TIME = "all_time"
}

/** Reads the *in-memory* room registry, so it only answers while the room is live and WAITING. */
@Serializable
data class DuoRoomPreviewDto(
    val roomCode: String,
    val host: DuoPlayerDto,
    val settings: DuoSettingsDto,
    val playerCount: Int,
)

// --- WebSocket payloads ---------------------------------------------------

@Serializable
data class ConnectedDto(
    val user: DuoPlayerDto,
    val activeMatchId: String? = null,
    val serverTimeMs: Long = 0,
)

@Serializable
data class QueueWaitingDto(
    val position: Int,
    val waitedSeconds: Int,
)

@Serializable
data class RoomCreatedDto(
    val matchId: String,
    val roomCode: String,
    val settings: DuoSettingsDto,
)

@Serializable
data class MatchFoundDto(
    val matchId: String,
    val roomCode: String? = null,
    val mode: DuoMatchMode,
    val opponent: DuoPlayerDto,
    val settings: DuoSettingsDto,
    val hostId: String,
    /** True for the random queue (the match loop is already running); false in a friend room,
     * where the host must send `match.start`. */
    val autoStart: Boolean,
)

/**
 * Built per player: health and mana differ between the two sides.
 *
 * [deadlineAt] is when the match is decided on health if neither player has fallen or cleared
 * their deck by then, and [speedReferenceSeconds] is what a correct answer is scored against --
 * not a deadline. Nothing cuts a player off mid-question any more.
 */
@Serializable
data class MatchStartedDto(
    val matchId: String,
    val deckSize: Int = 0,
    val speedReferenceSeconds: Int = 0,
    val tickHz: Int = 0,
    val snapshotHz: Int = 0,
    val serverTimeMs: Long = 0,
    val deadlineAt: Long = 0,
    val yourHp: Int = 0,
    val yourMaxHp: Int = 0,
    val yourMana: Int = 0,
    val opponentHp: Int = 0,
    val opponentMaxHp: Int = 0,
)

/**
 * A question, opened by the token that answers it.
 *
 * A token rather than an index because a wrong answer sends the same question back into the deck:
 * it can legitimately come round twice, and an index would no longer say which showing an answer
 * belongs to. [retry] marks that second showing, so the repeat reads as the rule it is rather than
 * as a bug.
 */
@Serializable
data class QuestionPushDto(
    val token: String,
    val question: ChallengeDto,
    val pushedAt: Long = 0,
    /** How many questions this player still has to get right, this one included. */
    val deckRemaining: Int = 0,
    val retry: Boolean = false,
)

/** One effect standing on a player, as the HUD needs to draw it. */
@Serializable
data class DuoActiveEffectDto(
    val code: String = "",
    /** The effect name from the server's catalog; shown, never branched on. */
    val effect: String = "",
    val magnitude: Int = 0,
    val expiresAt: Long = 0,
)

/**
 * One player's attack, fully resolved by the server.
 *
 * [strike] is QUICK / NORMAL / HEAVY and [damage] is already scaled by combo, criticals, the
 * topic element and any standing damage reduction -- the client only draws the number.
 */
@Serializable
data class DuoBlowDto(
    val damage: Int = 0,
    val strike: String = "",
    val comboCount: Int = 0,
    val comboMultiplier: Float = 1f,
    val isCritical: Boolean = false,
    val stunsOpponent: Boolean = false,
    val elementMultiplier: Float = 1f,
)

/**
 * What one answer did, sent only to the player who gave it.
 *
 * Note what is absent: the player's own health. An answer cannot cost health -- being wrong costs
 * the combo and a beat of tempo, and the only thing that takes health off you is a blow the
 * opponent lands, which arrives as [OpponentAnsweredDto].
 */
@Serializable
data class AnswerResultDto(
    val token: String,
    val correct: Boolean,
    val optionId: String = "",
    /** Measured on the server from the moment it pushed this player the question. */
    val elapsedMs: Int = 0,
    val correctOptionIds: List<String> = emptyList(),
    val explanation: String? = null,
    val points: Int = 0,
    /** Null for an answer that missed: a wrong answer lands nothing at all. */
    val blow: DuoBlowDto? = null,
    val yourScore: Int = 0,
    val yourMana: Int = 0,
    val yourCombo: Int = 0,
    val yourDeckRemaining: Int = 0,
    val opponentHp: Int = 0,
    val lockoutEndsAt: Long = 0,
)

/**
 * The other side answered, and whatever it cost has already been applied.
 *
 * This is how a blow is seen the instant it lands rather than at the end of a round; nothing here
 * is a prediction. [yourStunnedUntil] is set when that answer's combo stunned this player.
 */
@Serializable
data class OpponentAnsweredDto(
    val correct: Boolean = false,
    val damage: Int = 0,
    val isCritical: Boolean = false,
    val yourHp: Int = 0,
    val opponentScore: Int = 0,
    val opponentCombo: Int = 0,
    val opponentDeckRemaining: Int = 0,
    val yourStunnedUntil: Long = 0,
)

/**
 * The whole match, ten times a second.
 *
 * [t] is the server's own clock at the moment the snapshot was made, and every other instant here
 * is on that same scale. Pairing them is what lets the client work out its offset from one frame
 * and animate at 60 fps off a 10 Hz feed, with no clock synchronisation of its own.
 */
@Serializable
data class DuoStateTickDto(
    val t: Long = 0,
    val yourHp: Int = 0,
    val yourMaxHp: Int = 0,
    val yourMana: Int = 0,
    val yourCombo: Int = 0,
    val yourScore: Int = 0,
    val yourDeckRemaining: Int = 0,
    val opponentHp: Int = 0,
    val opponentMaxHp: Int = 0,
    val opponentCombo: Int = 0,
    val opponentScore: Int = 0,
    val opponentDeckRemaining: Int = 0,
    val deadlineAt: Long = 0,
    /** Zero while no question is on screen and none is pending. */
    val lockoutEndsAt: Long = 0,
    val stunnedUntil: Long = 0,
    val effects: List<DuoActiveEffectDto> = emptyList(),
)

/**
 * Everything needed to rebuild the match screen after reconnecting.
 *
 * [token] and [question] come back together: the question that was on screen is handed over with
 * the token that answers it, so a client that dropped mid-question carries on where it left off.
 * Both are null while the player was between two questions.
 */
@Serializable
data class MatchResumeDto(
    val matchId: String,
    val opponent: DuoPlayerDto,
    val settings: DuoSettingsDto,
    val deckSize: Int = 0,
    val serverTimeMs: Long = 0,
    val deadlineAt: Long = 0,
    val token: String? = null,
    val question: ChallengeDto? = null,
    val pushedAt: Long = 0,
    val yourDeckRemaining: Int = 0,
    val opponentDeckRemaining: Int = 0,
    val yourScore: Int = 0,
    val opponentScore: Int = 0,
    val yourHp: Int = 0,
    val yourMaxHp: Int = 0,
    val opponentHp: Int = 0,
    val opponentMaxHp: Int = 0,
    val yourMana: Int = 0,
    val yourCombo: Int = 0,
    val opponentCombo: Int = 0,
    val lockoutEndsAt: Long = 0,
    val stunnedUntil: Long = 0,
    val effects: List<DuoActiveEffectDto> = emptyList(),
)

@Serializable
data class OpponentDisconnectedDto(val graceSeconds: Int)

@Serializable
data class RatingChangeDto(
    val before: Int,
    val after: Int,
    val delta: Int,
)

@Serializable
data class DuoExpChangeDto(
    val before: Int,
    val after: Int,
    val delta: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val leveledUp: Boolean,
)

@Serializable
data class DuoGoldChangeDto(
    val before: Int,
    val after: Int,
    val delta: Int,
)

@Serializable
data class DuoLootDto(
    val code: String,
    val name: String,
    /** COMMON / RARE / EPIC / LEGENDARY, kept as a string so a new rarity cannot break decoding. */
    val rarity: String = "",
)

/** The season ladder moves separately from the all-time rating, and can promote a player. */
@Serializable
data class DuoSeasonChangeDto(
    val seasonCode: String,
    val ratingBefore: Int,
    val ratingAfter: Int,
    val tierBefore: String = "",
    val tierAfter: String = "",
    val promoted: Boolean = false,
)

@Serializable
data class DuoStreakDto(
    val dayStreak: Int,
    val bestDayStreak: Int,
    val extended: Boolean,
)

@Serializable
data class MatchFinishedDto(
    val matchId: String,
    val result: MatchOutcome,
    val endReason: DuoMatchEndReason,
    val yourScore: Int,
    val opponentScore: Int,
    val yourCorrect: Int,
    val opponentCorrect: Int,
    val deckSize: Int = 0,
    val yourDeckCleared: Boolean = false,
    val opponentDeckCleared: Boolean = false,
    val durationSeconds: Int,
    val rating: RatingChangeDto,
    val exp: DuoExpChangeDto? = null,
    val gold: DuoGoldChangeDto? = null,
    val yourHpLeft: Int = 0,
    val opponentHpLeft: Int = 0,
    val loot: DuoLootDto? = null,
    val season: DuoSeasonChangeDto? = null,
    val streak: DuoStreakDto? = null,
    val energyLeft: Int? = null,
    /** Daily quests this match finished for the player it was sent to. */
    val questsCompleted: List<QuestCompletedDto> = emptyList(),
)

/**
 * Sent to *both* players, so each sees what the other cast. [private] is filled in only for the
 * caster -- drawing it for the wrong side would hand the opponent's reveal over.
 */
@Serializable
data class DuoSkillUsedDto(
    val userId: String,
    val skillCode: String,
    val skillName: String,
    /** The effect name from the server's catalog; shown, never branched on for correctness. */
    val effect: String = "",
    val magnitude: Int = 0,
    val manaSpent: Int = 0,
    val yourHp: Int = 0,
    val opponentHp: Int = 0,
    val yourMana: Int = 0,
    /** When the caster may fire this again. Zero for the other side, which has no business
     * knowing -- and what the dock greys the slot out until. */
    val readyAgainAt: Long = 0,
    /** This player's own lockout, which a TIME_PENALTY landing on them pushes out. */
    val lockoutEndsAt: Long = 0,
    val private: DuoSkillPrivateDto? = null,
)

@Serializable
data class DuoSkillPrivateDto(
    val removedOptionIds: List<String> = emptyList(),
)

/** Echoed back beside the server's own clock, so the client can work out its offset. */
@Serializable
data class DuoPongDto(
    val clientTimeMs: Long = 0,
    val serverTimeMs: Long = 0,
)

/** Broadcast to both players, including whoever sent it. */
@Serializable
data class ChatMessageDto(
    val userId: String,
    val message: String,
    val sentAt: String,
)

/** [code] stays a raw string on the wire so an error code added on the server later degrades to
 * [DuoErrorCode.UNKNOWN] instead of crashing the decoder. */
@Serializable
data class ErrorDto(
    val code: String = "",
    val message: String = "",
) {
    val errorCode: DuoErrorCode
        get() = DuoErrorCode.entries.firstOrNull { it.name == code } ?: DuoErrorCode.UNKNOWN
}

/** Stable error codes from `app/schemas/duo/events.py`, plus a client-side catch-all. */
enum class DuoErrorCode {
    INVALID_PAYLOAD,

    /** Frames arrived faster than a person could send them; the server dropped the extra ones. */
    RATE_LIMITED,
    UNKNOWN_EVENT,
    ALREADY_IN_MATCH,
    ALREADY_IN_QUEUE,
    NOT_IN_QUEUE,
    ROOM_NOT_FOUND,
    ROOM_FULL,
    NOT_HOST,
    NOT_ENOUGH_PLAYERS,
    MATCH_ALREADY_STARTED,
    NOT_IN_MATCH,

    /**
     * Answered a question that is no longer the one on screen -- a late frame, a replayed tap, or
     * a tap during the pause after the previous answer. Replaces the lock-step engine's
     * ROUND_CLOSED / ALREADY_ANSWERED pair, neither of which means anything without rounds.
     */
    QUESTION_CLOSED,
    INVALID_OPTION,
    NO_QUESTIONS_AVAILABLE,
    STUNNED,
    SKILL_NOT_EQUIPPED,
    SKILL_ON_COOLDOWN,
    NOT_ENOUGH_MANA,
    NOT_ENOUGH_ENERGY,
    UNKNOWN,
    ;

    /**
     * True for the codes that are ordinary play rather than something worth a dialog: a skill
     * recharging, an empty mana bar, a stun. The match screen flashes these on the skill that was
     * tapped instead of interrupting the fight.
     */
    val isInMatchNudge: Boolean
        get() = this == STUNNED || this == SKILL_ON_COOLDOWN ||
            this == NOT_ENOUGH_MANA || this == SKILL_NOT_EQUIPPED || this == QUESTION_CLOSED
}
