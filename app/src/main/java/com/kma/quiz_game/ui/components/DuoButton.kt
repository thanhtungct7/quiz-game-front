package com.kma.quiz_game.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Green600
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Indigo600
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Neutral200
import com.kma.quiz_game.ui.theme.Neutral400
import com.kma.quiz_game.ui.theme.Neutral600
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Rose600
import com.kma.quiz_game.ui.theme.ShapeFull
import com.kma.quiz_game.ui.theme.ShapeXl
import com.kma.quiz_game.ui.theme.Sky500
import com.kma.quiz_game.ui.theme.Sky600

enum class DuoButtonVariant { Primary, Secondary, Danger, Super, Locked, Outline, DangerOutline }
enum class DuoButtonSize { Default, Small, Large, Pill }

private data class DuoButtonColors(val background: Color, val shadow: Color, val content: Color, val border: Color? = null)

private fun colorsFor(variant: DuoButtonVariant): DuoButtonColors = when (variant) {
    DuoButtonVariant.Primary -> DuoButtonColors(Green500, Green600, Color.White)
    DuoButtonVariant.Secondary -> DuoButtonColors(Sky500, Sky600, Color.White)
    DuoButtonVariant.Danger -> DuoButtonColors(Rose500, Rose600, Color.White)
    DuoButtonVariant.Super -> DuoButtonColors(Indigo500, Indigo600, Color.White)
    DuoButtonVariant.Locked -> DuoButtonColors(Neutral200, Neutral400, Neutral600)
    DuoButtonVariant.Outline -> DuoButtonColors(Color.White, Neutral200, Neutral600, border = Neutral200)
    DuoButtonVariant.DangerOutline -> DuoButtonColors(Color.White, Rose500, Rose500, border = Rose500)
}

private fun heightFor(size: DuoButtonSize): Dp = when (size) {
    DuoButtonSize.Small -> 40.dp
    DuoButtonSize.Large -> 56.dp
    DuoButtonSize.Pill -> 48.dp
    DuoButtonSize.Default -> 48.dp
}

/**
 * Recreates duolingo-clone's signature "3D pressed" button (border-2 border-b-4, which
 * collapses to border-b-0 on press): a darker shadow layer sits under a lighter top layer;
 * pressing offsets the top layer down onto the shadow.
 */
@Composable
fun DuoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DuoButtonVariant = DuoButtonVariant.Primary,
    size: DuoButtonSize = DuoButtonSize.Default,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = colorsFor(variant)
    val shape: RoundedCornerShape = if (size == DuoButtonSize.Pill) ShapeFull else ShapeXl
    val height = heightFor(size)
    val shadowDepth = 4.dp

    val widthModifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier

    Box(modifier = modifier.then(widthModifier).height(height + shadowDepth)) {
        // Bottom "shadow" layer, fills the whole container; the top layer covers all but a
        // shadowDepth-tall strip of it, giving the illusion of a thick bottom border.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(shape)
                .background(if (enabled) colors.shadow else Neutral100)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = if (pressed && enabled) shadowDepth else 0.dp)
                .clip(shape)
                .background(if (enabled) colors.background else Neutral200)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides if (enabled) colors.content else Neutral400) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    leadingIcon?.invoke()
                    Text(
                        text = text.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled) colors.content else Neutral400,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DuoButtonPreview() {
    Quiz_gameTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DuoButton(text = "Continue", onClick = {})
        }
    }
}
