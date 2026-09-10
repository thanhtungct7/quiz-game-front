package com.kma.quiz_game.data.remote

import com.kma.quiz_game.data.remote.dto.AchievementListDto
import com.kma.quiz_game.data.remote.dto.CombatBreakdownDto
import com.kma.quiz_game.data.remote.dto.PublicProfileDto
import com.kma.quiz_game.data.remote.dto.SelfProfileDto
import kotlinx.serialization.ExperimentalSerializationApi
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
 * Decodes `app/src/test/resources/profile_wire_capture.json` with the same serializer
 * configuration the app uses.
 *
 * This matters more than a usual DTO test because the app decodes with `ignoreUnknownKeys`. A
 * field renamed on the server does not throw -- it silently falls back to the DTO's default, and
 * the card shows a level of 1 and a rating of 0 with nothing in any log to explain it. The
 * assertions below therefore check *values*, not just that decoding succeeded.
 *
 * The fixture is generated from the backend's own response models, so regenerating after a
 * schema change is what makes this fail:
 *
 *     cd duo-game-back
 *     conda run -n backend python -m scripts.profile_wire_capture
 */
class ProfileWireContractTest {

    /** Mirrors `NetworkModule.json`, rebuilt here so the test pulls in no Android classes. */
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val capture: JsonObject by lazy {
        val raw = checkNotNull(javaClass.getResourceAsStream("/profile_wire_capture.json")) {
            "profile_wire_capture.json is missing from test resources"
        }.bufferedReader().readText()
        json.parseToJsonElement(raw).jsonObject
    }

    private fun body(key: String): String = capture.getValue(key).jsonObject.toString()

    private fun self(key: String = "SELF"): SelfProfileDto =
        json.decodeFromString(SelfProfileDto.serializer(), body(key))

    private fun public(): PublicProfileDto =
        json.decodeFromString(PublicProfileDto.serializer(), body("PUBLIC"))

    private fun breakdown(): CombatBreakdownDto =
        json.decodeFromString(CombatBreakdownDto.serializer(), body("COMBAT_BREAKDOWN"))

    private fun achievements(): AchievementListDto =
        json.decodeFromString(AchievementListDto.serializer(), body("ACHIEVEMENTS"))

    // --- the self card ------------------------------------------------------

    @Test
    fun `self profile decodes every top-level field`() {
        val card = self()

        assertEquals(34, card.level)
        assertEquals("B1", card.cefr)
        assertEquals(620, card.toeicEstimate)
        assertEquals("MAGE", card.classCode)
        assertEquals(12, card.dayStreak)
        assertEquals(40, card.bestDayStreak)
        assertNotNull(card.joinedAt)
    }

    @Test
    fun `self profile decodes the private half`() {
        val card = self()

        assertEquals("player@example.com", card.email)
        assertTrue(card.hasUploadedAvatar)
        assertEquals(1250, card.gold)
        assertEquals(56_100, card.totalExp)
        assertEquals(1_700, card.expToNextLevel)
        assertEquals("B2", card.nextCefr)
        assertEquals(51, card.nextCefrAtLevel)
        assertEquals(3, card.energy?.current)
        assertEquals(5, card.energy?.maximum)
    }

    @Test
    fun `self profile decodes the nested stat blocks`() {
        val card = self()

        assertEquals(1420, card.pvp.rating)
        assertEquals("GOLD", card.pvp.tier)
        assertEquals(19, card.pvp.wins)
        assertEquals(63.3, card.pvp.winRate, 0.001)
        assertEquals(420, card.learning.challengesAttempted)
        assertEquals(355, card.learning.challengesMastered)
        assertEquals(610, card.learning.totalAttempts)
        assertEquals(58.2, card.learning.accuracy, 0.001)
    }

    /** The bar under the name. Wrong field names would leave the span at zero and the bar empty. */
    @Test
    fun `level fraction is derived from the experience the server sent`() {
        val card = self()

        // 56_100 of the way from 54_450 to 57_800.
        assertEquals(0.4925f, card.levelFraction, 0.001f)
    }

    // --- the public card ----------------------------------------------------

    @Test
    fun `public profile decodes the same shared fields`() {
        val card = public()

        assertEquals(34, card.level)
        assertEquals("B1", card.cefr)
        assertEquals(1420, card.pvp.rating)
        assertEquals(355, card.learning.challengesMastered)
    }

    /**
     * The public payload must not carry the private half at all. This is asserted on the JSON the
     * server actually produced, not on the Kotlin class: a leak would be a server-side mistake,
     * and the client type would hide it by simply not having a field to put it in.
     */
    @Test
    fun `public payload carries no private field`() {
        val keys = capture.getValue("PUBLIC").jsonObject.keys

        assertTrue("email leaked into the public card", "email" !in keys)
        assertTrue("gold leaked into the public card", "gold" !in keys)
        assertTrue("energy leaked into the public card", "energy" !in keys)
        assertTrue("total_exp leaked into the public card", "total_exp" !in keys)
        assertTrue("has_uploaded_avatar leaked", "has_uploaded_avatar" !in keys)
    }

    // --- a brand-new account ------------------------------------------------

