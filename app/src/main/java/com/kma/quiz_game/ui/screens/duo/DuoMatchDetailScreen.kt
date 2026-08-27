package com.kma.quiz_game.ui.screens.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.data.remote.dto.DuoMatchDetailDto
import com.kma.quiz_game.data.remote.dto.DuoRoundDto
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/** One finished match, round by round: who picked what, how fast, and what it scored. */
@Composable
fun DuoMatchDetailScreen(
    matchId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val viewModel: DuoMatchDetailViewModel = viewModel(
        key = "duo_match_$matchId",
        factory = viewModelFactory {
            initializer { DuoMatchDetailViewModel(matchId, app.duoRepository) }
        },
    )
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
            }
            Text(text = "Chi tiết trận", style = MaterialTheme.typography.headlineMedium)
        }

        val match = state.match
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            match == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = state.errorMessage ?: "Không tải được trận đấu.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Neutral500,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { DetailHeader(match) }
                items(match.rounds, key = { it.roundIndex }) { round -> RoundCard(round) }
            }
        }
    }
}

@Composable
private fun DetailHeader(match: DuoMatchDetailDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Neutral050)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${match.myScore} - ${match.opponentScore}",
            style = MaterialTheme.typography.displayLarge,
            color = Neutral700,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "vs ${match.opponent?.username ?: "đối thủ"}",
            style = MaterialTheme.typography.bodyLarge,
            color = Neutral500,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${match.myCorrect}/${match.questionCount} đúng · " +
                (match.durationSeconds?.let { "${it}s" } ?: "chưa kết thúc"),
            style = MaterialTheme.typography.bodyMedium,
            color = Neutral500,
        )
    }
}

@Composable
private fun RoundCard(round: DuoRoundDto) {
    Surface(color = Neutral050, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Hiệp ${round.roundIndex + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = Neutral500,
            )
            Text(
                text = round.question ?: "(câu hỏi đã bị xoá)",
                style = MaterialTheme.typography.bodyLarge,
                color = Neutral700,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SideResult("Bạn", round.myCorrect, round.myElapsedMs, round.myPoints, Green500)
                SideResult("Đối thủ", round.opponentCorrect, round.opponentElapsedMs, round.opponentPoints, Sky500)
            }
        }
    }
}

@Composable
private fun SideResult(
    label: String,
    correct: Boolean,
    elapsedMs: Int?,
    points: Int,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = Neutral500)
        Text(
            text = if (correct) "Đúng" else "Sai",
            style = MaterialTheme.typography.titleMedium,
            color = if (correct) Green500 else Rose500,
            fontWeight = FontWeight.Bold,
        )
        Text(
            // Server-measured, so it is the real answer time, not what a client claimed.
            text = elapsedMs?.let { "%.1fs · %d điểm".format(it / 1000f, points) } ?: "Không trả lời",
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
        )
    }
}
