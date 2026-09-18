package com.kma.quiz_game.data.remote.dto

import kotlinx.serialization.Serializable

/*
 * AI conversation practice: `duo-game-back/app/schemas/conversation/conversation.py`.
 *
 * Field names are camelCase here and snake_case on the wire through the app-wide
 * `JsonNamingStrategy.SnakeCase` -- `translationVi` is `translation_vi`.
 */

enum class ConversationMessageRole { USER, ASSISTANT }

enum class ConversationStatus { ACTIVE, FINISHED }

/** One everyday situation to practise in. The `setting`/`aiRole`/`userRole` text is English: it is
 * what the AI is told, shown as-is for the learner to read. */
@Serializable
data class ScenarioDto(
    val code: String,
    val titleVi: String,
    val categoryVi: String,
    val suggestedLevels: List<String> = emptyList(),
    val setting: String,
    val aiRole: String,
    val userRole: String,
    /** Null for open conversation, which has nothing to complete. */
    val goalVi: String? = null,
    val usefulPhrases: List<String> = emptyList(),
    val maxTurns: Int,
)

/** No defaults on purpose: the app's JSON config does not encode default values. */
@Serializable
data class ConversationStartRequest(val scenarioCode: String)

@Serializable
data class ConversationMessageRequest(val content: String)

@Serializable
data class ConversationMessageDto(
    val id: String,
    val seq: Int,
    val role: ConversationMessageRole,
    val content: String,
    /** Filled the first time the learner asks for it, then served from the server's copy. */
    val translationVi: String? = null,
    val createdAt: String,
)

@Serializable
data class ConversationSummaryDto(
    val id: String,
    val scenarioCode: String,
    val titleVi: String,
    val status: ConversationStatus,
    val userTurns: Int,
    val maxTurns: Int,
    val score: Int? = null,
    val startedAt: String,
    val finishedAt: String? = null,
)

@Serializable
data class CorrectionDto(
    val original: String,
    val corrected: String,
    val explanationVi: String,
)

@Serializable
data class BetterPhraseDto(
    val original: String,
    val natural: String,
    val noteVi: String,
)

@Serializable
data class ConversationFeedbackDto(
    val score: Int,
    /** Null for open conversation. */
    val goalCompleted: Boolean? = null,
    val summaryVi: String,
    val corrections: List<CorrectionDto> = emptyList(),
    val betterPhrases: List<BetterPhraseDto> = emptyList(),
    val newWords: List<String> = emptyList(),
)

@Serializable
data class ConversationDetailDto(
    val id: String,
    val scenario: ScenarioDto,
    val cefr: String,
    val status: ConversationStatus,
    val userTurns: Int,
    val maxTurns: Int,
    /** The AI marked the goal done, or the turns ran out: time to ask for feedback. */
    val shouldFinish: Boolean,
    /** The last message is the learner's and has no reply yet: it can only be retried. */
    val awaitingReply: Boolean,
    val messages: List<ConversationMessageDto>,
    val feedback: ConversationFeedbackDto? = null,
    val startedAt: String,
    val finishedAt: String? = null,
)

/** One exchange: the learner's line as stored, and the AI's reply to it. */
@Serializable
data class ConversationTurnDto(
    val userMessage: ConversationMessageDto,
    val assistantMessage: ConversationMessageDto,
    val userTurns: Int,
    val maxTurns: Int,
    val shouldFinish: Boolean,
)

@Serializable
data class HintSuggestionDto(val en: String, val vi: String)

@Serializable
data class ConversationHintDto(val suggestions: List<HintSuggestionDto>)

@Serializable
data class ConversationTranslationDto(val messageId: String, val translationVi: String)
