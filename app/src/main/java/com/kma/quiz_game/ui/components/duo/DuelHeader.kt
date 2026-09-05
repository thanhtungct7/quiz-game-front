package com.kma.quiz_game.ui.components.duo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.data.remote.dto.DuoPlayerDto
import com.kma.quiz_game.ui.components.UserAvatar
import com.kma.quiz_game.ui.screens.battle.BattleHpBar
import com.kma.quiz_game.ui.screens.battle.BattleTheme

/**
 * The two duellists, their health, and how much of their deck each still owes.
 *
 * The deck counter is the number that replaces the old round label, and the change of emphasis is
 * the point: there is no shared round to be on any more. "còn 4" against "còn 7" is the whole race
 * at a glance, and it is the only way to see an opponent pulling ahead while you read a question.
 *
 * Drawn in [BattleTheme] rather than the app's light palette, because it sits directly above the
 * arena and a white card over a night-time stage reads as two screens stitched together.
 */
@Composable
fun DuelHeader(
    me: DuoPlayerDto?,
    opponent: DuoPlayerDto?,
    myHp: Int,
    myMaxHp: Int,
    opponentHp: Int,
    opponentMaxHp: Int,
    myDeckRemaining: Int,
    opponentDeckRemaining: Int,
    clockLabel: String,
    modifier: Modifier = Modifier,
    opponentConnected: Boolean = true,
    impactDelayMillis: Int = 0,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Duellist(
            player = me,
            fallbackName = "Bạn",
            hp = myHp,
            maxHp = myMaxHp,
            deckRemaining = myDeckRemaining,
            accent = BattleTheme.Venom,
            impactDelayMillis = impactDelayMillis,
            modifier = Modifier.weight(1f),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            Text(
                text = "VS",
                style = MaterialTheme.typography.labelLarge,
                color = BattleTheme.ParchmentFaint,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = clockLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = BattleTheme.ParchmentDim,
            )
        }

        Duellist(
            player = opponent,
            fallbackName = "Đối thủ",
            hp = opponentHp,
            maxHp = opponentMaxHp,
            deckRemaining = opponentDeckRemaining,
            accent = if (opponentConnected) BattleTheme.Ember else BattleTheme.ParchmentFaint,
            impactDelayMillis = impactDelayMillis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Duellist(
    player: DuoPlayerDto?,
    fallbackName: String,
    hp: Int,
    maxHp: Int,
    deckRemaining: Int,
    accent: Color,
    impactDelayMillis: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, BattleTheme.Edge, CircleShape),
            ) {
                UserAvatar(
                    userId = player?.id.orEmpty(),
                    username = player?.username ?: fallbackName,
                    avatarUrl = player?.avatarUrl,
                    size = 28.dp,
                )
            }
            Text(
                text = player?.username ?: fallbackName,
                style = MaterialTheme.typography.bodyMedium,
                color = BattleTheme.Parchment,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(4.dp))
        BattleHpBar(
            fraction = if (maxHp <= 0) 0f else hp.toFloat() / maxHp,
            height = 10.dp,
            impactDelayMillis = impactDelayMillis,
        )
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$hp",
                style = MaterialTheme.typography.bodyMedium,
                color = BattleTheme.ParchmentDim,
            )
            Text(
                // The race, not the progress: this is what is still owed, and it goes *up* when a
                // question is answered wrongly.
                text = "còn $deckRemaining",
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}
