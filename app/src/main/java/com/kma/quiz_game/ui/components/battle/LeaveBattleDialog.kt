package com.kma.quiz_game.ui.components.battle

import androidx.compose.runtime.Composable
import com.kma.quiz_game.R
import com.kma.quiz_game.ui.components.BaseMascotDialog
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant

/**
 * Confirms walking out of a lesson battle.
 *
 * Separate from [com.kma.quiz_game.ui.components.duo.ForfeitDialog] because the stakes are the
 * opposite: quitting a duo match is scored as a loss and costs rating, while quitting a battle
 * costs only the battle. The wording says so plainly -- the answers already given keep the
 * progress they earned, and a player who thinks otherwise will grind a lesson they had already
 * finished.
 */
@Composable
fun LeaveBattleDialog(
    onDismiss: () -> Unit,
    onConfirmLeave: () -> Unit,
) {
    BaseMascotDialog(
        mascotRes = R.drawable.mascot_sad,
        title = "Bỏ trận?",
        description = "Bạn sẽ mất tiến trình của trận này và không nhận thưởng. Những câu đã trả " +
            "lời vẫn được ghi vào bài học.",
        onDismiss = onDismiss,
        actions = {
            DuoButton(text = "Đánh tiếp", onClick = onDismiss, variant = DuoButtonVariant.Primary)
            DuoButton(text = "Bỏ trận", onClick = onConfirmLeave, variant = DuoButtonVariant.DangerOutline)
        },
    )
}
