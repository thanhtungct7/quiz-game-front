package com.kma.quiz_game.ui.screens.conversation

import com.kma.quiz_game.data.remote.dto.ConversationDetailDto
import com.kma.quiz_game.data.remote.dto.ConversationMessageDto
import com.kma.quiz_game.data.remote.dto.ConversationMessageRole
import com.kma.quiz_game.data.remote.dto.ConversationStatus
import com.kma.quiz_game.data.remote.dto.ConversationTurnDto
import com.kma.quiz_game.data.remote.dto.ScenarioDto
import java.time.ZoneId
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** The rules the chat screen draws from: what may be sent, and how server answers fold into the lines. */
class ConversationUiStateTest {

    private val scenario = ScenarioDto(
        code = "cafe_order",
        titleVi = "Gọi đồ ở quán cà phê",
        categoryVi = "Ăn uống",
        suggestedLevels = listOf("A1", "A2"),
        setting = "A cafe.",
        aiRole = "a barista",
        userRole = "a customer",
        goalVi = "Gọi đồ",
        usefulPhrases = listOf("I'd like..."),
        maxTurns = 12,
    )

    private fun message(id: String, seq: Int, role: ConversationMessageRole, content: String = id) =
        ConversationMessageDto(id = id, seq = seq, role = role, content = content, createdAt = "2026-09-17T04:00:00Z")

    private fun detail(
        messages: List<ConversationMessageDto>,
        userTurns: Int = 0,
        awaitingReply: Boolean = false,
        shouldFinish: Boolean = false,
        status: ConversationStatus = ConversationStatus.ACTIVE,
    ) = ConversationDetailDto(
        id = "s1",
        scenario = scenario,
        cefr = "A2",
        status = status,
        userTurns = userTurns,
        maxTurns = 12,
        shouldFinish = shouldFinish,
        awaitingReply = awaitingReply,
        messages = messages,
        startedAt = "2026-09-17T04:00:00Z",
    )

    private val opened = ConversationChatUiState().withDetail(
        detail(listOf(message("m1", 1, ConversationMessageRole.ASSISTANT))),
    )

    @Test
    fun `a message can be sent only when there is text and nothing is stuck`() {
        assertFalse(opened.canSend)
        assertTrue(opened.copy(input = "Hello").canSend)
        assertFalse(opened.copy(input = "   ").canSend)
        assertFalse(opened.copy(input = "Hello", isSending = true).canSend)
        assertFalse(opened.copy(input = "Hello", awaitingReply = true).canSend)
        assertFalse(opened.copy(input = "Hello", unsentContent = "Hi").canSend)
        assertFalse(opened.copy(input = "Hello", shouldFinish = true).canSend)
        assertFalse(opened.copy(input = "x".repeat(MAX_MESSAGE_LENGTH + 1)).canSend)
    }

    @Test
    fun `feedback needs at least one line from the learner`() {
        assertFalse(opened.canFinish)
        assertTrue(opened.copy(userTurns = 1).canFinish)
        assertFalse(opened.copy(userTurns = 1, isSending = true).canFinish)
    }

    @Test
    fun `a stored line without a reply asks for a retry`() {
        val stuck = ConversationChatUiState().withDetail(
            detail(
                listOf(message("m1", 1, ConversationMessageRole.ASSISTANT), message("m2", 2, ConversationMessageRole.USER)),
                userTurns = 1,
                awaitingReply = true,
            ),
        )

        assertTrue(stuck.needsRetry)
        assertFalse(stuck.canType)
        assertFalse(stuck.copy(isSending = true).needsRetry)
    }

