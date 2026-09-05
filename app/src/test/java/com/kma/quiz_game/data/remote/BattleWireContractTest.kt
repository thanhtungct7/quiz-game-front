package com.kma.quiz_game.data.remote

import com.kma.quiz_game.data.remote.dto.BattleEvent
import com.kma.quiz_game.data.remote.dto.BattleHistoryDto
import com.kma.quiz_game.data.remote.dto.BattleStatusDto
import com.kma.quiz_game.data.remote.dto.CourseMonstersDto
import com.kma.quiz_game.data.remote.dto.MonsterCatalogDto
import com.kma.quiz_game.data.remote.dto.MonsterPreviewDto
import com.kma.quiz_game.data.remote.dto.decodeBattleEvent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes PvE payloads (`app/src/test/resources/pve_wire_capture.json`) with the same serializer
 * configuration the app uses.
 *
 * The point is to catch a DTO drifting away from the server's schema. The reducer tests build
 * their events by hand, so they would happily keep passing after the server renamed a field; this
 * fails instead, because [decodeBattleEvent] drops a frame it cannot parse and the assertions here
 * require every captured frame to survive.
 *
 * The fixture is captured from a **live server**, not generated from the backend's Pydantic models:
 * run the backend and `python -m scripts.pve_smoke --capture`. That distinction is the whole value
 * of the file. A fixture written by the same schema that writes the frames can only ever agree with
 * itself; this one is the frames.
 */
class BattleWireContractTest {

    /** Mirrors `NetworkModule.json`, rebuilt here so the test pulls in no Android classes. */
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val capture: JsonObject by lazy {
        val raw = checkNotNull(javaClass.getResourceAsStream("/pve_wire_capture.json")) {
            "pve_wire_capture.json is missing from test resources"
        }.bufferedReader().readText()
        json.parseToJsonElement(raw).jsonObject
    }

    private fun wsFrames(): List<Pair<String, String>> =
        capture.getValue("ws").jsonArray.map { element ->
            val type = element.jsonObject.getValue("type").jsonPrimitive.content
            type to element.toString()
        }

    private fun rawData(type: String): JsonObject =
        capture.getValue("ws").jsonArray
            .map { it.jsonObject }
            .first { it.getValue("type").jsonPrimitive.content == type }
            .getValue("data").jsonObject

    private inline fun <reified T : BattleEvent> events(type: String): List<T> =
        wsFrames().filter { it.first == type }.map { json.decodeBattleEvent(it.second) as T }

    @Test
    fun `every captured server frame decodes`() {
        val frames = wsFrames()
        assertTrue("fixture should hold a whole battle", frames.size >= 10)

        val undecodable = frames.filter { (_, raw) -> json.decodeBattleEvent(raw) == null }.map { it.first }
        assertEquals("frames the client cannot parse", emptyList<String>(), undecodable)
    }

    @Test
    fun `the opening frame carries both clocks the client has to draw against`() {
        val started = events<BattleEvent.Started>("battle.started").first()

        assertEquals("SLIME", started.data.monster.code)
        assertEquals(started.data.monster.maxHp, started.data.monsterHp)
        // Not a length: the pool goes round again rather than ending the fight.
        assertTrue(started.data.questionsInPool > 0)
        assertTrue("the arena sizes the wind-up bar against this", started.data.monster.castIntervalMs > 0)
        assertTrue(started.data.tickHz > 0)
        assertTrue(started.data.snapshotHz > 0)
    }

    @Test
    fun `a snapshot publishes deadlines the client can interpolate between`() {
        val tick = events<BattleEvent.StateTick>("state.tick").first()

        // The pair that makes clock synchronisation unnecessary: `t` is the server's own clock at
        // the moment the frame was built, and the deadline is on that same scale.
        assertTrue("the cast must end in the future of its own snapshot", tick.data.castEndsAt > tick.data.t)
        assertTrue(tick.data.nextSwingDamage > 0)
        assertTrue(tick.data.monsterMaxHp > 0)
        assertTrue(tick.data.yourMaxHp > 0)
    }

    @Test
    fun `a question arrives with its options and never with its answer`() {
        val push = events<BattleEvent.QuestionPush>("question.push").first()

        assertTrue(push.data.token.isNotEmpty())
        assertTrue(push.data.question.options.isNotEmpty())

        val raw = rawData("question.push")
        assertFalse("a push must not leak the key", raw.containsKey("correct_option_ids"))
        assertFalse(raw.containsKey("explanation"))
    }

