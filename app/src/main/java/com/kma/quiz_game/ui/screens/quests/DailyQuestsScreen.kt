package com.kma.quiz_game.ui.screens.quests

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.remote.dto.ActivityChestDto
import com.kma.quiz_game.data.remote.dto.DailyQuestDto
import com.kma.quiz_game.data.remote.dto.DailyQuestsDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonSize
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.game.PixelTile
import com.kma.quiz_game.ui.components.game.ProgressTrack
import com.kma.quiz_game.ui.components.game.RewardCelebrationDialog
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.ShapeXl
import com.kma.quiz_game.ui.theme.Sky500
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Today's four quests and the three chests their activity points open.
 *
 * The chest bar sits on top because it is the goal: the quests are the way there. Progress is
 * counted by the server at the end of every battle, match, lesson and AI conversation, so this
 * screen never moves a number itself -- it reloads when it comes into view and after every claim.
 */
@Composable
fun DailyQuestsScreen(onBack: () -> Unit) {
    val viewModel: DailyQuestsViewModel = viewModel(factory = rememberAppViewModelFactory())
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    // Ticks the countdown, and fetches the next day's set the moment this one expires.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(state.day?.resetsAt) {
        val resetsAt = state.day?.resetsAt ?: return@LaunchedEffect
        while (true) {
            now = Instant.now()
            if (hasExpired(resetsAt, now)) {
                viewModel.load()
                break
            }
            delay(1_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        QuestTopBar(
            onBack = onBack,
            subtitle = state.day?.let { "Làm mới sau ${countdown(it.resetsAt, now)}" },
        )

        val day = state.day
        when {
            day == null && state.isLoading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            day == null -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = state.errorMessage ?: "Chưa tải được nhiệm vụ hôm nay.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    DuoButton(text = "Thử lại", onClick = viewModel::load)
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "chests") {
                    ActivityCard(
                        day = day,
                        claiming = state.claiming,
                        onOpenChest = viewModel::claimChest,
                    )
                }
                item(key = "heading") {
                    Text(
                        text = "Nhiệm vụ hôm nay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(day.quests, key = { it.id }) { quest ->
                    QuestCard(
                        quest = quest,
                        claiming = state.claiming == quest.id,
                        onClaim = { viewModel.claimQuest(quest) },
                    )
                }
                item(key = "hint") {
                    Text(
                        text = "Tiến độ được tính khi kết thúc mỗi ải, trận đối kháng, bài học " +
                            "và hội thoại AI. Phần thưởng chưa nhận sẽ mất lúc 0 giờ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    state.celebration?.let { celebration ->
        RewardCelebrationDialog(
            title = celebration.title,
            prize = {
                val tier = celebration.chestTier
                if (tier != null) {
                    PixelTile(path = chestArt(tier), size = 112.dp)
                } else {
                    Text(text = "🏅", fontSize = 80.sp)
                }
            },
            reward = celebration.reward,
            onDismiss = viewModel::dismissCelebration,
        )
    }

    // Only once the day is on screen: before that, the error is the whole page.
    if (state.day != null) {
        state.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("Đóng") } },
                text = { Text(message) },
            )
        }
    }
}

