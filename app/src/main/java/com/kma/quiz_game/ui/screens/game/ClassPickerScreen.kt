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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.GameClassDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import androidx.compose.ui.graphics.Color
import com.kma.quiz_game.ui.components.profile.HeroPortrait
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.ShapeXl
import com.kma.quiz_game.ui.theme.Sky500

/**
 * The class picker: the stat block a player brings into every fight, PvP and PvE alike.
 *
 * The three classes are shown side by side rather than one at a time because the choice is
 * entirely a comparison -- more health against more damage against more opening mana -- and a
 * carousel would hide the very numbers being traded off. They sit in one row now (not stacked)
 * so the same layout fits both the old full-screen deep link and the "Trường phái" sub-tab of
 * the Character Hub (see `duo-game-back/android.md` §6.2) without two designs to maintain.
 *
 * The confirmation is not boilerplate. The first pick is free, but every later one costs gold
 * *and clears the equipped skills*, and losing a carefully built bar to a mistap is not something
 * an undo can fix.
 *
 * [onBack] is `null` when this is embedded in a tab rather than pushed as its own route: there is
 * no back stack to pop, so the trailing "Xong" button -- which exists only to leave that route --
 * is left out rather than wired to a no-op.
 */
@Composable
fun ClassPickerScreen(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val viewModel: ClassPickerViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(text = "Trường phái", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Trường phái quyết định máu, sát thương, giáp và mana khởi đầu của bạn trong mọi trận đấu.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) { CircularProgressIndicator() }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.classes.forEach { gameClass ->
                ClassCard(
                    gameClass = gameClass,
                    onClick = { viewModel.select(gameClass) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.hasClass) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Đổi trường phái tốn $CLASS_CHANGE_COST vàng và xoá thanh kỹ năng đang trang bị. " +
                    "Kỹ năng đã mở khoá thì không mất.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Vàng hiện có: ${state.profile?.gold ?: 0}",
                style = MaterialTheme.typography.titleMedium,
                color = Orange400,
                fontWeight = FontWeight.Bold,
            )
        }

        state.errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(text = message, color = Rose500, style = MaterialTheme.typography.bodyMedium)
            // "Tải lại" as well as "Đã hiểu", the same pair the shop and the wardrobe offer: the
            // failure that lands here is a failed fetch, and dismissing it left the screen empty
            // with no way back other than leaving the tab.
            Row {
                TextButton(onClick = viewModel::dismissError) { Text("Đã hiểu") }
                TextButton(onClick = viewModel::load) { Text("Tải lại") }
            }
        }

        if (onBack != null) {
            Spacer(Modifier.height(20.dp))
            DuoButton(text = "Xong", onClick = onBack, variant = DuoButtonVariant.Outline)
        }
    }

    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(if (state.hasClass) "Đổi sang ${pending.name}?" else "Chọn ${pending.name}?") },
            text = {
                Text(
                    if (!state.hasClass) {
                        "Lần chọn đầu tiên miễn phí."
                    } else if (state.canAfford) {
                        "Tốn $CLASS_CHANGE_COST vàng và thanh kỹ năng sẽ bị xoá sạch."
                    } else {
                        "Bạn không đủ $CLASS_CHANGE_COST vàng."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = state.canAfford && !state.isSaving,
                    onClick = { viewModel.confirm { onBack?.invoke() } },
                ) { Text("Xác nhận") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirm) { Text("Huỷ") } },
        )
    }
}

@Composable
private fun ClassCard(gameClass: GameClassDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(ShapeXl)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 2.dp,
                color = if (gameClass.isCurrent) Green500 else MaterialTheme.colorScheme.outline,
                shape = ShapeXl,
            )
            .clickable(enabled = !gameClass.isCurrent, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (gameClass.isCurrent) {
            Text(
                text = "Đang dùng",
                style = MaterialTheme.typography.labelSmall,
                color = Green500,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
        }
        // The character, not just the numbers. A school is a sprite before it is a stat block, and
        // until this was here the only way to find out what you had picked was to start a fight.
        HeroPortrait(
            modifier = Modifier
                .fillMaxWidth()
                .height(PORTRAIT_HEIGHT),
            glow = classGlow(gameClass.code),
            classCode = gameClass.code,
            // Idle only on the current pick: three sprite loops on one screen is a lot of motion
            // for a decision that wants a calm read.
            animated = gameClass.isCurrent,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = gameClass.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = gameClass.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StatCell("Máu", "${gameClass.maxHp}", Green500)
            // The damage a correct answer really deals, not the multiplier behind it: this is the
            // number a player weighs against the HP on the line above, and "x0,90" cannot be
            // weighed against anything. Same colours and same pairing as the profile card, so the
            // two screens read as one system. `damagePermille` is deliberately not drawn: it is
            // the same fact in a form nobody picking a class can use.
            StatCell("Sát thương", "${gameClass.atk}", Rose500)
            StatCell("Giáp", "${gameClass.defence}", Sky500)
            StatCell("Mana đầu", "${gameClass.startingMana}", Indigo500)
        }
    }
}

/** One colour per school, so the three plinths do not all glow the same. */
private fun classGlow(code: String): Color = when (code.uppercase()) {
    "WARRIOR" -> Rose500
    "MAGE" -> Indigo500
    else -> Green500
}

@Composable
private fun StatCell(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Tall enough to read the silhouette, short enough that three fit in one row on a phone. */
private val PORTRAIT_HEIGHT = 96.dp
