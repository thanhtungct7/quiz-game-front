package com.kma.quiz_game.ui.screens.conversation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kma.quiz_game.R
import com.kma.quiz_game.data.speech.AndroidSpeechListener
import com.kma.quiz_game.data.speech.AndroidSpeechSpeaker
import com.kma.quiz_game.ui.components.BaseMascotDialog
import com.kma.quiz_game.ui.components.DuoButtonVariant
import kotlinx.coroutines.flow.Flow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.data.remote.dto.ConversationMessageRole
import com.kma.quiz_game.data.remote.dto.HintSuggestionDto
import com.kma.quiz_game.data.remote.dto.ScenarioDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.theme.Sky500

/**
 * Talking through one scenario with the AI.
 *
 * Mistakes are not corrected here, on purpose: stopping the conversation for every slip is what
 * makes learners afraid to speak. The corrections come all together on the feedback screen.
 */
@Composable
fun ConversationChatScreen(
    sessionId: String,
    onBack: () -> Unit,
    onFinished: (sessionId: String) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val app = context.applicationContext as DuoGameApplication
    val viewModel: ConversationChatViewModel = viewModel(
        key = "conversation_$sessionId",
        factory = viewModelFactory {
            initializer {
                ConversationChatViewModel(
                    sessionId = sessionId,
                    repository = app.conversationRepository,
                    speaker = AndroidSpeechSpeaker(app),
                    listener = AndroidSpeechListener(app),
                    voicePreference = object : ConversationChatViewModel.VoicePreference {
                        override val voiceOn: Flow<Boolean> = app.settingsStore.conversationVoiceOn
                        override suspend fun setVoiceOn(on: Boolean) = app.settingsStore.setConversationVoiceOn(on)
                    },
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopVoice() }

    // The microphone is asked for the first time it is needed, with the reason given first.
    var showMicRationale by remember { mutableStateOf(false) }
    var showMicBlocked by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> viewModel.startListening()
            // Refused for good: the system will not ask again, only settings can change it.
            activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO) ->
                showMicBlocked = true
            else -> viewModel.onMicPermissionDenied()
        }
    }
    val onMicClick: () -> Unit = {
        when {
            state.voice.isListening -> viewModel.stopListening()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> viewModel.startListening()
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO) ->
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            else -> showMicRationale = true
        }
    }

    LaunchedEffect(state.finishedSessionId) {
        val finished = state.finishedSessionId ?: return@LaunchedEffect
        viewModel.consumeFinished()
        onFinished(finished)
    }

    // Keep the newest line in view as lines arrive and while the AI is "typing".
    LaunchedEffect(state.lines.size, state.isSending, state.needsRetry, state.voice.isListening) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    val scenario = state.scenario
    if (scenario == null) {
        Column(Modifier.fillMaxSize()) {
            ConversationTopBar(title = "Luyện hội thoại", onBack = onBack)
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                CenteredMessage(
                    message = state.loadErrorMessage ?: "Không tải được cuộc hội thoại.",
                    actionLabel = "Thử lại",
                    onAction = viewModel::load,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // The Scaffold around the nav host already pads for the navigation bar.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        ConversationTopBar(
            title = scenario.titleVi,
            subtitle = "CEFR ${state.cefr} · ${state.turnsLabel}",
            onBack = onBack,
            actions = {
                if (state.voice.canSpeak) {
                    IconButton(onClick = viewModel::toggleVoice) {
                        Icon(
                            imageVector = if (state.voice.voiceOn) {
                                Icons.AutoMirrored.Filled.VolumeUp
                            } else {
                                Icons.AutoMirrored.Filled.VolumeOff
                            },
                            contentDescription = if (state.voice.voiceOn) "Tắt tiếng AI" else "Bật tiếng AI",
                        )
                    }
                }
                TextButton(onClick = viewModel::onFinishClick, enabled = state.canFinish) { Text("Kết thúc") }
            },
        )
        HorizontalDivider()

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "brief") {
                BriefCard(scenario = scenario, expanded = state.showBrief, onToggle = viewModel::toggleBrief)
            }
            items(state.lines, key = { it.id }) { line ->
                ChatBubble(
                    line = line,
                    showTranslation = line.id in state.shownTranslationIds,
                    isTranslating = line.id in state.translatingIds,
                    onLongPress = { viewModel.toggleTranslation(line.id) },
                    canSpeak = state.voice.canSpeak,
                    isSpeaking = state.voice.speakingLineId == line.id,
                    onSpeak = { viewModel.toggleSpeak(line.id) },
                )
            }
            if (state.voice.isListening) {
                item(key = "listening") {
                    ListeningBubble(text = state.voice.partialText, onCancel = viewModel::cancelListening)
                }
            }
            if (state.isSending) {
                item(key = "typing") { TypingBubble() }
            }
            if (state.needsRetry) {
                item(key = "retry") { RetryRow(onRetry = viewModel::retry) }
            }
        }

        state.errorMessage?.let { message ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                    TextButton(onClick = viewModel::dismissError) { Text("Đóng") }
                }
            }
        }

        if (state.isClosed) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (state.userTurns >= state.maxTurns) {
                        "Bạn đã dùng hết lượt nói. Xem AI nhận xét nhé!"
                    } else {
                        "Cuộc hội thoại đã hoàn thành. Xem AI nhận xét nhé!"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                DuoButton(text = "Xem nhận xét", onClick = viewModel::finish, enabled = state.canFinish)
            }
        } else {
            InputBar(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                enabled = state.canType && !state.voice.isListening,
                canSend = state.canSend,
                isLoadingHints = state.isLoadingHints,
                canAskHint = state.canAskHint && !state.voice.isListening,
                onSend = viewModel::send,
                onHint = viewModel::requestHints,
                showMic = state.voice.micAvailable && state.input.isBlank(),
                isListening = state.voice.isListening,
                level = state.voice.level,
                canListen = state.canListen || state.voice.isListening,
                onMic = onMicClick,
            )
        }
    }

    state.hints?.let { hints ->
        HintSheet(hints = hints, onPick = viewModel::pickHint, onDismiss = viewModel::dismissHints)
    }

    if (showMicRationale) {
        BaseMascotDialog(
            mascotRes = R.drawable.mascot,
            title = "Trả lời bằng giọng nói?",
            description = "Cho phép dùng micro để nói tiếng Anh với AI. Câu bạn nói sẽ được chuyển thành chữ và gửi đi.",
            onDismiss = { showMicRationale = false },
            actions = {
                DuoButton(text = "Cho phép", onClick = {
                    showMicRationale = false
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                })
                DuoButton(text = "Để sau", onClick = { showMicRationale = false }, variant = DuoButtonVariant.Outline)
            },
        )
    }

    if (showMicBlocked) {
        AlertDialog(
            onDismissRequest = { showMicBlocked = false },
            title = { Text("Chưa có quyền micro") },
            text = { Text("Bạn đã tắt quyền micro cho ứng dụng. Hãy bật lại trong Cài đặt để trả lời bằng giọng nói, hoặc gõ câu trả lời.") },
            confirmButton = {
                TextButton(onClick = {
                    showMicBlocked = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Mở cài đặt") }
            },
            dismissButton = { TextButton(onClick = { showMicBlocked = false }) { Text("Đóng") } },
        )
    }

    if (state.showFinishDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFinishDialog,
            title = { Text("Kết thúc sớm?") },
            text = {
                Text("Bạn mới nói ${state.turnsLabel}. AI sẽ nhận xét những gì bạn đã nói, và không thể nói tiếp cuộc này nữa.")
            },
            confirmButton = { TextButton(onClick = viewModel::finish) { Text("Kết thúc") } },
            dismissButton = { TextButton(onClick = viewModel::dismissFinishDialog) { Text("Nói tiếp") } },
        )
    }

    if (state.isFinishing) {
        Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text("AI đang nhận xét bài nói của bạn...", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun BriefCard(scenario: ScenarioDto, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = scenario.goalVi?.let { "Mục tiêu: $it" } ?: "Trò chuyện tự do",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Thu gọn" else "Xem tình huống",
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                ScenarioBrief(scenario)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Nhấn giữ câu của AI để xem nghĩa tiếng Việt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    line: ChatLine,
    showTranslation: Boolean,
    isTranslating: Boolean,
    onLongPress: () -> Unit,
    canSpeak: Boolean,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
) {
    val isUser = line.role == ConversationMessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            color = if (isUser) Sky500 else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(enabled = !isUser, onClick = {}, onLongClick = onLongPress),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = line.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (line.isLocal) Color.White.copy(alpha = 0.8f) else Color.Unspecified,
                )
                when {
                    isTranslating -> Text(
                        text = "Đang dịch...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    showTranslation && line.translationVi != null -> {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(
                            text = line.translationVi,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (!isUser && canSpeak) {
            IconButton(onClick = onSpeak) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isSpeaking) "Dừng đọc" else "Nghe câu này",
                    tint = if (isSpeaking) Sky500 else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** The learner's line while it is still being spoken, before it is sent. */
@Composable
private fun ListeningBubble(text: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = "Huỷ", modifier = Modifier.size(20.dp))
        }
        Surface(
            color = Sky500.copy(alpha = 0.55f),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text.ifBlank { "Đang nghe..." },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = "  AI đang trả lời...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RetryRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Chưa nhận được phản hồi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" Gửi lại")
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    canSend: Boolean,
    isLoadingHints: Boolean,
    canAskHint: Boolean,
    onSend: () -> Unit,
    onHint: () -> Unit,
    showMic: Boolean,
    isListening: Boolean,
    level: Float,
    canListen: Boolean,
    onMic: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoadingHints) {
                Box(Modifier.size(48.dp), Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = onHint, enabled = canAskHint) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = "Gợi ý câu trả lời")
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                placeholder = { Text(if (showMic) "Gõ, hoặc bấm mic để nói..." else "Nhập câu tiếng Anh...") },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            // One button, like a messaging app: the mic while nothing is typed, send once something is.
            if (showMic || isListening) {
                MicButton(isListening = isListening, level = level, enabled = canListen, onClick = onMic)
            } else {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi")
                }
            }
        }
    }
}

@Composable
private fun MicButton(isListening: Boolean, level: Float, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp), Alignment.Center) {
        if (isListening) {
            // Grows with the learner's voice, so it is plain the phone is hearing them.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(1f + level * 0.35f)
                    .background(Sky500.copy(alpha = 0.25f), CircleShape),
            )
        }
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (isListening) "Dừng và gửi" else "Nói để trả lời",
                tint = if (isListening) Sky500 else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HintSheet(
    hints: List<HintSuggestionDto>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Bạn có thể nói", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Chọn một câu để đưa vào ô nhập, rồi sửa theo ý bạn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hints.forEach { hint ->
                Card(
                    onClick = { onPick(hint.en) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(hint.en, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = hint.vi,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
