package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.remote.api.GameApi
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The player's game-layer standing, shared by every screen that draws it.
 *
 * Two things are cached in flows rather than fetched per screen:
 *
 * - the **profile**, because the lobby reads energy from it before every match, the profile tab
 *   reads level and gold from it, and the skill tree reads gold from it;
 * - the **loadout**, because both the duo match screen and the lesson battle screen need it to
 *   draw a skill bar, and neither can afford a round trip once the round has started.
 *
 * Both are refreshed rather than mutated locally. Energy in particular is spent server-side after
 * the question draw succeeds, so any local guess about what it should be would be wrong for every
 * lobby that fails to open a match.
 */
class GameRepository(private val gameApi: GameApi) {

    private val _profile = MutableStateFlow<GameProfileDto?>(null)
    val profile: StateFlow<GameProfileDto?> = _profile.asStateFlow()

    private val _loadout = MutableStateFlow<LoadoutDto?>(null)
    val loadout: StateFlow<LoadoutDto?> = _loadout.asStateFlow()

    suspend fun refreshProfile(): Result<GameProfileDto> =
        runCatching { gameApi.getProfile() }.onSuccess { _profile.value = it }

    suspend fun refreshLoadout(): Result<LoadoutDto> =
        runCatching { gameApi.getLoadout() }.onSuccess { _loadout.value = it }

    suspend fun startBenchmark(capLevel: Int): Result<BenchmarkAttemptDto> =
        runCatching { gameApi.startBenchmarkAttempt(BenchmarkAttemptStartRequest(capLevel)) }

    suspend fun answerBenchmark(
        attemptId: String,
        challengeId: String,
        selectedOptionId: String?,
        selectedOptionIds: List<String>?,
    ): Result<BenchmarkAnswerAckDto> =
        runCatching {
            gameApi.answerBenchmarkQuestion(
                attemptId,
                BenchmarkAnswerRequest(challengeId, selectedOptionId, selectedOptionIds),
            )
        }

    /**
     * Hand a paper in.
     *
     * The response carries the profile as it stands after grading, so it replaces the cached one
     * outright: on a pass the level it carries is the one that was being held back, and every
     * screen reading [profile] -- the path header, the profile tab -- is showing the capped number
     * until this lands.
     */
    suspend fun submitBenchmark(attemptId: String): Result<BenchmarkResultDto> =
        runCatching { gameApi.submitBenchmarkAttempt(attemptId) }
            .onSuccess { _profile.value = it.profile }

    suspend fun classes(): Result<List<GameClassDto>> = runCatching { gameApi.listClasses() }

    /** Changing class clears the equipped bar server-side, so the cached loadout goes with it. */
    suspend fun chooseClass(classCode: String): Result<GameProfileDto> =
        runCatching { gameApi.chooseClass(ChooseClassRequest(classCode)) }
            .onSuccess {
                _profile.value = it
                refreshLoadout()
            }

    suspend fun skillTree(): Result<SkillTreeDto> = runCatching { gameApi.getSkillTree() }

    /** Spends gold, so the profile is stale the moment this returns. */
    suspend fun unlockSkill(skillId: String): Result<SkillNodeDto> =
        runCatching { gameApi.unlockSkill(skillId) }.onSuccess { refreshProfile() }

    suspend fun setLoadout(skillIds: List<String>): Result<LoadoutDto> =
        runCatching { gameApi.setLoadout(LoadoutRequest(skillIds)) }.onSuccess { _loadout.value = it }

    suspend fun inventory(): Result<InventoryDto> = runCatching { gameApi.getInventory() }

    suspend fun setEquipment(
        weaponId: String?,
        armorId: String?,
        trinketId: String?,
    ): Result<InventoryDto> =
        runCatching { gameApi.setEquipment(EquipmentRequest(weaponId, armorId, trinketId)) }

    /**
     * Changes how this player's character is drawn, which the profile card reads from its own
     * endpoint -- so that card is stale until it refetches, not something this can patch.
     */
    suspend fun wearSkin(skinCode: String?): Result<InventoryDto> =
        runCatching { gameApi.wearSkin(WearSkinRequest(skinCode)) }

    suspend fun shop(): Result<ShopDto> = runCatching { gameApi.getShop() }

    /**
     * Spends gold, so the cached profile is stale the moment this returns -- the lobby and the
     * profile tab both draw the balance from it.
     */
    suspend fun purchaseItem(itemId: String): Result<ShopDto> =
        runCatching { gameApi.purchaseItem(itemId) }.onSuccess { refreshProfile() }

    suspend fun currentSeason(): Result<SeasonDto> = runCatching { gameApi.getCurrentSeason() }

    /** Logout: the next account must not inherit this one's energy bar or skill bar. */
    fun clear() {
        _profile.value = null
        _loadout.value = null
    }
}
