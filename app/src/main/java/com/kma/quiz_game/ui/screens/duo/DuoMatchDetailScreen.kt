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
import com.kma.quiz_game.data.remote.dto.DuoSkillUseDto
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Sky500

/**
 * One finished match: the score, the health both sides were left on, and every skill fired.
 *
 * There is no answer-by-answer replay, and there cannot be one. The two players work through their
 * own decks at their own pace, so there is no shared round for a list to be a list of -- what a
 * match is decided on is the health bar and who cleared their deck, and that is what this shows.
 */
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
                if (match.skillUses.isNotEmpty()) {
                    item { SkillLog(match.skillUses) }
                }
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

        // Health, not points, is what the match was decided on -- so the replay leads with it.
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${match.myHpLeft}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Green500,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = "máu của bạn", style = MaterialTheme.typography.bodyMedium, color = Neutral500)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${match.opponentHpLeft}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Sky500,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = "máu đối thủ", style = MaterialTheme.typography.bodyMedium, color = Neutral500)
            }
        }
    }
}

/**
 * Every skill either player fired, in the order they fired them.
 *
 * The one place a match that was lost to a well-timed shield can be explained: the health bars say
 * what happened, and this says why.
 */
@Composable
private fun SkillLog(skillUses: List<DuoSkillUseDto>) {
    Surface(color = Neutral050, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Kỹ năng đã dùng",
                style = MaterialTheme.typography.labelLarge,
                color = Neutral500,
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            skillUses.forEach { use ->
                Text(
                    text = "${if (use.mine) "Bạn" else "Đối thủ"} dùng ${use.skillCode} " +
                        "(${use.manaSpent} mana, sau ${use.roundIndex} câu)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (use.mine) Indigo500 else Sky500,
                )
            }
        }
    }
}
