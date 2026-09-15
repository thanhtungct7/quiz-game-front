package com.kma.quiz_game.ui.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.ShopItemDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonSize
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.game.ItemIcon
import com.kma.quiz_game.ui.components.game.rarityColor
import com.kma.quiz_game.ui.components.game.rarityLabel
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.ShapeXl

/**
 * Sub-tab 2 of the character hub: the only place gold is spent on something other than a skill.
 *
 * Everything here is a skin, and that is a deliberate rule rather than a starting catalog -- a
 * priced item carries no EXP or Gold buff server-side, so no amount of grinding can buy a better
 * match. Equipment stays chest-only, which also means nothing on this shelf can be had free from
 * a chest later. See `duo-game-back/android.md` §3B.2.
 */
@Composable
fun ShopScreen(modifier: Modifier = Modifier) {
    val viewModel: ShopViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()

    // Re-read on every visit: gold earned since the last look decides what is affordable here.
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Cửa hàng", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "${state.gold} vàng",
                style = MaterialTheme.typography.titleMedium,
                color = Orange400,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "Vàng kiếm được từ bài học và trận đấu. Trang phục chỉ đổi vẻ ngoài, " +
                "không cộng chỉ số nào.",
            style = MaterialTheme.typography.bodyLarge,
            color = Neutral500,
        )
        Spacer(Modifier.height(16.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) { CircularProgressIndicator() }
        }

        state.items.forEach { item ->
            ShopRow(
                item = item,
                action = state.actionFor(item),
                isBuying = state.buying == item.id,
                onBuy = { viewModel.buy(item) },
            )
        }

        state.justBought?.let { name ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Đã mua $name. Xem ở tab Tủ đồ.",
                style = MaterialTheme.typography.bodyMedium,
                color = Green500,
            )
            TextButton(onClick = viewModel::dismissError) { Text("Đã hiểu") }
        }

        state.errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(text = message, color = Rose500, style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = viewModel::dismissError) { Text("Đã hiểu") }
                TextButton(onClick = viewModel::load) { Text("Tải lại") }
            }
        }

        if (state.items.isEmpty() && !state.isLoading) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Cửa hàng chưa có món nào.",
                style = MaterialTheme.typography.bodyLarge,
                color = Neutral500,
            )
            Spacer(Modifier.height(12.dp))
            DuoButton(text = "Tải lại", onClick = viewModel::load, variant = DuoButtonVariant.Outline)
        }
    }
}

@Composable
private fun ShopRow(
    item: ShopItemDto,
    action: ShopItemAction,
    isBuying: Boolean,
    onBuy: () -> Unit,
) {
    val color = rarityColor(item.rarity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(ShapeXl)
            .background(Neutral050)
            .border(
                width = 2.dp,
                color = if (action == ShopItemAction.OWNED) Green500 else color,
                shape = ShapeXl,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemIcon(code = item.code, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = Neutral700,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${rarityLabel(item.rarity)} · ${item.goldPrice} vàng",
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
        }
        when (action) {
            ShopItemAction.OWNED -> Text(
                text = "Đã sở hữu",
                style = MaterialTheme.typography.labelLarge,
                color = Green500,
                fontWeight = FontWeight.Bold,
            )

            ShopItemAction.TOO_EXPENSIVE -> Text(
                text = "Chưa đủ vàng",
                style = MaterialTheme.typography.labelLarge,
                color = Neutral500,
                fontWeight = FontWeight.Bold,
            )

            // An explicit width, because `fullWidth = false` only drops the button's own
            // fillMaxWidth -- its inner layers still fill whatever they are offered, so an
            // unbounded one would measure to the whole row and starve the weighted text column.
            ShopItemAction.BUY -> DuoButton(
                text = if (isBuying) "Đang mua" else "Mua",
                onClick = onBuy,
                modifier = Modifier.width(112.dp),
                enabled = !isBuying,
                variant = DuoButtonVariant.Primary,
                size = DuoButtonSize.Small,
                fullWidth = false,
            )
        }
    }
}
