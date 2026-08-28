package com.kma.quiz_game.ui.screens.leaderboard

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.DuoLeaderboardEntryDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.UserAvatar
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Sky500

private val PODIUM_HEIGHTS = listOf(96.dp, 72.dp, 56.dp)
private val PODIUM_COLORS = listOf(Orange400, Neutral100, Color(0xFFCD7F32))

/** Top players by Elo. Only players with a finished match are on the board at all. */
@Composable
fun LeaderboardScreen(
    onPlayDuo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LeaderboardViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Bảng xếp hạng",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.errorMessage != null && state.entries.isEmpty() -> LeaderboardMessage(
                title = "Không tải được bảng xếp hạng",
                subtitle = state.errorMessage.orEmpty(),
                actionLabel = "Thử lại",
                onAction = viewModel::refresh,
            )

            state.entries.isEmpty() -> LeaderboardMessage(
                title = "Chưa có ai lên bảng",
                subtitle = "Bảng xếp hạng chỉ tính người đã đấu ít nhất một trận. Đấu trận đầu tiên đi!",
                actionLabel = "Vào đấu",
                onAction = onPlayDuo,
            )

            else -> LeaderboardList(state, viewModel::refresh)
        }
    }
}

@Composable
private fun LeaderboardList(state: LeaderboardUiState, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.podium.isNotEmpty()) {
                item { Podium(state.podium, state.myUserId) }
            }
            items(state.rest, key = { it.userId }) { entry ->
                LeaderboardRow(entry, isMe = entry.userId == state.myUserId)
            }
            if (state.errorMessage != null) {
                item {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Ranked but off the page -- pin the row so the player can always find themselves.
        if (state.needsPinnedSelfRow) {
            HorizontalDivider()
            Text(
                text = "Hạng của bạn: #${state.myRank}",
                style = MaterialTheme.typography.titleMedium,
                color = Green500,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Neutral050)
                    .padding(20.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Top three, tallest in the middle. */
@Composable
private fun Podium(top: List<DuoLeaderboardEntryDto>, myUserId: String?) {
    val ordered = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    val heights = listOf(PODIUM_HEIGHTS[1], PODIUM_HEIGHTS[0], PODIUM_HEIGHTS[2])
    val colors = listOf(PODIUM_COLORS[1], PODIUM_COLORS[0], PODIUM_COLORS[2])

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        ordered.forEachIndexed { slot, entry ->
            Column(
                modifier = Modifier.width(96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UserAvatar(
                    userId = entry.userId,
                    username = entry.username,
                    avatarUrl = entry.avatarUrl,
                    size = if (slot == 1) 64.dp else 52.dp,
                    highlighted = entry.userId == myUserId,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.username ?: "Người chơi",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.rating}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Sky500,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heights[slot])
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(colors[slot]),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = "#${entry.rank}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: DuoLeaderboardEntryDto, isMe: Boolean) {
    Surface(
        color = if (isMe) Green500.copy(alpha = 0.12f) else Neutral050,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${entry.rank}",
                style = MaterialTheme.typography.titleMedium,
                color = Neutral500,
                modifier = Modifier.width(36.dp),
            )
            UserAvatar(entry.userId, entry.username, entry.avatarUrl, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.username ?: "Người chơi",
                    style = MaterialTheme.typography.titleMedium,
                    color = Neutral700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.matchesPlayed} trận · ${entry.wins} thắng",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neutral500,
                )
            }
            Text(
                text = "${entry.rating}",
                style = MaterialTheme.typography.titleLarge,
                color = Sky500,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LeaderboardMessage(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Neutral500,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        DuoButton(text = actionLabel, onClick = onAction, variant = DuoButtonVariant.Primary)
    }
}
