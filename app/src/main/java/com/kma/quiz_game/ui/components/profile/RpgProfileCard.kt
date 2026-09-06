package com.kma.quiz_game.ui.components.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.data.remote.dto.LearningStatsDto
import com.kma.quiz_game.data.remote.dto.PublicProfileDto
import com.kma.quiz_game.data.remote.dto.PvpStatsDto
import com.kma.quiz_game.ui.components.UserAvatar
import com.kma.quiz_game.ui.components.game.TierBadge
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral600
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/**
 * One player, drawn the same way everywhere they appear.
 *
 * The same composable backs the profile tab and the public modal, which is the point: a player
 * who checks their own card and then their opponent's must not have to read two different
 * layouts to compare the same six numbers.
 *
 * [levelFraction] is the only thing the public card cannot know -- progress through the current
 * level is derived from experience totals, which are private -- so it is a separate parameter
 * rather than a field, and the bar is simply not drawn when it is null.
 *
 * [showAvatar] is off on the profile tab, where the screen already owns a larger avatar with the
 * photo picker attached to it; drawing one here too would be the same face twice.
 */
@Composable
fun RpgProfileCard(
    profile: PublicProfileDto,
    modifier: Modifier = Modifier,
    levelFraction: Float? = null,
    showAvatar: Boolean = true,
    avatarSize: Dp = 88.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Neutral050)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showAvatar) {
            UserAvatar(
                userId = profile.id,
                username = profile.username,
                avatarUrl = profile.avatarUrl,
                size = avatarSize,
            )
            Spacer(Modifier.height(10.dp))
        }

        Text(
            text = profile.username ?: "Người chơi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Reserved for the character module. Nothing is drawn until it exists.
        profile.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Orange400,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CefrBadge(cefr = profile.cefr)
            TierBadge(tier = profile.pvp.tier)
        }

        Spacer(Modifier.height(14.dp))
        LevelBar(level = profile.level, fraction = levelFraction)

        Text(
            text = "TOEIC ước tính ~${profile.toeicEstimate}",
            style = MaterialTheme.typography.bodyMedium,
            color = Neutral500,
            modifier = Modifier.padding(top = 6.dp),
        )

        profile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyMedium,
                color = Neutral600,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        StatRow(
            StatCellData("Cấp độ", "${profile.level}", Sky500),
            StatCellData("Chuỗi ngày", "${profile.dayStreak}", Orange400),
            StatCellData("Kỷ lục chuỗi", "${profile.bestDayStreak}", Orange400),
        )
    }
}

/**
 * The level and how far into it the player is.
 *
 * The bar animates to a new value rather than snapping, because the one moment it is most likely
 * to change on screen is straight after a match -- and a bar that jumps reads as a glitch where a
 * bar that fills reads as a reward.
 */
@Composable
private fun LevelBar(level: Int, fraction: Float?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Lv.$level",
            style = MaterialTheme.typography.titleMedium,
            color = Sky500,
            fontWeight = FontWeight.Bold,
        )
        if (fraction == null) return@Row
        val animated by animateFloatAsState(
            targetValue = fraction.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 600),
            label = "levelFraction",
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Neutral100),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Green500),
            )
        }
    }
}

data class StatCellData(val label: String, val value: String, val color: Color)

/**
 * A row of figures, evenly spread.
 *
 * Public so the profile screen can lay out its own rows with the same cells -- the alternative
 * was a second, slightly different stat cell three files away, which is how two screens start
 * disagreeing about what a number looks like.
 */
@Composable
fun StatRow(vararg cells: StatCellData, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        cells.forEach { cell -> StatCell(cell) }
    }
}

@Composable
private fun StatCell(cell: StatCellData) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = cell.value,
            style = MaterialTheme.typography.titleLarge,
            color = cell.color,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = cell.label,
            style = MaterialTheme.typography.bodyMedium,
            color = Neutral500,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The PvP record.
 *
 * A player who has never finished a match is told so rather than shown a 0% win rate: every
 * number here would be a default, and a default drawn as a measurement is a lie the player has
 * no way to see through.
 */
@Composable
fun PvpStatsBlock(pvp: PvpStatsDto, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeading("Đấu trường", "${pvp.rating} điểm")
        Spacer(Modifier.height(10.dp))
        if (!pvp.hasPlayed) {
            EmptyNote("Chưa đấu trận nào.")
            return@Column
        }
        StatRow(
            StatCellData("Thắng", "${pvp.wins}", Green500),
            StatCellData("Thua", "${pvp.losses}", Rose500),
            StatCellData("Hoà", "${pvp.draws}", Neutral500),
            StatCellData("Tỉ lệ", "${pvp.winRate}%", Sky500),
        )
    }
}

/** The study record. Same empty-state rule as [PvpStatsBlock]. */
@Composable
fun LearningStatsBlock(learning: LearningStatsDto, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeading("Học tập", "")
        Spacer(Modifier.height(10.dp))
        if (!learning.hasAnswered) {
            EmptyNote("Chưa trả lời câu nào.")
            return@Column
        }
        StatRow(
            StatCellData("Câu đã làm", "${learning.challengesAttempted}", Sky500),
            StatCellData("Đã thuộc", "${learning.challengesMastered}", Green500),
            StatCellData("Lượt trả lời", "${learning.totalAttempts}", Neutral500),
            StatCellData("Chính xác", "${learning.accuracy}%", Orange400),
        )
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = Neutral700)
        Spacer(Modifier.weight(1f))
        if (trailing.isNotBlank()) {
            Text(text = trailing, style = MaterialTheme.typography.titleMedium, color = Sky500)
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Neutral500)
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun RpgProfileCardPreview() {
    val card = PublicProfileDto(
        id = "user-1",
        username = "Tùng",
        bio = "Đang cày B2.",
        avatarUrl = null,
        level = 34,
        cefr = "B1",
        toeicEstimate = 620,
        dayStreak = 12,
        bestDayStreak = 40,
        pvp = PvpStatsDto(
            rating = 1420,
            tier = "GOLD",
            matchesPlayed = 30,
            wins = 19,
            losses = 9,
            draws = 2,
            winRate = 63.3,
        ),
        learning = LearningStatsDto(
            challengesAttempted = 420,
            challengesMastered = 355,
            totalAttempts = 610,
            accuracy = 58.2,
        ),
    )
    Quiz_gameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RpgProfileCard(profile = card, levelFraction = 0.45f)
            PvpStatsBlock(card.pvp)
            LearningStatsBlock(card.learning)
        }
    }
}
