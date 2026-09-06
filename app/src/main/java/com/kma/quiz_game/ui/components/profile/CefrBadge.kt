package com.kma.quiz_game.ui.components.profile

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
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Neutral300
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/**
 * The CEFR band a player's level has reached, derived on the server from the level so the two can
 * never disagree.
 *
 * Like [com.kma.quiz_game.ui.components.game.TierBadge], the band arrives as a raw string, so an
 * unknown value has to render as *something*: it falls through to the A1 styling with the
 * server's own word, which is wrong but readable, rather than crashing or vanishing.
 */
@Composable
fun CefrBadge(
    cefr: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (cefr.isBlank()) return
    val style = cefrStyle(cefr)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(style.color.copy(alpha = 0.15f))
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = style.band,
            style = MaterialTheme.typography.labelMedium,
            color = style.color,
            fontWeight = FontWeight.Bold,
        )
        if (!compact) {
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelMedium,
                color = style.color,
            )
        }
    }
}

data class CefrStyle(val band: String, val label: String, val color: Color)

/** The six bands of `app/services/game/cefr.py`, named the way a learner would describe them. */
fun cefrStyle(cefr: String): CefrStyle = when (cefr.uppercase()) {
    "A1" -> CefrStyle("A1", "Nhập môn", Neutral300)
    "A2" -> CefrStyle("A2", "Sơ cấp", Green500)
    "B1" -> CefrStyle("B1", "Trung cấp", Sky500)
    "B2" -> CefrStyle("B2", "Trung cao cấp", Indigo500)
    "C1" -> CefrStyle("C1", "Cao cấp", Orange400)
    "C2" -> CefrStyle("C2", "Thành thạo", Rose500)
    else -> CefrStyle(cefr, "", Neutral300)
}

@Preview(showBackground = true)
@Composable
private fun CefrBadgePreview() {
    Quiz_gameTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CefrBadge("A1")
            CefrBadge("B1")
            CefrBadge("C2", compact = true)
        }
    }
}
