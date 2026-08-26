package com.kma.quiz_game.ui.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Green600
import com.kma.quiz_game.ui.theme.Neutral200
import com.kma.quiz_game.ui.theme.Neutral400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.ShapeFull

enum class LessonNodeStatus { LOCKED, ACTIVE, COMPLETE }

data class LessonPathItem(
    val id: String,
    val title: String,
    val status: LessonNodeStatus,
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
) {
    val size = 64.dp
    val interactionSource = remember { MutableInteractionSource() }
    Box(contentAlignment = Alignment.TopCenter, modifier = modifier) {
        if (status == LessonNodeStatus.ACTIVE) {
            StartTooltip(modifier = Modifier.offset(y = (-28).dp))
        }
        Box(
            modifier = Modifier
                .padding(top = if (status == LessonNodeStatus.ACTIVE) 20.dp else 0.dp)
                .size(size)
                .clip(ShapeFull)
                .background(nodeBackground(status))
                .border(
                    width = if (status == LessonNodeStatus.ACTIVE) 4.dp else 0.dp,
                    color = Green600.copy(alpha = 0.4f),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = status != LessonNodeStatus.LOCKED,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                LessonNodeStatus.COMPLETE -> Icon(Icons.Filled.Check, contentDescription = "completed", tint = Color.White, modifier = Modifier.size(28.dp))
                LessonNodeStatus.LOCKED -> Icon(Icons.Filled.Lock, contentDescription = "locked", tint = Neutral400, modifier = Modifier.size(24.dp))
                LessonNodeStatus.ACTIVE -> Icon(Icons.Filled.Star, contentDescription = "start", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun StartTooltip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(ShapeFull)
            .background(Color.White)
            .border(width = 2.dp, color = Neutral200, shape = ShapeFull)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = "START", style = MaterialTheme.typography.labelMedium, color = Green600)
    }
}

private fun nodeBackground(status: LessonNodeStatus): Color = when (status) {
    LessonNodeStatus.COMPLETE -> Green500
    LessonNodeStatus.ACTIVE -> Green500
    LessonNodeStatus.LOCKED -> Neutral200
}

@Preview(showBackground = true)
@Composable
private fun LessonPathPreview() {
    Quiz_gameTheme {
        LessonPath(
            lessons = listOf(
                LessonPathItem("1", "Lesson 1", LessonNodeStatus.COMPLETE),
                LessonPathItem("2", "Lesson 2", LessonNodeStatus.ACTIVE),
                LessonPathItem("3", "Lesson 3", LessonNodeStatus.LOCKED),
                LessonPathItem("4", "Lesson 4", LessonNodeStatus.LOCKED),
            ),
            onLessonClick = {},
        )
    }
}
