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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Quiz_gameTheme

/**
 * The mana bar: what skills are paid for with.
 *
 * Deliberately purple rather than any shade of the health/timer palette. Health is green-to-red
 * and the round clock rides the same ramp, so a third bar in those colours would read as a fourth
 * warning state instead of a separate resource.
 *
 * Mana climbs on every answer -- 20 for a correct one plus a speed bonus, 5 even for a wrong one --
 * so this bar moves every round and a spend has to be legible against that constant drift; the
 * animation is quicker than the health bar's for exactly that reason.
 */
@Composable
fun ManaBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = safeFraction,
        animationSpec = tween(durationMillis = 300),
        label = "mana",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Neutral100),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(Indigo500),
        )
    }
}

@Preview(showBackground = true, widthDp = 240)
@Composable
private fun ManaBarPreview() {
    Quiz_gameTheme {
        ManaBar(fraction = 0.6f, modifier = Modifier.padding(12.dp))
    }
}
