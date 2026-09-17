package com.kma.quiz_game.ui.screens.battle

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kma.quiz_game.data.remote.dto.ChallengeOptionDto
import com.kma.quiz_game.data.remote.dto.LoadoutSlotDto
import com.kma.quiz_game.data.remote.dto.PVP_LIFELINE_EFFECTS
import com.kma.quiz_game.ui.components.ChallengeOptionState
import com.kma.quiz_game.ui.components.OptionImage
import com.kma.quiz_game.ui.components.SpeakerButton
import com.kma.quiz_game.ui.components.game.effectDescription
import com.kma.quiz_game.ui.components.game.effectSymbol

/**
 * The fight's own HUD: the same controls the shared ones offer, cut from stone instead of paper.
 *
 * These are copies rather than parameters on the shared components under `ui/components/game`,
 * and that is the point. Those are drawn by the duo match, the leaderboard and the duo home screen
 * in the app's light theme; threading a palette through them would put every one of those screens
 * one careless default away from turning dark. Nothing in this file is used outside a PVE fight.
 */

/** How much health is left, framed. Animated so a blow that lands is worth watching. */
@Composable
fun BattleHpBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    height: Dp = 14.dp,
    impactDelayMillis: Int = 0,
) {
    val safe = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = safe,
        animationSpec = tween(durationMillis = 450, delayMillis = impactDelayMillis),
        label = "battleHp",
    )
    BattleMeter(
        fraction = animated,
        color = color ?: BattleTheme.healthColor(safe),
        height = height,
        modifier = modifier,
    )
}

/** Mana, out of the hundred the server caps it at. Thinner than health: it is the lesser bar. */
@Composable
fun BattleManaBar(mana: Int, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    BattleMeter(
        fraction = (mana.toFloat() / MAX_MANA).coerceIn(0f, 1f),
        color = BattleTheme.Mana,
        height = height,
        modifier = modifier,
    )
}

/**
 * One bar: a sunken track, a lit fill, a hairline frame.
 *
 * The frame is what does the work. A flat rectangle of colour on a dark panel reads as a coloured
 * rectangle; the same rectangle inside a darker socket reads as a gauge with something in it.
 */
@Composable
private fun BattleMeter(fraction: Float, color: Color, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(BattleTheme.BarShape)
            .background(BattleTheme.StoneSunken)
            .border(1.dp, BattleTheme.Edge, BattleTheme.BarShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .padding(1.5.dp)
                .clip(BattleTheme.BarShape)
                .background(BattleTheme.barBrush(color)),
        )
    }
}

/**
 * An answer, as a stone tile. Same five states the shared card has, read at a glance by colour.
 *
 * An option with an [imagePath] is answered by its picture, and its word stays hidden until the
 * answer is graded -- the word is what the question names. [onPlayAudio] adds a speaker to an
 * option that carries a recording.
 */
@Composable
fun BattleOptionCard(
    text: String,
    state: ChallengeOptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imagePath: String? = null,
    onPlayAudio: (() -> Unit)? = null,
) {
    val border = when (state) {
        ChallengeOptionState.NONE -> BattleTheme.Edge
        ChallengeOptionState.SELECTED -> BattleTheme.Gold
        ChallengeOptionState.CORRECT -> BattleTheme.Venom
        ChallengeOptionState.WRONG -> BattleTheme.Blood
        ChallengeOptionState.REMOVED -> BattleTheme.Edge
    }
    val content = when (state) {
        ChallengeOptionState.NONE -> BattleTheme.Parchment
        ChallengeOptionState.SELECTED -> BattleTheme.Gold
        ChallengeOptionState.CORRECT -> BattleTheme.Venom
        ChallengeOptionState.WRONG -> BattleTheme.Blood
        ChallengeOptionState.REMOVED -> BattleTheme.ParchmentFaint
    }
    val lit = state != ChallengeOptionState.NONE && state != ChallengeOptionState.REMOVED
    val graded = state == ChallengeOptionState.CORRECT || state == ChallengeOptionState.WRONG

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(BattleTheme.TileShape)
            .background(if (lit) BattleTheme.tileLitBrush else BattleTheme.tileBrush)
            .border(2.dp, border, BattleTheme.TileShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .alpha(if (state == ChallengeOptionState.REMOVED) 0.5f else 1f)
            .padding(if (imagePath != null) 6.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val label: @Composable () -> Unit = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                textAlign = TextAlign.Center,
                textDecoration =
                    if (state == ChallengeOptionState.REMOVED) TextDecoration.LineThrough else null,
            )
        }
        when {
            imagePath != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OptionImage(
                    path = imagePath,
                    contentDescription = if (graded) text else null,
                    modifier = Modifier.clip(BattleTheme.TileShape),
                    fallback = label,
                )
                if (graded) label()
            }
            onPlayAudio != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.weight(1f, fill = false)) { label() }
                SpeakerButton(onClick = onPlayAudio, tint = content)
            }
            else -> label()
        }
    }
}

