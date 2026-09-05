package com.kma.quiz_game.ui.components.game

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kma.quiz_game.data.remote.dto.DuoBlowDto
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

private const val RISE_MILLIS = 600
private const val RISE_DP = -28f

/**
 * The damage number that floats up off whoever just took the blow.
 *
 * Keyed on [blowKey] rather than on the blow itself: two identical blows in consecutive rounds are
 * two events the player must see twice, and a value comparison would show only one. The round
 * index is what the caller passes.
 *
 * A QUICK strike is flat bonus damage and a HEAVY one pierces a defence, so they are worth telling
 * apart -- but only in a word beside the number, never by moving the number itself, because the
 * number is what the eye is tracking.
 */
@Composable
fun BlowPopup(
    blow: DuoBlowDto?,
    blowKey: Int,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(blowKey) {
        shown = blow != null && blow.damage > 0
    }

    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = RISE_MILLIS, easing = LinearOutSlowInEasing),
        label = "blow",
    )

    if (blow == null || blow.damage <= 0 || progress <= 0f) return

    Text(
        text = blow.label(),
        color = blow.color(),
        fontWeight = FontWeight.Bold,
        fontSize = if (blow.isCritical) 26.sp else 20.sp,
        modifier = modifier
            .offset(y = (RISE_DP * progress).dp)
            .alpha(1f - progress),
    )
}

/** `-18` on its own for an ordinary hit; the strike is named only when it changed the number. */
private fun DuoBlowDto.label(): String = when {
    isCritical -> "-$damage CHÍ MẠNG"
    strike == "QUICK" -> "-$damage NHANH"
    strike == "HEAVY" -> "-$damage XUYÊN"
    else -> "-$damage"
}

private fun DuoBlowDto.color(): Color = when {
    isCritical -> Rose500
    strike == "HEAVY" -> Orange400
    strike == "QUICK" -> Sky500
    else -> Rose500
}

@Preview(showBackground = true)
@Composable
private fun BlowPopupPreview() {
    Quiz_gameTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
        ) {
            BlowPopup(DuoBlowDto(damage = 14, strike = "QUICK"), blowKey = 1)
            BlowPopup(DuoBlowDto(damage = 32, strike = "HEAVY", isCritical = true), blowKey = 2)
        }
    }
}
