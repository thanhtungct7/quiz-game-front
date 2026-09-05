package com.kma.quiz_game.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Neutral300
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/**
 * The rank tier, derived on the server from the rating so the two can never disagree.
 *
 * The tier arrives as a raw string rather than an enum, so an unknown value has to render as
 * *something*: it falls through to the bronze styling with the server's own word, which is wrong
 * but readable, rather than crashing or vanishing.
 */
@Composable
fun TierBadge(
    tier: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (tier.isBlank()) return
    val style = tierStyle(tier)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(style.color.copy(alpha = 0.15f))
            .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = style.symbol, style = MaterialTheme.typography.labelMedium)
        if (!compact) {
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelMedium,
                color = style.color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

data class TierStyle(val label: String, val symbol: String, val color: Color)

/** The six tiers of `app/services/game/season.py`, in Vietnamese as the ladder names them. */
fun tierStyle(tier: String): TierStyle = when (tier.uppercase()) {
    "SILVER" -> TierStyle("Bạc", "🥈", Neutral500)
    "GOLD" -> TierStyle("Vàng", "🥇", Orange400)
    "PLATINUM" -> TierStyle("Bạch kim", "💠", Sky500)
    "DIAMOND" -> TierStyle("Kim cương", "💎", Indigo500)
    "MASTER" -> TierStyle("Cao thủ", "👑", Rose500)
    "BRONZE" -> TierStyle("Đồng", "🥉", Neutral300)
    else -> TierStyle(tier, "🥉", Neutral300)
}

@Preview(showBackground = true)
@Composable
private fun TierBadgePreview() {
    Quiz_gameTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TierBadge("BRONZE")
            TierBadge("GOLD")
            TierBadge("MASTER")
            TierBadge("DIAMOND", compact = true)
        }
    }
}
