package com.kma.quiz_game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Neutral200
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.ShapeXl

/** One word of an ORDER challenge: a stable id (what gets submitted) and the word itself. */
data class WordTile(val id: String, val text: String)

/**
 * The word-ordering ("ghép câu") answer surface.
 *
 * Two areas: the sentence being built on top, the bank of unused words below. Tapping a bank word
 * appends it to the sentence; tapping a word in the sentence sends it back.
 *
 * A word that has been used is not dropped from the bank -- it is left in place as an empty slot.
 * Removing it would reflow every word after it, under a thumb already travelling towards the next
 * one, and would make the bank change shape on every single tap.
 *
 * @param placedIds the sentence so far, in order; every id must be one of [tiles].
 * @param status colours the finished sentence once the answer has been checked; while it is
 *   [SentenceStatus.OPEN] the surface is interactive.
 */
@Composable
fun SentenceBuilder(
    tiles: List<WordTile>,
    placedIds: List<String>,
    status: SentenceStatus,
    onPlace: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tilesById = remember(tiles) { tiles.associateBy(WordTile::id) }
    val enabled = status == SentenceStatus.OPEN

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 96.dp)
                .clip(ShapeXl)
                .background(Neutral050)
                .border(width = 2.dp, color = borderFor(status), shape = ShapeXl)
                .padding(12.dp),
        ) {
            if (placedIds.isEmpty()) {
                Text(
                    text = "Chạm vào các từ bên dưới để ghép thành câu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neutral200,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    placedIds.forEach { id ->
                        val tile = tilesById[id] ?: return@forEach
                        WordChip(
                            text = tile.text,
                            contentColor = contentFor(status),
                            borderColor = borderFor(status),
                            enabled = enabled,
                            onClick = { onRemove(id) },
                        )
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tiles.forEach { tile ->
                val used = tile.id in placedIds
                // The used word keeps its slot -- invisible, but still measured -- so the bank
                // never reflows mid-answer.
                Box {
                    WordChip(
                        text = tile.text,
                        contentColor = Neutral700,
                        borderColor = Neutral200,
                        enabled = enabled && !used,
                        onClick = { onPlace(tile.id) },
                        modifier = if (used) Modifier.alpha(0f) else Modifier,
                    )
                    if (used) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(ShapeXl)
                                .background(Neutral100),
                        )
                    }
                }
            }
        }
    }
}

/** How the built sentence should read: still being answered, or already graded. */
enum class SentenceStatus { OPEN, CORRECT, WRONG }

private fun borderFor(status: SentenceStatus): Color = when (status) {
    SentenceStatus.OPEN -> Neutral200
    SentenceStatus.CORRECT -> Green500
    SentenceStatus.WRONG -> Rose500
}

private fun contentFor(status: SentenceStatus): Color = when (status) {
    SentenceStatus.OPEN -> Neutral700
    SentenceStatus.CORRECT -> Green500
    SentenceStatus.WRONG -> Rose500
}

@Composable
private fun WordChip(
    text: String,
    contentColor: Color,
    borderColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(ShapeXl)
            .background(Color.White)
            .border(width = 2.dp, color = borderColor, shape = ShapeXl)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = contentColor)
    }
}

@Preview(showBackground = true)
@Composable
private fun SentenceBuilderPreview() {
    val tiles = listOf(
        WordTile("1", "ngủ"),
        WordTile("2", "Tôi"),
        WordTile("3", "đi"),
        WordTile("4", "phải"),
    )
    Quiz_gameTheme {
        SentenceBuilder(
            tiles = tiles,
            placedIds = listOf("2", "4"),
            status = SentenceStatus.OPEN,
            onPlace = {},
            onRemove = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
