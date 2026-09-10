package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.AchievementListDto
import com.kma.quiz_game.data.remote.dto.CombatBreakdownDto
import com.kma.quiz_game.data.remote.dto.PublicProfileDto
import com.kma.quiz_game.data.remote.dto.SelfProfileDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * The aggregated player profile: identity, level, CEFR band, PvP record and study totals, all in
 * one round trip.
 *
 * Distinct from [UsersApi], which owns the account -- reading and *changing* the name, bio and
 * avatar. This one only reads, and it reads across every module.
 */
interface ProfileApi {
    @GET("profile/me")
    suspend fun getMyProfile(): SelfProfileDto

    /**
     * Where each of the four combat numbers on the card came from.
     *
     * Self only, and deliberately not folded into the card: the totals ride along on every
     * profile, and the itemised version is a player who tapped a bar to ask about their build.
     */
    @GET("profile/me/combat")
    suspend fun getMyCombatBreakdown(): CombatBreakdownDto

    /**
     * The whole shelf -- earned and still to earn, each with how far off it is.
     *
     * The one read path on the server that also syncs, so an account older than an achievement
     * picks it up the first time this is opened. The card's three featured badges never sync,
     * which is why opening this tab can reveal badges the card did not show.
     */
    @GET("profile/me/achievements")
    suspend fun getMyAchievements(): AchievementListDto

    /**
     * Another player's public card. Needs a bearer token like everything else, but any signed-in
     * player may read any other player's public half.
     */
    @GET("profile/{userId}")
    suspend fun getPublicProfile(@Path("userId") userId: String): PublicProfileDto
}
