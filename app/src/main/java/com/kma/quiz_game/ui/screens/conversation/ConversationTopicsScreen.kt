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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.ScenarioDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.rememberAppViewModelFactory

/** The everyday situations to practise a conversation in, grouped by what they are about. */
@Composable
fun ConversationTopicsScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onStarted: (sessionId: String) -> Unit,
) {
    val viewModel: ConversationTopicsViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.startedSessionId) {
        val sessionId = state.startedSessionId ?: return@LaunchedEffect
        viewModel.consumeStarted()
        onStarted(sessionId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ConversationTopBar(
            title = "Luyện hội thoại",
            onBack = onBack,
            actions = {
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Filled.History, contentDescription = "Lịch sử hội thoại")
                }
            },
        )

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.scenarios.isEmpty() -> CenteredMessage(
                message = state.errorMessage ?: "Chưa có chủ đề nào.",
                actionLabel = "Thử lại",
                onAction = viewModel::load,
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "Chọn một tình huống và trò chuyện bằng tiếng Anh với AI. " +
                            "Cuối buổi, AI sẽ nhận xét và sửa lỗi cho bạn.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.groups.forEach { (category, scenarios) ->
                    item(key = "category-$category") {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(scenarios, key = { it.code }) { scenario ->
                        ScenarioCard(scenario = scenario, onClick = { viewModel.select(scenario) })
                    }
                }
            }
        }
    }

    state.selected?.let { scenario ->
        ScenarioBriefDialog(
            scenario = scenario,
            isStarting = state.isStarting,
            errorMessage = state.startErrorMessage,
            onStart = viewModel::start,
            onDismiss = viewModel::dismissSelection,
        )
    }
}

@Composable
private fun ScenarioCard(scenario: ScenarioDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = scenario.titleVi,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                LevelChip(levelLabel(scenario.suggestedLevels) ?: "Mọi trình độ")
            }
            Text(
                text = scenario.goalVi ?: "Nói chuyện tự nhiên về cuộc sống hằng ngày.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun LevelChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** What the conversation will be, before it starts and counts against today's allowance. */
@Composable
private fun ScenarioBriefDialog(
    scenario: ScenarioDto,
    isStarting: Boolean,
    errorMessage: String?,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(scenario.titleVi) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScenarioBrief(scenario)
                Text(
                    text = "Tối đa ${scenario.maxTurns} lượt nói.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                errorMessage?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (isStarting) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                TextButton(onClick = onStart) { Text("Bắt đầu") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isStarting) { Text("Để sau") }
        },
    )
}

/** The scene, the two roles, the goal and a few phrases to lean on. Shared with the chat screen. */
@Composable
internal fun ScenarioBrief(scenario: ScenarioDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BriefRow(label = "Bối cảnh", value = scenario.setting)
        BriefRow(label = "AI đóng vai", value = scenario.aiRole)
        BriefRow(label = "Bạn là", value = scenario.userRole)
        scenario.goalVi?.let { BriefRow(label = "Mục tiêu", value = it) }
        if (scenario.usefulPhrases.isNotEmpty()) {
            BriefRow(label = "Mẫu câu", value = scenario.usefulPhrases.joinToString("\n") { "• $it" })
        }
    }
}

@Composable
private fun BriefRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ConversationTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

@Composable
internal fun CenteredMessage(message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (actionLabel != null) DuoButton(text = actionLabel, onClick = onAction, fullWidth = false)
        }
    }
}
