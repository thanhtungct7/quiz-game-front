package com.kma.quiz_game.ui.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.EquipmentSlot
import com.kma.quiz_game.data.remote.dto.ItemDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.game.RewardCard
import com.kma.quiz_game.ui.components.game.RewardRow
import com.kma.quiz_game.ui.components.game.rarityColor
import com.kma.quiz_game.ui.components.game.rarityLabel
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.ShapeXl

/**
 * Equipment: three slots, and the chests that filled them.
 *
 * The totals card sits above the slots because it is the only thing here with a mechanical effect
 * -- an item's own numbers matter only through the capped total a match actually fights with.
 */
@Composable
fun InventoryScreen(modifier: Modifier = Modifier) {
    val viewModel: InventoryViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(text = "Trang bị", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Rương rơi ra sau mỗi trận. Trang bị cộng thẳng vào máu, sát thương và mana.",
            style = MaterialTheme.typography.bodyLarge,
            color = Neutral500,
        )
        Spacer(Modifier.height(16.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) { CircularProgressIndicator() }
        }

        state.inventory?.let { inventory ->
            RewardCard {
                RewardRow("Máu tối đa", "+${inventory.bonusMaxHp}", Green500)
                RewardRow(
                    label = "Sát thương",
                    value = "+${"%.1f".format(inventory.bonusDamagePermille / 10f)}%",
                    color = Rose500,
                )
                RewardRow("Mana khởi đầu", "+${inventory.bonusStartingMana}", Indigo500)
            }
            Spacer(Modifier.height(20.dp))

            SlotSection("Vũ khí", state.forSlot(EquipmentSlot.WEAPON), viewModel::toggle)
            SlotSection("Giáp", state.forSlot(EquipmentSlot.ARMOR), viewModel::toggle)
            SlotSection("Phụ kiện", state.forSlot(EquipmentSlot.TRINKET), viewModel::toggle)

            if (state.cosmetics.isNotEmpty()) {
                Text(
                    text = "Sưu tầm",
                    style = MaterialTheme.typography.titleMedium,
                    color = Neutral700,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                state.cosmetics.forEach { item ->
                    Text(
                        text = "${item.name} · ${rarityLabel(item.rarity)}" +
                            if (item.quantity > 1) " ×${item.quantity}" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Neutral500,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        state.errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(text = message, color = Rose500, style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = viewModel::dismissError) { Text("Đã hiểu") }
                TextButton(onClick = viewModel::load) { Text("Tải lại") }
            }
        }

        if (state.inventory?.items.isNullOrEmpty() && !state.isLoading) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Chưa có món nào. Đánh một trận để mở rương đầu tiên.",
                style = MaterialTheme.typography.bodyLarge,
                color = Neutral500,
            )
            Spacer(Modifier.height(12.dp))
            DuoButton(text = "Tải lại", onClick = viewModel::load)
        }
    }
}

@Composable
private fun SlotSection(title: String, items: List<ItemDto>, onToggle: (ItemDto) -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = Neutral700,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    if (items.isEmpty()) {
        Text(
            text = "Chưa có món nào cho ô này.",
            style = MaterialTheme.typography.bodyMedium,
            color = Neutral500,
        )
        return
    }
    items.forEach { item -> ItemRow(item = item, onClick = { onToggle(item) }) }
}

@Composable
private fun ItemRow(item: ItemDto, onClick: () -> Unit) {
    val color = rarityColor(item.rarity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(ShapeXl)
            .background(Neutral050)
            .border(
                width = 2.dp,
                color = if (item.equipped) Green500 else color,
                shape = ShapeXl,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = Neutral700,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = rarityLabel(item.rarity) + " · " + item.bonusLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
        }
        Text(
            text = if (item.equipped) "Đang dùng" else "Trang bị",
            style = MaterialTheme.typography.labelLarge,
            color = if (item.equipped) Green500 else Indigo500,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Only the numbers this item actually moves -- a zero bonus is left out rather than shown as +0. */
private fun ItemDto.bonusLine(): String {
    val parts = buildList {
        if (bonusMaxHp != 0) add("+$bonusMaxHp máu")
        if (bonusDamagePermille != 0) add("+${"%.1f".format(bonusDamagePermille / 10f)}% sát thương")
        if (bonusStartingMana != 0) add("+$bonusStartingMana mana")
    }
    return if (parts.isEmpty()) "không có chỉ số" else parts.joinToString(", ")
}
