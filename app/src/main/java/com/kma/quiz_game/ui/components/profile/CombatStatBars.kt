package com.kma.quiz_game.ui.components.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.data.remote.dto.CombatStatsDto
import com.kma.quiz_game.ui.screens.profile.CombatStat
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500
/**
 * HP, ATK and DEF on one line, each chip a tap away from where its number came from.
 *
 * The profile is an academic record first -- `android.md` §1.1/§3A -- so the fighting build gets a
 * line here instead of a third of the card. It used to have a full-width bar per stat, drawn
 * against the server's ceilings; those were dropped once no screen was left that wanted a third of
 * the card spent on the build.
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
