package com.kma.quiz_game.ui.screens.conversation

import com.kma.quiz_game.data.remote.dto.ConversationDetailDto
import com.kma.quiz_game.data.remote.dto.ConversationMessageDto
import com.kma.quiz_game.data.remote.dto.ConversationMessageRole
import com.kma.quiz_game.data.remote.dto.ConversationStatus
import com.kma.quiz_game.data.remote.dto.ConversationSummaryDto
import com.kma.quiz_game.data.remote.dto.ConversationTurnDto
import com.kma.quiz_game.data.remote.dto.HintSuggestionDto
import com.kma.quiz_game.data.remote.dto.ScenarioDto
import com.kma.quiz_game.data.remote.toUserMessage
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import retrofit2.HttpException

/** Mirrors `MAX_MESSAGE_LENGTH` in the backend's conversation schema. */
const val MAX_MESSAGE_LENGTH = 500

/**
 * What a failed conversation request says to the learner.
 *
 * A 429 is two different things here: the per-minute rate limit, which carries a `Retry-After`
 * and is worded by [toUserMessage], and the daily cap on conversations, which does not.
 */
fun Throwable.toConversationMessage(): String =
    if (this is HttpException && code() == 429 && response()?.headers()?.get("Retry-After") == null) {
        "Bạn đã dùng hết lượt luyện hội thoại hôm nay. Hãy quay lại vào ngày mai nhé."
    } else {
        toUserMessage(
            byStatus = mapOf(
                400 to "Hãy nói ít nhất một câu trước khi kết thúc.",
                404 to "Không tìm thấy cuộc hội thoại này.",
                409 to "Cuộc hội thoại đã thay đổi, đã tải lại.",
                503 to "AI đang bận hoặc tạm thời không khả dụng. Vui lòng thử lại sau.",
            ),
        )
    }

/** Scenarios under their category, categories in the order the server lists them. */
fun groupScenarios(scenarios: List<ScenarioDto>): List<Pair<String, List<ScenarioDto>>> =
    scenarios.groupBy { it.categoryVi }.toList()

