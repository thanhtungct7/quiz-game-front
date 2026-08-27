package com.kma.quiz_game.data.remote

import com.kma.quiz_game.data.remote.dto.DuoEvent
import com.kma.quiz_game.data.remote.dto.DuoLeaderboardDto
import com.kma.quiz_game.data.remote.dto.DuoMatchDetailDto
import com.kma.quiz_game.data.remote.dto.DuoMatchSummaryDto
import com.kma.quiz_game.data.remote.dto.DuoStatsDto
import com.kma.quiz_game.data.remote.dto.decodeDuoEvent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes payloads captured from a real match against a running backend
 * (`app/src/test/resources/duo_wire_capture.json`) using the same serializer configuration the
 * app uses.
 *
 * The point is to catch a DTO drifting away from the server's schema. The reducer tests build
 * their events by hand, so they would happily keep passing after the server renamed a field;
 * this fails instead, because [decodeDuoEvent] drops a frame it cannot parse and the assertions
 * here require every captured frame to survive.
 *
 * To refresh the fixture: run the backend, play a match, and re-capture the frames.
 */
class DuoWireContractTest {

    /** Mirrors `NetworkModule.json`, rebuilt here so the test pulls in no Android classes. */
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val capture: JsonObject by lazy {
        val raw = checkNotNull(javaClass.getResourceAsStream("/duo_wire_capture.json")) {
            "duo_wire_capture.json is missing from test resources"
        }.bufferedReader().readText()
        json.parseToJsonElement(raw).jsonObject
    }

    private fun wsFrames(): List<Pair<String, String>> =
        capture.getValue("ws").jsonArray.map { element ->
            val type = element.jsonObject.getValue("type").jsonPrimitive.content
            type to element.toString()
        }

    @Test
    fun `every captured server frame decodes`() {
        val frames = wsFrames()
        assertTrue("fixture should hold real frames", frames.size >= 8)

        val undecodable = frames.filter { (_, raw) -> json.decodeDuoEvent(raw) == null }.map { it.first }
        assertEquals("frames the client cannot parse", emptyList<String>(), undecodable)
    }

    @Test
    fun `the frames that drive the match screen decode into the right events`() {
        val byType = wsFrames().associate { (type, raw) -> type to json.decodeDuoEvent(raw) }

        // A question arrives with its options but never with the answer key.
        val roundStart = byType["round.start"] as DuoEvent.RoundStart
        assertTrue(roundStart.data.question.options.isNotEmpty())
        assertTrue(roundStart.data.timeLimitSeconds > 0)

        // The answer is only revealed with the result.
        val roundResult = byType["round.result"] as DuoEvent.RoundResult
        assertTrue(roundResult.data.correctOptionIds.isNotEmpty())

        val found = byType["match.found"] as DuoEvent.MatchFound
        assertNotNull(found.data.opponent)
        // Captured from a friend room, where the host still has to start the match.
        assertEquals(false, found.data.autoStart)

        val finished = byType["match.finished"] as DuoEvent.MatchFinished
        assertEquals(
            "Elo is zero-sum, so the delta must be the difference it reports",
            finished.data.rating.after - finished.data.rating.before,
            finished.data.rating.delta,
        )

        val error = byType["error"] as DuoEvent.Failed
        assertEquals(
            "a real server error code must map to a known enum value, not the fallback",
            error.data.code,
            error.data.errorCode.name,
        )
    }

    @Test
    fun `every captured REST body decodes`() {
        val rest = capture.getValue("rest").jsonObject

        val matches = json.decodeFromString(
            ListSerializer(DuoMatchSummaryDto.serializer()),
            rest.getValue("MATCHES").toString(),
        )
        assertTrue(matches.isNotEmpty())

        val detail = json.decodeFromString(DuoMatchDetailDto.serializer(), rest.getValue("MATCH_DETAIL").toString())
        assertEquals("the detail view needs one row per round", detail.questionCount, detail.rounds.size)

        val stats = json.decodeFromString(DuoStatsDto.serializer(), rest.getValue("STATS").toString())
        assertEquals(stats.matchesPlayed, stats.wins + stats.losses + stats.draws)

        val board = json.decodeFromString(DuoLeaderboardDto.serializer(), rest.getValue("LEADERBOARD").toString())
        assertTrue(board.entries.isNotEmpty())
        // Ordered by rating, which the pinned self-row on the leaderboard relies on.
        assertEquals(
            board.entries.sortedByDescending { it.rating }.map { it.userId },
            board.entries.map { it.userId },
        )
    }
}
