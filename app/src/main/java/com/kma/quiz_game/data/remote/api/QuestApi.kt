package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.DailyQuestsDto
import com.kma.quiz_game.data.remote.dto.QuestClaimDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Daily quests. The server draws a player's four on the day's first look and counts progress
 * itself at the end of every battle, match, lesson and AI conversation -- the app only reads and
 * claims. Every endpoint needs a bearer token.
 */
interface QuestApi {
    @GET("quests/daily")
    suspend fun getDaily(): DailyQuestsDto

    /** 400 before the quest is done, 409 once collected or when it belongs to an earlier day. */
    @POST("quests/daily/{questId}/claim")
    suspend fun claimQuest(@Path("questId") questId: String): QuestClaimDto

    /** [milestone] is 30, 60 or 100. 400 before today's points reach it, 409 once opened. */
    @POST("quests/daily/chests/{milestone}/claim")
    suspend fun claimChest(@Path("milestone") milestone: Int): QuestClaimDto
}