/**
 * The three equipped skills, as rune slots along the foot of the panel.
 *
 * Behaviour is deliberately identical to the shared dock: a tap always reaches the caller even
 * when the skill cannot be cast, because that tap is how a player asks why, and a long press
 * explains what the skill does.
 *
 * [isPvp] narrows what can actually be cast to the three "Trợ Lực Tri Thức" ([PVP_LIFELINE_EFFECTS])
 * without hiding the other equipped skills -- a slot outside that set still shows, greyed with a
 * lock, so a player who built a combat loadout for PvE sees where it went rather than a bar that
 * mysteriously shrank. PvE (`BattleScreen`) leaves this at its default and keeps all eight effects
 * castable, per `duo-game-back/android.md` §6.3.
 */
@Composable
fun BattleSkillDock(
    slots: List<LoadoutSlotDto>,
    mana: Int,
    canCast: Boolean,
    usedCodes: Set<String>,
    onCast: (LoadoutSlotDto) -> Unit,
    modifier: Modifier = Modifier,
    isPvp: Boolean = false,
) {
    var explained by remember { mutableStateOf<LoadoutSlotDto?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (isPvp && slots.isNotEmpty()) {
            Text(
                text = "Trợ Lực Tri Thức",
                style = MaterialTheme.typography.labelMedium,
                color = BattleTheme.ParchmentDim,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                textAlign = TextAlign.Center,
            )
        }

        if (slots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(BattleTheme.TileShape)
                    .background(BattleTheme.StoneSunken)
                    .border(1.dp, BattleTheme.Edge, BattleTheme.TileShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Chưa trang bị kỹ năng nào",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BattleTheme.ParchmentDim,
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            slots.forEach { slot ->
                val lifeline = !isPvp || slot.effect in PVP_LIFELINE_EFFECTS
                val ready = lifeline && canCast && slot.code !in usedCodes && mana >= slot.manaCost
                val spent = slot.code in usedCodes
                BattleSkillSlot(
                    slot = slot,
                    ready = ready,
                    spent = spent,
                    starved = lifeline && !spent && canCast && mana < slot.manaCost,
                    blocked = !lifeline,
                    onClick = { onCast(slot) },
                    onLongClick = { explained = if (explained == slot) null else slot },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        explained?.let { slot ->
            val blocked = isPvp && slot.effect !in PVP_LIFELINE_EFFECTS
            Text(
                text = if (blocked) {
                    "${slot.name}: chỉ dùng được khi luyện tập, không dùng được trong Đấu 1v1."
                } else {
                    "${slot.name}: ${effectDescription(slot.effect)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = BattleTheme.ParchmentDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun BattleSkillSlot(
    slot: LoadoutSlotDto,
    ready: Boolean,
    spent: Boolean,
    starved: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    blocked: Boolean = false,
) {
    val border by animateColorAsState(
        targetValue = if (ready) BattleTheme.Gold else BattleTheme.Edge,
        label = "battleSkillBorder",
    )

    Column(
        modifier = modifier
            .clip(BattleTheme.TileShape)
            .background(if (ready) BattleTheme.tileLitBrush else BattleTheme.tileBrush)
            .border(2.dp, border, BattleTheme.TileShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .alpha(if (ready) 1f else if (blocked) 0.35f else 0.55f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(50))
                .background(BattleTheme.StoneSunken)
                .border(1.dp, if (ready) BattleTheme.EdgeLit else BattleTheme.Edge, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = if (blocked) "🔒" else effectSymbol(slot.effect), fontSize = 18.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = slot.name,
            style = MaterialTheme.typography.labelMedium,
            color = BattleTheme.Parchment,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (blocked) "chỉ PvE" else if (spent) "đã dùng" else "${slot.manaCost} mana",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                blocked -> BattleTheme.ParchmentFaint
                spent -> BattleTheme.ParchmentFaint
                starved -> BattleTheme.Blood
                else -> BattleTheme.Mana
            },
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The word-ordering ("ghép câu") answer surface, in stone.
 *
 * A local counterpart to `ui/components/SentenceBuilder`, not a call into it: that one is drawn by
 * the lesson screen in the app's light theme, and the same rule the bubble above follows applies
 * here -- this screen is the only place a word tile has to be stone.
 *
 * Three bands, in the shape every language app builds a sentence in. The answer sits on ruled
 * lines, laid out behind the words rather than around them: the lines are always there, so an
 * empty answer still reads as somewhere to put words, and the sentence does not change shape as it
 * fills. The word bank is under it, and a word already used keeps its slot there -- an empty
 * outline -- so nothing reflows under a thumb already travelling towards the next word. The check
 * button closes the answer, and only lights up once every tile has been placed: a partial sentence
 * is not a sentence, and the server refuses one rather than marking it wrong.
 */
@Composable
fun BattleSentenceBuilder(
    tiles: List<ChallengeOptionDto>,
    placedIds: List<String>,
    state: ChallengeOptionState,
    onPlace: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tilesById = remember(tiles) { tiles.associateBy(ChallengeOptionDto::id) }
    val accent = when (state) {
        ChallengeOptionState.CORRECT -> BattleTheme.Venom
        ChallengeOptionState.WRONG -> BattleTheme.Blood
        else -> BattleTheme.Edge
    }
    val ink = when (state) {
        ChallengeOptionState.CORRECT -> BattleTheme.Venom
        ChallengeOptionState.WRONG -> BattleTheme.Blood
        else -> BattleTheme.Parchment
    }
    val complete = placedIds.size == tiles.size && tiles.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // The ruled lines, behind the words: drawn at exactly one word's height apiece, so a
            // word laid down lands on a line instead of floating between two.
            Column(modifier = Modifier.fillMaxWidth()) {
                repeat(SENTENCE_LINES) {
                    Box(modifier = Modifier.fillMaxWidth().height(SENTENCE_LINE_HEIGHT)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(accent),
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(SENTENCE_LINE_GAP),
            ) {
                placedIds.forEach { id ->
                    val tile = tilesById[id] ?: return@forEach
                    BattleWordChip(
                        text = tile.text,
                        contentColor = ink,
                        borderColor = accent,
                        enabled = enabled,
                        onClick = { onRemove(id) },
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(SENTENCE_LINE_GAP),
        ) {
            tiles.forEach { tile ->
                val used = tile.id in placedIds
                // The used word keeps its slot -- invisible, but still measured -- so the bank
                // never reflows mid-answer.
                Box {
                    BattleWordChip(
                        text = tile.text,
                        contentColor = BattleTheme.Parchment,
                        borderColor = BattleTheme.Edge,
                        enabled = enabled && !used,
                        onClick = { onPlace(tile.id) },
                        modifier = if (used) Modifier.alpha(0f) else Modifier,
                    )
                    if (used) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(BattleTheme.TileShape)
                                .background(BattleTheme.StoneSunken)
                                .border(1.dp, BattleTheme.Edge, BattleTheme.TileShape),
                        )
                    }
                }
            }
        }

        BattleCheckButton(
            enabled = enabled && complete,
            onClick = onCheck,
        )
    }
}

/** Closes a built sentence. Dark and unlit until every word is down. */
@Composable
private fun BattleCheckButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(BattleTheme.TileShape)
            .background(if (enabled) BattleTheme.tileLitBrush else BattleTheme.tileBrush)
            .border(2.dp, if (enabled) BattleTheme.Gold else BattleTheme.Edge, BattleTheme.TileShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "KIỂM TRA",
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) BattleTheme.Gold else BattleTheme.ParchmentFaint,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BattleWordChip(
    text: String,
    contentColor: Color,
    borderColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(SENTENCE_WORD_HEIGHT)
            .clip(BattleTheme.TileShape)
            .background(BattleTheme.tileBrush)
            .border(2.dp, borderColor, BattleTheme.TileShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}

/** One word tile, and the ruled line it sits on: the line is a word tall plus the gap under it. */
private val SENTENCE_WORD_HEIGHT = 38.dp
private val SENTENCE_LINE_GAP = 6.dp
private val SENTENCE_LINE_HEIGHT = SENTENCE_WORD_HEIGHT + SENTENCE_LINE_GAP

/** How many lines the answer is ruled for. Two holds the longest sentence in the bank. */
private const val SENTENCE_LINES = 2

/** Where the server caps mana; the bar needs a maximum and the session only carries the amount. */
private const val MAX_MANA = 100
