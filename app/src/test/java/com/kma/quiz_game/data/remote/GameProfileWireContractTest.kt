package com.kma.quiz_game.data.remote

import com.kma.quiz_game.data.remote.dto.BenchmarkAnswerRequest
import com.kma.quiz_game.data.remote.dto.BenchmarkAttemptDto
import com.kma.quiz_game.data.remote.dto.BenchmarkAttemptStartRequest
import com.kma.quiz_game.data.remote.dto.BenchmarkResultDto
import com.kma.quiz_game.data.remote.dto.GameProfileDto
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `GET /game/profile` and the Benchmark Exam endpoints, at the field-name level.
 *
 * The app decodes with `ignoreUnknownKeys`, which means a field the DTO spells differently from
 * the server does not fail -- it silently falls back to the default. That is exactly how
 * `pending_benchmark_level` went unread for as long as it did: the server had been sending it,
 * the app decoded a null, and a learner capped at level 10 saw no prompt and no explanation.
 * These assertions check values, not that decoding succeeded.
 *
 * The payloads mirror `duo-game-back/app/schemas/game/game.py` (`GameProfileRead`) and
 * `duo-game-back/app/schemas/game/benchmark_exam.py`.
 */
class GameProfileWireContractTest {

    /** Mirrors `NetworkModule.json`, rebuilt here so the test pulls in no Android classes. */
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun profileJson(pendingBenchmarkLevel: String) = """
        {
          "user_id": "user-1",
          "level": 10,
          "total_exp": 4800,
          "exp_for_current_level": 4500,
          "exp_for_next_level": 5500,
          "exp_to_next_level": 700,
          "gold": 320,
          "class_code": "ASSASSIN",
          "class_name": "Xạ thủ",
          "energy": {"current": 4, "maximum": 5, "next_regen_at": "2026-09-12T10:00:00Z"},
          "day_streak": 6,
          "best_day_streak": 11,
          "pending_benchmark_level": $pendingBenchmarkLevel
        }
    """.trimIndent()

    @Test
    fun `a capped profile carries the cap holding it back`() {
        val profile = json.decodeFromString(GameProfileDto.serializer(), profileJson("10"))

        assertEquals(10, profile.pendingBenchmarkLevel)
    }

    @Test
    fun `an uncapped profile reports nothing pending`() {
        val profile = json.decodeFromString(GameProfileDto.serializer(), profileJson("null"))

        assertNull(profile.pendingBenchmarkLevel)
    }

    /** Older servers predate the field entirely; absent must read the same as null, not crash. */
    @Test
    fun `a payload without the field decodes as nothing pending`() {
        val withoutField = profileJson("null")
            .lines()
            .filterNot { it.contains("pending_benchmark_level") }
            .joinToString("\n")
            .replace("\"best_day_streak\": 11,", "\"best_day_streak\": 11")

        val profile = json.decodeFromString(GameProfileDto.serializer(), withoutField)

        assertNull(profile.pendingBenchmarkLevel)
    }

    @Test
    fun `the rest of the profile still decodes alongside it`() {
        val profile = json.decodeFromString(GameProfileDto.serializer(), profileJson("25"))

        assertEquals(10, profile.level)
        assertEquals(4800, profile.totalExp)
        assertEquals(320, profile.gold)
        assertEquals(6, profile.dayStreak)
        assertEquals(4, profile.energy.current)
    }

    /** The server field is `cap_level`; sending `capLevel` would be a 422 on every sitting. */
    @Test
    fun `a sitting is started with cap_level`() {
        val body = json.encodeToString(
            BenchmarkAttemptStartRequest.serializer(),
            BenchmarkAttemptStartRequest(capLevel = 25),
        )

        assertEquals("""{"cap_level":25}""", body)
    }

    /** Exactly one option field goes out, and the unused one is dropped rather than sent as null. */
    @Test
    fun `an answer carries only the shape its question takes`() {
        val single = json.encodeToString(
            BenchmarkAnswerRequest.serializer(),
            BenchmarkAnswerRequest(challengeId = "c1", selectedOptionId = "o1"),
        )
        val ordered = json.encodeToString(
            BenchmarkAnswerRequest.serializer(),
            BenchmarkAnswerRequest(challengeId = "c2", selectedOptionIds = listOf("t2", "t1")),
        )

        assertEquals("""{"challenge_id":"c1","selected_option_id":"o1"}""", single)
        assertEquals("""{"challenge_id":"c2","selected_option_ids":["t2","t1"]}""", ordered)
    }

    @Test
    fun `a drawn paper decodes its id, bar and clock`() {
        val paper = json.decodeFromString(
            BenchmarkAttemptDto.serializer(),
            """
            {
              "attempt_id": "a1",
              "cap_level": 25,
              "questions": [],
              "total": 30,
              "pass_percent": 80,
              "started_at": "2026-09-13T12:00:00Z",
              "expires_at": "2026-09-13T12:45:00Z"
            }
            """.trimIndent(),
        )

        assertEquals("a1", paper.attemptId)
        assertEquals(80, paper.passPercent)
        assertEquals("2026-09-13T12:45:00Z", paper.expiresAt)
    }

    /** The grade and the lifted profile arrive together; the profile must not decode to defaults. */
    @Test
    fun `a graded paper decodes the grade and the profile after it`() {
        val result = json.decodeFromString(
            BenchmarkResultDto.serializer(),
            """
            {
              "attempt_id": "a1",
              "cap_level": 10,
              "status": "PASSED",
              "correct_count": 24,
              "total": 30,
              "percent": 80,
              "pass_percent": 80,
              "passed": true,
              "submitted_at": "2026-09-13T12:30:00Z",
              "profile": ${profileJson("null")}
            }
            """.trimIndent(),
        )

        assertEquals(24, result.correctCount)
        assertEquals(80, result.passPercent)
        assertEquals(true, result.passed)
        assertEquals(4800, result.profile.totalExp)
        assertNull(result.profile.pendingBenchmarkLevel)
    }
}
