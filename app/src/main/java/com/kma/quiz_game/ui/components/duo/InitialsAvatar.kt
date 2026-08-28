package com.kma.quiz_game.ui.components.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Indigo500
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

private val AVATAR_PALETTE = listOf(Green500, Sky500, Orange400, Indigo500, Rose500)

/**
 * A player's avatar as their initial on a colour picked from their id.
 *
 * The fallback half of [com.kma.quiz_game.ui.components.UserAvatar]: what a user without a
 * picture gets, and what is shown while one is loading. Prefer `UserAvatar` at call sites --
 * this is only the right thing directly when there is no avatar URL to be had.
 */
@Composable
fun InitialsAvatar(
    userId: String,
    username: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    highlighted: Boolean = false,
) {
    val label = username?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = AVATAR_PALETTE[(userId.hashCode().mod(AVATAR_PALETTE.size))]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .then(
                if (highlighted) Modifier.border(3.dp, Neutral100, CircleShape) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
