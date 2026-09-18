package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.api.ConversationApi
import com.kma.quiz_game.data.remote.dto.ConversationDetailDto
import com.kma.quiz_game.data.remote.dto.ConversationHintDto
import com.kma.quiz_game.data.remote.dto.ConversationMessageRequest
import com.kma.quiz_game.data.remote.dto.ConversationStartRequest
import com.kma.quiz_game.data.remote.dto.ConversationSummaryDto
import com.kma.quiz_game.data.remote.dto.ConversationTranslationDto
import com.kma.quiz_game.data.remote.dto.ConversationTurnDto
import com.kma.quiz_game.data.remote.dto.ScenarioDto
import com.kma.quiz_game.data.remote.throwIfUnsuccessful

/**
 * AI conversation practice. Nothing is cached: a conversation is only ever open on one screen, and
 * the server's copy -- turns used, whether the last line got its reply -- is what decides what that
 * screen may do next.
 */
class ConversationRepository(private val api: ConversationApi) {

    suspend fun scenarios(): Result<List<ScenarioDto>> = runCatching { api.listScenarios() }

    suspend fun start(scenarioCode: String): Result<ConversationDetailDto> =
        runCatching { api.start(ConversationStartRequest(scenarioCode)) }

    suspend fun history(limit: Int, before: String? = null): Result<List<ConversationSummaryDto>> =
        runCatching { api.list(limit, before) }

    suspend fun get(sessionId: String): Result<ConversationDetailDto> = runCatching { api.get(sessionId) }

    suspend fun send(sessionId: String, content: String): Result<ConversationTurnDto> =
        runCatching { api.send(sessionId, ConversationMessageRequest(content)) }

    suspend fun retry(sessionId: String): Result<ConversationTurnDto> = runCatching { api.retry(sessionId) }

    suspend fun hint(sessionId: String): Result<ConversationHintDto> = runCatching { api.hint(sessionId) }

    suspend fun translate(sessionId: String, messageId: String): Result<ConversationTranslationDto> =
        runCatching { api.translate(sessionId, messageId) }

    suspend fun finish(sessionId: String): Result<ConversationDetailDto> = runCatching { api.finish(sessionId) }

    suspend fun delete(sessionId: String): Result<Unit> =
        runCatching { api.delete(sessionId).throwIfUnsuccessful() }
}
