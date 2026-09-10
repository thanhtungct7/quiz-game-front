package com.kma.quiz_game.ui.components.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.data.remote.dto.AchievementCategory
import com.kma.quiz_game.data.remote.dto.AchievementDto
import com.kma.quiz_game.data.remote.dto.AchievementProgressDto
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Neutral300
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/**
 * One earned badge, as the identity card's overview shows it.
 *
 * The artwork is chosen from `icon_code` rather than from `code`, which is what lets the server
 * add an achievement without the app shipping first: a new code the catalog grew lands on the
 * fallback star and still reads as a badge. Same rule the arena follows for a monster's `art_code`.
 */
@Composable
fun AchievementBadge(
    achievement: AchievementDto,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val accent = categoryColor(achievement.category)
    Column(
        modifier = modifier.width(size + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f))
                .border(width = 2.dp, color = accent.copy(alpha = 0.55f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = achievementIcon(achievement.iconCode),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(size / 2),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = achievement.name,
            style = MaterialTheme.typography.labelSmall,
            color = Neutral700,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A badge on the shelf, earned or not.
 *
 * An unearned badge is drawn greyed with its progress bar rather than hidden, because the bar is
 * the only thing on this screen that says what to do next. Hidden achievements never reach the
 * client at all until they are unlocked, so nothing here spoils one.
 */
@Composable
fun AchievementProgressRow(item: AchievementProgressDto, modifier: Modifier = Modifier) {
    val accent = if (item.unlocked) categoryColor(item.category) else Neutral300

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (item.unlocked) accent.copy(alpha = 0.08f) else Neutral050)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = achievementIcon(item.iconCode),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (item.unlocked) Neutral700 else Neutral500,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = Neutral500,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.unlocked) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Neutral100),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(item.fraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(categoryColor(item.category).copy(alpha = 0.7f)),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${item.current}/${item.threshold}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Neutral500,
                    )
                }
            }
        }
    }
}

/** The ten `icon_code`s in `services/game/catalog.py`, and a star for anything it grows later. */
fun achievementIcon(iconCode: String): ImageVector = when (iconCode.uppercase()) {
    "LEVEL" -> Icons.Filled.MilitaryTech
    "CROWN" -> Icons.Filled.WorkspacePremium
    "FLAME" -> Icons.Filled.LocalFireDepartment
    "BOOK" -> Icons.Filled.MenuBook
    "TARGET" -> Icons.Filled.GpsFixed
    "PATH" -> Icons.Filled.Route
    "SWORD" -> Icons.Filled.SportsMartialArts
    "TROPHY" -> Icons.Filled.EmojiEvents
    "BOLT" -> Icons.Filled.Bolt
    "SKULL" -> Icons.Filled.Pets
    else -> Icons.Filled.Star
}

/** The four categories of `models/game/achievement.py`, colour-matched to the screens they came
 * from: study green, ladder red, monsters purple, levelling blue. */
fun categoryColor(category: String): Color = when (category.uppercase()) {
    AchievementCategory.PROGRESSION -> Sky500
    AchievementCategory.LEARNING -> Green500
    AchievementCategory.PVP -> Rose500
    AchievementCategory.PVE -> Indigo500
    else -> Neutral500
}

fun categoryLabel(category: String): String = when (category.uppercase()) {
    AchievementCategory.PROGRESSION -> "Thăng tiến"
    AchievementCategory.LEARNING -> "Học tập"
    AchievementCategory.PVP -> "Đấu trường"
    AchievementCategory.PVE -> "Săn quái"
    else -> category
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AchievementPreview() {
    Quiz_gameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AchievementBadge(AchievementDto("STREAK_7", "Trọn một tuần", "", "LEARNING", "FLAME"))
                AchievementBadge(AchievementDto("PVP_WINS_10", "Tay đấu", "", "PVP", "SWORD"))
                AchievementBadge(AchievementDto("LEVEL_20", "Lão luyện", "", "PROGRESSION", "LEVEL"))
            }
            AchievementProgressRow(
                AchievementProgressDto(
                    code = "LEVEL_50",
                    name = "Bậc thầy",
                    description = "Đạt cấp 50.",
                    category = "PROGRESSION",
                    iconCode = "CROWN",
                    threshold = 50,
                    current = 34,
                ),
            )
        }
    }
}
