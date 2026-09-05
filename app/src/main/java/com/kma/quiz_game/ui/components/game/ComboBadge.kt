package com.kma.quiz_game.ui.components.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500

/** Three in a row deal 1.5x. */
private const val COMBO_TIER_1 = 3

/** Five in a row deal 2x, count as a critical, and stun the opponent for the next round. */
private const val COMBO_TIER_2 = 5

/**
 * The answer streak, shown only once it is worth something.
 *
 * Hidden below [COMBO_TIER_1] on purpose: a "x1" badge on every round is noise, and the number
 * only starts changing damage at three. It turns red at five, where it also stuns -- the badge
 * changing colour is the only warning the opponent gets that they are about to lose a round.
 */
@Composable
fun ComboBadge(combo: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = combo >= COMBO_TIER_1,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier,
    ) {
        val hot = combo >= COMBO_TIER_2
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background((if (hot) Rose500 else Orange400).copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (hot) "🔥 x$combo" else "x$combo",
                style = MaterialTheme.typography.labelLarge,
                color = if (hot) Rose500 else Orange400,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ComboBadgePreview() {
    Quiz_gameTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            ComboBadge(combo = 3)
            ComboBadge(combo = 5)
        }
    }
}
