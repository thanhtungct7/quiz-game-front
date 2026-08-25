package com.kma.quiz_game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.kma.quiz_game.ui.theme.Neutral200
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.ShapeXl
import com.kma.quiz_game.ui.theme.Sky500

enum class ChallengeOptionState { NONE, SELECTED, CORRECT, WRONG }

private data class OptionColors(val border: Color, val background: Color, val content: Color)

private fun colorsFor(state: ChallengeOptionState): OptionColors = when (state) {
    ChallengeOptionState.NONE -> OptionColors(Neutral200, Color.White, Neutral700)
    ChallengeOptionState.SELECTED -> OptionColors(Sky500, Sky500.copy(alpha = 0.1f), Sky500)
    ChallengeOptionState.CORRECT -> OptionColors(Green500, Green500.copy(alpha = 0.1f), Green500)
    ChallengeOptionState.WRONG -> OptionColors(Rose500, Rose500.copy(alpha = 0.1f), Rose500)
}

/** A single answer option, used both for SELECT (grid) and ASSIST (single column) challenges. */
@Composable
fun ChallengeOptionCard(
    text: String,
    state: ChallengeOptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = colorsFor(state)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeXl)
            .background(colors.background)
            .border(width = 2.dp, color = colors.border, shape = ShapeXl)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = colors.content,
        )
    }
}

/** Speech-bubble prompt used above ASSIST-type challenges. */
@Composable
fun QuestionBubble(question: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeXl)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(text = question, style = MaterialTheme.typography.titleLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun ChallengeOptionCardPreview() {
    Quiz_gameTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            ChallengeOptionCard(text = "el hombre", state = ChallengeOptionState.NONE, onClick = {})
            ChallengeOptionCard(text = "la mujer", state = ChallengeOptionState.SELECTED, onClick = {})
            ChallengeOptionCard(text = "el niño", state = ChallengeOptionState.CORRECT, onClick = {})
            ChallengeOptionCard(text = "la niña", state = ChallengeOptionState.WRONG, onClick = {})
        }
    }
}
