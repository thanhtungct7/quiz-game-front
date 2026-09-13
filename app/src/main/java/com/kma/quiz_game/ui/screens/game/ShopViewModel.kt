package com.kma.quiz_game.ui.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.ShopDto
import com.kma.quiz_game.data.remote.dto.ShopItemDto
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the buy button on one row is allowed to say. */
enum class ShopItemAction { BUY, OWNED, TOO_EXPENSIVE }

data class ShopUiState(
    val isLoading: Boolean = true,
    val shop: ShopDto? = null,
    val buying: String? = null,
    val errorMessage: String? = null,
    val justBought: String? = null,
) {
    val gold: Int get() = shop?.gold ?: 0
    val items: List<ShopItemDto> get() = shop?.items.orEmpty()

    fun actionFor(item: ShopItemDto): ShopItemAction = when {
        item.owned -> ShopItemAction.OWNED
        item.goldPrice > gold -> ShopItemAction.TOO_EXPENSIVE
        else -> ShopItemAction.BUY
    }
}

/**
 * The cosmetics on sale, and the one button that spends gold outside the skill tree.
 *
 * Everything on the shelf is a zero-bonus skin, so there is no "is this worth it" maths to do
 * here: a row is owned, affordable, or not, and the server decides which by returning the shelf
 * and the balance together. A purchase therefore replaces the whole state from the response
 * rather than patching the bought row and guessing the new balance.
 */
class ShopViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    /**
     * Loaded by the screen on entering composition rather than here in `init`: the balance this
     * shelf prices against moves every time a lesson or a match pays out, and this view model
     * outlives the tab swap that would otherwise have reloaded it.
     */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            gameRepository.shop()
                .onSuccess { shop -> _uiState.update { it.copy(isLoading = false, shop = shop) } }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    fun buy(item: ShopItemDto) {
        if (_uiState.value.actionFor(item) != ShopItemAction.BUY) return
        if (_uiState.value.buying != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(buying = item.id, errorMessage = null) }
            gameRepository.purchaseItem(item.id)
                .onSuccess { shop ->
                    _uiState.update {
                        it.copy(buying = null, shop = shop, justBought = item.name)
                    }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(buying = null, errorMessage = cause.toUserMessage()) }
                }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null, justBought = null) }
}
