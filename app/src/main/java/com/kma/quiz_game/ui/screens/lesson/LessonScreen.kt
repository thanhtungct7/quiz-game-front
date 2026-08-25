package com.kma.quiz_game.ui.screens.lesson

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.R
import com.kma.quiz_game.data.local.entities.ChallengeType
import com.kma.quiz_game.ui.components.ChallengeOptionCard
import com.kma.quiz_game.ui.components.ChallengeOptionState
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.ExitDialog
import com.kma.quiz_game.ui.components.HeartsDialog
import com.kma.quiz_game.ui.components.QuestionBubble
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Rose500

@Composable
fun LessonScreen(lessonId: Long, onExit: () -> Unit) {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val viewModel: LessonViewModel = viewModel(
        key = "lesson_$lessonId",
        factory = viewModelFactory {
            initializer { LessonViewModel(lessonId, app.challengeRepository, app.userProgressRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    AnswerSoundEffect(uiState.answerStatus)
    FinishSoundEffect(uiState.isComplete)

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.isComplete) {
        LessonResultScreen(
            earnedPoints = uiState.earnedPoints,
            hearts = uiState.hearts,
            isPro = uiState.isPro,
            onFinish = onExit,
        )
        return
    }

    val challenge = uiState.currentChallenge

    Column(modifier = Modifier.fillMaxSize()) {
        LessonHeader(
            progress = uiState.progressPercent,
            hearts = uiState.hearts,
            isPro = uiState.isPro,
            onExitClick = { viewModel.setExitDialogVisible(true) },
        )

        if (challenge != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (challenge.challenge.type == ChallengeType.ASSIST) {
                    QuestionBubble(question = challenge.challenge.question, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        challenge.options.forEach { option ->
                            ChallengeOptionCard(
                                text = option.text,
                                state = optionState(option.id, option.correct, uiState),
                                onClick = { viewModel.selectOption(option.id) },
                                enabled = uiState.answerStatus == AnswerStatus.NONE,
                            )
                        }
                    }
                } else {
                    Text(text = challenge.challenge.question, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(challenge.options) { option ->
                            ChallengeOptionCard(
                                text = option.text,
                                state = optionState(option.id, option.correct, uiState),
                                onClick = { viewModel.selectOption(option.id) },
                                enabled = uiState.answerStatus == AnswerStatus.NONE,
                            )
                        }
                    }
                }
            }
        }

        LessonFooter(
            status = uiState.answerStatus,
            canCheck = uiState.selectedOptionId != null,
            onCheck = viewModel::onCheck,
            onContinue = viewModel::onContinue,
        )
    }

    if (uiState.showExitDialog) {
        ExitDialog(
            onDismiss = { viewModel.setExitDialogVisible(false) },
            onConfirmExit = onExit,
        )
    }

    if (uiState.showHeartsDialog) {
        HeartsDialog(
            canRefillWithPoints = uiState.points >= com.kma.quiz_game.data.GameConstants.POINTS_TO_REFILL,
            onDismiss = viewModel::dismissHeartsDialog,
            onRefillWithPoints = viewModel::refillHeartsWithPoints,
            onGoToShop = onExit,
        )
    }
}

private fun optionState(optionId: Long, isCorrectOption: Boolean, uiState: LessonUiState): ChallengeOptionState {
    val selected = uiState.selectedOptionId == optionId
    return when (uiState.answerStatus) {
        AnswerStatus.NONE -> if (selected) ChallengeOptionState.SELECTED else ChallengeOptionState.NONE
        AnswerStatus.CORRECT -> if (selected) ChallengeOptionState.CORRECT else ChallengeOptionState.NONE
        AnswerStatus.WRONG -> when {
            selected -> ChallengeOptionState.WRONG
            isCorrectOption -> ChallengeOptionState.CORRECT
            else -> ChallengeOptionState.NONE
        }
    }
}

@Composable
private fun LessonHeader(
    progress: Float,
    hearts: Int,
    isPro: Boolean,
    onExitClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onExitClick) {
            Icon(Icons.Filled.Close, contentDescription = "Exit lesson")
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(12.dp),
            color = Green500,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(R.drawable.ic_heart), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
            Text(text = if (isPro) "∞" else hearts.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LessonFooter(
    status: AnswerStatus,
    canCheck: Boolean,
    onCheck: () -> Unit,
    onContinue: () -> Unit,
) {
    val backgroundColor = when (status) {
        AnswerStatus.CORRECT -> Green500.copy(alpha = 0.15f)
        AnswerStatus.WRONG -> Rose500.copy(alpha = 0.15f)
        AnswerStatus.NONE -> MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(16.dp),
    ) {
        when (status) {
            AnswerStatus.CORRECT -> Text(text = "Nicely done!", color = Green500, style = MaterialTheme.typography.titleMedium)
            AnswerStatus.WRONG -> Text(text = "Correct solution required", color = Rose500, style = MaterialTheme.typography.titleMedium)
            AnswerStatus.NONE -> Unit
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (status) {
            AnswerStatus.NONE -> DuoButton(text = "Check", onClick = onCheck, enabled = canCheck, variant = DuoButtonVariant.Primary)
            AnswerStatus.CORRECT -> DuoButton(text = "Continue", onClick = onContinue, variant = DuoButtonVariant.Primary)
            AnswerStatus.WRONG -> DuoButton(text = "Got it", onClick = onContinue, variant = DuoButtonVariant.Danger)
        }
    }
}
