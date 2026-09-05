package com.kma.quiz_game.ui.screens.duo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import com.kma.quiz_game.data.repository.ConnectionState
import com.kma.quiz_game.data.repository.DuoPhase
import com.kma.quiz_game.data.repository.DuoSession
import com.kma.quiz_game.ui.components.ChallengeOptionState
import com.kma.quiz_game.ui.components.duo.DuelHeader
import com.kma.quiz_game.ui.components.duo.ForfeitDialog
import com.kma.quiz_game.ui.components.duo.MatchChatSheet
import com.kma.quiz_game.ui.components.game.effectSymbol
import com.kma.quiz_game.ui.game.ArenaSurface
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.screens.battle.BattleManaBar
import com.kma.quiz_game.ui.screens.battle.BattleOptionCard
import com.kma.quiz_game.ui.screens.battle.BattleSkillDock
import com.kma.quiz_game.ui.screens.battle.BattleTheme
import kotlinx.coroutines.delay

/**
 * Two players, one deck each, fought in real time.
 *
 * The same two layers as a lesson battle. The stage in the upper band is libGDX and redraws at
 * sixty frames a second -- the backdrop, both knights, the damage numbers -- and everything under
 * it is Compose: the question, the options, the bars, the skills. The only difference is who
 * stands on the right: a second knight rather than a monster.
 *
 * Nobody waits for anybody. Tapping an option submits immediately, the answer is graded on the
 * spot, and the next question follows after a beat -- while the opponent works through their own
 * deck at their own pace. A correct answer lands its blow the instant it is given, which is why
 * the arena and not the panel is where a match is actually watched.
 *
 * Being wrong costs no health. It costs the combo, a second and a half of tempo, and the question
 * itself, which goes to the back of the deck to be met again.
 */
@Composable
fun DuoMatchScreen(
    onFinished: () -> Unit,
    onLeft: () -> Unit,
    onOpenLoadout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DuoMatchViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()
    val session = state.session
    val now = rememberHudClock()
    val serverNow = session.serverNowMs(now)

    LaunchedEffect(session.phase) {
        when (session.phase) {
            // A knockout lands while the arena is still showing it. Leaving for the result the
            // instant the frame arrives cuts the blow that won the match; one beat is enough to
            // see the bar hit zero.
            DuoPhase.FINISHED -> {
                delay(RESULT_HANDOVER_MILLIS)
                onFinished()
            }
            // Back to the lobby: the room was left, or the queue was cancelled from elsewhere.
            DuoPhase.IDLE, DuoPhase.QUEUEING, DuoPhase.ROOM_WAITING -> onLeft()
            else -> Unit
        }
    }

    BackHandler { viewModel.setExitDialogVisible(true) }

    Box(modifier = modifier.fillMaxSize().background(BattleTheme.Night)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                session = session,
                onExit = { viewModel.setExitDialogVisible(true) },
                onChat = { viewModel.setChatVisible(true) },
            )

            DuelHeader(
                me = session.me,
                opponent = session.opponent,
                myHp = session.myHp,
                myMaxHp = session.myMaxHp,
                opponentHp = session.opponentHp,
                opponentMaxHp = session.opponentMaxHp,
                myDeckRemaining = session.myDeckRemaining,
                opponentDeckRemaining = session.opponentDeckRemaining,
                clockLabel = clockLabel(session, now),
                opponentConnected = session.opponentConnected,
                impactDelayMillis = IMPACT_DELAY_MS,
            )

            // The stage. Nothing is drawn over it except what the last cast did.
            Box(modifier = Modifier.weight(STAGE_WEIGHT).fillMaxWidth()) {
                ArenaSurface(bridge = viewModel.arena, modifier = Modifier.fillMaxSize())
                CastFeed(
                    session = session,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(PANEL_WEIGHT)
                    .fillMaxWidth()
                    .clip(BattleTheme.PanelShape)
                    .background(BattleTheme.panelBrush)
                    .border(1.dp, BattleTheme.Edge, BattleTheme.PanelShape),
            ) {
                ResourceStrip(session)
                StatusBanners(session, serverNow)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val question = session.question
                    when {
                        session.phase == DuoPhase.MATCHED -> WaitingForMatch(session)
                        serverNow < session.stunnedUntil -> StunnedPanel()
                        question == null -> BetweenQuestions(session)
                        else -> QuestionBody(
                            session = session,
                            question = question,
                            onSelect = viewModel::selectOption,
                        )
                    }
                }

                state.castNudge?.let { nudge ->
                    Text(
                        text = nudge,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BattleTheme.Ember,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                BattleSkillDock(
                    slots = state.loadout,
                    mana = session.mana,
                    // A skill is not tied to a question: raising a shield while reading the last
                    // explanation is exactly when it is worth the mana.
                    canCast = session.phase == DuoPhase.FIGHTING && serverNow >= session.stunnedUntil,
                    usedCodes = state.rechargingCodes(serverNow),
                    onCast = viewModel::castSkill,
                )
                // The one place a player learns the skill bar exists at all, so it opens the
                // loadout *over* the match rather than making them leave it.
                if (state.loadout.isEmpty()) {
                    Text(
                        text = "Trang bị kỹ năng",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BattleTheme.Mana,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenLoadout)
                            .padding(bottom = 10.dp),
                    )
                }
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

/**
 * A coarse clock for the HUD.
 *
 * Ten times a second, not once a frame: the only things on this side that move with time are the
 * match countdown, a greyed-out skill button and a lockout hint, and recomposing the option grid
 * at 60 Hz to animate them would be a waste. Everything that genuinely has to be smooth is drawn
 * by the arena on the GL thread, off this clock entirely.
 */
@Composable
private fun rememberHudClock(): Long {
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(HUD_TICK_MS)
            now = android.os.SystemClock.elapsedRealtime()
        }
    }
    return now
}

@Composable
private fun TopBar(session: DuoSession, onExit: () -> Unit, onChat: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.Filled.Close, contentDescription = "Thoát trận", tint = BattleTheme.ParchmentDim)
        }
        Text(
            text = scoreLabel(session),
            style = MaterialTheme.typography.bodyMedium,
            color = BattleTheme.ParchmentFaint,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onChat) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Trò chuyện",
                tint = if (session.chat.isEmpty()) BattleTheme.ParchmentDim else BattleTheme.Mana,
            )
        }
    }
}

