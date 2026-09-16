package com.kma.quiz_game.ui.screens.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.DuoDifficulty
import com.kma.quiz_game.data.remote.dto.DuoPlayerDto
import com.kma.quiz_game.data.remote.dto.LearningStatsDto
import com.kma.quiz_game.data.remote.dto.PublicProfileDto
import com.kma.quiz_game.data.remote.dto.PvpStatsDto
import com.kma.quiz_game.data.remote.dto.DuoSettingsDto
import com.kma.quiz_game.data.repository.ConnectionState
import com.kma.quiz_game.data.repository.DuoPhase
import com.kma.quiz_game.data.repository.DuoRepository
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.duo.DuoStatsCard
import com.kma.quiz_game.ui.components.duo.InitialsAvatar
import com.kma.quiz_game.ui.screens.profile.PublicProfileSheet
import com.kma.quiz_game.ui.components.duo.RoomCodeCard
import com.kma.quiz_game.ui.components.game.EnergyPips
import com.kma.quiz_game.ui.components.game.TierBadge
import com.kma.quiz_game.ui.components.duo.toUserMessage
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500

/**
 * The PvP lobby: your rating, the way into a match, and the waiting states that follow.
 *
 * Queueing and the friend-room lobby render here rather than on their own routes -- they are the
 * same screen with the buttons swapped out, and keeping them here means cancelling never has to
 * unwind a back stack.
 */
@Composable
fun DuoHomeScreen(
    onMatchStarting: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenClasses: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenLoadout: () -> Unit,
    onOpenInventory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DuoHomeViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()
    val session = state.session
    // The opponent whose card is open, or null. Screen state, so it dies with the screen.
    var openedOpponent by remember { mutableStateOf<DuoPlayerDto?>(null) }

    openedOpponent?.let { opponent ->
        PublicProfileSheet(
            userId = opponent.id,
            onDismiss = { openedOpponent = null },
            seed = opponent.asProfileSeed(),
        )
    }

    // The socket, not a button press, decides when a match exists.
    LaunchedEffect(session.phase) {
        if (session.isInMatch) onMatchStarting()
    }

    // Runs again every time the tab is re-entered, which is what keeps the rating, the energy bar
    // and the ladder current after a match instead of showing the values they had before it.
    LaunchedEffect(Unit) {
        viewModel.refreshStats()
        viewModel.refreshProfile()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Đấu 1 vs 1",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        PlayerCard(state)
        Spacer(Modifier.height(12.dp))

        EnergyPips(state.profile?.energy)
        Spacer(Modifier.height(16.dp))

        DuoStatsCard(state.stats)
        Spacer(Modifier.height(16.dp))

        GameEntryRow(
            onOpenClasses = onOpenClasses,
            onOpenSkills = onOpenSkills,
            onOpenLoadout = onOpenLoadout,
            onOpenInventory = onOpenInventory,
        )
        Spacer(Modifier.height(20.dp))

        ConnectionNotice(session.connection, onRetry = viewModel::reconnect)

        when (session.phase) {
            DuoPhase.QUEUEING -> QueueingPanel(
                position = session.queuePosition,
                waitedSeconds = session.queueWaitedSeconds,
                onCancel = viewModel::cancelQueue,
            )

            DuoPhase.ROOM_WAITING -> RoomLobbyPanel(
                roomCode = session.roomCode.orEmpty(),
                opponent = session.opponent,
                isHost = session.isHost,
                onStart = viewModel::startMatch,
                onLeave = viewModel::leaveRoom,
                onOpenOpponent = { openedOpponent = it },
            )

            else -> LobbyActions(
                settingsSummary = state.settings.summary(),
                onFindMatch = viewModel::findMatch,
                onCreateRoom = viewModel::createRoomChecked,
                onJoinRoom = viewModel::openJoinDialog,
                onOpenSettings = viewModel::openSettings,
                onOpenHistory = onOpenHistory,
            )
        }

        val errorText = session.lastError?.toUserMessage() ?: state.errorMessage
        if (errorText != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = Rose500,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = viewModel::dismissError) { Text("Đã hiểu") }
        }

        if (state.showOutOfEnergy) {
            OutOfEnergyDialog(onDismiss = viewModel::dismissOutOfEnergy)
        }

        if (session.queueTimedOut) {
            QueueTimeoutDialog(onDismiss = viewModel::dismissError, onRetry = {
                viewModel.dismissError()
                viewModel.findMatch()
            })
        }
    }

    if (state.showSettingsSheet) {
        MatchSettingsSheet(
            settings = state.settings,
            onChange = viewModel::updateSettings,
            onDismiss = viewModel::closeSettings,
        )
    }

    if (state.showJoinDialog) {
        JoinRoomDialog(state = state, viewModel = viewModel)
    }
}

