package com.kma.quiz_game.data.remote

import com.kma.quiz_game.data.remote.dto.ConversationDetailDto
import com.kma.quiz_game.data.remote.dto.ConversationHintDto
import com.kma.quiz_game.data.remote.dto.ConversationMessageRequest
import com.kma.quiz_game.data.remote.dto.ConversationMessageRole
import com.kma.quiz_game.data.remote.dto.ConversationStartRequest
import com.kma.quiz_game.data.remote.dto.ConversationStatus
import com.kma.quiz_game.data.remote.dto.ConversationSummaryDto
import com.kma.quiz_game.data.remote.dto.ConversationTurnDto
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AI conversation endpoints, read with the app's serializer settings. The bodies below were
 * produced by the backend's own response models (`app/schemas/conversation/conversation.py`), so a
 * field renamed on either side fails here rather than on a device.
 */
class ConversationWireContractTest {

    /** Mirrors `NetworkModule.json`, rebuilt here so the test pulls in no Android classes. */
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `requests are sent in snake case`() {
        assertEquals(
            """{"scenario_code":"cafe_order"}""",
            json.encodeToString(ConversationStartRequest.serializer(), ConversationStartRequest("cafe_order")),
        )
        assertEquals(
            """{"content":"Hello"}""",
            json.encodeToString(ConversationMessageRequest.serializer(), ConversationMessageRequest("Hello")),
        )
    }

    @Test
    fun `a finished conversation reads with its scenario, transcript and feedback`() {
        val detail = json.decodeFromString(ConversationDetailDto.serializer(), DETAIL)

        assertEquals("free_talk", detail.scenario.code)
        assertNull(detail.scenario.goalVi)
        assertTrue(detail.scenario.suggestedLevels.isEmpty())
        assertEquals(4, detail.scenario.usefulPhrases.size)
        assertEquals(ConversationStatus.FINISHED, detail.status)
        assertTrue(detail.shouldFinish)
        assertEquals(listOf(ConversationMessageRole.ASSISTANT, ConversationMessageRole.USER), detail.messages.map { it.role })

        val feedback = detail.feedback!!
        assertEquals(55, feedback.score)
        assertEquals(false, feedback.goalCompleted)
        assertEquals("How much does it cost?", feedback.corrections.single().corrected)
        assertEquals("Dùng does.", feedback.corrections.single().explanationVi)
        assertEquals("Lịch sự hơn.", feedback.betterPhrases.single().noteVi)
        assertEquals(listOf("receipt"), feedback.newWords)
    }

    @Test
    fun `a turn, a history row and hints read`() {
        val turn = json.decodeFromString(ConversationTurnDto.serializer(), TURN)
        assertEquals("A latte, please.", turn.userMessage.content)
        assertEquals(12, turn.maxTurns)

        val row = json.decodeFromString(ConversationSummaryDto.serializer(), SUMMARY)
        assertEquals("Gọi đồ ở quán cà phê", row.titleVi)
        assertNull(row.score)

        val hints = json.decodeFromString(ConversationHintDto.serializer(), HINTS)
        assertEquals("Chào", hints.suggestions.single().vi)
    }

    private companion object {
        const val DETAIL = """{"id":"s1","scenario":{"code":"free_talk","title_vi":"Trò chuyện với bạn nước ngoài","category_vi":"Trò chuyện tự do","suggested_levels":[],"setting":"A relaxed video call between two friends.","ai_role":"Emma, a friendly British friend","user_role":"Emma's friend","goal_vi":null,"useful_phrases":["It's been pretty busy.","What about you?","Last weekend I...","In Vietnam, we usually..."],"max_turns":15},"cefr":"A2","status":"FINISHED","user_turns":1,"max_turns":15,"should_finish":true,"awaiting_reply":false,"messages":[{"id":"m1","seq":1,"role":"ASSISTANT","content":"Hi there!","translation_vi":null,"created_at":"2026-09-17T04:08:26.196834Z"},{"id":"m2","seq":2,"role":"USER","content":"A latte, please.","translation_vi":null,"created_at":"2026-09-17T04:08:26.196834Z"}],"feedback":{"score":55,"goal_completed":false,"summary_vi":"Tốt.","corrections":[{"original":"How much it cost?","corrected":"How much does it cost?","explanation_vi":"Dùng does."}],"better_phrases":[{"original":"I want","natural":"I'd like","note_vi":"Lịch sự hơn."}],"new_words":["receipt"]},"started_at":"2026-09-17T04:08:26.196834Z","finished_at":"2026-09-17T04:08:26.196834Z"}"""
        const val TURN = """{"user_message":{"id":"m2","seq":2,"role":"USER","content":"A latte, please.","translation_vi":null,"created_at":"2026-09-17T04:08:26.196834Z"},"assistant_message":{"id":"m3","seq":3,"role":"ASSISTANT","content":"Coming up!","translation_vi":null,"created_at":"2026-09-17T04:08:26.196834Z"},"user_turns":1,"max_turns":12,"should_finish":false}"""
        const val SUMMARY = """{"id":"s1","scenario_code":"cafe_order","title_vi":"Gọi đồ ở quán cà phê","status":"ACTIVE","user_turns":0,"max_turns":12,"score":null,"started_at":"2026-09-17T04:08:26.196834Z","finished_at":null}"""
        const val HINTS = """{"suggestions":[{"en":"Hi","vi":"Chào"}]}"""
    }
}
