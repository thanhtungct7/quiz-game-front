package com.kma.quiz_game.ui.screens.quests

import com.kma.quiz_game.data.remote.dto.ActivityChestDto
import com.kma.quiz_game.data.remote.dto.DailyQuestsDto
import com.kma.quiz_game.data.remote.dto.QuestRewardDto
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** What a chest on the activity bar looks like right now. */
enum class ChestState { LOCKED, READY, OPENED }

fun ActivityChestDto.state(): ChestState = when {
    claimed -> ChestState.OPENED
    reached -> ChestState.READY
    else -> ChestState.LOCKED
}

/**
 * The popup a claim opens: what was collected and what it paid. [chestTier] is set for a chest,
 * which draws the chest opening; a quest draws its title instead.
 */
data class QuestCelebration(
    val title: String,
    val reward: QuestRewardDto,
    val chestTier: String? = null,
)

data class DailyQuestsUiState(
    val isLoading: Boolean = true,
    val day: DailyQuestsDto? = null,
    /** The quest id or [chestKey] being claimed, so its button shows it and a second tap does
     * nothing. */
    val claiming: String? = null,
    val celebration: QuestCelebration? = null,
    val errorMessage: String? = null,
)

fun chestKey(milestone: Int): String = "chest-$milestone"

/** Server difficulty codes, said the way the quest card says them. Unknown codes read as easy. */
fun difficultyLabel(difficulty: String): String = when (difficulty.uppercase()) {
    "HARD" -> "Khó"
    "MEDIUM" -> "Vừa"
    else -> "Dễ"
}

/** The chest's sprite under `assets/`. Unknown tiers draw as bronze, the plainest chest. */
fun chestArt(tier: String): String = when (tier.uppercase()) {
    "GOLD" -> "chests/gold.png"
    "SILVER" -> "chests/silver.png"
    else -> "chests/bronze.png"
}

/**
 * Time left until the set expires, as `h:mm:ss`. Zero once it has -- the screen reloads then and
 * the server hands over the new day's set.
 */
fun countdown(resetsAt: String, now: Instant): String {
    val end = runCatching { OffsetDateTime.parse(resetsAt).toInstant() }.getOrNull() ?: return ""
    val left = Duration.between(now, end).coerceAtLeast(Duration.ZERO)
    val seconds = left.seconds
    return "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}

fun hasExpired(resetsAt: String, now: Instant): Boolean {
    val end = runCatching { OffsetDateTime.parse(resetsAt).toInstant() }.getOrNull() ?: return false
    return !now.isBefore(end)
}

/** Messages for the claim failures a learner can actually cause, in place of the server's English. */
val CLAIM_ERRORS: Map<Int, String> = mapOf(
    400 to "Chưa đủ điều kiện nhận thưởng này.",
    404 to "Không tìm thấy phần thưởng này.",
    409 to "Phần thưởng này đã được nhận, hoặc đã sang ngày mới.",
)
