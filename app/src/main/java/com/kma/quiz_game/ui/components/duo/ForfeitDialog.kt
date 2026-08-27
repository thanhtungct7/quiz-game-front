package com.kma.quiz_game.ui.components.duo

import androidx.compose.runtime.Composable
import com.kma.quiz_game.R
import com.kma.quiz_game.ui.components.BaseMascotDialog
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant

/**
 * Confirms walking out of a live match.
 *
 * Separate from [com.kma.quiz_game.ui.components.ExitDialog] because the stakes are different:
 * abandoning a lesson costs nothing, while the server scores a forfeited match as a loss and
 * takes the Elo for it regardless of the score at the time.
 */
@Composable
fun ForfeitDialog(
    onDismiss: () -> Unit,
    onConfirmForfeit: () -> Unit,
) {
    BaseMascotDialog(
        mascotRes = R.drawable.mascot_sad,
        title = "Bỏ trận?",
        description = "Rời trận giữa chừng sẽ bị xử thua và trừ điểm xếp hạng, dù bạn đang " +
            "dẫn trước đi nữa.",
        onDismiss = onDismiss,
        actions = {
            DuoButton(text = "Chơi tiếp", onClick = onDismiss, variant = DuoButtonVariant.Primary)
            DuoButton(text = "Bỏ trận", onClick = onConfirmForfeit, variant = DuoButtonVariant.DangerOutline)
        },
    )
}
