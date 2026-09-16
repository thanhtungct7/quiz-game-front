package com.kma.quiz_game.ui.screens.battle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.data.remote.dto.ChallengeDto
import com.kma.quiz_game.data.remote.dto.ChallengeTypeDto
import com.kma.quiz_game.data.repository.BattlePhase
import com.kma.quiz_game.data.repository.BattleRepository
import com.kma.quiz_game.data.repository.BattleSession
import com.kma.quiz_game.data.repository.ConnectionState
import com.kma.quiz_game.ui.components.ChallengeOptionState
import com.kma.quiz_game.ui.components.rememberOptionAudioPlayer
import com.kma.quiz_game.ui.components.battle.LeaveBattleDialog
import com.kma.quiz_game.ui.game.ArenaSurface
import kotlinx.coroutines.delay

/**
 * One lesson, fought instead of studied -- in real time.
 *
 * Two layers. The stage in the upper band is libGDX and redraws at sixty frames a second: the
 * backdrop, the knight, the monster, the wind-up bar filling over its head, the damage numbers.
 * Everything under it is Compose: the question, the options, the bars, the skills.
 *
 * The arena is confined to that band rather than filling the screen, and that is what makes the
 * fight sit still. A GL surface fills whatever view it is given, so a surface behind the whole
 * screen would put the ground line wherever the panel above it happened to end up on that device;
 * a surface inside the band puts it in the same place on every phone.
 *
 * The palette is [BattleTheme] and stops at this screen. Nothing here draws a shared component
 * from `ui/components/game` -- duo uses those, in the app's light theme, and they stay that way.
 *
 * Tapping an option submits immediately, because the server times the answer from the moment it
 * pushed the question and a faster answer hits harder. Being wrong no longer costs health -- it
 * costs the combo and a beat of tempo, while the monster's own clock keeps running either way.
 */