@Composable
private fun QuestTopBar(onBack: () -> Unit, subtitle: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Nhiệm vụ ngày", style = MaterialTheme.typography.titleLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Today's activity points, and the three chests along the way to the maximum. */
@Composable
private fun ActivityCard(
    day: DailyQuestsDto,
    claiming: String?,
    onOpenChest: (ActivityChestDto) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = ShapeXl,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Điểm năng động",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${day.activityPoints}/${day.maxActivityPoints}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Orange400,
                    fontWeight = FontWeight.Bold,
                )
            }
            ProgressTrack(fraction = day.fraction, color = Orange400, height = 12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                day.chests.forEach { chest ->
                    ChestTile(
                        chest = chest,
                        claiming = claiming == chestKey(chest.milestone),
                        onOpen = { onOpenChest(chest) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One chest: greyed out until its milestone, then bobbing until it is opened. */
@Composable
private fun ChestTile(
    chest: ActivityChestDto,
    claiming: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = chest.state()
    val bob by rememberInfiniteTransition(label = "chest").animateFloat(
        initialValue = 1f,
        targetValue = if (state == ChestState.READY) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 600), RepeatMode.Reverse),
        label = "chest-bob",
    )
    val accent = when (state) {
        ChestState.READY -> Orange400
        ChestState.OPENED -> Green500
        ChestState.LOCKED -> MaterialTheme.colorScheme.outline
    }
    Surface(
        onClick = onOpen,
        enabled = state == ChestState.READY && !claiming,
        shape = RoundedCornerShape(12.dp),
        color = if (state == ChestState.READY) Orange400.copy(alpha = 0.12f) else Color.Transparent,
        border = BorderStroke(2.dp, accent),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(modifier = Modifier.scale(bob)) {
                PixelTile(
                    path = chestArt(chest.tier),
                    size = 48.dp,
                    alpha = when (state) {
                        ChestState.READY -> 1f
                        ChestState.OPENED -> 0.75f
                        ChestState.LOCKED -> 0.6f
                    },
                    colorFilter = if (state == ChestState.LOCKED) Greyscale else null,
                )
                when (state) {
                    ChestState.LOCKED -> ChestBadge(
                        icon = Icons.Filled.Lock,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )

                    ChestState.OPENED -> ChestBadge(
                        icon = Icons.Filled.Check,
                        color = Green500,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )

                    ChestState.READY -> Unit
                }
            }
            Text(
                text = chest.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    claiming -> "Đang mở..."
                    state == ChestState.READY -> "Chạm để mở"
                    state == ChestState.OPENED -> "Đã mở"
                    else -> "${chest.milestone} điểm"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state == ChestState.READY) Orange400 else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The corner mark on a chest that is not ready: a lock before its milestone, a tick after. */
@Composable
private fun ChestBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(x = 10.dp, y = 4.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

private val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * One quest. A finished one whose reward is still waiting is outlined in green so it stands out
 * from the rest; a collected one greys its title, since there is nothing left to do with it.
 */
@Composable
private fun QuestCard(quest: DailyQuestDto, claiming: Boolean, onClaim: () -> Unit) {
    val difficultyColor = when (quest.difficulty.uppercase()) {
        "HARD" -> Indigo500
        "MEDIUM" -> Sky500
        else -> Green500
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (quest.claimable) {
                Green500.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (quest.claimable) BorderStroke(2.dp, Green500) else null,
        shape = ShapeXl,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = difficultyLabel(quest.difficulty),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(difficultyColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Text(
                    text = quest.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (quest.claimed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressTrack(
                    fraction = quest.fraction,
                    color = if (quest.completed) Green500 else difficultyColor,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${quest.progress}/${quest.target}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (quest.completed) Green500 else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "+${quest.activityPoints} điểm · +${quest.rewardGold} vàng · +${quest.rewardExp} KN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when {
                    quest.claimed -> Text(
                        text = "Đã nhận ✓",
                        style = MaterialTheme.typography.labelLarge,
                        color = Green500,
                        fontWeight = FontWeight.Bold,
                    )

                    // An explicit width, as in the shop: `fullWidth = false` still lets the
                    // button fill what the row offers, which left the reward text a sliver of
                    // one letter per line.
                    quest.completed -> DuoButton(
                        text = if (claiming) "..." else "Nhận",
                        onClick = onClaim,
                        modifier = Modifier.width(96.dp),
                        enabled = !claiming,
                        size = DuoButtonSize.Small,
                        variant = DuoButtonVariant.Primary,
                        fullWidth = false,
                    )

                    else -> Unit
                }
            }
        }
    }
}
