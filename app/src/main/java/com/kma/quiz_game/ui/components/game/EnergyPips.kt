package com.kma.quiz_game.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.data.remote.dto.EnergyDto
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Sky500

/**
 * The energy bar, as discrete pips rather than a continuous bar.
 *
 * Energy is spent one whole point per match out of five, so a fraction would be a lie: what the
 * player needs to know is "how many more matches", which is a count.
 *
 * The subtitle is the point of the component. An empty bar refills on its own at one point per
 * thirty minutes, but finishing a lesson refills two -- that is the intended loop, and saying so
 * here is what turns a dead end into a prompt.
 */
@Composable
fun EnergyPips(
    energy: EnergyDto?,
    modifier: Modifier = Modifier,
    showHint: Boolean = true,
) {
    val current = energy?.current ?: 0
    val maximum = (energy?.maximum ?: DEFAULT_MAX_ENERGY).coerceAtLeast(1)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(maximum) { index ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (index < current) Sky500 else Neutral100),
                )
            }
        }
        if (showHint) {
            // Same ⚡ and the same blue as the chip over the learn path: it is one number.
            Text(
                text = if (current <= 0) {
                    "⚡ Hết lượt. Học xong một bài được +2 lượt."
                } else {
                    "⚡ $current/$maximum lượt chơi"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Neutral500,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Only used before the profile has loaded, so the row does not pop into existence. */
private const val DEFAULT_MAX_ENERGY = 5

@Preview(showBackground = true)
@Composable
private fun EnergyPipsPreview() {
    Quiz_gameTheme {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EnergyPips(EnergyDto(current = 3, maximum = 5))
            EnergyPips(EnergyDto(current = 0, maximum = 5))
        }
    }
}
