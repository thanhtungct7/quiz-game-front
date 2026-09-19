package com.kma.quiz_game.ui.screens.duo

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.R
import com.kma.quiz_game.data.remote.dto.DuoMatchEndReason
import com.kma.quiz_game.data.remote.dto.MatchFinishedDto
import com.kma.quiz_game.data.remote.dto.MatchOutcome
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.duo.RatingDeltaBadge
import com.kma.quiz_game.ui.components.game.ExpReward
import com.kma.quiz_game.ui.components.game.GoldReward
import com.kma.quiz_game.ui.components.game.HpBar
import com.kma.quiz_game.ui.components.game.LootReward
import com.kma.quiz_game.ui.components.game.QuestToast
import com.kma.quiz_game.ui.components.game.QuestsCompletedCard
import com.kma.quiz_game.ui.components.game.RewardCard
import com.kma.quiz_game.ui.components.game.RewardRow
import com.kma.quiz_game.ui.components.game.SeasonReward
import com.kma.quiz_game.ui.components.game.StreakReward
import com.kma.quiz_game.ui.components.game.TierBadge
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/**
 * How the match ended, and everything it paid.
 *
 * The order is deliberate and matches how a match is actually settled: what happened (outcome and
 * health), then how it was scored, then what it moved -- rating, experience, gold, chest, season,
 * streak, energy. Rating first among the rewards because it is the only one an opponent also felt.
 */
@Composable
fun DuoResultScreen(
    onPlayAgain: () -> Unit,
    onBackToLobby: () -> Unit,
    onOpenQuests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DuoResultViewModel = viewModel(factory = rememberAppViewModelFactory())
    val result by viewModel.result.collectAsState()

    val finished = result
    if (finished == null) {
        // Nothing to show (a cold start on this route) -- send the player back to the lobby.
        DuoButton(text = "Về sảnh", onClick = onBackToLobby, modifier = modifier.padding(24.dp))
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Image(
                painter = painterResource(
                    if (finished.result == MatchOutcome.LOSE) R.drawable.mascot_sad else R.drawable.mascot,
                ),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = finished.title(),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = finished.result.color(),
            )
            finished.endReason.note()?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Neutral500,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))
            HealthBoard(finished)

            Spacer(Modifier.height(16.dp))
            ScoreBoard(finished)

            Spacer(Modifier.height(16.dp))
            RatingPanel(finished)

            Spacer(Modifier.height(16.dp))
            RewardCard {
                finished.exp?.let { exp ->
                    ExpReward(
                        delta = exp.delta,
                        levelBefore = exp.levelBefore,
                        levelAfter = exp.levelAfter,
                        leveledUp = exp.leveledUp,
                    )
                }
                finished.gold?.let { gold -> GoldReward(delta = gold.delta) }
                finished.streak?.let { streak ->
                    StreakReward(dayStreak = streak.dayStreak, extended = streak.extended)
                }
                // Energy is spent by the match, so what is left is what the lobby will offer next.
                finished.energyLeft?.let { left -> RewardRow("Lượt còn lại", "$left", Orange400) }
            }

            finished.season?.let { season ->
                Spacer(Modifier.height(12.dp))
                RewardCard {
                    SeasonReward(
                        seasonCode = season.seasonCode,
                        ratingBefore = season.ratingBefore,
                        ratingAfter = season.ratingAfter,
                        tierAfter = season.tierAfter,
                        promoted = season.promoted,
                    )
                }
            }

            finished.loot?.let { loot ->
                Spacer(Modifier.height(12.dp))
                LootReward(name = loot.name, rarity = loot.rarity)
            }

            if (finished.questsCompleted.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                QuestsCompletedCard(finished.questsCompleted, onOpenQuests = onOpenQuests)
            }

            Spacer(Modifier.height(24.dp))
            DuoButton(
                text = "Đấu tiếp",
                onClick = {
                    viewModel.playAgain()
                    onPlayAgain()
                },
                variant = DuoButtonVariant.Primary,
            )
            Spacer(Modifier.height(12.dp))
            DuoButton(
                text = "Về sảnh",
                onClick = {
                    viewModel.acknowledge()
                    onBackToLobby()
                },
                variant = DuoButtonVariant.Outline,
            )
        }
        // Over the content, so the moment is seen before the learner scrolls to the card.
        QuestToast(quests = finished.questsCompleted, modifier = Modifier.align(Alignment.TopCenter))
    }
}

/**
 * Health left on both sides.
 *
 * First among the boards because health is what the match was decided on: a knockout ends it
 * outright, and every tie breaks on health before it ever looks at points.
 */
