package com.kma.quiz_game.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.kma.quiz_game.data.remote.toAbsoluteMediaUrl
import com.kma.quiz_game.ui.components.duo.InitialsAvatar

/**
 * A player's avatar: their picture when they have one, their initial when they don't.
 *
 * [InitialsAvatar] does double duty as the placeholder and the error state, so a slow or
 * failed load degrades into the same circle the user would have had anyway rather than a gap.
 */
@Composable
fun UserAvatar(
    userId: String,
    username: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    highlighted: Boolean = false,
) {
    val initials = @Composable {
        InitialsAvatar(
            userId = userId,
            username = username,
            size = size,
            highlighted = highlighted,
        )
    }

    if (avatarUrl.isNullOrBlank()) {
        initials()
        return
    }

    SubcomposeAsyncImage(
        model = avatarUrl.toAbsoluteMediaUrl(),
        contentDescription = "Ảnh đại diện",
        contentScale = ContentScale.Crop,
        loading = { initials() },
        error = { initials() },
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    )
}
