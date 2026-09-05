package com.kma.quiz_game.ui.components.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.ShapeXl
import com.kma.quiz_game.ui.theme.Sky500

/**
 * The reward blocks a finished fight pays out, shared by both result screens.
 *
 * A duo match and a lesson battle settle through the same game layer -- the same experience curve,
 * the same gold ledger, the same chest roll, the same daily streak -- so they must *look* the
 * same too. Two screens drawing "+45 kinh nghiệm" in two different shapes would suggest two
 * different economies to a player who only ever sees one at a time.
 *
 * Every block takes primitives rather than a DTO: the two modes carry the same numbers in
 * differently-named payloads, and passing them apart here is cheaper than a shared wire type that
 * neither protocol actually has.
 */
@Composable
fun RewardCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeXl)
            .background(Neutral050)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun RewardRow(label: String, value: String, color: Color = Neutral700) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = Neutral500)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Experience, with the level bar underneath it.
 *
 * The bar is what makes a payout mean something: "+45" is a number, "+45, and the bar is nearly
 * full" is a reason to play the next match. A level-up is called out separately because it is the
 * moment a skill tier opens.
 *
 * Quitting a match is the one payout that goes negative, and it is floored at the current level --
 * so the bar can stall, but it never walks a level back.
 */
@Composable
fun ExpReward(
    delta: Int,
    levelBefore: Int,
    levelAfter: Int,
    leveledUp: Boolean,
    levelFraction: Float? = null,
) {
    RewardRow(
        label = "Kinh nghiệm",
        value = when {
            delta > 0 -> "+$delta"
            delta < 0 -> "$delta"
            else -> "0"
        },
        color = when {
            delta > 0 -> Green500
            delta < 0 -> com.kma.quiz_game.ui.theme.Rose500
            else -> Neutral500
        },
    )
    if (leveledUp) {
        RewardRow("Lên cấp", "$levelBefore → $levelAfter", Sky500)
    }
    if (levelFraction != null) {
        val animated by animateFloatAsState(
            targetValue = levelFraction.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 700),
            label = "level",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Neutral100),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Sky500),
            )
        }
    }
}

@Composable
fun GoldReward(delta: Int) {
    RewardRow(
        label = "Vàng",
        value = if (delta > 0) "+$delta" else "0",
        color = if (delta > 0) Orange400 else Neutral500,
    )
}

/**
 * The chest, drawn only when one dropped.
 *
 * The rarity is the whole content of the reward -- equipment moves the same three numbers a class
 * moves, and a skin moves none -- so it is the border colour rather than a word buried in a row.
 */
@Composable
fun LootReward(name: String, rarity: String) {
    val color = rarityColor(rarity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(width = 2.dp, color = color, shape = RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "🎁", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = Neutral700,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = rarityLabel(rarity),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
        }
    }
}

@Composable
fun StreakReward(dayStreak: Int, extended: Boolean) {
    RewardRow(
        label = if (extended) "Chuỗi ngày (+1)" else "Chuỗi ngày",
        value = "$dayStreak",
        color = if (extended) Orange400 else Neutral500,
    )
}

/**
 * The season ladder, which moves separately from the all-time rating and can promote.
 *
 * A soft reset at the start of a season carries only 70% of the previous rating over, so a player
 * whose number went down without losing needs to see that this is the *season* row, not their
 * rating.
 */
@Composable
fun SeasonReward(
    seasonCode: String,
    ratingBefore: Int,
    ratingAfter: Int,
    tierAfter: String,
    promoted: Boolean,
) {
    RewardRow(
        label = "Mùa $seasonCode",
        value = "$ratingBefore → $ratingAfter",
        color = Neutral700,
    )
    if (promoted) {
        val style = tierStyle(tierAfter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(style.color.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = style.symbol, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Thăng hạng ${style.label}!",
                style = MaterialTheme.typography.titleMedium,
                color = style.color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

fun rarityColor(rarity: String): Color = when (rarity.uppercase()) {
    "RARE" -> Sky500
    "EPIC" -> Indigo500
    "LEGENDARY" -> Orange400
    else -> Neutral500
}

fun rarityLabel(rarity: String): String = when (rarity.uppercase()) {
    "RARE" -> "Hiếm"
    "EPIC" -> "Sử thi"
    "LEGENDARY" -> "Huyền thoại"
    "COMMON" -> "Thường"
    else -> rarity
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RewardBlocksPreview() {
    Quiz_gameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RewardCard {
                ExpReward(delta = 75, levelBefore = 4, levelAfter = 5, leveledUp = true, levelFraction = 0.3f)
                GoldReward(delta = 41)
                StreakReward(dayStreak = 6, extended = true)
            }
            RewardCard {
                LootReward(name = "Kiếm Bão Tố", rarity = "EPIC")
                Spacer(Modifier.height(4.dp))
                SeasonReward("S3", 1180, 1214, "GOLD", promoted = true)
            }
        }
    }
}
