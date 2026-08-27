package com.kma.quiz_game.ui.components.duo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500

private const val WARNING_FRACTION = 0.5f
private const val DANGER_FRACTION = 0.2f

/**
 * The round clock.
 *
 * [fraction] is driven by the caller's countdown rather than an infinite animation, so the bar
 * always agrees with the seconds actually left -- including after a reconnect, where the round
 * resumes part-way through.
 */
@Composable
fun RoundTimerBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    val color by animateColorAsState(
        targetValue = when {
            safeFraction <= DANGER_FRACTION -> Rose500
            safeFraction <= WARNING_FRACTION -> Orange400
            else -> Green500
        },
        label = "timerColor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Neutral100),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safeFraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(color),
        )
    }
}