/** Mana and combo: one thin line, because neither is worth a row of its own. */
@Composable
private fun ResourceStrip(session: DuoSession) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${session.mana}",
            style = MaterialTheme.typography.bodyMedium,
            color = BattleTheme.Mana,
            fontWeight = FontWeight.Bold,
        )
        BattleManaBar(mana = session.mana, modifier = Modifier.weight(1f))
        if (session.combo >= 2) {
            Text(
                text = "Chuỗi x${session.combo}",
                style = MaterialTheme.typography.bodyMedium,
                color = BattleTheme.Gold,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatusBanners(session: DuoSession, serverNowMs: Long) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        // Losing the socket mid-match is survivable: the server holds the seat for 30 seconds.
        if (session.connection == ConnectionState.RECONNECTING) {
            Banner("Mất kết nối. Đang vào lại trận…", BattleTheme.Ember)
        }
        if (!session.opponentConnected) {
            Banner(
                "Đối thủ mất kết nối. Còn ${session.opponentGraceSeconds}s để họ quay lại.",
                BattleTheme.Ember,
            )
        }
        // Worth saying plainly: a question coming round a second time is the rule, not a bug.
        if (session.questionRetry && session.hasQuestion && serverNowMs >= session.stunnedUntil) {
            Banner("Câu này bạn từng trả lời sai — làm lại nào.", BattleTheme.Gold)
        }
    }
}

/**
 * What was cast, both sides.
 *
 * The opponent's casts matter as much as our own: mana vanishing or a question arriving late with
 * no explanation reads as a bug, and this line is the explanation.
 */
@Composable
private fun CastFeed(session: DuoSession, modifier: Modifier = Modifier) {
    val cast = session.lastSkill ?: return
    val mine = cast.userId == session.me?.id
    Text(
        text = "${effectSymbol(cast.effect)} ${if (mine) "Bạn" else "Đối thủ"} dùng ${cast.skillName}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (mine) BattleTheme.Mana else BattleTheme.Ember,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(BattleTheme.TileShape)
            .background(BattleTheme.Night.copy(alpha = 0.82f))
            .border(1.dp, BattleTheme.Edge, BattleTheme.TileShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Banner(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WaitingForMatch(session: DuoSession) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = BattleTheme.Gold)
        Spacer(Modifier.height(16.dp))
        Text(
            text = session.opponent?.username?.let { "Đã ghép với $it. Chuẩn bị!" }
                ?: "Đang chuẩn bị trận…",
            style = MaterialTheme.typography.titleMedium,
            color = BattleTheme.Parchment,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A stun takes the whole answer area, not a line of text.
 *
 * Five correct answers in a row freeze the opponent for a few seconds: they cannot answer and
 * cannot cast, while their own clock keeps running. Leaving the options on screen but inert would
 * read as a frozen app -- those seconds are genuinely not theirs, and the screen should say so.
 */
@Composable
private fun StunnedPanel() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "💫", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Bạn bị choáng",
            style = MaterialTheme.typography.headlineSmall,
            color = BattleTheme.Ember,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Đối thủ ăn 5 câu liên tiếp. Chờ tỉnh lại rồi đánh tiếp.",
            style = MaterialTheme.typography.bodyLarge,
            color = BattleTheme.ParchmentDim,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The beat between two questions.
 *
 * Shows the last answer's explanation rather than a spinner, because that beat is exactly as long
 * as the server's lockout and the opponent is still answering through it -- there is nothing to
 * wait for, only something to read.
 */
@Composable
private fun BetweenQuestions(session: DuoSession) {
    val result = session.answerResult
    if (result == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = BattleTheme.Gold)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Đang vào trận…",
                style = MaterialTheme.typography.titleMedium,
                color = BattleTheme.Parchment,
            )
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        AnswerReveal(session)
    }
}

@Composable
private fun QuestionBody(
    session: DuoSession,
    question: ChallengeDto,
    onSelect: (String) -> Unit,
) {
    // An answer typed into a dead socket is an answer the server never times, so the options lock
    // until the connection is actually back.
    val enabled = session.hasQuestion &&
        !session.hasAnswered &&
        session.connection == ConnectionState.CONNECTED

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
                color = BattleTheme.ParchmentDim,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(BattleTheme.TileShape)
                    .background(BattleTheme.StoneSunken)
                    .border(1.dp, BattleTheme.Edge, BattleTheme.TileShape)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(12.dp))
        }

        if (question.type == ChallengeTypeDto.ASSIST) {
            QuestionBubble(question.question)
        } else {
            Text(
                text = question.question,
                style = MaterialTheme.typography.headlineSmall,
                color = BattleTheme.Parchment,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))

        if (question.type == ChallengeTypeDto.ASSIST) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.forEach { option ->
                    BattleOptionCard(
                        text = option.text,
                        state = optionState(option.id, session),
                        onClick = { onSelect(option.id) },
                        enabled = enabled && option.id !in session.removedOptionIds,
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
                    BattleOptionCard(
                        text = option.text,
                        state = optionState(option.id, session),
                        onClick = { onSelect(option.id) },
                        enabled = enabled && option.id !in session.removedOptionIds,
                    )
                }
            }
        }
    }
}