    /**
     * The payload every default has to survive: no game profile row, no rating row, nothing
     * answered. It is the first thing a new player sees and the easiest one to get wrong.
     */
    @Test
    fun `a new account decodes without any nulls where numbers belong`() {
        val card = self("SELF_NEW_ACCOUNT")

        assertNull(card.username)
        assertNull(card.bio)
        assertEquals(1, card.level)
        assertEquals("A1", card.cefr)
        assertEquals(0, card.gold)
        assertEquals(0, card.dayStreak)
        assertEquals(1000, card.pvp.rating)
        assertTrue("a new account has played nothing", !card.pvp.hasPlayed)
        assertTrue("a new account has answered nothing", !card.learning.hasAnswered)
    }

    @Test
    fun `a new account fights on the baseline build`() {
        val card = self("SELF_NEW_ACCOUNT")

        assertEquals(100, card.combat.hp)
        assertEquals(20, card.combat.atk)
        assertEquals(0, card.combat.defence)
        assertTrue("a new account has earned nothing", card.featuredAchievements.isEmpty())
        assertEquals(0, card.totalAchievementsUnlocked)
    }

    @Test
    fun `a new account has an empty level bar rather than a full one`() {
        assertEquals(0f, self("SELF_NEW_ACCOUNT").levelFraction, 0.001f)
    }

    // --- the combat stats the card draws as bars ----------------------------

    /**
     * The four resolved numbers. A rename here is the failure this whole fixture exists to catch:
     * with `ignoreUnknownKeys` a renamed `defence` would draw a defence bar at zero for every
     * player, and nothing would appear in any log.
     */
    @Test
    fun `the card carries the resolved combat stats`() {
        val card = self()

        assertEquals(118, card.combat.hp)
        assertEquals(27, card.combat.atk)
        assertEquals(6, card.combat.defence)
        assertEquals(14, card.combat.mana)
        assertEquals(1350, card.combat.damagePermille)
    }

    @Test
    fun `another player's card carries the same combat stats`() {
        assertEquals(118, public().combat.hp)
        assertEquals(27, public().combat.atk)
    }

    /** The server's promise about this payload, and the one the breakdown sheet prints a sum to
     * demonstrate. If it ever stops holding, the sheet is showing arithmetic that does not work. */
    @Test
    fun `breakdown lines add up to the total`() {
        val breakdown = breakdown()

        assertEquals(breakdown.total.hp, breakdown.sources.sumOf { it.hp })
        assertEquals(breakdown.total.atk, breakdown.sources.sumOf { it.atk })
        assertEquals(breakdown.total.defence, breakdown.sources.sumOf { it.defence })
        assertEquals(breakdown.total.mana, breakdown.sources.sumOf { it.mana })
    }

    @Test
    fun `breakdown decodes each source kind`() {
        val kinds = breakdown().sources.map { it.kind }

        assertTrue("CLASS" in kinds)
        assertTrue("EQUIPMENT" in kinds)
        assertTrue("STREAK" in kinds)
        assertEquals("Pháp sư", breakdown().sources.first().label)
    }

    // --- achievements -------------------------------------------------------

    @Test
    fun `the card carries the three featured badges`() {
        val card = self()

        assertEquals(3, card.featuredAchievements.size)
        assertEquals(7, card.totalAchievementsUnlocked)
        assertEquals("STREAK_7", card.featuredAchievements.first().code)
        assertEquals("FLAME", card.featuredAchievements.first().iconCode)
        assertEquals("LEARNING", card.featuredAchievements.first().category)
        assertNotNull(card.featuredAchievements.first().unlockedAt)
    }

    @Test
    fun `the shelf decodes earned and unearned alike`() {
        val shelf = achievements()

        assertEquals(7, shelf.unlockedCount)
        assertEquals(21, shelf.total)

        val earned = shelf.items.first { it.code == "LEVEL_20" }
        assertTrue(earned.unlocked)
        assertNotNull(earned.unlockedAt)
        assertEquals(1f, earned.fraction, 0.001f)

        val inProgress = shelf.items.first { it.code == "LEVEL_50" }
        assertTrue(!inProgress.unlocked)
        assertNull(inProgress.unlockedAt)
        assertEquals(34, inProgress.current)
        assertEquals(0.68f, inProgress.fraction, 0.001f)
    }

    @Test
    fun `an untouched achievement has an empty bar rather than a full one`() {
        val untouched = achievements().items.first { it.code == "BATTLES_WON_25" }

        assertEquals(0f, untouched.fraction, 0.001f)
    }

    // --- placeholders for the module that does not exist yet ----------------

    @Test
    fun `character fields decode as absent`() {
        val card = public()

        assertNull(card.title)
        assertNull(card.companionCharacter)
        assertNull(card.skinCode)
    }

    /** The public half of the self card is what the shared composable draws. */
    @Test
    fun `as public keeps every shared field`() {
        val card = self()
        val shared = card.asPublic()

        assertEquals(card.id, shared.id)
        assertEquals(card.level, shared.level)
        assertEquals(card.cefr, shared.cefr)
        assertEquals(card.pvp, shared.pvp)
        assertEquals(card.learning, shared.learning)
        assertEquals(card.combat, shared.combat)
        assertEquals(card.featuredAchievements, shared.featuredAchievements)
    }
}
