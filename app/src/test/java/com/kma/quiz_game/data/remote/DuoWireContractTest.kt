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
 * Decodes `app/src/test/resources/duo_wire_capture.json` with the same serializer configuration
 * the app uses.
 *
 * The point is to catch a DTO drifting away from the server's schema. The reducer tests build
 * their events by hand, so they would happily keep passing after the server renamed a field; this
 * fails instead, because [decodeDuoEvent] drops a frame it cannot parse and the assertions here
 * require every frame in the fixture to survive.
 *
 * That only works because the fixture is not typed out by hand either. It is generated from the
 * backend's own response models -- the same `envelope(...)` the engine sends and the same models
 * the routes return -- so renaming a field on the server and regenerating is what makes this fail:
 *
 *     cd duo-game-back
 *     conda run -n backend python -m scripts.duo_wire_capture
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
        val push = byType["question.push"] as DuoEvent.QuestionPush
        assertTrue(push.data.question.options.isNotEmpty())
        assertTrue(push.data.token.isNotEmpty())
        assertTrue(push.data.deckRemaining > 0)

        // The answer is only revealed with the result, and only to the player who gave it.
        val answer = byType["answer.result"] as DuoEvent.AnswerResult
        assertTrue(answer.data.correctOptionIds.isNotEmpty())
        assertEquals(push.data.token, answer.data.token)
        assertNotNull(answer.data.blow)

        // A blow the opponent landed reaches us as its own frame, already applied.
        val incoming = byType["opponent.answered"] as DuoEvent.OpponentAnswered
        assertTrue(incoming.data.damage > 0)

        // Every absolute stamp is on one clock, and the snapshot is what pins it to ours.
        val tick = byType["state.tick"] as DuoEvent.StateTick
        assertTrue(tick.data.t > 0)
        assertTrue(tick.data.deadlineAt > tick.data.t)
        assertTrue(tick.data.yourDeckRemaining >= 0)

        val started = byType["match.started"] as DuoEvent.MatchStarted
        assertEquals(started.data.deckSize, tick.data.yourDeckRemaining + 1)

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
        // No answer-by-answer replay any more -- what a detail still owes the screen is the
        // health both sides finished on and every skill either of them fired.
        assertTrue(detail.myHpLeft >= 0)
        assertTrue(detail.skillUses.isNotEmpty())

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
