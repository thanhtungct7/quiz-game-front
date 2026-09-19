package com.kma.quiz_game.data.remote

import com.kma.quiz_game.data.remote.dto.BattleFinishedDto
import com.kma.quiz_game.data.remote.dto.DailyQuestsDto
import com.kma.quiz_game.data.remote.dto.QuestClaimDto
import com.kma.quiz_game.data.remote.dto.QuestCompletedDto
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes the daily quest payloads (`app/src/test/resources/quest_wire_capture.json`) with the
 * app's serializer settings.
 *
 * The REST bodies in the fixture were captured from a live backend with curl -- `GET
 * /quests/daily` and one claim of each kind -- so a field the server renames fails here rather
 * than on a phone. See [BattleWireContractTest] for why a captured fixture is the point.
 */
class QuestWireContractTest {

    /** Mirrors `NetworkModule.json`, rebuilt here so the test pulls in no Android classes. */
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val capture: JsonObject by lazy {
        val raw = checkNotNull(javaClass.getResourceAsStream("/quest_wire_capture.json")) {
            "quest_wire_capture.json is missing from test resources"
        }.bufferedReader().readText()
        json.parseToJsonElement(raw).jsonObject
    }

    @Test
    fun `today's quests decode with every field the screen draws`() {
        val day = json.decodeFromJsonElement(DailyQuestsDto.serializer(), capture.getValue("daily"))

        assertEquals(4, day.quests.size)
        assertEquals(listOf(30, 60, 100), day.chests.map { it.milestone })
        assertEquals(100, day.maxActivityPoints)
        assertTrue(day.resetsAt.endsWith("Z"))
        day.quests.forEach { quest ->
            assertTrue(quest.title.isNotBlank())
            assertTrue(quest.target > 0)
            assertTrue(quest.difficulty in setOf("EASY", "MEDIUM", "HARD"))
        }
        // The red dot counts exactly what the screen offers to collect.
        assertEquals(
            day.quests.count { it.claimable } + day.chests.count { it.claimable },
            day.claimableCount,
        )
    }

    @Test
    fun `opening the gold chest pays gold, experience and an item`() {
        val claim = json.decodeFromJsonElement(QuestClaimDto.serializer(), capture.getValue("claim_chest"))

        assertEquals(60, claim.reward.gold.delta)
        assertEquals(60, claim.reward.exp.delta)
        assertNotNull(claim.reward.loot)
        assertTrue(claim.quests.chests.last().claimed)
    }

    @Test
    fun `claiming a quest pays without an item and returns the day as it stands after`() {
        val claim = json.decodeFromJsonElement(QuestClaimDto.serializer(), capture.getValue("claim_quest"))

        assertNull(claim.reward.loot)
        assertTrue(claim.reward.gold.delta > 0)
        val before = json.decodeFromJsonElement(DailyQuestsDto.serializer(), capture.getValue("daily"))
        assertEquals(before.claimableCount - 1, claim.quests.claimableCount)
    }

    @Test
    fun `a finished battle carries the quests it finished, and an older server none`() {
        val quests = json.decodeFromJsonElement(
            ListSerializer(QuestCompletedDto.serializer()),
            capture.getValue("quests_completed"),
        )
        assertEquals(25, quests.single().activityPoints)

        // A frame from before the field existed must still decode, with nothing to show.
        val older = """
            {"battle_id":"b","outcome":"WON","end_reason":"MONSTER_DOWN","your_hp_left":80,
             "monster_hp_left":0,"answers_given":9,"correct_count":9,"best_combo":9,
             "first_clear":true,
             "exp":{"before":0,"after":50,"delta":50,"level_before":1,"level_after":1,"leveled_up":false},
             "gold":{"before":0,"after":20,"delta":20},
             "lesson_progress":{"status":"COMPLETED","correct":9,"total":10}}
        """.trimIndent()
        assertTrue(json.decodeFromString(BattleFinishedDto.serializer(), older).questsCompleted.isEmpty())
    }
}
