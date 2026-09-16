package com.kma.quiz_game.ui.screens.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.data.remote.dto.BattleEndReasonDto
import com.kma.quiz_game.data.remote.dto.BattleFinishedDto
import com.kma.quiz_game.data.remote.dto.BattleStatusDto
import com.kma.quiz_game.data.remote.dto.LessonProgressStatusDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.NotificationPermissionRequest
import com.kma.quiz_game.ui.components.battle.monsterArt
import com.kma.quiz_game.ui.components.game.ExpReward
import com.kma.quiz_game.ui.components.game.GoldReward
import com.kma.quiz_game.ui.components.game.LootReward
import com.kma.quiz_game.ui.components.game.RewardCard
import com.kma.quiz_game.ui.components.game.RewardRow
import com.kma.quiz_game.ui.components.game.StreakReward
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.ShapeXl
import com.kma.quiz_game.ui.theme.Sky500

/**
 * How the battle ended, and what it was worth.
 *
 * Two things are here that a duo result never shows, because they are the point of the feature:
 * where the lesson now stands, and whether this was the first clear -- the run that pays full
 * price, once and forever.
 */
@Composable
fun BattleResultScreen(
    lessonId: String,
    onBackToPath: () -> Unit,
    onFightAgain: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val viewModel: BattleResultViewModel = viewModel(
        factory = viewModelFactory { initializer { BattleResultViewModel(app.battleRepository) } },
    )
    val result by viewModel.result.collectAsState()
    val monsterName by viewModel.monsterName.collectAsState()
    val artCode by viewModel.monsterArtCode.collectAsState()

    val finished = result
    if (finished == null) {
        // Nothing to show (a cold start on this route) -- send the player back to the path.
        DuoButton(text = "Về lộ trình", onClick = onBackToPath, modifier = modifier.padding(24.dp))
        return
    }

    NotificationPermissionRequest(
        pushRepository = app.pushRepository,
        ask = finished.outcome == BattleStatusDto.WON,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = monsterArt(artCode), fontSize = 72.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = finished.outcome.title(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = finished.outcome.color(),
        )
        finished.endReason.note(monsterName)?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodyLarge,
                color = Neutral500,
                textAlign = TextAlign.Center,
            )
        }

        if (finished.firstClear) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Lần đầu hạ cửa này — thưởng đầy đủ!",
                style = MaterialTheme.typography.titleMedium,
                color = Orange400,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))
        StatsCard(finished)

        Spacer(Modifier.height(16.dp))
        BattleRewards(finished)

        Spacer(Modifier.height(16.dp))
        LessonCard(finished)

        Spacer(Modifier.height(24.dp))
        DuoButton(
            text = "Về lộ trình",
            onClick = {
                viewModel.acknowledge()
                onBackToPath()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        DuoButton(
            text = if (finished.outcome == BattleStatusDto.WON) "Đánh lại" else "Thử lại",
            onClick = {
                viewModel.acknowledge()
                onFightAgain(lessonId)
            },
            variant = DuoButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatsCard(finished: BattleFinishedDto) {
    RewardCard {
        RewardRow("Câu đúng", "${finished.correctCount}/${finished.answersGiven}")
        RewardRow("Chuỗi dài nhất", "x${finished.bestCombo}")
        RewardRow("Thời gian", formatDuration(finished.durationMs))
        // Zero is the new perfect run: the monster never got a wind-up off.
        RewardRow("Đòn quái đánh trúng", "${finished.monsterSwings}")
        RewardRow("Máu còn lại", "${finished.yourHpLeft}")
        RewardRow("Máu quái còn lại", "${finished.monsterHpLeft}")
    }
}

/**
 * The payout, drawn with the blocks the duo result screen uses.
 *
 * Both modes settle through the same game layer -- one experience curve, one gold ledger, one
 * chest table, one daily streak -- so a player who plays both must not be shown two economies.
 */
@Composable
private fun BattleRewards(finished: BattleFinishedDto) {
    RewardCard {
        ExpReward(
            delta = finished.exp.delta,
            levelBefore = finished.exp.levelBefore,
            levelAfter = finished.exp.levelAfter,
            leveledUp = finished.exp.leveledUp,
        )
        GoldReward(delta = finished.gold.delta)
        finished.streak?.let { streak ->
            StreakReward(dayStreak = streak.dayStreak, extended = streak.extended)
        }
        finished.loot?.let { loot ->
            LootReward(name = loot.name, rarity = loot.rarity)
        }
    }
}

/** The whole point of the feature: the fight moved the learn path, not a parallel score. */
@Composable
private fun LessonCard(finished: BattleFinishedDto) {
    RewardCard {
        RewardRow(
            label = "Bài học",
            value = finished.lessonProgress.status.label(),
            color = if (finished.lessonProgress.status == LessonProgressStatusDto.COMPLETED) {
                Green500
            } else {
                Neutral700
            },
        )
        RewardRow(
            "Đã thuộc",
            "${finished.lessonProgress.correct}/${finished.lessonProgress.total}",
        )
    }
}

private fun BattleStatusDto.title(): String = when (this) {
    BattleStatusDto.WON -> "Hạ gục!"
    BattleStatusDto.LOST -> "Thua rồi"
    BattleStatusDto.ABANDONED -> "Đã bỏ trận"
    BattleStatusDto.IN_PROGRESS -> "Đang đánh"
}

private fun BattleStatusDto.color(): androidx.compose.ui.graphics.Color = when (this) {
    BattleStatusDto.WON -> Green500
    BattleStatusDto.LOST -> Rose500
    else -> Neutral500
}

private fun BattleEndReasonDto.note(monsterName: String): String? = when (this) {
    BattleEndReasonDto.MONSTER_DOWN -> if (monsterName.isBlank()) null else "$monsterName đã gục."
    BattleEndReasonDto.PLAYER_DOWN -> "Bạn hết máu. Thua không mất gì cả — đánh lại thôi."
    BattleEndReasonDto.OUT_OF_QUESTIONS -> "Hết câu hỏi mà quái vẫn còn đứng."
    BattleEndReasonDto.LEFT -> "Bạn đã rời trận. Câu đã trả lời vẫn được ghi."
    BattleEndReasonDto.CANCELLED -> "Trận bị huỷ."
}

private fun LessonProgressStatusDto.label(): String = when (this) {
    LessonProgressStatusDto.COMPLETED -> "Hoàn thành"
    LessonProgressStatusDto.IN_PROGRESS -> "Đang học"
    LessonProgressStatusDto.NOT_STARTED -> "Chưa bắt đầu"
}

/** `1:04` -- a fight is a minute or two, so minutes and seconds is the whole range that matters. */
private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
