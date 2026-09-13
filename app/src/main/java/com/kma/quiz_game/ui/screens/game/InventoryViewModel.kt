package com.kma.quiz_game.ui.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.EquipmentSlot
import com.kma.quiz_game.data.remote.dto.InventoryDto
import com.kma.quiz_game.data.remote.dto.ItemDto
import com.kma.quiz_game.data.remote.dto.ItemKind
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InventoryUiState(
    val isLoading: Boolean = true,
    val inventory: InventoryDto? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    private val items: List<ItemDto> get() = inventory?.items.orEmpty()

    fun forSlot(slot: String): List<ItemDto> =
        items.filter { it.kind == ItemKind.EQUIPMENT && it.slot == slot }

    fun equipped(slot: String): ItemDto? = forSlot(slot).firstOrNull { it.equipped }

    /** Wearable: the only cosmetic that changes anything the player can see. */
    val skins: List<ItemDto> get() = items.filter { it.kind == ItemKind.SKIN }

    /** Collectible only -- a card has nowhere to be worn, so it is listed, not offered. */
    val cards: List<ItemDto> get() = items.filter { it.kind == ItemKind.CARD }

    fun isWorn(item: ItemDto): Boolean =
        inventory?.skinCode != null && inventory.skinCode == item.code
}

/**
 * The three equipment slots and what a chest has dropped into them.
 *
 * Equipment moves the same three numbers a class moves and nothing else, and the whole set is
 * capped server-side -- so the totals shown here are what a match will actually use, not a sum the
 * client worked out for itself.
 */
class InventoryViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    /**
     * Loaded by the screen on entering composition rather than here in `init`, because this view
     * model outlives its tab: buying a skin in the neighbouring Cửa hàng tab changes what belongs
     * in this list, and an `init`-only load would still be showing the collection as it stood
     * before the purchase.
     */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            gameRepository.inventory()
                .onSuccess { inventory -> _uiState.update { it.copy(isLoading = false, inventory = inventory) } }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    /**
     * Equips one item, or unequips it when it is already on.
     *
     * The endpoint takes all three slots at once, so the other two are resent as they stand: a
     * partial payload would read as "empty the slots I did not mention".
     */
    fun toggle(item: ItemDto) {
        val slot = item.slot ?: return
        val state = _uiState.value
        val next = if (state.equipped(slot)?.id == item.id) null else item.id

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            gameRepository.setEquipment(
                weaponId = if (slot == EquipmentSlot.WEAPON) next else state.equipped(EquipmentSlot.WEAPON)?.id,
                armorId = if (slot == EquipmentSlot.ARMOR) next else state.equipped(EquipmentSlot.ARMOR)?.id,
                trinketId = if (slot == EquipmentSlot.TRINKET) next else state.equipped(EquipmentSlot.TRINKET)?.id,
            )
                .onSuccess { inventory ->
                    _uiState.update { it.copy(isSaving = false, inventory = inventory) }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    /**
     * Puts a skin on, or takes it off when it is the one already worn.
     *
     * Toggling rather than a separate "cởi ra" control: only one can be on at a time, so tapping
     * the one you are wearing has no other sensible meaning.
     */
    fun toggleSkin(item: ItemDto) {
        val next = if (_uiState.value.isWorn(item)) null else item.code

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            gameRepository.wearSkin(next)
                .onSuccess { inventory ->
                    _uiState.update { it.copy(isSaving = false, inventory = inventory) }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
}
