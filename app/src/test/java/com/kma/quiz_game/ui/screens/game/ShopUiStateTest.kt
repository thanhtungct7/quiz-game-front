package com.kma.quiz_game.ui.screens.game

import com.kma.quiz_game.data.remote.dto.ShopDto
import com.kma.quiz_game.data.remote.dto.ShopItemDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which of the three things a row's button says, decided entirely client-side from the balance
 * and the price the shelf came with. Getting it wrong spends a round trip on a 400 or a 409 the
 * user could have been told about without one.
 */
class ShopUiStateTest {
    private fun item(
        id: String = "item-1",
        goldPrice: Int = 250,
        owned: Boolean = false,
    ) = ShopItemDto(
        id = id,
        code = "SKIN_SCHOLAR",
        name = "Trang phục Học giả",
        kind = "SKIN",
        rarity = "RARE",
        goldPrice = goldPrice,
        owned = owned,
    )

    private fun state(gold: Int, vararg items: ShopItemDto) =
        ShopUiState(isLoading = false, shop = ShopDto(gold = gold, items = items.toList()))

    @Test
    fun `an affordable item can be bought`() {
        val row = item(goldPrice = 250)

        assertEquals(ShopItemAction.BUY, state(300, row).actionFor(row))
    }

    @Test
    fun `exactly enough gold is still enough`() {
        val row = item(goldPrice = 250)

        assertEquals(ShopItemAction.BUY, state(250, row).actionFor(row))
    }

    @Test
    fun `an item costing more than the balance cannot be bought`() {
        val row = item(goldPrice = 250)

        assertEquals(ShopItemAction.TOO_EXPENSIVE, state(249, row).actionFor(row))
    }

    @Test
    fun `an owned item is never offered again however much gold is left`() {
        val row = item(goldPrice = 250, owned = true)

        assertEquals(ShopItemAction.OWNED, state(10_000, row).actionFor(row))
    }

    @Test
    fun `a shelf that has not loaded reports no gold rather than crashing`() {
        assertEquals(0, ShopUiState().gold)
        assertEquals(emptyList<ShopItemDto>(), ShopUiState().items)
    }
}
