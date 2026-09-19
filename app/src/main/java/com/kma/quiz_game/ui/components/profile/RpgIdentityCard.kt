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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.data.remote.dto.CombatStatsDto
import com.kma.quiz_game.data.remote.dto.LearningStatsDto
import com.kma.quiz_game.data.remote.dto.PublicProfileDto
import com.kma.quiz_game.data.remote.dto.PvpStatsDto
import com.kma.quiz_game.ui.components.UserAvatar
import com.kma.quiz_game.ui.components.game.TierBadge
import com.kma.quiz_game.ui.components.game.tierStyle
import com.kma.quiz_game.ui.screens.profile.CombatStat
import com.kma.quiz_game.ui.theme.Neutral400
import com.kma.quiz_game.ui.theme.Neutral800
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Sky500

/**
 * The card at the top of the profile: who the player is, and what they walk into a fight with.
 *
 * A dark card in an otherwise light app, and that is the point -- it is the one surface here that
 * is a *thing the player owns* rather than a page about them, and the arena it borrows its
 * character and its palette from is dark too.
 *
 * Everything the character module has not shipped yet is absent rather than faked. There is no
 * placeholder title, no empty frame slot and no locked-skin teaser: the server answers null for
 * those fields today, and a card that drew a slot for each one would be advertising features that
 * do not exist. What *is* earned decides the look instead -- the rank tier colours the avatar ring
 * and the CEFR band lights the stage.
 */
@Composable
fun RpgIdentityCard(
    profile: PublicProfileDto,
    modifier: Modifier = Modifier,
    /** Tapping a bar. Absent on another player's card: a breakdown is self-only. */
    onOpenBreakdown: ((CombatStat) -> Unit)? = null,
    // Down from 190dp: with the three stat bars collapsed to one line of chips, the card is no
    // longer allowed to eat half the profile -- the academic figures below it come first.
    portraitHeight: Dp = 140.dp,
) {
    val glow = heroGlowFor(profile.skinCode, profile.cefr)
    val ring = tierStyle(profile.pvp.tier).color

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CARD_SHAPE)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Neutral800,
                        // A hint of the player's own colour in the lower half, so two players'
                        // cards are not the same slab of grey.
                        lerpToward(Neutral800, glow, 0.22f),
                    ),
                ),
            )
            .border(width = 1.dp, color = glow.copy(alpha = 0.35f), shape = CARD_SHAPE)
            .padding(18.dp),
    ) {
        IdentityHeader(profile = profile, ring = ring)

        Spacer(Modifier.height(6.dp))
        HeroPortrait(
            modifier = Modifier
                .fillMaxWidth()
                .height(portraitHeight),
            glow = glow,
            skinCode = profile.skinCode,
            classCode = profile.classCode,
        )

        Spacer(Modifier.height(6.dp))
        CombatStatSummary(
            combat = profile.combat,
            // Another player's card shows the same three numbers and simply does not react to a
            // tap -- the itemised build behind them is the account holder's business.
            onOpenBreakdown = { stat -> onOpenBreakdown?.invoke(stat) },
        )
        if (profile.combat.mana > 0) {
            Spacer(Modifier.height(10.dp))
            ManaChip(mana = profile.combat.mana)
        }
    }
}

/** Avatar in its ring, name, title, and the two badges that say how far the player has come. */
@Composable
private fun IdentityHeader(profile: PublicProfileDto, ring: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarInFrame(profile = profile, ring = ring)
        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.username ?: "Người chơi",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Absent until the character module can equip one. See the class comment.
            profile.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Orange400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelBadge(level = profile.level)
                CefrBadge(cefr = profile.cefr, compact = true)
                TierBadge(tier = profile.pvp.tier, compact = true)
            }
        }
    }
}

/**
 * The avatar, ringed in the player's rank colour.
 *
 * This is the "frame" slot the spec asks for, filled with the only thing the account actually
 * carries today. When the character module ships real frames it replaces the ring here and
 * nothing else on the card has to move.
 */
@Composable
private fun AvatarInFrame(profile: PublicProfileDto, ring: Color) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(AVATAR_SIZE + FRAME_WIDTH * 2)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(ring, ring.copy(alpha = 0.25f), ring, ring.copy(alpha = 0.25f), ring),
                    ),
                ),
        )
        UserAvatar(
            userId = profile.id,
            username = profile.username,
            avatarUrl = profile.avatarUrl,
            size = AVATAR_SIZE,
        )
    }
}

@Composable
private fun LevelBadge(level: Int) {
    Text(
        text = "Lv.$level",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Sky500)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * What lights the stage the character stands on.
 *
 * The worn skin decides it, and the CEFR band decides it when none is worn -- the band is the one
 * thing on the card the player has unambiguously earned by studying, which is what this app is
 * for. Reusing [cefrStyle] for that fallback means the glow, the band badge and the level bar
 * cannot drift into three different opinions about what B1 looks like.
 *
 * There is one `hero.png`, so a skin cannot change the sprite -- the glow is the whole of what it
 * buys. Every code the shop sells therefore needs a colour here: a missing one falls through to
 * the CEFR branch, and the player has paid gold for a character that looks exactly the same.
 */
fun heroGlowFor(skinCode: String?, cefr: String): Color = when (skinCode?.uppercase()) {
    null, "" -> cefrStyle(cefr).color
    "SKIN_ROOKIE" -> Neutral400
    "SKIN_SCHOLAR" -> Sky500
    "SKIN_OFFICE" -> Color(0xFF1F9E86)
    "SKIN_NIGHT" -> Color(0xFF7C5CFF)
    "SKIN_ORATOR" -> Color(0xFFE2568D)
    "SKIN_LAUREATE" -> Orange400
    else -> cefrStyle(cefr).color
}

/** Straight-line blend, so the card's gradient can carry a tint of the glow without a whole
 * colour system for one background. */
private fun lerpToward(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = from.alpha,
)

private val CARD_SHAPE = RoundedCornerShape(24.dp)
private val AVATAR_SIZE = 56.dp
private val FRAME_WIDTH = 3.dp

@Preview(showBackground = true, heightDp = 520)
@Composable
private fun RpgIdentityCardPreview() {
    val card = PublicProfileDto(
        id = "user-1",
        username = "Tùng",
        bio = "Đang cày B2.",
        level = 34,
        cefr = "B1",
        toeicEstimate = 620,
        dayStreak = 12,
        bestDayStreak = 40,
        pvp = PvpStatsDto(rating = 1420, tier = "GOLD", matchesPlayed = 30, wins = 19, losses = 9, draws = 2, winRate = 63.3),
        learning = LearningStatsDto(420, 355, 610, 58.2),
        combat = CombatStatsDto(hp = 118, atk = 27, defence = 6, mana = 14, damagePermille = 1350),
    )
    Quiz_gameTheme {
        Box(Modifier.padding(16.dp)) { RpgIdentityCard(profile = card) }
    }
}