/** "A1–A2", or null for a scenario open to every level. */
fun levelLabel(levels: List<String>): String? = when (levels.size) {
    0 -> null
    1 -> levels.first()
    else -> "${levels.first()}–${levels.last()}"
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")

/** A server timestamp in the device's own time, or the raw string if it cannot be read. */
fun formatTimestamp(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(DATE_FORMAT) }.getOrDefault(iso)

// --- topics -------------------------------------------------------------------------------------

data class ConversationTopicsUiState(
    val isLoading: Boolean = true,
    val scenarios: List<ScenarioDto> = emptyList(),
    val errorMessage: String? = null,
    /** The scenario whose brief is open, waiting for "Bắt đầu". */
    val selected: ScenarioDto? = null,
    val isStarting: Boolean = false,
    val startErrorMessage: String? = null,
    /** Set once a conversation is opened, for the screen to navigate to and then consume. */
    val startedSessionId: String? = null,
) {
    val groups: List<Pair<String, List<ScenarioDto>>> get() = groupScenarios(scenarios)
}

// --- chat ---------------------------------------------------------------------------------------

/** One bubble. [isLocal] is a learner line shown before the server has confirmed it. */
data class ChatLine(
    val id: String,
    val role: ConversationMessageRole,
    val content: String,
    val translationVi: String? = null,
    val isLocal: Boolean = false,
)

private fun ConversationMessageDto.toLine() =
    ChatLine(id = id, role = role, content = content, translationVi = translationVi)

/**
 * The spoken side of a conversation: the AI reading its lines aloud, and the learner answering by
 * voice. Separate from the rest so the chat state still reads as a chat.
 */
data class VoiceUiState(
    /** The phone has an English voice to read with. */
    val canSpeak: Boolean = false,
    /** The learner has not muted the AI. */
    val voiceOn: Boolean = true,
    val speakingLineId: String? = null,
    /** The phone has a speech recognition service at all. */
    val micAvailable: Boolean = false,
    val isListening: Boolean = false,
    val partialText: String = "",
    /** Loudness while listening, 0..1. */
    val level: Float = 0f,
)

data class ConversationChatUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val scenario: ScenarioDto? = null,
    val cefr: String = "",
    val status: ConversationStatus = ConversationStatus.ACTIVE,
    val lines: List<ChatLine> = emptyList(),
    val userTurns: Int = 0,
    val maxTurns: Int = 0,
    val shouldFinish: Boolean = false,
    /** The server holds the learner's last line and has no reply to it: only a retry can move on. */
    val awaitingReply: Boolean = false,
    /** A line that failed to send and whose fate is unknown, because the server could not be
     * re-read either. Retrying finds out whether it arrived before sending it again. */
    val unsentContent: String? = null,
    val input: String = "",
    val isSending: Boolean = false,
    val showBrief: Boolean = true,
    val shownTranslationIds: Set<String> = emptySet(),
    val translatingIds: Set<String> = emptySet(),
    /** Non-null while the hint sheet is open. */
    val hints: List<HintSuggestionDto>? = null,
    val isLoadingHints: Boolean = false,
    val showFinishDialog: Boolean = false,
    val isFinishing: Boolean = false,
    val errorMessage: String? = null,
    /** Set once feedback exists, for the screen to navigate to and then consume. */
    val finishedSessionId: String? = null,
    val voice: VoiceUiState = VoiceUiState(),
) {
    val isClosed: Boolean get() = status == ConversationStatus.FINISHED || shouldFinish

    /** The input is usable: nothing is stuck and the conversation still takes lines. */
    val canType: Boolean
        get() = !isLoading && scenario != null && !isClosed && !awaitingReply && unsentContent == null

    val canSend: Boolean
        get() = canType && !isSending && input.isNotBlank() && input.trim().length <= MAX_MESSAGE_LENGTH

    /** A spoken line is sent as soon as it is heard, so the mic follows the same rule as sending. */
    val canListen: Boolean get() = voice.micAvailable && canType && !isSending && !isFinishing

    val needsRetry: Boolean get() = !isSending && (awaitingReply || unsentContent != null)

    val canAskHint: Boolean get() = canType && !isSending && !isLoadingHints

    /** The server refuses feedback on a conversation the learner has not said anything in. */
    val canFinish: Boolean get() = userTurns > 0 && !isSending && !isFinishing

    val turnsLabel: String get() = "$userTurns/$maxTurns lượt"

    /** Everything the server knows, keeping what only this screen knows (draft, open translations). */
    fun withDetail(detail: ConversationDetailDto): ConversationChatUiState = copy(
        isLoading = false,
        loadErrorMessage = null,
        scenario = detail.scenario,
        cefr = detail.cefr,
        status = detail.status,
        lines = detail.messages.sortedBy { it.seq }.map { it.toLine() },
        userTurns = detail.userTurns,
        maxTurns = detail.maxTurns,
        shouldFinish = detail.shouldFinish,
        awaitingReply = detail.awaitingReply,
        unsentContent = null,
        // A conversation already under way does not need its brief in the way.
        showBrief = showBrief && detail.userTurns == 0,
    )

    /**
     * The AI's opening line, when the conversation has only just begun: nothing said by the
     * learner yet and nothing else from the AI. A conversation reopened later is not read again.
     */
    val openingLine: ChatLine?
        get() = lines.singleOrNull()?.takeIf { userTurns == 0 && it.role == ConversationMessageRole.ASSISTANT }

    fun withVoice(change: VoiceUiState.() -> VoiceUiState): ConversationChatUiState = copy(voice = voice.change())

    fun withLocalLine(content: String, localId: String): ConversationChatUiState = copy(
        lines = lines + ChatLine(localId, ConversationMessageRole.USER, content, isLocal = true),
    )

    /**
     * A completed exchange. The learner's line replaces its optimistic copy ([localId]) -- or the
     * stored line with the same id, when this is the reply to a retried one.
     */
    fun withTurn(turn: ConversationTurnDto, localId: String? = null): ConversationChatUiState {
        val replaced = setOf(localId, turn.userMessage.id, turn.assistantMessage.id)
        return copy(
            lines = lines.filterNot { it.id in replaced } +
                turn.userMessage.toLine() +
                turn.assistantMessage.toLine(),
            userTurns = turn.userTurns,
            maxTurns = turn.maxTurns,
            shouldFinish = turn.shouldFinish,
            awaitingReply = false,
            unsentContent = null,
        )
    }

    fun withTranslation(messageId: String, translation: String): ConversationChatUiState = copy(
        lines = lines.map { if (it.id == messageId) it.copy(translationVi = translation) else it },
        shownTranslationIds = shownTranslationIds + messageId,
        translatingIds = translatingIds - messageId,
    )
}

// --- feedback -----------------------------------------------------------------------------------

data class ConversationFeedbackUiState(
    val isLoading: Boolean = true,
    val detail: ConversationDetailDto? = null,
    val errorMessage: String? = null,
    val showTranscript: Boolean = false,
    val isStartingAgain: Boolean = false,
    val startedSessionId: String? = null,
)

/** A word for the score, so a number alone does not have to carry the verdict. */
fun scoreLabel(score: Int): String = when {
    score >= 85 -> "Xuất sắc"
    score >= 70 -> "Tốt"
    score >= 50 -> "Khá"
    else -> "Cần luyện thêm"
}

// --- history ------------------------------------------------------------------------------------

const val HISTORY_PAGE_SIZE = 20

data class ConversationHistoryUiState(
    val isLoading: Boolean = true,
    val items: List<ConversationSummaryDto> = emptyList(),
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val pendingDelete: ConversationSummaryDto? = null,
)
