package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.ConversationDetailDto
import com.kma.quiz_game.data.remote.dto.ConversationHintDto
import com.kma.quiz_game.data.remote.dto.ConversationMessageRequest
import com.kma.quiz_game.data.remote.dto.ConversationStartRequest
import com.kma.quiz_game.data.remote.dto.ConversationSummaryDto
import com.kma.quiz_game.data.remote.dto.ConversationTranslationDto
import com.kma.quiz_game.data.remote.dto.ConversationTurnDto
import com.kma.quiz_game.data.remote.dto.ScenarioDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * AI conversation practice. Every endpoint needs a bearer token.
 *
 * Statuses a screen has to tell apart: 503 when the AI is unavailable (no key on the server, or
 * the provider failed), 429 for the daily conversation cap as well as ordinary rate limiting, 409
 * when a conversation is closed or its last line is still waiting for a reply.
 */
interface ConversationApi {
    @GET("conversations/scenarios")
    suspend fun listScenarios(): List<ScenarioDto>

    /** Opens a conversation. The AI's opening line comes back in `messages`; no AI call is made. */
    @POST("conversations")
    suspend fun start(@Body body: ConversationStartRequest): ConversationDetailDto

    /** Newest first. [before] is the last row's `started_at`, for the next page. */
    @GET("conversations")
    suspend fun list(
        @Query("limit") limit: Int,
        @Query("before") before: String? = null,
    ): List<ConversationSummaryDto>

    @GET("conversations/{sessionId}")
    suspend fun get(@Path("sessionId") sessionId: String): ConversationDetailDto

    /** One line and the AI's reply. On 503 the line is stored anyway: call [retry] for it. */
    @POST("conversations/{sessionId}/messages")
    suspend fun send(
        @Path("sessionId") sessionId: String,
        @Body body: ConversationMessageRequest,
    ): ConversationTurnDto

    @POST("conversations/{sessionId}/retry")
    suspend fun retry(@Path("sessionId") sessionId: String): ConversationTurnDto

    @POST("conversations/{sessionId}/hint")
    suspend fun hint(@Path("sessionId") sessionId: String): ConversationHintDto

    @POST("conversations/{sessionId}/messages/{messageId}/translate")
    suspend fun translate(
        @Path("sessionId") sessionId: String,
        @Path("messageId") messageId: String,
    ): ConversationTranslationDto

    /** Ends the conversation and returns it with its feedback. Safe to repeat: no second AI call. */
    @POST("conversations/{sessionId}/finish")
    suspend fun finish(@Path("sessionId") sessionId: String): ConversationDetailDto

    /** 204. */
    @DELETE("conversations/{sessionId}")
    suspend fun delete(@Path("sessionId") sessionId: String): Response<Unit>
}
