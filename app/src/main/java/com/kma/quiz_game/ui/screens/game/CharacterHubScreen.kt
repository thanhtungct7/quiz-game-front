package com.kma.quiz_game.ui.screens.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tab 4 of the bottom nav, "Nhân vật": the character companion and everything owned or worn.
 *
 * Three sub-tabs behind one segmented row rather than three routes, per
 * `duo-game-back/android.md` §3B / §6.2 -- equipment, purchases and class are all "who my
 * character is right now", not three separate destinations to navigate between.
 *
 * [InventoryScreen], [ShopScreen] and [ClassPickerScreen] are reused as-is: each is already a
 * single scrolling `Column` with no `Scaffold` of its own, so they drop into a sub-tab without
 * change. Tủ đồ and Cửa hàng are two views of the same collection -- what a chest dropped and
 * what gold can still add to it -- which is the other reason they belong behind one row of tabs.
 */
@Composable
fun CharacterHubScreen(onPlayDuo: () -> Unit, modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(CharacterHubTab.WARDROBE) }

    Column(modifier = modifier.fillMaxSize()) {
        CharacterHubTabRow(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when (selectedTab) {
            CharacterHubTab.WARDROBE -> InventoryScreen(onPlayDuo = onPlayDuo, modifier = Modifier.weight(1f))
            CharacterHubTab.CLASS -> ClassPickerScreen(onBack = null, modifier = Modifier.weight(1f))
            CharacterHubTab.SHOP -> ShopScreen(modifier = Modifier.weight(1f))
        }
    }
}

private enum class CharacterHubTab { WARDROBE, SHOP, CLASS }

@Composable
private fun CharacterHubTabRow(
    selected: CharacterHubTab,
    onSelect: (CharacterHubTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
    ) {
        CharacterHubTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (tab == selected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

private val CharacterHubTab.label: String
    get() = when (this) {
        CharacterHubTab.WARDROBE -> "Tủ đồ"
        CharacterHubTab.SHOP -> "Cửa hàng"
        CharacterHubTab.CLASS -> "Trường phái"
    }
