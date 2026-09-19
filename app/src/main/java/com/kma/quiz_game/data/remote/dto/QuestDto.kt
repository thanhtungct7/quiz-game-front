package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire types for the `quests` endpoints: today's four daily quests and the three activity chests
 * their points open.
 *
 * `questType`, `difficulty` and `tier` stay raw strings for the reason [GameProfileDto]'s catalog
 * values do: the server adds a kind of quest with a row in its catalog, and a new row must not
 * break decoding in an app that has not heard of it yet.
 */

@Serializable
data class DailyQuestDto(
    val id: String,
    val code: String,
    val questType: String,
    val difficulty: String,
    /** Ready to show, target included: "Trả lời đúng 15 câu". */
    val title: String,
    val progress: Int,
    val target: Int,
    val activityPoints: Int,
    val rewardGold: Int,
    val rewardExp: Int,
    val completed: Boolean,
    val claimed: Boolean,
) {
    val fraction: Float get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)

    val claimable: Boolean get() = completed && !claimed
}

@Serializable
data class ActivityChestDto(
    val tier: String,
    val name: String,
    val milestone: Int,
    val rewardGold: Int,
    val rewardExp: Int,
    /** The gold chest also rolls one item from the loot table. */
    val rollsItem: Boolean = false,
    val reached: Boolean,
    val claimed: Boolean,
) {
    val claimable: Boolean get() = reached && !claimed
}

@Serializable
data class DailyQuestsDto(
    /** The learner's calendar day (Vietnam time) the set belongs to, `YYYY-MM-DD`. */
    val questDate: String,
    /** When the set expires, ISO-8601 UTC: the next local midnight. */
    val resetsAt: String,
    val activityPoints: Int,
    val maxActivityPoints: Int,
    val quests: List<DailyQuestDto> = emptyList(),
    val chests: List<ActivityChestDto> = emptyList(),
    /** Finished quests and reached chests not collected yet -- the red dot on the way in. */
    val claimableCount: Int = 0,
) {
    val questsDone: Int get() = quests.count { it.completed }

    val fraction: Float
        get() = if (maxActivityPoints <= 0) 0f else (activityPoints.toFloat() / maxActivityPoints).coerceIn(0f, 1f)
}

@Serializable
data class QuestExpChangeDto(
    val before: Int,
    val after: Int,
    val delta: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val leveledUp: Boolean,
)

@Serializable
data class QuestGoldChangeDto(
    val before: Int,
    val after: Int,
    val delta: Int,
)

@Serializable
data class QuestLootDto(
    val code: String,
    val name: String,
    val rarity: String,
)

@Serializable
data class QuestRewardDto(
    val exp: QuestExpChangeDto,
    val gold: QuestGoldChangeDto,
    val loot: QuestLootDto? = null,
)

/** What a claim paid, and the day as it stands afterwards -- so nothing is left to re-fetch. */
@Serializable
data class QuestClaimDto(
    val reward: QuestRewardDto,
    val quests: DailyQuestsDto,
)

/** A quest the battle or match just played finished, as its result screen names it. */
@Serializable
data class QuestCompletedDto(
    val id: String,
    val title: String,
    val activityPoints: Int,
)
