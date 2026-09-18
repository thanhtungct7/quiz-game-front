package com.kma.quiz_game.ui.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.data.remote.dto.ConversationDetailDto
import com.kma.quiz_game.data.remote.dto.ConversationFeedbackDto
import com.kma.quiz_game.data.remote.dto.ConversationMessageRole
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Orange500
import com.kma.quiz_game.ui.theme.Rose500

/** What the AI made of one conversation: a score, the mistakes, better ways to say things. */
@Composable
fun ConversationFeedbackScreen(
    sessionId: String,
    onBack: () -> Unit,
    onPracticeAgain: (newSessionId: String) -> Unit,
    onOtherTopics: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val viewModel: ConversationFeedbackViewModel = viewModel(
        key = "conversation_feedback_$sessionId",
        factory = viewModelFactory { initializer { ConversationFeedbackViewModel(sessionId, app.conversationRepository) } },
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.startedSessionId) {
        val started = state.startedSessionId ?: return@LaunchedEffect
        viewModel.consumeStarted()
        onPracticeAgain(started)
    }

    val detail = state.detail
    val feedback = detail?.feedback
    Column(Modifier.fillMaxSize()) {
        ConversationTopBar(title = detail?.scenario?.titleVi ?: "Nhận xét", subtitle = "Nhận xét của AI", onBack = onBack)

        if (detail == null || feedback == null) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text("Đang tải nhận xét...")
                    }
                }
            } else {
                CenteredMessage(
                    message = state.errorMessage ?: "Chưa có nhận xét.",
                    actionLabel = "Thử lại",
                    onAction = viewModel::load,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScoreCard(feedback) }

            if (feedback.corrections.isNotEmpty()) {
                item { SectionTitle("Sửa lỗi (${feedback.corrections.size})") }
                items(feedback.corrections) { correction ->
                    FeedbackCard {
                        Text(
                            text = correction.original,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Rose500,
                            textDecoration = TextDecoration.LineThrough,
                        )
                        Text(
                            text = correction.corrected,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Green500,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(text = correction.explanationVi, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                item {
                    FeedbackCard {
                        Text("Không có lỗi ngữ pháp nào. Rất tốt!", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (feedback.betterPhrases.isNotEmpty()) {
                item { SectionTitle("Nói tự nhiên hơn") }
                items(feedback.betterPhrases) { phrase ->
                    FeedbackCard {
                        Text(
                            text = phrase.original,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "→ ${phrase.natural}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (phrase.noteVi.isNotBlank()) {
                            Text(text = phrase.noteVi, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (feedback.newWords.isNotEmpty()) {
                item { SectionTitle("Từ và cụm từ nên học") }
                item { WordChips(feedback.newWords) }
            }

            item {
                TextButton(onClick = viewModel::toggleTranscript) {
                    Text(if (state.showTranscript) "Ẩn đoạn hội thoại" else "Xem lại đoạn hội thoại")
                    Icon(
                        imageVector = if (state.showTranscript) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            if (state.showTranscript) {
                item { Transcript(detail) }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            DuoButton(
                text = if (state.isStartingAgain) "Đang mở..." else "Luyện lại",
                onClick = viewModel::practiceAgain,
                enabled = !state.isStartingAgain,
            )
            DuoButton(text = "Chủ đề khác", onClick = onOtherTopics, variant = DuoButtonVariant.Outline)
        }
    }
}

@Composable
private fun ScoreCard(feedback: ConversationFeedbackDto) {
    val color = when {
        feedback.score >= 70 -> Green500
        feedback.score >= 50 -> Orange500
        else -> Rose500
    }
    FeedbackCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { feedback.score / 100f },
                    modifier = Modifier.size(72.dp),
                    color = color,
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(text = "${feedback.score}", style = MaterialTheme.typography.headlineSmall, color = color)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = scoreLabel(feedback.score), style = MaterialTheme.typography.titleLarge)
                when (feedback.goalCompleted) {
                    true -> Text("✓ Đã hoàn thành mục tiêu", color = Green500, style = MaterialTheme.typography.bodyMedium)
                    false -> Text("Chưa hoàn thành mục tiêu", color = Orange500, style = MaterialTheme.typography.bodyMedium)
                    null -> Unit
                }
            }
        }
        Text(text = feedback.summaryVi, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun FeedbackCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordChips(words: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        words.forEach { word ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(text = word, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun Transcript(detail: ConversationDetailDto) {
    FeedbackCard {
        detail.messages.sortedBy { it.seq }.forEach { message ->
            val isUser = message.role == ConversationMessageRole.USER
            Text(
                text = (if (isUser) "Bạn: " else "AI: ") + message.content,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isUser) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
