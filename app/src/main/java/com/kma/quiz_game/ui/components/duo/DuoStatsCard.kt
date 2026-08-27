package com.kma.quiz_game.ui.components.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kma.quiz_game.data.remote.dto.DuoStatsDto
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/** Elo and the W/L/D record behind it. A player who has never finished a match sits at 1000. */
@Composable
fun DuoStatsCard(stats: DuoStatsDto?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Neutral050)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Điểm xếp hạng", style = MaterialTheme.typography.bodyLarge, color = Neutral500)
        Text(
            text = "${stats?.rating ?: DEFAULT_RATING}",
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = Sky500,
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("Thắng", "${stats?.wins ?: 0}", Green500)
            StatCell("Thua", "${stats?.losses ?: 0}", Rose500)
            StatCell("Hoà", "${stats?.draws ?: 0}", Neutral500)
            StatCell("Tỉ lệ", "${stats?.winRate ?: 0.0}%", Sky500)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("Số trận", "${stats?.matchesPlayed ?: 0}", Neutral700)
            StatCell("Chuỗi thắng", "${stats?.currentStreak ?: 0}", Orange400)
            StatCell("Kỷ lục", "${stats?.bestStreak ?: 0}", Orange400)
        }
    }
}

private const val DEFAULT_RATING = 1000

@Composable
private fun StatCell(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Neutral500)
    }
}