@Composable
private fun ConnectionNotice(connection: ConnectionState, onRetry: () -> Unit) {
    val text = when (connection) {
        ConnectionState.CONNECTING -> "Đang kết nối máy chủ…"
        ConnectionState.RECONNECTING -> "Mất kết nối. Đang thử lại…"
        ConnectionState.DISCONNECTED -> "Chưa kết nối được tới máy chủ."
        ConnectionState.CONNECTED -> null
    } ?: return

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Orange400)
        // The socket gives up after its retries, so without this the only way back online would
        // be leaving the tab and coming back.
        if (connection == ConnectionState.DISCONNECTED) {
            TextButton(onClick = onRetry) { Text("Kết nối lại") }
        }
    }
}

@Composable
private fun LobbyActions(
    settingsSummary: String,
    onFindMatch: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        DuoButton(text = "Tìm trận", onClick = onFindMatch, variant = DuoButtonVariant.Primary)
        DuoButton(text = "Tạo phòng", onClick = onCreateRoom, variant = DuoButtonVariant.Secondary)
        DuoButton(text = "Nhập mã phòng", onClick = onJoinRoom, variant = DuoButtonVariant.Outline)
        DuoButton(text = "Lịch sử đấu", onClick = onOpenHistory, variant = DuoButtonVariant.Outline)

        TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Thể thức: $settingsSummary", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QueueingPanel(position: Int, waitedSeconds: Int, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text = "Đang tìm đối thủ…", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (position > 0) "Vị trí $position · đã chờ ${waitedSeconds}s" else "Đã chờ ${waitedSeconds}s",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // The band widens by 50 Elo every 5 seconds, so waiting genuinely helps.
        Text(
            text = "Càng chờ lâu, khoảng chênh lệch trình độ càng được nới rộng.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        DuoButton(text = "Huỷ", onClick = onCancel, variant = DuoButtonVariant.DangerOutline)
    }
}

/**
 * The friend room, once it has a code.
 *
 * The opponent is drawn as their full card rather than a name: rank and level are what tell a
 * player whether the friend who just walked in is going to flatten them, and it is the last moment
 * before the match starts at which that can be seen.
 */
