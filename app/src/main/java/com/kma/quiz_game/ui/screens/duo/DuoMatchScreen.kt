package com.kma.quiz_game.ui.screens.duo

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import com.kma.quiz_game.data.repository.ConnectionState
import com.kma.quiz_game.data.repository.DuoPhase
import com.kma.quiz_game.data.repository.DuoSession
import com.kma.quiz_game.ui.components.ChallengeOptionCard
import com.kma.quiz_game.ui.components.ChallengeOptionState
import com.kma.quiz_game.ui.components.QuestionBubble
import com.kma.quiz_game.ui.components.duo.ForfeitDialog
import com.kma.quiz_game.ui.components.duo.MatchChatSheet
import com.kma.quiz_game.ui.components.duo.RoundTimerBar
import com.kma.quiz_game.ui.components.duo.VersusHeader
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/**
 * The live 1v1 round.
 *
 * Tapping an option submits immediately: the server times the answer from the moment it sent the
 * question, and the speed bonus is worth up to half the round's points, so there is no confirm
 * step to spend that time on.
 */
@Composable
fun DuoMatchScreen(
    onFinished: () -> Unit,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DuoMatchViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()
    val session = state.session

    LaunchedEffect(session.phase) {
        when (session.phase) {
            DuoPhase.FINISHED -> onFinished()
            // Back to the lobby: the room was left, or the queue was cancelled from elsewhere.
            DuoPhase.IDLE, DuoPhase.QUEUEING, DuoPhase.ROOM_WAITING -> onLeft()
            else -> Unit
        }
    }

    BackHandler { viewModel.setExitDialogVisible(true) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.setExitDialogVisible(true) }) {
                Icon(Icons.Filled.Close, contentDescription = "Thoát trận", tint = Neutral500)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.setChatVisible(true) }) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Trò chuyện",
                    tint = if (session.chat.isEmpty()) Neutral500 else Sky500,
                )
            }
        }

        VersusHeader(
            me = session.me,
            opponent = session.opponent,
            myScore = session.myScore,
            opponentScore = session.opponentScore,
            roundLabel = roundLabel(session),
            opponentConnected = session.opponentConnected,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        StatusBanners(session, state.secondsLeft)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                session.phase == DuoPhase.MATCHED || session.question == null ->
                    WaitingForRound(session)

                else -> RoundBody(
                    session = session,
                    question = session.question,
                    onSelect = viewModel::selectOption,
                )
            }
        }
    }

    if (state.showChat) {
        MatchChatSheet(
            messages = session.chat,
            myUserId = session.me?.id,
            onSend = viewModel::sendChat,
            onDismiss = { viewModel.setChatVisible(false) },
        )
    }

    if (state.showExitDialog) {
        ForfeitDialog(
            onDismiss = { viewModel.setExitDialogVisible(false) },
            onConfirmForfeit = viewModel::forfeit,
        )
    }
}

@Composable
private fun StatusBanners(session: DuoSession, secondsLeft: Int) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))

        if (session.phase == DuoPhase.IN_ROUND) {
            RoundTimerBar(fraction = if (session.settings.timePerQuestion > 0) {
                secondsLeft.toFloat() / session.settings.timePerQuestion
            } else 0f)
            Text(
                text = "${secondsLeft}s",
                style = MaterialTheme.typography.bodyMedium,
                color = if (secondsLeft <= 5) Rose500 else Neutral500,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.End,
            )
        }

        // Losing the socket mid-match is survivable: the server holds the seat for 30 seconds.
        if (session.connection == ConnectionState.RECONNECTING) {
            Banner("Mất kết nối. Đang vào lại trận…", Orange400)
        }

        if (!session.opponentConnected) {
            Banner(
                "Đối thủ mất kết nối. Còn ${session.opponentGraceSeconds}s để họ quay lại.",
                Orange400,
            )
        }

        if (session.opponentAnswered && session.phase == DuoPhase.IN_ROUND) {
            Banner("Đối thủ đã trả lời.", Sky500)
        }
    }
}

@Composable
private fun Banner(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WaitingForRound(session: DuoSession) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (session.opponent != null) {
                "Đã ghép với ${session.opponent.username ?: "đối thủ"}. Chuẩn bị!"
            } else {
                "Đang chuẩn bị trận…"
            },
            style = MaterialTheme.typography.titleMedium,
            color = Neutral700,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RoundBody(
    session: DuoSession,
    question: ChallengeDto,
    onSelect: (String) -> Unit,
) {
    val revealing = session.phase == DuoPhase.ROUND_REVEAL
    val enabled = session.phase == DuoPhase.IN_ROUND && !session.hasAnswered

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        question.passage?.let { passage ->
            Text(
                text = passage.content,
                style = MaterialTheme.typography.bodyLarge,
                color = Neutral700,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Neutral050)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(12.dp))
        }

        if (question.type == ChallengeTypeDto.ASSIST) {
            QuestionBubble(question = question.question, modifier = Modifier.fillMaxWidth())
        } else {
            Text(text = question.question, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(16.dp))

        if (question.type == ChallengeTypeDto.ASSIST) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.forEach { option ->
                    ChallengeOptionCard(
                        text = option.text,
                        state = optionState(option.id, session),
                        onClick = { onSelect(option.id) },
                        enabled = enabled,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 400.dp),
            ) {
                items(question.options) { option ->
                    ChallengeOptionCard(
                        text = option.text,
                        state = optionState(option.id, session),
                        onClick = { onSelect(option.id) },
                        enabled = enabled,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            revealing -> RoundReveal(session)
            session.hasAnswered -> Text(
                text = "Đã trả lời. Đang chờ đối thủ…",
                style = MaterialTheme.typography.bodyLarge,
                color = Neutral500,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** What each side scored this round, plus the explanation the server reveals with the answer. */
@Composable
private fun RoundReveal(session: DuoSession) {
    val result = session.roundResult ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Neutral050)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Bạn: +${result.you.points}",
                style = MaterialTheme.typography.titleMedium,
                color = if (result.you.correct) Green500 else Rose500,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Đối thủ: +${result.opponent.points}",
                style = MaterialTheme.typography.titleMedium,
                color = if (result.opponent.correct) Green500 else Rose500,
                fontWeight = FontWeight.Bold,
            )
        }
        result.explanation?.let { explanation ->
            Spacer(Modifier.height(8.dp))
            Text(text = explanation, style = MaterialTheme.typography.bodyMedium, color = Neutral700)
        }
    }
}

/** Options stay neutral until `round.result` arrives -- `round.start` never carries the answer. */
private fun optionState(optionId: String, session: DuoSession): ChallengeOptionState {
    val result = session.roundResult
    if (session.phase != DuoPhase.ROUND_REVEAL || result == null) {
        return if (session.myOptionId == optionId) ChallengeOptionState.SELECTED
        else ChallengeOptionState.NONE
    }
    return when {
        optionId in result.correctOptionIds -> ChallengeOptionState.CORRECT
        optionId == result.you.optionId -> ChallengeOptionState.WRONG
        else -> ChallengeOptionState.NONE
    }
}

private fun roundLabel(session: DuoSession): String =
    if (session.totalRounds > 0) "${session.roundIndex + 1}/${session.totalRounds}" else "—"