@Composable
fun BattleScreen(
    lessonId: String,
    onFinished: () -> Unit,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val viewModel: BattleViewModel = viewModel(
        // Keyed by lesson: a different gate is a different fight, not a reused ViewModel.
        key = lessonId,
        factory = viewModelFactory {
            initializer { BattleViewModel(lessonId, app.battleRepository, app.gameRepository) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val session = state.session
    val now = rememberHudClock()

    // One buzz per graded answer, fired for the whole screen rather than from each option card:
    // grading turns the correct card CORRECT *and* the tapped one WRONG, so a haptic per card
    // would fire twice for one answer.
    //
    // Keyed on the result's `token` -- the id of the question push it answers -- rather than on
    // the result itself, so the same question coming round again in a later pool pass still
    // buzzes instead of comparing equal to the answer before it and being skipped.
    val haptics = LocalHapticFeedback.current
    val graded = session.answerResult
    LaunchedEffect(graded?.token) {
        if (graded != null) {
            haptics.performHapticFeedback(
                if (graded.correct) HapticFeedbackType.Confirm else HapticFeedbackType.Reject,
            )
        }
    }

    LaunchedEffect(session.phase) {
        when (session.phase) {
            BattlePhase.FINISHED -> onFinished()
            // Refused before it began -- a missing lesson, one with no questions, or a socket
            // that never came up and timed the start out.
            BattlePhase.IDLE -> if (session.lastError != null) onLeft()
            else -> Unit
        }
    }

    // The server never confirmed the walk-out. It cannot, on a socket that is down.
    LaunchedEffect(state.hasLeft) { if (state.hasLeft) onLeft() }

    BackHandler { viewModel.setExitDialogVisible(true) }

    Box(modifier = modifier.fillMaxSize().background(BattleTheme.Night)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                session = session,
                onExit = { viewModel.setExitDialogVisible(true) },
            )
            MonsterHeader(session = session)

            // The stage. Nothing is drawn over it except the warning of what the cast will cost.
            Box(modifier = Modifier.weight(STAGE_WEIGHT).fillMaxWidth()) {
                ArenaSurface(bridge = viewModel.arena, modifier = Modifier.fillMaxSize())
                SwingWarning(
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
                PlayerBar(session = session)
                StatusBanners(session = session)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val question = session.question
                    if (question == null) {
                        BetweenQuestions(session = session)
                    } else {
                        QuestionBody(
                            session = session,
                            question = question,
                            placedOptionIds = state.placedOptionIds,
                            onSelect = viewModel::selectOption,
                            onPlace = viewModel::placeWord,
                            onRemove = viewModel::removeWord,
                            onCheck = viewModel::checkSentence,
                        )
                    }
                }

                BattleSkillDock(
                    slots = state.loadout,
                    mana = session.mana,
                    // A skill is not tied to a question any more: raising a shield while reading
                    // the last explanation is exactly when it is worth the mana.
                    canCast = session.isFighting,
                    usedCodes = state.rechargingCodes(session.serverNowMs(now)),
                    onCast = viewModel::castSkill,
                )
            }
        }
    }

    if (state.showExitDialog) {
        LeaveBattleDialog(
            onDismiss = { viewModel.setExitDialogVisible(false) },
            onConfirmLeave = viewModel::leave,
        )
    }
}

/**
 * A coarse clock for the HUD.
 *
 * Ten times a second, not once a frame: the only things on this side that move with time are a
 * greyed-out skill button and a lockout hint, and recomposing the option grid at 60 Hz to animate
 * them would be a waste. The one thing that genuinely has to be smooth -- the wind-up bar -- is
 * drawn by the arena on the GL thread, off this clock entirely.
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
private fun TopBar(session: BattleSession, onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.Filled.Close, contentDescription = "Thoát trận", tint = BattleTheme.ParchmentDim)
        }
        Text(
            text = session.lessonTitle,
            style = MaterialTheme.typography.titleMedium,
            color = BattleTheme.Parchment,
            modifier = Modifier.weight(1f),
        )
        // Not a progress counter: the pool goes round again, so this says how much of the lesson
        // is in play rather than how far through it the player is.
        Text(
            text = poolLabel(session),
            style = MaterialTheme.typography.bodyMedium,
            color = BattleTheme.ParchmentFaint,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

@Composable
private fun MonsterHeader(session: BattleSession) {
    val monster = session.monster ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (monster.isBoss) "${monster.name} · Trùm" else monster.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (monster.isBoss) BattleTheme.Ember else BattleTheme.Gold,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${session.monsterHp}/${session.monsterMaxHp}",
                style = MaterialTheme.typography.bodyMedium,
                color = BattleTheme.ParchmentDim,
            )
        }
        Spacer(Modifier.height(6.dp))
        BattleHpBar(
            fraction = session.monsterHpFraction,
            color = BattleTheme.Blood,
            impactDelayMillis = IMPACT_DELAY_MS,
        )
    }
}

/** What the wind-up filling over the monster's head will cost when it lands. */
@Composable
private fun SwingWarning(session: BattleSession, modifier: Modifier = Modifier) {
    val text = BattleRepository.swingText(session)
    if (text.isEmpty() || !session.isFighting) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (session.enragedNext) BattleTheme.Blood else BattleTheme.Ember,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(BattleTheme.TileShape)
            .background(BattleTheme.Night.copy(alpha = 0.82f))
            .border(1.dp, BattleTheme.Edge, BattleTheme.TileShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun PlayerBar(session: BattleSession) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Máu ${session.hp}/${session.maxHp}",
                style = MaterialTheme.typography.bodyMedium,
                color = BattleTheme.Parchment,
            )
            if (session.combo >= 2) {
                Text(
                    text = "Chuỗi x${session.combo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BattleTheme.Gold,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Mana ${session.mana}",
                style = MaterialTheme.typography.bodyMedium,
                color = BattleTheme.Mana,
            )
        }
        Spacer(Modifier.height(6.dp))
        BattleHpBar(fraction = session.hpFraction, impactDelayMillis = IMPACT_DELAY_MS)
        Spacer(Modifier.height(4.dp))
        BattleManaBar(mana = session.mana)
    }
}