@Composable
private fun RoomLobbyPanel(
    roomCode: String,
    opponent: DuoPlayerDto?,
    isHost: Boolean,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    onOpenOpponent: (DuoPlayerDto) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        RoomCodeCard(roomCode)
        Spacer(Modifier.height(16.dp))

        if (opponent == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Đang chờ người thứ hai…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // The last moment before the match starts at which the opponent can be looked up.
                modifier = Modifier.clickable { onOpenOpponent(opponent) },
            ) {
                InitialsAvatar(
                    userId = opponent.id,
                    username = opponent.username ?: "Đối thủ",
                    size = 36.dp,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${opponent.username ?: "Đối thủ"} đã vào phòng",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TierBadge(tier = opponent.tier, compact = true)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Cấp ${opponent.level} · ${opponent.rating} điểm",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        if (isHost) {
            DuoButton(
                text = "Bắt đầu",
                onClick = onStart,
                variant = DuoButtonVariant.Primary,
                enabled = opponent != null,
            )
        } else {
            Text(
                text = "Chờ chủ phòng bắt đầu trận.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        DuoButton(text = "Rời phòng", onClick = onLeave, variant = DuoButtonVariant.DangerOutline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchSettingsSheet(
    settings: DuoSettingsDto,
    onChange: (DuoSettingsDto) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(text = "Thể thức trận", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            // The rule that surprises a returning player most, said once and plainly.
            Text(
                text = "Hai bên chạy song song: trả lời xong là sang câu tiếp, không phải chờ " +
                    "đối thủ. Đúng thì chém ngay, sai thì mất chuỗi và câu đó bị đẩy xuống cuối " +
                    "để làm lại. Hết máu hoặc xong sạch bộ câu trước là kết thúc.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            // Random matchmaking compares settings for equality, so this is a real trade-off.
            Text(
                text = "Ghép trận ngẫu nhiên chỉ ghép hai người chọn thể thức giống hệt nhau. " +
                    "Đổi thông số ở đây sẽ khiến bạn khó tìm được đối thủ hơn.",
                style = MaterialTheme.typography.bodyMedium,
                color = Orange400,
            )
            Spacer(Modifier.height(20.dp))

            Text("Số câu mỗi bộ: ${settings.questionCount}", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = settings.questionCount.toFloat(),
                onValueChange = { onChange(settings.copy(questionCount = it.toInt())) },
                valueRange = DuoSettingsDto.MIN_QUESTION_COUNT.toFloat()..DuoSettingsDto.MAX_QUESTION_COUNT.toFloat(),
                steps = DuoSettingsDto.MAX_QUESTION_COUNT - DuoSettingsDto.MIN_QUESTION_COUNT - 1,
            )

            Spacer(Modifier.height(12.dp))
            // Not a deadline any more: nothing cuts a player off mid-question. This is the mark
            // a correct answer is scored against, so a lower number makes speed worth more.
            Text(
                "Mốc tính tốc độ: ${settings.timePerQuestion}s",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Trả lời trong mốc này thì đòn nặng nhất; chậm hơn vẫn tính đúng, chỉ nhẹ đòn.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = settings.timePerQuestion.toFloat(),
                onValueChange = { onChange(settings.copy(timePerQuestion = it.toInt())) },
                valueRange = DuoSettingsDto.MIN_TIME_PER_QUESTION.toFloat()..DuoSettingsDto.MAX_TIME_PER_QUESTION.toFloat(),
            )

            Spacer(Modifier.height(16.dp))
            Text("Độ khó", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DifficultyChip("Bất kỳ", settings.difficulty == null) { onChange(settings.copy(difficulty = null)) }
                DuoDifficulty.entries.forEach { level ->
                    DifficultyChip(level.label(), settings.difficulty == level) {
                        onChange(settings.copy(difficulty = level))
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    DuoButton(
        text = label,
        onClick = onClick,
        variant = if (selected) DuoButtonVariant.Primary else DuoButtonVariant.Outline,
        size = com.kma.quiz_game.ui.components.DuoButtonSize.Small,
        fullWidth = false,
    )
}

@Composable
private fun JoinRoomDialog(state: DuoHomeUiState, viewModel: DuoHomeViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeJoinDialog,
        title = { Text("Vào phòng của bạn bè") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.roomCodeInput,
                    onValueChange = viewModel::updateRoomCode,
                    label = { Text("Mã phòng") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        letterSpacing = 6.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                when {
                    state.isPreviewingRoom -> Text("Đang kiểm tra phòng…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.joinErrorMessage != null -> Text(state.joinErrorMessage, color = Rose500)
                    state.roomPreview != null -> Text(
                        text = "Phòng của ${state.roomPreview.host.username ?: "người chơi"} · " +
                            "${state.roomPreview.settings.questionCount} câu · " +
                            "mốc ${state.roomPreview.settings.timePerQuestion}s",
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    else -> Text(
                        text = "Nhập ${DuoRepository.ROOM_CODE_LENGTH} ký tự chủ phòng đọc cho bạn.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::confirmJoinRoom, enabled = state.canJoinRoom) {
                Text("Vào phòng")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeJoinDialog) { Text("Huỷ") }
        },
    )
}

@Composable
private fun QueueTimeoutDialog(onDismiss: () -> Unit, onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Không tìm được đối thủ") },
        text = {
            Text(
                "Đã chờ hết thời gian mà chưa có ai cùng thể thức. Thử lại, hoặc rủ bạn bè " +
                    "vào phòng riêng.",
            )
        },
        confirmButton = { TextButton(onClick = onRetry) { Text("Tìm lại") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

private fun DuoDifficulty.label(): String = when (this) {
    DuoDifficulty.EASY -> "Dễ"
    DuoDifficulty.MEDIUM -> "Vừa"
    DuoDifficulty.HARD -> "Khó"
}

private fun DuoSettingsDto.summary(): String {
    val level = difficulty?.label() ?: "Bất kỳ"
    return "$questionCount câu · mốc ${timePerQuestion}s · $level"
}

/**
 * Who the player is, in the same terms an opponent sees them: rank, level, streak.
 *
 * Deliberately above the Elo card. The rating is a number that means something only against
 * another rating; the tier is the version of it a player can hold in their head.
 */
@Composable
private fun PlayerCard(state: DuoHomeUiState) {
    val me = state.session.me
    val profile = state.profile

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialsAvatar(
            userId = me?.id ?: "me",
            username = me?.username ?: "Bạn",
            size = 48.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = me?.username ?: "Bạn",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TierBadge(tier = me?.tier.orEmpty())
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Cấp ${profile?.level ?: me?.level ?: 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val streak = profile?.dayStreak ?: me?.dayStreak ?: 0
            if (streak > 0) {
                // The streak is not vanity: it buys a small head start in the next match.
                Text(
                    text = "🔥 $streak ngày liên tiếp · khởi đầu mạnh hơn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Orange400,
                )
            }
            state.season?.let { season ->
                Text(
                    // The server already names a season "Mùa 09/2026".
                    text = "${season.name}: ${season.rating} điểm" +
                        (season.ratingToNextTier?.let { " · còn $it lên hạng" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${profile?.gold ?: 0}",
                style = MaterialTheme.typography.titleMedium,
                color = Orange400,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "vàng", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The four screens that decide how a player fights, one row, always reachable from the lobby. */
@Composable
private fun GameEntryRow(
    onOpenClasses: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenLoadout: () -> Unit,
    onOpenInventory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameEntry("🛡", "Lớp", onOpenClasses, Modifier.weight(1f))
        GameEntry("🌳", "Kỹ năng", onOpenSkills, Modifier.weight(1f))
        GameEntry("✨", "Trang bị KN", onOpenLoadout, Modifier.weight(1f))
        GameEntry("🎁", "Đồ", onOpenInventory, Modifier.weight(1f))
    }
}

@Composable
private fun GameEntry(symbol: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = symbol, fontSize = 20.sp)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The empty-energy dialog.
 *
 * It exists to name the way out. Energy trickles back at one point every thirty minutes, but a
 * lesson gives two immediately -- so the primary action here is to go and study, and the wait is
 * the fallback rather than the message.
 */
@Composable
private fun OutOfEnergyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hết lượt chơi") },
        text = {
            Text(
                "Mỗi trận tốn 1 lượt. Lượt tự hồi 1 điểm mỗi 30 phút — nhưng học xong một bài " +
                    "được thẳng 2 lượt, nhanh hơn nhiều.",
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đã hiểu") } },
    )
}

/**
 * What the lobby already knows about the player who just walked in, in the shape the profile card
 * draws. Everything else stays at its default until the full card lands a moment later.
 */
private fun DuoPlayerDto.asProfileSeed() = PublicProfileDto(
    id = id,
    username = username,
    avatarUrl = avatarUrl,
    level = level,
    classCode = classCode,
    dayStreak = dayStreak,
    pvp = PvpStatsDto(rating = rating, tier = tier),
    learning = LearningStatsDto(),
)
