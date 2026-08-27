package com.kma.quiz_game.ui.components.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Rose500

/** Elo movement for one match. K=32, so a delta lands roughly in ±16..32. */
@Composable
fun RatingDeltaBadge(delta: Int, modifier: Modifier = Modifier) {
    val color = when {
        delta > 0 -> Green500
        delta < 0 -> Rose500
        else -> Neutral500
    }
    val label = when {
        delta > 0 -> "+$delta"
        else -> "$delta"
    }

    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = androidx.compose.ui.graphics.Color.White,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
