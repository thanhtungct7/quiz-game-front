package com.kma.quiz_game.ui.components.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500

private const val WARNING_FRACTION = 0.5f
private const val DANGER_FRACTION = 0.25f

/**
 * A health bar for any fighter in either mode: the player or the monster in a lesson battle, and
 * both sides of a duo match.
 *
 * Animated on purpose: a blow that lands is the one moment of the fight worth seeing, and a bar
 * that snaps makes ten damage and forty damage look the same.
 *
 * [mirrored] empties the bar towards the left instead of the right. In a duo header the two bars
 * face each other, and both draining the same way would read as one long bar rather than two
 * opposed ones.
 */
@Composable
fun HpBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    color: Color? = null,
    mirrored: Boolean = false,
    /**
     * Delays the bar to match the moment a lunging blow actually connects in the arena. Duo has no
     * lunge, so it stays 0; PvE passes the same wind-up time the arena uses.
     */
    impactDelayMillis: Int = 0,
) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = safeFraction,
        animationSpec = tween(durationMillis = 450, delayMillis = impactDelayMillis),
        label = "hp",
    )
    val barColor = color ?: when {
        safeFraction <= DANGER_FRACTION -> Rose500
        safeFraction <= WARNING_FRACTION -> Orange400
        else -> Green500
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Neutral100),
        contentAlignment = if (mirrored) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(barColor),
        )
    }
}

@Preview(showBackground = true, widthDp = 240)
@Composable
private fun HpBarPreview() {
    Quiz_gameTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            HpBar(fraction = 0.85f)
            HpBar(fraction = 0.35f)
            HpBar(fraction = 0.15f, mirrored = true)
        }
    }
}
