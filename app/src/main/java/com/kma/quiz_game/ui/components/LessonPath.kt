package com.kma.quiz_game.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kma.quiz_game.ui.components.battle.monsterArt
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Green600
import com.kma.quiz_game.ui.theme.Neutral200
import com.kma.quiz_game.ui.theme.Neutral400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.ShapeFull

enum class LessonNodeStatus { LOCKED, ACTIVE, COMPLETE }

/**
 * One gate on the path.
 *
 * [monsterArtCode] comes from `GET /battles/courses/{id}/monsters` -- one request for the whole
 * map -- and is null until it arrives, or when the catalog has no monster for this gate. The node
 * still draws in that case: a missing monster must never cost the player their way into a lesson.
 */
data class LessonPathItem(
    val id: String,
    val title: String,
    val status: LessonNodeStatus,
    val monsterArtCode: String? = null,
    val isBoss: Boolean = false,
    /** True once this gate's monster has been beaten at least once. */
    val cleared: Boolean = false,
)

/** Zig-zag lesson path: a cycle of 8 horizontal offsets, alternating left/right. */
private val CYCLE_OFFSETS_DP = listOf(0, 40, 64, 40, 0, -40, -64, -40)

@Composable
fun LessonPath(
    lessons: List<LessonPathItem>,
    onLessonClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        lessons.forEachIndexed { index, item ->
            val offsetX = CYCLE_OFFSETS_DP[index % CYCLE_OFFSETS_DP.size].dp
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .offset(x = offsetX),
            ) {
                LessonNode(
                    status = item.status,
                    onClick = { onLessonClick(item.id) },
                    monsterArtCode = item.monsterArtCode,
                    isBoss = item.isBoss,
                    cleared = item.cleared,
                )
            }
        }
    }
}

@Composable
fun LessonNode(
    status: LessonNodeStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    monsterArtCode: String? = null,
    isBoss: Boolean = false,
    cleared: Boolean = false,
) {
    val size = if (isBoss) 76.dp else 64.dp
    val interactionSource = remember { MutableInteractionSource() }
    // A monster sits on a plain disc with a green ring rather than on solid green: some of the art
    // is green itself (the slime is 🟢), and on a green disc it simply disappeared.
    val showsMonster = status != LessonNodeStatus.LOCKED && monsterArtCode != null
    val pulse = if (status == LessonNodeStatus.ACTIVE) rememberPulse() else null
    Box(contentAlignment = Alignment.TopCenter, modifier = modifier) {
        if (status == LessonNodeStatus.ACTIVE) {
            StartTooltip(modifier = Modifier.offset(y = (-28).dp))
        }
        Box(
            modifier = Modifier
                .padding(top = if (status == LessonNodeStatus.ACTIVE) 20.dp else 0.dp)
                .graphicsLayer {
                    val scale = pulse?.value ?: 1f
                    scaleX = scale
                    scaleY = scale
                }
                .size(size)
                .clip(ShapeFull)
                .background(if (showsMonster) MaterialTheme.colorScheme.surface else nodeBackground(status))
                // Skipped rather than drawn at 0.dp: a zero-width border still paints a hairline.
                .then(
                    nodeBorderWidth(status, showsMonster).let { width ->
                        if (width > 0.dp) {
                            Modifier.border(
                                width = width,
                                color = if (showsMonster) Green500 else Green600.copy(alpha = 0.4f),
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        }
                    },
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = status != LessonNodeStatus.LOCKED,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // A locked gate keeps its padlock: showing which monster waits behind a gate the
                // player cannot open yet would only be a tease.
                status == LessonNodeStatus.LOCKED ->
                    Icon(Icons.Filled.Lock, contentDescription = "đã khoá", tint = Neutral400, modifier = Modifier.size(24.dp))

                monsterArtCode != null -> Text(
                    text = monsterArt(monsterArtCode),
                    fontSize = if (isBoss) 34.sp else 28.sp,
                )

                status == LessonNodeStatus.COMPLETE ->
                    Icon(Icons.Filled.Check, contentDescription = "đã hoàn thành", tint = Color.White, modifier = Modifier.size(28.dp))

                else ->
                    Icon(Icons.Filled.Star, contentDescription = "bắt đầu", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // A gate whose monster is down keeps its tick, over the monster rather than instead of it.
        if (cleared && status != LessonNodeStatus.LOCKED) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "đã hạ",
                tint = Color.White,
                modifier = Modifier
                    .offset(x = 22.dp, y = 26.dp)
                    .size(20.dp)
                    .clip(ShapeFull)
                    .background(Green600)
                    .padding(2.dp),
            )
        }
    }
}

/** The gate to play next breathes, so it reads as the one to tap without a label on every node. */
@Composable
private fun rememberPulse(): State<Float> =
    rememberInfiniteTransition(label = "activeNode").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "activeNodeScale",
    )

@Composable
private fun StartTooltip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(ShapeFull)
            .background(Color.White)
            .border(width = 2.dp, color = Neutral200, shape = ShapeFull)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = "BẮT ĐẦU", style = MaterialTheme.typography.labelMedium, color = Green600)
    }
}

private fun nodeBackground(status: LessonNodeStatus): Color = when (status) {
    LessonNodeStatus.COMPLETE -> Green500
    LessonNodeStatus.ACTIVE -> Green500
    LessonNodeStatus.LOCKED -> Neutral200
}

private fun nodeBorderWidth(status: LessonNodeStatus, showsMonster: Boolean) = when {
    status == LessonNodeStatus.ACTIVE -> 4.dp
    status == LessonNodeStatus.COMPLETE && showsMonster -> 3.dp
    else -> 0.dp
}

@Preview(showBackground = true)
@Composable
private fun LessonPathPreview() {
    Quiz_gameTheme {
        LessonPath(
            lessons = listOf(
                LessonPathItem("1", "Lesson 1", LessonNodeStatus.COMPLETE, "SLIME", cleared = true),
                LessonPathItem("2", "Lesson 2", LessonNodeStatus.ACTIVE, "GOBLIN"),
                LessonPathItem("3", "Lesson 3", LessonNodeStatus.LOCKED, "DIRE_WOLF"),
                LessonPathItem("4", "Lesson 4", LessonNodeStatus.LOCKED, "DRAGON", isBoss = true),
            ),
            onLessonClick = {},
        )
    }
}
