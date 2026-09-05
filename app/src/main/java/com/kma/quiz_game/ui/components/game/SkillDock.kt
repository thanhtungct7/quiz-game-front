package com.kma.quiz_game.ui.components.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kma.quiz_game.data.remote.dto.LoadoutSlotDto
import com.kma.quiz_game.data.remote.dto.SkillEffect
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Neutral300
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500

/** Why a slot cannot be tapped right now. [READY] is the only state that casts. */
enum class SkillSlotState { READY, NO_MANA, USED, BLOCKED }

/**
 * The three equipped skills, docked at the bottom of a fight.
 *
 * Docked rather than floating, and below the answers rather than over them: a round is timed in
 * seconds, and a control that can cover the option a player is reaching for is worse than no
 * control at all.
 *
 * The empty state is not decoration. Only an equipped skill can be cast, and a player who never
 * opened the loadout screen has an empty bar for a reason they cannot guess from the fight -- so
 * the bar says so, and offers the way there.
 */
@Composable
fun SkillDock(
    slots: List<LoadoutSlotDto>,
    mana: Int,
    canCast: Boolean,
    usedCodes: Set<String>,
    onCast: (LoadoutSlotDto) -> Unit,
    modifier: Modifier = Modifier,
    /** Bumped by the caller when the server refuses a cast, to flash the slot that was tapped. */
    rejectedCode: String? = null,
    rejectedSeq: Int = 0,
    onOpenLoadout: (() -> Unit)? = null,
) {
    var explained by remember { mutableStateOf<LoadoutSlotDto?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Neutral050)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (slots.isEmpty()) {
            EmptyDock(onOpenLoadout)
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            slots.forEach { slot ->
                SkillSlot(
                    slot = slot,
                    state = when {
                        slot.code in usedCodes -> SkillSlotState.USED
                        !canCast -> SkillSlotState.BLOCKED
                        mana < slot.manaCost -> SkillSlotState.NO_MANA
                        else -> SkillSlotState.READY
                    },
                    // Re-keyed on every rejection so two refusals in a row flash twice.
                    flashKey = if (slot.code == rejectedCode) rejectedSeq else 0,
                    onClick = { onCast(slot) },
                    onLongClick = { explained = if (explained == slot) null else slot },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        explained?.let { slot ->
            Text(
                text = "${slot.name}: ${effectDescription(slot.effect)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Neutral500,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyDock(onOpenLoadout: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Neutral100)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Chưa trang bị kỹ năng nào",
            style = MaterialTheme.typography.bodyMedium,
            color = Neutral500,
        )
        if (onOpenLoadout != null) {
            Text(
                text = "Mở trang bị kỹ năng",
                style = MaterialTheme.typography.labelLarge,
                color = Indigo500,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenLoadout,
                    ),
            )
        }
    }
}

@Composable
private fun SkillSlot(
    slot: LoadoutSlotDto,
    state: SkillSlotState,
    flashKey: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var flashing by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(flashKey) {
        if (flashKey > 0) {
            flashing = true
            kotlinx.coroutines.delay(FLASH_MILLIS)
            flashing = false
        }
    }

    val border by animateColorAsState(
        targetValue = when {
            flashing -> Rose500
            state == SkillSlotState.READY -> Indigo500
            else -> Neutral300
        },
        label = "skillBorder",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(width = 2.dp, color = border, shape = RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // Never disabled outright: a tap on a blocked skill is how a player asks why, and
                // the caller answers with the flash and the nudge text.
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 8.dp, horizontal = 6.dp)
            .alpha(if (state == SkillSlotState.READY) 1f else 0.55f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = effectSymbol(slot.effect), fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = slot.name,
            style = MaterialTheme.typography.labelMedium,
            color = Neutral700,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (state == SkillSlotState.USED) "đã dùng" else "${slot.manaCost} mana",
                style = MaterialTheme.typography.labelSmall,
                color = if (state == SkillSlotState.NO_MANA) Rose500 else Indigo500,
            )
        }
    }
}

private const val FLASH_MILLIS = 350L

/** One glyph per effect, so a bar of three is readable without reading. */
fun effectSymbol(effect: String): String = when (effect) {
    SkillEffect.DOUBLE_DAMAGE -> "⚡"
    SkillEffect.DAMAGE_REDUCTION -> "🛡"
    SkillEffect.HEAL -> "💧"
    SkillEffect.TIME_PENALTY -> "⏳"
    SkillEffect.REMOVE_OPTIONS -> "👁"
    SkillEffect.MANA_BURN -> "🔥"
    SkillEffect.COMBO_KEEP -> "🔗"
    SkillEffect.EXECUTE -> "🗡"
    else -> "✨"
}

/**
 * What an effect does, in one clause.
 *
 * The magnitude is deliberately absent: it is read against the effect (percent, thousandths,
 * health, seconds) and the loadout payload does not carry it, so a number here would be guesswork.
 * The skill tree screen, which does have it, is where exact numbers belong.
 */
fun effectDescription(effect: String): String = when (effect) {
    SkillEffect.DOUBLE_DAMAGE -> "tăng mạnh sát thương đòn tới"
    SkillEffect.DAMAGE_REDUCTION -> "giảm sát thương phải chịu"
    SkillEffect.HEAL -> "hồi máu cho bạn"
    SkillEffect.TIME_PENALTY -> "rút ngắn thời gian của đối thủ"
    SkillEffect.REMOVE_OPTIONS -> "loại bớt đáp án sai (chỉ bạn thấy)"
    SkillEffect.MANA_BURN -> "đốt mana của đối thủ"
    SkillEffect.COMBO_KEEP -> "giữ chuỗi combo khi trả lời sai"
    SkillEffect.EXECUTE -> "kết liễu đối thủ đang kiệt máu"
    else -> "hiệu ứng đặc biệt"
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SkillDockPreview() {
    Quiz_gameTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkillDock(
                slots = listOf(
                    LoadoutSlotDto(0, "1", "STRIKE", "Cường Kích", SkillEffect.DOUBLE_DAMAGE, 30),
                    LoadoutSlotDto(1, "2", "GUARD", "Hộ Giáp", SkillEffect.DAMAGE_REDUCTION, 25),
                    LoadoutSlotDto(2, "3", "MEND", "Hồi Phục", SkillEffect.HEAL, 40),
                ),
                mana = 35,
                canCast = true,
                usedCodes = setOf("GUARD"),
                onCast = {},
            )
            SkillDock(
                slots = emptyList(),
                mana = 0,
                canCast = false,
                usedCodes = emptySet(),
                onCast = {},
                onOpenLoadout = {},
            )
        }
    }
}
