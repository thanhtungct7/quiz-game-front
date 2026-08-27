package com.kma.quiz_game.ui.screens.duo

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.R
import com.kma.quiz_game.data.remote.dto.DuoMatchEndReason
import com.kma.quiz_game.data.remote.dto.MatchFinishedDto
import com.kma.quiz_game.data.remote.dto.MatchOutcome
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.duo.RatingDeltaBadge
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/** How the match ended, including the Elo it moved. */
@Composable
fun DuoResultScreen(
    onPlayAgain: () -> Unit,
    onBackToLobby: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DuoResultViewModel = viewModel(factory = rememberAppViewModelFactory())
    val result by viewModel.result.collectAsState()

    val finished = result
    if (finished == null) {
        // Nothing to show (a cold start on this route) -- send the player back to the lobby.
        DuoButton(text = "Về sảnh", onClick = onBackToLobby, modifier = modifier.padding(24.dp))
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(
                if (finished.result == MatchOutcome.LOSE) R.drawable.mascot_sad else R.drawable.mascot,
            ),
            contentDescription = null,
            modifier = Modifier.size(140.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = finished.result.title(),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = finished.result.color(),
        )
        finished.endReason.note()?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodyLarge,
                color = Neutral500,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))
        ScoreBoard(finished)

        Spacer(Modifier.height(20.dp))
        RatingPanel(finished)

        Spacer(Modifier.weight(1f))
        DuoButton(
            text = "Đấu tiếp",
            onClick = {
                viewModel.playAgain()
                onPlayAgain()
            },
            variant = DuoButtonVariant.Primary,
        )
        Spacer(Modifier.height(12.dp))
        DuoButton(
            text = "Về sảnh",
            onClick = {
                viewModel.acknowledge()
                onBackToLobby()
            },
            variant = DuoButtonVariant.Outline,
        )
    }
}

@Composable
private fun ScoreBoard(finished: MatchFinishedDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Neutral050)
            .padding(20.dp),
    ) {
        ScoreRow("Điểm", "${finished.yourScore}", "${finished.opponentScore}")
        Spacer(Modifier.height(8.dp))
        ScoreRow("Câu đúng", "${finished.yourCorrect}/${finished.totalRounds}", "${finished.opponentCorrect}/${finished.totalRounds}")
        Spacer(Modifier.height(8.dp))
        ScoreRow("Thời lượng", "${finished.durationSeconds}s", "")
    }
}

@Composable
private fun ScoreRow(label: String, mine: String, theirs: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = Neutral500, modifier = Modifier.weight(1f))
        Text(text = mine, style = MaterialTheme.typography.titleMedium, color = Green500, fontWeight = FontWeight.Bold)
        if (theirs.isNotEmpty()) {
            Text(text = "  vs  ", style = MaterialTheme.typography.bodyMedium, color = Neutral500)
            Text(text = theirs, style = MaterialTheme.typography.titleMedium, color = Sky500, fontWeight = FontWeight.Bold)
        }
    }
}

/** Elo is zero-sum at K=32, so the number here is exactly what the opponent lost or gained. */
@Composable
private fun RatingPanel(finished: MatchFinishedDto) {
    val animatedRating by animateIntAsState(targetValue = finished.rating.after, label = "rating")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Neutral050)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Điểm xếp hạng", style = MaterialTheme.typography.bodyLarge, color = Neutral500)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "${finished.rating.before}",
                style = MaterialTheme.typography.titleLarge,
                color = Neutral500,
            )
            Text(text = "→", style = MaterialTheme.typography.titleLarge, color = Neutral500)
            Text(
                text = "$animatedRating",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Neutral700,
            )
            RatingDeltaBadge(finished.rating.delta)
        }
    }
}

private fun MatchOutcome.title(): String = when (this) {
    MatchOutcome.WIN -> "Chiến thắng!"
    MatchOutcome.LOSE -> "Thua rồi"
    MatchOutcome.DRAW -> "Hoà"
}

private fun MatchOutcome.color() = when (this) {
    MatchOutcome.WIN -> Green500
    MatchOutcome.LOSE -> Rose500
    MatchOutcome.DRAW -> Neutral700
}

/** COMPLETED is the ordinary case and needs no explanation; the others very much do. */
private fun DuoMatchEndReason.note(): String? = when (this) {
    DuoMatchEndReason.COMPLETED -> null
    DuoMatchEndReason.OPPONENT_LEFT -> "Đối thủ đã bỏ trận."
    DuoMatchEndReason.OPPONENT_TIMEOUT -> "Đối thủ mất kết nối quá lâu."
    DuoMatchEndReason.CANCELLED -> "Trận bị huỷ."
}