/**
 * The prompt for an ASSIST challenge.
 *
 * A local copy of the shared bubble rather than the shared one itself: that one is drawn by the
 * lesson screen in the light theme, and a match is fought in stone.
 */
@Composable
private fun QuestionBubble(question: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp))
            .background(BattleTheme.StoneRaised)
            .border(
                1.dp,
                BattleTheme.Edge,
                RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.titleLarge,
            color = BattleTheme.Parchment,
        )
    }
}

/** What the answer did, plus the explanation the server reveals with it. */
@Composable
private fun AnswerReveal(session: DuoSession) {
    val result = session.answerResult ?: return
    val blow = result.blow

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BattleTheme.TileShape)
            .background(BattleTheme.StoneSunken)
            .border(1.dp, BattleTheme.Edge, BattleTheme.TileShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (blow != null) {
            Text(
                text = buildString {
                    append("Bạn gây ${blow.damage} sát thương")
                    if (blow.comboCount >= 3) append(" (chuỗi x${blow.comboMultiplier})")
                    if (blow.isCritical) append(" — chí mạng!")
                    if (blow.stunsOpponent) append(" — đối thủ choáng!")
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (blow.isCritical) BattleTheme.Ember else BattleTheme.Venom,
                fontWeight = FontWeight.Bold,
            )
        } else {
            // Deliberately not "bạn trúng đòn": a wrong answer takes no health. What it costs is
            // the chain, a beat of tempo, and the question, which comes back at the end.
            Text(
                text = "Sai rồi — mất chuỗi, câu này sẽ quay lại cuối bộ.",
                style = MaterialTheme.typography.titleMedium,
                color = BattleTheme.Blood,
                fontWeight = FontWeight.Bold,
            )
        }

        result.explanation?.let { explanation ->
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = BattleTheme.ParchmentDim,
            )
        }
    }
}

/** Options stay neutral until `answer.result` arrives -- `question.push` never carries the key. */
private fun optionState(optionId: String, session: DuoSession): ChallengeOptionState {
    val result = session.answerResult
    if (result == null || session.hasQuestion) {
        return when {
            optionId in session.removedOptionIds -> ChallengeOptionState.REMOVED
            session.myOptionId == optionId -> ChallengeOptionState.SELECTED
            else -> ChallengeOptionState.NONE
        }
    }
    return when {
        optionId in result.correctOptionIds -> ChallengeOptionState.CORRECT
        optionId == result.optionId -> ChallengeOptionState.WRONG
        else -> ChallengeOptionState.NONE
    }
}

/** The score, which no longer decides anything on its own but is still worth a glance. */
private fun scoreLabel(session: DuoSession): String =
    if (session.deckSize <= 0) "—" else "${session.myScore} — ${session.opponentScore}"

/** How long the match has before health decides it. Blank until the server has said. */
private fun clockLabel(session: DuoSession, localNowMs: Long): String {
    val remaining = session.matchRemainingMs(localNowMs)
    if (session.deadlineAt <= 0) return "—"
    val totalSeconds = remaining / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Long enough to watch the final blow land, short enough not to feel like a stall. */
private const val RESULT_HANDOVER_MILLIS = 800L

/** The stage gets a little less than PvE's: there are two decks to keep an eye on above it. */
private const val STAGE_WEIGHT = 0.38f
private const val PANEL_WEIGHT = 0.62f
private const val HUD_TICK_MS = 100L
/** Matches the arena's lunge-advance time, so a bar drains at the moment the blow lands. */
private const val IMPACT_DELAY_MS = 180
