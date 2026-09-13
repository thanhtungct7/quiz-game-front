package com.kma.quiz_game.ui.screens.benchmark

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import com.kma.quiz_game.ui.components.ChallengeOptionCard
import com.kma.quiz_game.ui.components.ChallengeOptionState
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.QuestionBubble
import com.kma.quiz_game.ui.components.SentenceBuilder
import com.kma.quiz_game.ui.components.SentenceStatus
import com.kma.quiz_game.ui.components.WordTile
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Rose500

/**
 * The Bài Thi Sát Hạch: one paper standing between the learner and the next CEFR band.
 *
 * Deliberately plain: no per-answer colour, no "Nicely done" -- an exam that told you the answer as
 * you went would not be measuring anything, and the whole point of this screen is that its verdict
 * means something. The server does not tell it either: an answer is taken and graded there, and the
 * only thing that comes back is the grade for the whole paper once it is handed in.
 */
@Composable
fun BenchmarkExamScreen(
    capLevel: Int,
    onExit: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val viewModel: BenchmarkExamViewModel = viewModel(
        key = "benchmark_$capLevel",
        factory = viewModelFactory {
            initializer {
                BenchmarkExamViewModel(capLevel, app.gameRepository)
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    // Walking out mid-exam leaves the paper behind, so Back asks first -- but only while there is a
    // paper being sat. Once it is handed in, Back is just "leave".
    BackHandler(enabled = !uiState.handedIn && !uiState.isLoading && uiState.questions.isNotEmpty()) {
        viewModel.setExitDialogVisible(true)
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text("Đang ra đề sát hạch...", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val result = uiState.result
    if (result != null) {
        BenchmarkResultScreen(
            result = result,
            band = uiState.band,
            onRetake = viewModel::draw,
            onFinish = onExit,
        )
        return
    }

    // Handed in, grade not back yet: either still on its way, or the request failed and the paper
    // is waiting on the server to be handed in again.
    if (uiState.handedIn) {
        HandingIn(
            isSubmitting = uiState.isSubmitting,
            errorMessage = uiState.submitErrorMessage,
            onRetry = viewModel::retrySubmitResult,
            onExit = onExit,
        )
        return
    }

    // The draw failed outright: there is no paper, so there is nothing to sit.
    if (uiState.questions.isEmpty()) {
        ExamUnavailable(message = uiState.errorMessage, onRetry = viewModel::draw, onExit = onExit)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ExamHeader(
            band = uiState.band,
            answered = uiState.currentIndex,
            total = uiState.questions.size,
            progress = uiState.progressPercent,
            remainingSeconds = uiState.remainingSeconds,
            onExitClick = { viewModel.setExitDialogVisible(true) },
        )

        uiState.currentQuestion?.let { question ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                when (question.type) {
                    ChallengeTypeDto.ORDER -> Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        QuestionBubble(question = question.question, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(16.dp))
                        SentenceBuilder(
                            tiles = question.options.map { WordTile(id = it.id, text = it.text) },
                            placedIds = uiState.placedOptionIds,
                            // Always OPEN: the builder's correct/wrong colouring is feedback, and
                            // an exam gives none until the end.
                            status = SentenceStatus.OPEN,
                            onPlace = viewModel::placeOption,
                            onRemove = viewModel::removePlacedOption,
                        )
                    }

                    ChallengeTypeDto.ASSIST -> {
                        QuestionBubble(question = question.question, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            question.options.forEach { option ->
                                ChallengeOptionCard(
                                    text = option.text,
                                    state = selectionState(option.id, uiState.selectedOptionId),
                                    onClick = { viewModel.selectOption(option.id) },
                                    enabled = !uiState.isSubmitting,
                                )
                            }
                        }
                    }

                    ChallengeTypeDto.SELECT -> {
                        Text(
                            text = question.question,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(question.options) { option ->
                                ChallengeOptionCard(
                                    text = option.text,
                                    state = selectionState(option.id, uiState.selectedOptionId),
                                    onClick = { viewModel.selectOption(option.id) },
                                    enabled = !uiState.isSubmitting,
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = Rose500,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            DuoButton(
                text = when {
                    uiState.isSubmitting -> "Đang gửi..."
                    uiState.isLastQuestion -> "Nộp bài"
                    else -> "Câu tiếp theo"
                },
                onClick = viewModel::submitAnswer,
                enabled = uiState.hasAnswer && !uiState.isSubmitting,
                variant = DuoButtonVariant.Primary,
            )
        }
    }

    if (uiState.showExitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setExitDialogVisible(false) },
            title = { Text("Thoát bài thi?") },
            text = {
                Text(
                    "Bạn sẽ không quay lại được bài đang làm. Thi lại lúc nào cũng được, đề sẽ " +
                        "được ra mới và bài này bị huỷ.",
                )
            },
            confirmButton = {
                TextButton(onClick = onExit) { Text("Thoát") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setExitDialogVisible(false) }) { Text("Làm tiếp") }
            },
        )
    }
}

/** The verdict, as graded by the server. A pass arrives with the cap already lifted. */
@Composable
private fun BenchmarkResultScreen(
    result: BenchmarkResult,
    band: String?,
    onRetake: () -> Unit,
    onFinish: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = if (result.passed) "Đạt!" else "Chưa đạt",
                style = MaterialTheme.typography.displaySmall,
                color = if (result.passed) Green500 else Rose500,
            )
            Text(
                text = "${result.correctCount}/${result.total} câu đúng (${result.percent}%)",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (result.passed) {
                    band?.let { "Bạn đã được công nhận bậc $it. Cấp độ sẽ tiếp tục tăng." }
                        ?: "Mốc năng lực đã được mở khoá."
                } else {
                    "Cần đúng tối thiểu ${result.passPercent}% để qua mốc này. " +
                        "Ôn lại các bài đã học rồi thi lại nhé."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            if (result.passed) {
                DuoButton(text = "Tiếp tục học", onClick = onFinish, variant = DuoButtonVariant.Primary)
            } else {
                DuoButton(text = "Thi lại", onClick = onRetake, variant = DuoButtonVariant.Primary)
                TextButton(onClick = onFinish) { Text("Để sau") }
            }
        }
    }
}

/** Between handing the paper in and hearing the grade. */
@Composable
private fun HandingIn(
    isSubmitting: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator()
                Text("Đang chấm bài...", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = errorMessage ?: "Chưa nộp được bài lên máy chủ.",
                    color = Rose500,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                DuoButton(text = "Nộp lại", onClick = onRetry, variant = DuoButtonVariant.Primary)
                TextButton(onClick = onExit) { Text("Để sau") }
            }
        }
    }
}

@Composable
private fun ExamUnavailable(message: String?, onRetry: () -> Unit, onExit: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = message ?: "Chưa tải được đề thi.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            DuoButton(text = "Thử lại", onClick = onRetry, variant = DuoButtonVariant.Primary)
            TextButton(onClick = onExit) { Text("Quay lại") }
        }
    }
}

@Composable
private fun ExamHeader(
    band: String?,
    answered: Int,
    total: Int,
    progress: Float,
    remainingSeconds: Long?,
    onExitClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onExitClick) {
                Icon(Icons.Filled.Close, contentDescription = "Thoát bài thi")
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp),
                color = Green500,
            )
            Text(
                text = "${answered + 1}/$total",
                style = MaterialTheme.typography.titleMedium,
            )
            remainingSeconds?.let { seconds ->
                Text(
                    text = BenchmarkExam.formatClock(seconds),
                    style = MaterialTheme.typography.titleMedium,
                    // The last minute is the one worth noticing.
                    color = if (seconds < 60) Rose500 else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = band?.let { "Bài thi sát hạch bậc $it" } ?: "Bài thi sát hạch",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** No CORRECT/WRONG here on purpose: during an exam an option is either picked or it is not. */
private fun selectionState(optionId: String, selectedOptionId: String?): ChallengeOptionState =
    if (optionId == selectedOptionId) ChallengeOptionState.SELECTED else ChallengeOptionState.NONE
