package com.kma.quiz_game.ui.components.duo

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.data.remote.dto.DuoPlayerDto
import com.kma.quiz_game.ui.components.UserAvatar
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/**
 * The two players facing off, with their live scores and the round counter between them.
 *
 * Scores animate rather than jump: a round can award anywhere from 0 to 1000 points, and watching
 * the number climb is what makes the speed bonus legible.
 */
@Composable
fun VersusHeader(
    me: DuoPlayerDto?,
    opponent: DuoPlayerDto?,
    myScore: Int,
    opponentScore: Int,
    roundLabel: String,
    modifier: Modifier = Modifier,
    opponentConnected: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Neutral050)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerColumn(
            player = me,
            fallbackName = "Bạn",
            score = myScore,
            accent = Green500,
            modifier = Modifier.weight(1f),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = "VS",
                style = MaterialTheme.typography.titleMedium,
                color = Neutral500,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = roundLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Neutral500,
            )
        }

        PlayerColumn(
            player = opponent,
            fallbackName = "Đối thủ",
            score = opponentScore,
            accent = if (opponentConnected) Sky500 else Rose500,
            modifier = Modifier.weight(1f),
            subtitle = if (opponentConnected) null else "Mất kết nối",
        )
    }
}

@Composable
private fun PlayerColumn(
    player: DuoPlayerDto?,
    fallbackName: String,
    score: Int,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val animatedScore by animateIntAsState(targetValue = score, label = "score")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        UserAvatar(
            userId = player?.id ?: fallbackName,
            username = player?.username ?: fallbackName,
            avatarUrl = player?.avatarUrl,
            size = 44.dp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = player?.username ?: fallbackName,
            style = MaterialTheme.typography.labelLarge,
            color = Neutral700,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "$animatedScore",
            style = MaterialTheme.typography.titleLarge,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Rose500,
                textAlign = TextAlign.Center,
            )
        }
    }
}
