package com.kma.quiz_game.ui.components.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.data.remote.dto.CombatStatsDto
import com.kma.quiz_game.ui.screens.profile.CombatStat
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/**
 * HP, ATK and DEF, as three bars a player can tap to ask where the number came from.
 *
 * The bars are drawn against fixed ceilings rather than against the player's own totals, because
 * the question they answer is "how good is this build" and that only means something against what
 * a build can reach. The ceilings mirror the server's own limits -- the best class figure plus the
 * equipment cap from `services/game/loot.py` -- so a bar is full exactly when nothing more can be
 * added, and a maxed player is not left looking at a bar with room in it.
 *
 * Mana is deliberately not a bar. It is a budget for casting rather than a measure of the build,
 * and a fourth bar would dilute the three numbers a fight is actually won on; it appears as a
 * column inside the breakdown instead.
 */
@Composable
fun CombatStatBars(
    combat: CombatStatsDto,
    onOpenBreakdown: (CombatStat) -> Unit,
    modifier: Modifier = Modifier,
    onSurface: Color = Color.White,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBarRow(
            icon = Icons.Filled.Favorite,
            label = "HP",
            value = combat.hp,
            ceiling = CEILING_HP,
            color = Green500,
            onSurface = onSurface,
            onClick = { onOpenBreakdown(CombatStat.HP) },
        )
        StatBarRow(
            icon = Icons.Filled.Whatshot,
            label = "ATK",
            value = combat.atk,
            ceiling = CEILING_ATK,
            color = Rose500,
            onSurface = onSurface,
            // The exact multiplier, for anyone comparing two weapons rather than two builds.
            trailing = "x${"%.2f".format(combat.damagePermille / 1000f)}",
            onClick = { onOpenBreakdown(CombatStat.ATK) },
        )
        StatBarRow(
            icon = Icons.Filled.Shield,
            label = "DEF",
            value = combat.defence,
            ceiling = CEILING_DEFENCE,
            color = Sky500,
            onSurface = onSurface,
            onClick = { onOpenBreakdown(CombatStat.DEFENCE) },
        )
    }
}

@Composable
private fun StatBarRow(
    icon: ImageVector,
    label: String,
    value: Int,
    ceiling: Int,
    color: Color,
    onSurface: Color,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    val fraction = if (ceiling <= 0) 0f else (value.toFloat() / ceiling).coerceIn(0f, 1f)
    // Animated for the same reason the level bar is: a build changes while the player is looking
    // at it -- they equip something and come back -- and a bar that fills reads as a gain where a
    // bar that snaps reads as a redraw.
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 500),
        label = "stat-$label",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = onSurface.copy(alpha = 0.75f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(BAR_HEIGHT)
                .clip(RoundedCornerShape(50))
                .background(onSurface.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }

        Text(
            text = "$value",
            style = MaterialTheme.typography.labelLarge,
            color = onSurface,
            fontWeight = FontWeight.Bold,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = onSurface.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.width(2.dp))
        // The affordance for the whole row. A row that opens a modal with nothing to say so is a
        // row nobody taps.
        Icon(
            Icons.Filled.Info,
            contentDescription = "Nguồn điểm $label",
            tint = onSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * The same three numbers as [CombatStatBars], on one line.
 *
 * The profile is an academic record first -- `android.md` §1.1/§3A -- so the fighting build gets a
 * line here instead of a third of the card, and the detail stays one tap away: each chip opens the
 * same breakdown a bar used to. [CombatStatBars] is kept for surfaces that are about the build
 * rather than about the learner, where three full-width bars are the right amount of room.
 */
@Composable
fun CombatStatSummary(
    combat: CombatStatsDto,
    onOpenBreakdown: (CombatStat) -> Unit,
    modifier: Modifier = Modifier,
    onSurface: Color = Color.White,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatChip(
            icon = Icons.Filled.Favorite,
            label = "HP",
            value = "${combat.hp}",
            color = Green500,
            onSurface = onSurface,
            onClick = { onOpenBreakdown(CombatStat.HP) },
        )
        StatChip(
            icon = Icons.Filled.Whatshot,
            label = "ATK",
            value = "${combat.atk}",
            color = Rose500,
            onSurface = onSurface,
            onClick = { onOpenBreakdown(CombatStat.ATK) },
        )
        StatChip(
            icon = Icons.Filled.Shield,
            label = "DEF",
            value = "${combat.defence}",
            color = Sky500,
            onSurface = onSurface,
            onClick = { onOpenBreakdown(CombatStat.DEFENCE) },
        )
    }
}

/**
 * One stat as a tappable chip.
 *
 * The tap is labelled rather than left to the icon: a screen reader announces "HP 118, xem nguồn
 * điểm HP", which is the whole of what the row used to need a separate info icon to say.
 */
@Composable
private fun StatChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    onSurface: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(onSurface.copy(alpha = 0.10f))
            .clickable(onClickLabel = "Xem nguồn điểm $label", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = onSurface.copy(alpha = 0.70f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The mana figure, drawn as a chip beside the bars rather than as one of them. */
@Composable
fun ManaChip(mana: Int, modifier: Modifier = Modifier, onSurface: Color = Color.White) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Indigo500.copy(alpha = 0.20f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.Bolt, contentDescription = null, tint = Indigo500, modifier = Modifier.size(14.dp))
        Text(
            text = "Mana khởi đầu $mana",
            style = MaterialTheme.typography.labelMedium,
            color = onSurface.copy(alpha = 0.85f),
        )
    }
}

private val BAR_HEIGHT = 10.dp

/**
 * What each bar is measured against.
 *
 * From the server's own numbers: the best class figure in `services/game/catalog.py` plus the
 * matching ceiling in `services/game/loot.py` -- 115 + 20 hp, x1.25 + x0.15 of 20 damage, 3 + 2
 * defence. If a class or a cap changes there, these have to change with it, or a full bar stops
 * meaning "nothing more can be added".
 */
private const val CEILING_HP = 135
private const val CEILING_ATK = 28
private const val CEILING_DEFENCE = 5

@Preview(showBackground = true, backgroundColor = 0xFF2B2B2B, widthDp = 320)
@Composable
private fun CombatStatBarsPreview() {
    Quiz_gameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CombatStatBars(
                combat = CombatStatsDto(hp = 118, atk = 27, defence = 6, mana = 14, damagePermille = 1350),
                onOpenBreakdown = {},
            )
            ManaChip(mana = 14)
        }
    }
}
