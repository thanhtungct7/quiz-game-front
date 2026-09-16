package com.kma.quiz_game.ui.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.LOADOUT_SLOTS
import com.kma.quiz_game.data.remote.dto.SkillNodeDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.game.effectDescription
import com.kma.quiz_game.ui.components.game.effectSymbol
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.ShapeXl

/**
 * Choosing the three skills a fight is played with.
 *
 * This is the screen that decides matches. Only an equipped skill can be cast, which makes the
 * build a decision taken *before* the fight rather than during it -- so the bar is shown at the
 * top, always visible, while the owned skills scroll underneath it.
 */
@Composable
fun LoadoutScreen(
    onBack: () -> Unit,
    onOpenSkillTree: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LoadoutViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Trang bị kỹ năng",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        SlotRow(selected = state.selected(), onRemove = viewModel::toggle)

        Text(
            text = "Tổng mana nếu dùng cả ba: ${state.totalManaCost}",
            style = MaterialTheme.typography.bodyMedium,
            color = Indigo500,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            textAlign = TextAlign.Center,
        )

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.owned.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Bạn chưa sở hữu kỹ năng nào.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                DuoButton(text = "Mở cây kỹ năng", onClick = onOpenSkillTree)
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.owned, key = { it.id }) { node ->
                    OwnedSkillRow(
                        node = node,
                        isSelected = node.id in state.selectedIds,
                        // A full bar greys the rest rather than hiding them: the player needs to
                        // see what they are choosing between, not just what is left.
                        isBlocked = state.isFull && node.id !in state.selectedIds,
                        onClick = { viewModel.toggle(node) },
                    )
                }
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Rose500,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            TextButton(onClick = viewModel::dismissError) { Text("Đã hiểu") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DuoButton(
                text = "Đóng",
                onClick = onBack,
                variant = DuoButtonVariant.Outline,
                modifier = Modifier.weight(1f),
            )
            DuoButton(
                text = if (state.isDirty) "Lưu" else "Đã lưu",
                onClick = { viewModel.save(onBack) },
                enabled = state.isDirty && !state.isSaving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SlotRow(selected: List<SkillNodeDto>, onRemove: (SkillNodeDto) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(LOADOUT_SLOTS) { index ->
            val node = selected.getOrNull(index)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (node == null) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        width = 2.dp,
                        color = if (node == null) MaterialTheme.colorScheme.outline else Indigo500,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = node != null) { node?.let(onRemove) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (node == null) {
                    Text(text = "Ô trống", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(text = effectSymbol(node.effect), fontSize = 20.sp)
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "${node.manaCost} mana",
                        style = MaterialTheme.typography.labelSmall,
                        color = Indigo500,
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnedSkillRow(
    node: SkillNodeDto,
    isSelected: Boolean,
    isBlocked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeXl)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 2.dp,
                color = if (isSelected) Indigo500 else MaterialTheme.colorScheme.outline,
                shape = ShapeXl,
            )
            .clickable(enabled = !isBlocked, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = effectSymbol(node.effect), fontSize = 20.sp)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isBlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${node.manaCost} mana · ${effectDescription(node.effect)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isSelected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleLarge,
                color = Green500,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
