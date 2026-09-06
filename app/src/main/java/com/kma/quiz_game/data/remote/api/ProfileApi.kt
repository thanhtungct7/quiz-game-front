package com.kma.quiz_game.data.remote.api

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
     * Another player's public card. Needs a bearer token like everything else, but any signed-in
     * player may read any other player's public half.
     */
    @GET("profile/{userId}")
    suspend fun getPublicProfile(@Path("userId") userId: String): PublicProfileDto
}