@Composable
private fun StatusBanners(session: BattleSession) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        // Worth saying plainly: from here on the answers no longer move the lesson, and a player
        // who is not told will read the repeat as a bug.
        if (session.poolPass > 0) {
            Text(
                text = "Đã hết câu trong bài — câu lặp lại không tính vào tiến độ nữa.",
                style = MaterialTheme.typography.bodyMedium,
                color = BattleTheme.ParchmentDim,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        // Unlike a duo match, there is no seat being held: a dropped socket has already ended the
        // fight on the server, so this says what happened rather than promising a comeback. Only
        // for a fight that was actually running -- there is nothing to have ended while the start
        // is still being retried, and saying so there contradicts the spinner.
        if (session.connection == ConnectionState.RECONNECTING &&
            session.phase == BattlePhase.FIGHTING
        ) {
            Text(
                text = "Mất kết nối. Trận này đã kết thúc.",
                style = MaterialTheme.typography.bodyMedium,
                color = BattleTheme.Ember,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The beat between two questions.
 *
 * Shows the last answer's explanation rather than a spinner, because that beat is exactly as long
 * as the server's lockout and the monster is still winding up through it -- there is nothing to
 * wait for, only something to read.
 */
@Composable
private fun BetweenQuestions(session: BattleSession) {
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
                text = session.monster?.let { "${it.name} chặn cửa. Chuẩn bị!" } ?: "Đang vào trận…",
                style = MaterialTheme.typography.titleMedium,
                color = BattleTheme.Parchment,
                textAlign = TextAlign.Center,
            )
            // A spinner that says nothing while the socket is down reads as the app hanging. Say
            // which of the two waits this is; the start times out on its own either way.
            if (session.connection != ConnectionState.CONNECTED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Mất kết nối tới máy chủ. Đang thử lại…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BattleTheme.Ember,
                    textAlign = TextAlign.Center,
                )
            }
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
    session: BattleSession,
    question: ChallengeDto,
    placedOptionIds: List<String>,
    onSelect: (String) -> Unit,
    onPlace: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCheck: () -> Unit,
) {
    val enabled = session.hasQuestion && !session.hasAnswered
    // A REMOVE_OPTIONS skill hides wrong options from the caster, for this question only.
    val options = question.options.filter { it.id !in session.removedOptionIds }

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
            BattleQuestionBubble(question = question.question)
        } else {
            Text(
                text = question.question,
                style = MaterialTheme.typography.headlineSmall,
                color = BattleTheme.Parchment,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))

        val audio = rememberOptionAudioPlayer()

        if (question.type == ChallengeTypeDto.ORDER) {
            // The sentence is the answer, so the whole word bank is the surface -- there is no
            // grid of options to draw and no single option to select.
            BattleSentenceBuilder(
                tiles = options,
                placedIds = placedOptionIds,
                state = sentenceState(session),
                onPlace = onPlace,
                onRemove = onRemove,
                onCheck = onCheck,
                enabled = enabled,
            )
        } else if (question.type == ChallengeTypeDto.ASSIST) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.forEach { option ->
                    BattleOptionCard(
                        text = option.text,
                        state = optionState(option.id, session),
                        onClick = { onSelect(option.id) },
                        enabled = enabled,
                        imagePath = option.imageSrc,
                        onPlayAudio = option.audioSrc?.let { src -> { audio.play(src) } },
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
                items(options) { option ->
                    BattleOptionCard(
                        text = option.text,
                        state = optionState(option.id, session),
                        onClick = { onSelect(option.id) },
                        enabled = enabled,
                        imagePath = option.imageSrc,
                        onPlayAudio = option.audioSrc?.let { src -> { audio.play(src) } },
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
 * lesson screen in the light theme, and this screen is the only place a bubble has to be stone.
 */
@Composable
private fun BattleQuestionBubble(question: String) {
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
private fun AnswerReveal(session: BattleSession) {
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
                    append("Bạn gây ${blow.finalDamage} sát thương")
                    if (blow.comboCount >= 3) append(" (chuỗi x${blow.comboMultiplier})")
                    if (blow.isCritical) append(" — chí mạng!")
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (blow.isCritical) BattleTheme.Ember else BattleTheme.Venom,
                fontWeight = FontWeight.Bold,
            )
        } else {
            // Deliberately not "bạn trúng đòn": a wrong answer takes no health any more. What it
            // costs is the chain and the seconds the monster spends winding up regardless.
            Text(
                text = "Sai rồi — mất chuỗi, và quái vẫn đang lên đòn.",
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

/**
 * How the built sentence should read: still being answered, or already graded.
 *
 * A sentence is right or wrong as a whole -- no tile of it is individually correct -- so unlike
 * [optionState] this looks only at whether the answer landed.
 */
private fun sentenceState(session: BattleSession): ChallengeOptionState {
    val result = session.answerResult
    if (result == null || session.hasQuestion) return ChallengeOptionState.NONE
    return if (result.correct) ChallengeOptionState.CORRECT else ChallengeOptionState.WRONG
}

/** Options stay neutral until `answer.result` arrives -- `question.push` never carries the key. */
private fun optionState(optionId: String, session: BattleSession): ChallengeOptionState {
    val result = session.answerResult
    if (result == null || session.hasQuestion) {
        return if (optionId in session.myOptionIds) ChallengeOptionState.SELECTED
        else ChallengeOptionState.NONE
    }
    return when {
        optionId in result.correctOptionIds -> ChallengeOptionState.CORRECT
        optionId == result.optionId -> ChallengeOptionState.WRONG
        else -> ChallengeOptionState.NONE
    }
}

private fun poolLabel(session: BattleSession): String = when {
    session.questionsInPool <= 0 -> "—"
    session.poolPass > 0 -> "${session.questionsInPool} câu · vòng ${session.poolPass + 1}"
    else -> "${session.questionsInPool} câu"
}

/** The stage gets more of the screen than the old arena strip: there are two fighters to see. */
private const val STAGE_WEIGHT = 0.42f
private const val PANEL_WEIGHT = 0.58f
private const val HUD_TICK_MS = 100L
/** Matches the arena's lunge-advance time, so a bar drains at the moment the blow lands. */
private const val IMPACT_DELAY_MS = 180