    @Test
    fun `an answer deals damage or costs the chain, and never costs health`() {
        val results = events<BattleEvent.AnswerResult>("answer.result")
        val landed = results.first { it.data.correct }
        val missed = results.first { !it.data.correct }

        assertNotNull(landed.data.blow)
        assertTrue(landed.data.correctOptionIds.isNotEmpty())
        assertTrue(landed.data.combo >= 1)

        assertNull(missed.data.blow)
        assertEquals(0, missed.data.combo)
        // Both still reveal the answer, which is what joins the fight to the lesson.
        assertTrue(missed.data.correctOptionIds.isNotEmpty())

        // The invariant the whole rewrite rests on. Under the lock-step engine a wrong answer
        // carried the monster's counter-attack and a new health total; the monster has its own
        // clock now, so an answer frame has no business reporting health at all.
        val raw = rawData("answer.result")
        assertFalse("an answer cannot change health any more", raw.containsKey("your_hp"))
        assertFalse(raw.containsKey("monster_attack"))
    }

    @Test
    fun `the monster swings on its own clock`() {
        val swing = events<BattleEvent.MonsterSwing>("monster.swing").first()

        assertTrue(swing.data.damage > 0)
        assertTrue(swing.data.swingIndex >= 1)
        // Already re-armed, so the ring restarts without waiting for the next snapshot.
        assertTrue(swing.data.castEndsAt > 0)
    }

    @Test
    fun `a cast reports its cost, its cooldown and where the monster's bar now stands`() {
        val skill = events<BattleEvent.SkillUsed>("skill.used").first()

        assertTrue(skill.data.manaSpent > 0)
        assertTrue("the dock greys the button out until this", skill.data.readyAgainAt > 0)
        assertTrue("a clock-stealing skill moves this", skill.data.castEndsAt > 0)
    }

    @Test
    fun `the payout reports the fight in time and swings, not in rounds`() {
        val finished = events<BattleEvent.Finished>("battle.finished")
        val won = finished.first { it.data.outcome == BattleStatusDto.WON }

        assertEquals(
            "experience must move by exactly the delta it reports",
            won.data.exp.after - won.data.exp.before,
            won.data.exp.delta,
        )
        assertTrue(won.data.answersGiven > 0)
        assertTrue(won.data.durationMs > 0)
        assertTrue("zero swings is the new perfect run", won.data.monsterSwings >= 0)
        // The point of the feature: the fight moved the learn path, not a parallel score.
        assertTrue(won.data.lessonProgress.total > 0)
    }

    @Test
    fun `a pong carries both clocks and an error code maps to a known value`() {
        val pong = events<BattleEvent.Pong>("pong").first()
        assertTrue(pong.data.clientTimeMs > 0)
        assertTrue(pong.data.serverTimeMs > 0)

        val error = events<BattleEvent.Failed>("error").first()
        assertEquals(
            "a real server error code must map to a known enum value, not the fallback",
            error.data.code,
            error.data.errorCode.name,
        )
    }

    @Test
    fun `every captured REST body decodes`() {
        val rest = capture.getValue("rest").jsonObject

        val monsters = json.decodeFromString(
            ListSerializer(MonsterCatalogDto.serializer()),
            rest.getValue("MONSTERS").toString(),
        )
        assertTrue(monsters.isNotEmpty())
        assertTrue("the catalog carries both kinds", monsters.any { it.isBoss } && monsters.any { !it.isBoss })

        // Captured before the fight, so this is the "never played" shape -- the one with the
        // nullable fields actually null, which is the shape most likely to break a decoder.
        val preview = json.decodeFromString(
            MonsterPreviewDto.serializer(),
            rest.getValue("LESSON_PREVIEW").toString(),
        )
        assertFalse(preview.cleared)
        assertNull(preview.bestHpLeft)
        assertTrue(preview.monster.code.isNotEmpty())

        val map = json.decodeFromString(
            CourseMonstersDto.serializer(),
            rest.getValue("COURSE_MONSTERS").toString(),
        )
        assertTrue("the map answers for the whole course in one body", map.lessons.size > 1)

        val history = json.decodeFromString(
            ListSerializer(BattleHistoryDto.serializer()),
            rest.getValue("HISTORY").toString(),
        )
        assertEquals(BattleStatusDto.WON, history.first().status)
        assertTrue(history.first().firstClear)
    }
}
