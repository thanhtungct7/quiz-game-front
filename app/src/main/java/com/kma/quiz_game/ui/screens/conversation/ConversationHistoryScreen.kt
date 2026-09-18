package com.kma.quiz_game.ui.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.ConversationStatus
import com.kma.quiz_game.data.remote.dto.ConversationSummaryDto
import com.kma.quiz_game.ui.rememberAppViewModelFactory

/** Every practice conversation, newest first. A finished one opens its feedback, an open one resumes. */
@Composable
fun ConversationHistoryScreen(
    onBack: () -> Unit,
    onOpen: (ConversationSummaryDto) -> Unit,
) {
    val viewModel: ConversationHistoryViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ConversationTopBar(title = "Lịch sử hội thoại", onBack = onBack)

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.items.isEmpty() -> CenteredMessage(
                message = state.errorMessage ?: "Bạn chưa luyện hội thoại lần nào.",
                actionLabel = if (state.errorMessage != null) "Thử lại" else null,
                onAction = viewModel::refresh,
            )

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.errorMessage?.let { message ->
                    item {
                        Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    HistoryRow(item = item, onClick = { onOpen(item) }, onDelete = { viewModel.askDelete(item) })
                }
                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Xoá cuộc hội thoại?") },
            text = { Text("\"${item.titleVi}\" và nhận xét của nó sẽ bị xoá hẳn.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Xoá", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Huỷ") } },
        )
    }
}

@Composable
private fun HistoryRow(item: ConversationSummaryDto, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = item.titleVi, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatTimestamp(item.startedAt) + " · ${item.userTurns}/${item.maxTurns} lượt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val score = item.score
            if (item.status == ConversationStatus.FINISHED && score != null) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                LevelChip("Đang dở")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Xoá")
            }
        }
    }
}