@Composable
private fun HealthBoard(finished: MatchFinishedDto) {
    val total = maxOf(finished.yourHpLeft, finished.opponentHpLeft, 1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Neutral050)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HealthRow("Máu của bạn", finished.yourHpLeft, finished.yourHpLeft.toFloat() / total, Green500)
        HealthRow("Máu đối thủ", finished.opponentHpLeft, finished.opponentHpLeft.toFloat() / total, Sky500)
    }
}

@Composable
private fun HealthRow(label: String, hp: Int, fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = Neutral500)
            Text(
                text = "$hp",
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        HpBar(fraction = fraction, color = color)
    }
}

@Composable
private fun ScoreBoard(finished: MatchFinishedDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Neutral050)
            .padding(20.dp),
    ) {
        ScoreRow("Điểm", "${finished.yourScore}", "${finished.opponentScore}")
        Spacer(Modifier.height(8.dp))
        ScoreRow(
            // How much of the deck each side actually cleared. A player only clears a question by
            // getting it right, so this is the race as it finished.
            label = "Đã xong",
            mine = "${finished.yourCorrect}/${finished.deckSize}",
            theirs = "${finished.opponentCorrect}/${finished.deckSize}",
        )
        Spacer(Modifier.height(8.dp))
        ScoreRow("Thời lượng", "${finished.durationSeconds}s", "")
    }
}

@Composable
private fun ScoreRow(label: String, mine: String, theirs: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = Neutral500, modifier = Modifier.weight(1f))
        Text(text = mine, style = MaterialTheme.typography.titleMedium, color = Green500, fontWeight = FontWeight.Bold)
        if (theirs.isNotEmpty()) {
            Text(text = "  vs  ", style = MaterialTheme.typography.bodyMedium, color = Neutral500)
            Text(text = theirs, style = MaterialTheme.typography.titleMedium, color = Sky500, fontWeight = FontWeight.Bold)
        }
    }
}

/** Elo is zero-sum at K=32, so the number here is exactly what the opponent lost or gained. */
@Composable
private fun RatingPanel(finished: MatchFinishedDto) {
    val animatedRating by animateIntAsState(targetValue = finished.rating.after, label = "rating")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Neutral050)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Điểm xếp hạng", style = MaterialTheme.typography.bodyLarge, color = Neutral500)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "${finished.rating.before}",
                style = MaterialTheme.typography.titleLarge,
                color = Neutral500,
            )
            Text(text = "→", style = MaterialTheme.typography.titleLarge, color = Neutral500)
            Text(
                text = "$animatedRating",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Neutral700,
            )
            RatingDeltaBadge(finished.rating.delta)
        }
        finished.season?.let { season ->
            Spacer(Modifier.height(8.dp))
            TierBadge(tier = season.tierAfter)
        }
    }
}

/** A knockout is a different kind of win and deserves its own word. */
private fun MatchFinishedDto.title(): String = when {
    result == MatchOutcome.WIN && endReason == DuoMatchEndReason.KNOCKOUT -> "Hạ gục!"
    result == MatchOutcome.LOSE && endReason == DuoMatchEndReason.KNOCKOUT -> "Bị hạ gục"
    result == MatchOutcome.WIN -> "Chiến thắng!"
    result == MatchOutcome.LOSE -> "Thua rồi"
    else -> "Hoà"
}

private fun MatchOutcome.color() = when (this) {
    MatchOutcome.WIN -> Green500
    MatchOutcome.LOSE -> Rose500
    MatchOutcome.DRAW -> Neutral700
}

/**
 * How the match ended, in a sentence.
 *
 * COMPLETED belongs to the lock-step engine and can only appear on an old row in history; nothing
 * finishes that way now.
 */
private fun DuoMatchEndReason.note(): String? = when (this) {
    DuoMatchEndReason.COMPLETED -> null
    DuoMatchEndReason.OPPONENT_LEFT -> "Đối thủ đã bỏ trận."
    DuoMatchEndReason.OPPONENT_TIMEOUT -> "Đối thủ mất kết nối quá lâu."
    DuoMatchEndReason.CANCELLED -> "Trận bị huỷ."
    // The blow itself was already shown in the arena as it landed.
    DuoMatchEndReason.KNOCKOUT -> "Một bên đã hết máu — trận kết thúc ngay tại đòn đó."
    DuoMatchEndReason.DECK_CLEARED -> "Có người trả lời đúng hết cả bộ câu hỏi trước."
    DuoMatchEndReason.TIME_UP -> "Hết giờ — ai còn nhiều máu hơn thì thắng."
}
