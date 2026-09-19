package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.api.QuestApi
import com.kma.quiz_game.data.remote.dto.DailyQuestsDto
import com.kma.quiz_game.data.remote.dto.QuestClaimDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Today's daily quests, cached in a flow for the two places that draw them: the banner on the
 * learn path and the quest screen itself.
 *
 * Nothing is counted here. The server moves quests at the end of every battle, match, lesson and
 * AI conversation, so the cached day is refreshed rather than patched -- after a claim from the
 * claim's own response, otherwise whenever a screen that shows it comes back into view.
 *
 * A claim pays gold and experience, so the two cached standings that show those -- the game
 * profile (gold, level cap) and the aggregated card (level, CEFR band on the path header) -- are
 * refreshed after it, best effort.
 */
class QuestRepository(
    private val questApi: QuestApi,
    private val gameRepository: GameRepository,
    private val profileRepository: ProfileRepository,
) {

    private val _today = MutableStateFlow<DailyQuestsDto?>(null)
    val today: StateFlow<DailyQuestsDto?> = _today.asStateFlow()

    suspend fun refresh(): Result<DailyQuestsDto> =
        runCatching { questApi.getDaily() }.onSuccess { _today.value = it }

    suspend fun claimQuest(questId: String): Result<QuestClaimDto> =
        runCatching { questApi.claimQuest(questId) }.onSuccess { onClaimed(it) }

    suspend fun claimChest(milestone: Int): Result<QuestClaimDto> =
        runCatching { questApi.claimChest(milestone) }.onSuccess { onClaimed(it) }

    private suspend fun onClaimed(claim: QuestClaimDto) {
        _today.value = claim.quests
        gameRepository.refreshProfile()
        profileRepository.refreshSelfProfile()
    }

    /** Logout: the next account must not see this one's quests. */
    fun clear() {
        _today.value = null
    }
}
