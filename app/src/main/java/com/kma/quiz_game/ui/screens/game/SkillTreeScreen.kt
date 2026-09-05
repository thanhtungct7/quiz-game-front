package com.kma.quiz_game.ui.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.SkillLockReason
import com.kma.quiz_game.data.remote.dto.SkillNodeDto
import com.kma.quiz_game.data.remote.dto.SkillUnlockKind
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonSize
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.game.effectDescription
import com.kma.quiz_game.ui.components.game.effectSymbol
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral200
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.ShapeXl
import com.kma.quiz_game.ui.theme.Sky500

/**
 * The skill tree.
 *
 * Laid out as tier bands rather than as a drawn graph with edges. A tier *is* the depth of a node,
 * so banding puts every parent above its children already; each node then names the parent it
 * opens from. On a phone that reads better than a pannable canvas, and it keeps the long
 * descriptions -- which are the actual content -- at full width.
 *
 * The five lock reasons are worded as five different sentences on purpose. "Chưa mở khoá" for all
 * of them would hide the one that matters most: an ultimate bought with study rather than gold,
 * which needs a unit finished and cannot be bought at any price.
 */
@Composable
fun SkillTreeScreen(
    onBack: () -> Unit,
    onOpenLoadout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SkillTreeViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "Cây kỹ năng", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Cấp ${state.tree?.level ?: 1} · ${state.tree?.gold ?: 0} vàng",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Neutral500,
                )
            }
            TextButton(onClick = onOpenLoadout) { Text("Trang bị") }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.tree == null -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.errorMessage ?: "Không tải được cây kỹ năng.",
                    color = Neutral500,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                DuoButton(text = "Thử lại", onClick = viewModel::load)
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.tiers.forEach { (tier, nodes) ->
                    item(key = "tier-$tier") {
                        Text(
                            text = "Bậc $tier",
                            style = MaterialTheme.typography.titleMedium,
                            color = Neutral500,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(nodes.size, key = { nodes[it].id }) { index ->
                        SkillNodeCard(
                            node = nodes[index],
                            isUnlocking = state.unlocking == nodes[index].id,
                            onUnlock = { viewModel.unlock(nodes[index]) },
                        )
                    }
                }
            }
        }

        state.justUnlocked?.let { name ->
            Text(
                text = "Đã mở khoá $name. Nhớ trang bị nó vào thanh kỹ năng!",
                style = MaterialTheme.typography.bodyMedium,
                color = Green500,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        state.errorMessage?.takeIf { state.tree != null }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Rose500,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            TextButton(onClick = viewModel::dismissError) { Text("Đã hiểu") }
        }

        DuoButton(
            text = "Xong",
            onClick = onBack,
            variant = DuoButtonVariant.Outline,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun SkillNodeCard(node: SkillNodeDto, isUnlocking: Boolean, onUnlock: () -> Unit) {
    val accent = when {
        node.owned -> Green500
        node.unlockable -> Indigo500
        else -> Neutral200
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeXl)
            .background(Neutral050)
            .border(width = 2.dp, color = accent, shape = ShapeXl)
            .padding(14.dp)
            .alpha(if (node.owned || node.unlockable) 1f else 0.7f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = effectSymbol(node.effect), fontSize = 22.sp)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Neutral700,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${node.manaCost} mana · ${effectDescription(node.effect)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Indigo500,
                )
            }
            if (node.equippedSlot != null) {
                Text(
                    text = "Ô ${node.equippedSlot + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Green500,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = node.description,
            style = MaterialTheme.typography.bodyMedium,
            color = Neutral500,
        )

        node.parentCode?.let { parent ->
            Text(
                text = "↳ mở từ $parent",
                style = MaterialTheme.typography.bodySmall,
                color = Neutral500,
            )
        }

        Spacer(Modifier.height(8.dp))
        when {
            node.owned -> Text(
                text = "Đã sở hữu",
                style = MaterialTheme.typography.labelLarge,
                color = Green500,
                fontWeight = FontWeight.Bold,
            )

            node.unlockable -> DuoButton(
                text = if (node.goldPrice > 0) "Mở khoá · ${node.goldPrice} vàng" else "Mở khoá",
                onClick = onUnlock,
                enabled = !isUnlocking,
                size = DuoButtonSize.Small,
            )

            else -> Text(
                text = node.lockNote(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (node.unlockKind == SkillUnlockKind.UNIT_COMPLETION) Sky500 else Orange400,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Why this node is shut, in the player's own terms.
 *
 * `NEEDS_UNIT` is the one that must not be flattened into the others: it is not a price, it is a
 * unit of the course, and the way to it is study rather than gold.
 */
private fun SkillNodeDto.lockNote(): String = when (lockedReason) {
    SkillLockReason.WRONG_CLASS -> "Chỉ dành cho lớp khác"
    SkillLockReason.NEEDS_PARENT -> "Cần mở khoá ${parentCode ?: "kỹ năng trước"} trước"
    SkillLockReason.NEEDS_LEVEL -> "Cần đạt cấp $unlockLevel"
    SkillLockReason.NEEDS_GOLD -> "Cần $goldPrice vàng"
    SkillLockReason.NEEDS_UNIT -> "Học xong toàn bộ một unit để mở — không mua được bằng vàng"
    else -> "Chưa mở khoá được"
}
