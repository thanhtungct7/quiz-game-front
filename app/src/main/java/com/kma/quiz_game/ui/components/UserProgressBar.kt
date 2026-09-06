package com.kma.quiz_game.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.components.profile.CefrBadge
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/**
 * The header strip above the learn path: who the player is, in four figures.
 *
 * Every value here comes from the server. It used to draw a Spanish flag and a locally-counted
 * points total, which meant the one strip on screen at all times was the one thing in the app not
 * telling the truth about the account.
 *
 * [level] and [cefr] come from the aggregated profile, [hearts] from the energy bar. A null
 * [level] means the profile has not loaded yet, and those two chips are simply left out rather
 * than shown as a placeholder that will visibly change a moment later.
 */
@Composable
fun UserProgressBar(
    hearts: Int,
    isPro: Boolean,
    modifier: Modifier = Modifier,
    level: Int? = null,
    cefr: String = "",
    dayStreak: Int = 0,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cefr.isNotBlank()) {
            CefrBadge(cefr = cefr, compact = true)
        }
        if (level != null) {
            LabelledStat(symbol = "Lv", value = "$level", tint = Sky500)
        }
        // A zero streak is not worth a chip: it is the state of every account that has not
        // studied today, and drawing it makes the strip busier without saying anything.
        if (dayStreak > 0) {
            LabelledStat(symbol = "🔥", value = "$dayStreak", tint = Orange400)
        }
        Spacer(Modifier.width(0.dp))
        HeartsChip(hearts = hearts, isPro = isPro)
    }
}

@Composable
private fun LabelledStat(symbol: String, value: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = symbol, style = MaterialTheme.typography.labelMedium, color = tint)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HeartsChip(hearts: Int, isPro: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "❤", style = MaterialTheme.typography.titleMedium, color = Rose500)
        if (isPro) {
            Icon(
                Icons.Filled.AllInclusive,
                contentDescription = "không giới hạn",
                tint = Rose500,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = hearts.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserProgressBarPreview() {
    Quiz_gameTheme {
        UserProgressBar(hearts = 3, isPro = false, level = 34, cefr = "B1", dayStreak = 12)
    }
}
