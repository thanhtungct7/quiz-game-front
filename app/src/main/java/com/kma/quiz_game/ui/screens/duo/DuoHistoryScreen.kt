package com.kma.quiz_game.ui.screens.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.DuoMatchSummaryDto
import com.kma.quiz_game.data.remote.dto.MatchOutcome
import com.kma.quiz_game.ui.components.UserAvatar
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Rose500

/** Every 1v1 you have played, newest first. */
@Composable
fun DuoHistoryScreen(
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DuoHistoryViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Load the next page once the last few rows come into view.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
            }
            Text(text = "Lịch sử đấu", style = MaterialTheme.typography.headlineMedium)
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.matches.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = state.errorMessage ?: "Bạn chưa đấu trận nào.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Neutral500,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.matches, key = { it.matchId }) { match ->
                    MatchRow(match) { onOpenMatch(match.matchId) }
                }
                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchRow(match: DuoMatchSummaryDto, onClick: () -> Unit) {
    Surface(
        color = Neutral050,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutcomeBadge(match.outcome)
            Spacer(Modifier.width(12.dp))
            UserAvatar(
                userId = match.opponent?.id ?: match.matchId,
                username = match.opponent?.username,
                avatarUrl = match.opponent?.avatarUrl,
                size = 36.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = match.opponent?.username ?: "Đối thủ đã rời",
                    style = MaterialTheme.typography.titleMedium,
                    color = Neutral700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${match.myCorrect}/${match.questionCount} câu đúng",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neutral500,
                )
            }
            Text(
                text = "${match.myScore} - ${match.opponentScore}",
                style = MaterialTheme.typography.titleMedium,
                color = Neutral700,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OutcomeBadge(outcome: MatchOutcome?) {
    val (label, color) = when (outcome) {
        MatchOutcome.WIN -> "T" to Green500
        MatchOutcome.LOSE -> "B" to Rose500
        MatchOutcome.DRAW -> "H" to Neutral500
        null -> "?" to Neutral500
    }
    Box(
        modifier = Modifier
            .width(32.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
