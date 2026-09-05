package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.ChooseClassRequest
import com.kma.quiz_game.data.remote.dto.EquipmentRequest
import com.kma.quiz_game.data.remote.dto.GameClassDto
import com.kma.quiz_game.data.remote.dto.GameProfileDto
import com.kma.quiz_game.data.remote.dto.InventoryDto
import com.kma.quiz_game.data.remote.dto.LoadoutDto
import com.kma.quiz_game.data.remote.dto.LoadoutRequest
import com.kma.quiz_game.data.remote.dto.SeasonDto
import com.kma.quiz_game.data.remote.dto.SkillNodeDto
import com.kma.quiz_game.data.remote.dto.SkillTreeDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The player's standing outside a match: level, gold, energy, class, skills, equipment, season.
 *
 * Both game modes read from here -- a duo match and a lesson battle fight with the same class
 * stats and the same three equipped skills -- so this is not a "duo" API despite living next to
 * one. Every endpoint needs a bearer token.
 */
interface GameApi {
    @GET("game/profile")
    suspend fun getProfile(): GameProfileDto

    @GET("game/classes")
    suspend fun listClasses(): List<GameClassDto>

    /** The first class is free; changing later costs gold and clears the equipped bar. */
    @POST("game/class")
    suspend fun chooseClass(@Body body: ChooseClassRequest): GameProfileDto

    @GET("game/skills")
    suspend fun getSkillTree(): SkillTreeDto

    @POST("game/skills/{skillId}/unlock")
    suspend fun unlockSkill(@Path("skillId") skillId: String): SkillNodeDto

    @GET("game/loadout")
    suspend fun getLoadout(): LoadoutDto

    /** At most three ids; the empty list clears the bar. */
    @PUT("game/loadout")
    suspend fun setLoadout(@Body body: LoadoutRequest): LoadoutDto

    @GET("game/items")
    suspend fun getInventory(): InventoryDto

    /** All three slots at once -- a null id empties that slot. */
    @PUT("game/equipment")
    suspend fun setEquipment(@Body body: EquipmentRequest): InventoryDto

    @GET("game/season/current")
    suspend fun getCurrentSeason(): SeasonDto
}
