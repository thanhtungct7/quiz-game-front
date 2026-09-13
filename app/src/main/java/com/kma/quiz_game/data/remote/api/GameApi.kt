package com.kma.quiz_game.data.remote.api

import com.kma.quiz_game.data.remote.dto.BenchmarkAnswerAckDto
import com.kma.quiz_game.data.remote.dto.BenchmarkAnswerRequest
import com.kma.quiz_game.data.remote.dto.BenchmarkAttemptDto
import com.kma.quiz_game.data.remote.dto.BenchmarkAttemptStartRequest
import com.kma.quiz_game.data.remote.dto.BenchmarkResultDto
import com.kma.quiz_game.data.remote.dto.ChooseClassRequest
import com.kma.quiz_game.data.remote.dto.EquipmentRequest
import com.kma.quiz_game.data.remote.dto.GameClassDto
import com.kma.quiz_game.data.remote.dto.GameProfileDto
import com.kma.quiz_game.data.remote.dto.InventoryDto
import com.kma.quiz_game.data.remote.dto.LoadoutDto
import com.kma.quiz_game.data.remote.dto.LoadoutRequest
import com.kma.quiz_game.data.remote.dto.SeasonDto
import com.kma.quiz_game.data.remote.dto.ShopDto
import com.kma.quiz_game.data.remote.dto.SkillNodeDto
import com.kma.quiz_game.data.remote.dto.SkillTreeDto
import com.kma.quiz_game.data.remote.dto.WearSkinRequest
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

    /**
     * Open a sitting of the Benchmark Exam for one cap, on a paper the server draws.
     *
     * 400 for a cap the raw level has not reached, 409 for one already cleared or when the course
     * holds too little content to draw from.
     */
    @POST("game/benchmark-exam/attempts")
    suspend fun startBenchmarkAttempt(@Body body: BenchmarkAttemptStartRequest): BenchmarkAttemptDto

    /** One answer. 409 once the sitting has closed or the question was already answered. */
    @POST("game/benchmark-exam/attempts/{attemptId}/answers")
    suspend fun answerBenchmarkQuestion(
        @Path("attemptId") attemptId: String,
        @Body body: BenchmarkAnswerRequest,
    ): BenchmarkAnswerAckDto

    /** Hand the paper in and get the grade. Safe to retry: a graded sitting is never regraded. */
    @POST("game/benchmark-exam/attempts/{attemptId}/submit")
    suspend fun submitBenchmarkAttempt(@Path("attemptId") attemptId: String): BenchmarkResultDto

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

    /** Puts an owned skin on show. A null code takes the current one off. */
    @PUT("game/skin")
    suspend fun wearSkin(@Body body: WearSkinRequest): InventoryDto

    @GET("game/shop")
    suspend fun getShop(): ShopDto

    /** Bought once and kept. Buying the same item again is a 409, not a second charge. */
    @POST("game/shop/{itemId}/purchase")
    suspend fun purchaseItem(@Path("itemId") itemId: String): ShopDto

    @GET("game/season/current")
    suspend fun getCurrentSeason(): SeasonDto
}
