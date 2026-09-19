package com.kma.quiz_game.ui.components.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kma.quiz_game.data.remote.dto.DailyQuestsDto
import com.kma.quiz_game.data.remote.dto.QuestCompletedDto
import com.kma.quiz_game.data.remote.dto.QuestRewardDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.ShapeXl
import com.kma.quiz_game.ui.theme.ShapeXxl
import com.kma.quiz_game.ui.theme.Sky500
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The daily quests as the learn path shows them: how far today has got, and a red dot while
 * something is waiting to be collected. Tapping it opens the quest screen.
 *
 * Drawn before the day has loaded too, as a plain way in, so the path does not shift when the
 * request lands.
 */
@Composable
fun QuestBanner(day: DailyQuestsDto?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box {
                Text(text = "🎯", fontSize = 32.sp)
                val claimable = day?.claimableCount ?: 0
                if (claimable > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Rose500),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$claimable",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Nhiệm vụ ngày", style = MaterialTheme.typography.titleMedium)
                if (day == null) {
                    Text(
                        text = "Hoàn thành nhiệm vụ để mở rương mỗi ngày.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = "${day.questsDone}/${day.quests.size} nhiệm vụ · " +
                            "${day.activityPoints}/${day.maxActivityPoints} điểm",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ProgressTrack(fraction = day.fraction, color = Orange400)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** A rounded progress bar that animates to [fraction]. */
@Composable
fun ProgressTrack(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 8) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape((height / 2).dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape((height / 2).dp))
                .background(color),
        )
    }
}

/**
 * The quests a battle or a match just finished, on its result screen.
 *
 * Only the names and the points: the gold and experience are collected on the quest screen, and
 * saying them here would read as already paid.
 */
@Composable
fun QuestsCompletedCard(quests: List<QuestCompletedDto>, onOpenQuests: (() -> Unit)? = null) {
    if (quests.isEmpty()) return
    RewardCard {
        Text(
            text = "Nhiệm vụ hoàn thành",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Green500,
        )
        quests.forEach { quest ->
            RewardRow(label = "✅ ${quest.title}", value = "+${quest.activityPoints} điểm", color = Orange400)
        }
        if (onOpenQuests != null) {
            DuoButton(
                text = "Nhận thưởng",
                onClick = onOpenQuests,
                variant = DuoButtonVariant.Secondary,
            )
        }
    }
}

/**
 * The in-game toast: a banner that slides down from the top for three seconds when a battle or a
 * match finished a quest, so the moment is noticed before the learner scrolls to the card.
 *
 * Place it last in a [Box] over the screen's content.
 */
@Composable
fun QuestToast(quests: List<QuestCompletedDto>, modifier: Modifier = Modifier) {
    var visible by remember(quests) { mutableStateOf(false) }
    LaunchedEffect(quests) {
        if (quests.isEmpty()) return@LaunchedEffect
        visible = true
        delay(TOAST_MILLIS)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = ShapeXl,
            color = Green500,
            shadowElevation = 6.dp,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "🎉", fontSize = 24.sp)
                Text(
                    text = if (quests.size == 1) {
                        "Hoàn thành nhiệm vụ: ${quests.first().title}"
                    } else {
                        "Hoàn thành ${quests.size} nhiệm vụ ngày!"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val TOAST_MILLIS = 3_000L

/**
 * What a claim paid, made into a moment: the chest (or medal) pops in over turning rays,
 * confetti falls, and the gold and experience count up from zero.
 *
 * Compose animation only -- no Lottie file to ship. [prize] draws what is being opened: a chest's
 * sprite, or a medal for a quest.
 */
@Composable
fun RewardCelebrationDialog(
    title: String,
    prize: @Composable () -> Unit,
    reward: QuestRewardDto,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(shape = ShapeXxl, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                        TurningRays(color = Orange400, modifier = Modifier.fillMaxSize())
                        PopIn(prize)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    CountUpRow(label = "Vàng", value = reward.gold.delta, color = Orange400)
                    CountUpRow(label = "Kinh nghiệm", value = reward.exp.delta, color = Sky500)
                    if (reward.exp.leveledUp) {
                        Text(
                            text = "Lên cấp ${reward.exp.levelBefore} → ${reward.exp.levelAfter}!",
                            style = MaterialTheme.typography.titleMedium,
                            color = Indigo500,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    reward.loot?.let { loot -> LootReward(name = loot.name, rarity = loot.rarity) }
                    Spacer(Modifier.height(4.dp))
                    DuoButton(text = "Tuyệt vời!", onClick = onDismiss)
                }
            }
            // Over the card, the size of it; a bare Canvas takes no touches, so the button works.
            ConfettiBurst(modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun CountUpRow(label: String, value: Int, color: Color) {
    if (value <= 0) return
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) { target = value }
    val shown by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 900),
        label = "count-up",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "+$shown",
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PopIn(content: @Composable () -> Unit) {
    val scale = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f))
    }
    Box(modifier = Modifier.scale(scale.value)) { content() }
}

/** The halo behind the prize: twelve soft wedges turning slowly. */
@Composable
private fun TurningRays(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rays")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 9_000, easing = LinearEasing)),
        label = "rays-angle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "rays-pulse",
    )
    Canvas(modifier = modifier.rotate(angle)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        val wedge = (Math.PI * 2 / RAY_COUNT / 2).toFloat()
        repeat(RAY_COUNT) { index ->
            val start = (Math.PI * 2 * index / RAY_COUNT).toFloat()
            val path = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + radius * cos(start), center.y + radius * sin(start))
                lineTo(center.x + radius * cos(start + wedge), center.y + radius * sin(start + wedge))
                close()
            }
            drawPath(path, color.copy(alpha = pulse))
        }
    }
}

private const val RAY_COUNT = 12

private data class Confetto(
    val x: Float,
    val drift: Float,
    val speed: Float,
    val spin: Float,
    val color: Color,
)

/** One burst of falling paper, drawn once and left to settle below the card. */
@Composable
private fun ConfettiBurst(modifier: Modifier = Modifier) {
    val pieces = remember {
        val palette = listOf(Orange400, Sky500, Green500, Rose500, Indigo500)
        List(CONFETTI_COUNT) {
            Confetto(
                x = Random.nextFloat(),
                drift = Random.nextFloat() * 0.3f - 0.15f,
                speed = 0.7f + Random.nextFloat() * 0.6f,
                spin = Random.nextFloat() * 720f - 360f,
                color = palette[it % palette.size],
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 1_800, easing = LinearEasing)) }
    Canvas(modifier = modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        pieces.forEach { piece ->
            val x = (piece.x + piece.drift * t) * size.width
            val y = (-0.1f + piece.speed * t * 1.2f) * size.height
            rotate(degrees = piece.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = piece.color.copy(alpha = 1f - t * 0.6f),
                    topLeft = Offset(x - 4f, y - 8f),
                    size = Size(8.dp.toPx() / 1.2f, 14.dp.toPx() / 1.6f),
                )
            }
        }
    }
}

private const val CONFETTI_COUNT = 40