    @Test
    fun `a turn replaces the optimistic line and appends the reply`() {
        val pending = opened.withLocalLine("A latte", "local-0")
        assertTrue(pending.lines.last().isLocal)

        val turn = ConversationTurnDto(
            userMessage = message("m2", 2, ConversationMessageRole.USER, "A latte"),
            assistantMessage = message("m3", 3, ConversationMessageRole.ASSISTANT, "Sure!"),
            userTurns = 1,
            maxTurns = 12,
            shouldFinish = false,
        )
        val after = pending.withTurn(turn, "local-0")

        assertEquals(listOf("m1", "m2", "m3"), after.lines.map { it.id })
        assertFalse(after.lines.any { it.isLocal })
        assertEquals("1/12 lượt", after.turnsLabel)
    }

    @Test
    fun `a retried reply does not duplicate the stored line`() {
        val stuck = ConversationChatUiState().withDetail(
            detail(
                listOf(message("m1", 1, ConversationMessageRole.ASSISTANT), message("m2", 2, ConversationMessageRole.USER)),
                userTurns = 1,
                awaitingReply = true,
            ),
        )
        val turn = ConversationTurnDto(
            userMessage = message("m2", 2, ConversationMessageRole.USER),
            assistantMessage = message("m3", 3, ConversationMessageRole.ASSISTANT),
            userTurns = 1,
            maxTurns = 12,
            shouldFinish = true,
        )

        val after = stuck.withTurn(turn)

        assertEquals(listOf("m1", "m2", "m3"), after.lines.map { it.id })
        assertFalse(after.awaitingReply)
        assertTrue(after.isClosed)
    }

    @Test
    fun `the brief folds away once the conversation is under way`() {
        assertTrue(opened.showBrief)
        val resumed = ConversationChatUiState().withDetail(
            detail(listOf(message("m1", 1, ConversationMessageRole.ASSISTANT)), userTurns = 3),
        )
        assertFalse(resumed.showBrief)
    }

    @Test
    fun `a translation is stored on its line and shown`() {
        val translating = opened.copy(translatingIds = setOf("m1"))

        val after = translating.withTranslation("m1", "Xin chào!")

        assertEquals("Xin chào!", after.lines.single().translationVi)
        assertTrue("m1" in after.shownTranslationIds)
        assertTrue(after.translatingIds.isEmpty())
    }

    @Test
    fun `scenarios group by category in server order and levels read as a range`() {
        val other = scenario.copy(code = "free_talk", categoryVi = "Tự do", suggestedLevels = emptyList())
        val groups = groupScenarios(listOf(scenario, other, scenario.copy(code = "b")))

        assertEquals(listOf("Ăn uống", "Tự do"), groups.map { it.first })
        assertEquals(listOf("cafe_order", "b"), groups.first().second.map { it.code })
        assertEquals("A1–A2", levelLabel(scenario.suggestedLevels))
        assertEquals("B1", levelLabel(listOf("B1")))
        assertNull(levelLabel(emptyList()))
    }

    @Test
    fun `timestamps show in local time`() {
        assertEquals("11:08 17/09/2026", formatTimestamp("2026-09-17T04:08:26.196834Z", ZoneId.of("Asia/Ho_Chi_Minh")))
        assertEquals("garbage", formatTimestamp("garbage"))
    }

    @Test
    fun `the daily cap is told apart from rate limiting`() {
        val dailyCap = HttpException(
            Response.error<Any>(429, """{"detail": "limit"}""".toResponseBody("application/json".toMediaType())),
        )
        assertEquals(
            "Bạn đã dùng hết lượt luyện hội thoại hôm nay. Hãy quay lại vào ngày mai nhé.",
            dailyCap.toConversationMessage(),
        )

        val raw = okhttp3.Response.Builder()
            .code(429)
            .message("Too Many Requests")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header("Retry-After", "30")
            .build()
        val rateLimited = HttpException(Response.error<Any>("".toResponseBody(null), raw))
        assertTrue(rateLimited.toConversationMessage().contains("30 giây"))

        val unavailable = HttpException(
            Response.error<Any>(503, """{"detail": "down"}""".toResponseBody("application/json".toMediaType())),
        )
        assertTrue(unavailable.toConversationMessage().startsWith("AI đang bận"))
    }
}
