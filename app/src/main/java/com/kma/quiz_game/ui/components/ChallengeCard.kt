package com.kma.quiz_game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral200
import com.kma.quiz_game.ui.theme.Neutral300
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.ShapeXl
import com.kma.quiz_game.ui.theme.Sky500

/**
 * [REMOVED] is an option a skill has taken off the table for this player only.
 *
 * It is a state rather than a filter: dropping the card out of the list would reflow the grid
 * mid-round, under a thumb already moving towards an answer.
 */
enum class ChallengeOptionState { NONE, SELECTED, CORRECT, WRONG, REMOVED }

private data class OptionColors(val border: Color, val background: Color, val content: Color)

private fun colorsFor(state: ChallengeOptionState): OptionColors = when (state) {
    ChallengeOptionState.NONE -> OptionColors(Neutral200, Color.White, Neutral700)
    ChallengeOptionState.SELECTED -> OptionColors(Sky500, Sky500.copy(alpha = 0.1f), Sky500)
    ChallengeOptionState.CORRECT -> OptionColors(Green500, Green500.copy(alpha = 0.1f), Green500)
    ChallengeOptionState.WRONG -> OptionColors(Rose500, Rose500.copy(alpha = 0.1f), Rose500)
    ChallengeOptionState.REMOVED -> OptionColors(Neutral200, Neutral050, Neutral300)
}

/**
 * A single answer option, used both for SELECT (grid) and ASSIST (single column) challenges.
 *
 * An option with an [imagePath] is answered by its picture, and its word stays hidden until the
 * answer is graded. [onPlayAudio] adds a speaker to an option that carries a recording.
 */
@Composable
fun ChallengeOptionCard(
    text: String,
    state: ChallengeOptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imagePath: String? = null,
    onPlayAudio: (() -> Unit)? = null,
) {
    val colors = colorsFor(state)
    val interactionSource = remember { MutableInteractionSource() }
    val graded = state == ChallengeOptionState.CORRECT || state == ChallengeOptionState.WRONG
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
            .padding(if (imagePath != null) 8.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val label: @Composable () -> Unit = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = colors.content,
                textDecoration = if (state == ChallengeOptionState.REMOVED) TextDecoration.LineThrough else null,
            )
        }
        when {
            imagePath != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OptionImage(
                    path = imagePath,
                    contentDescription = if (graded) text else null,
                    modifier = Modifier.clip(ShapeXl),
                    fallback = label,
                )
                if (graded) label()
            }
            onPlayAudio != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.weight(1f, fill = false)) { label() }
                SpeakerButton(onClick = onPlayAudio, tint = colors.content)
            }
            else -> label()
        }
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
