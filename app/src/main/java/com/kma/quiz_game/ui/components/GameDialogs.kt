package com.kma.quiz_game.ui.components

import androidx.compose.runtime.Composable
import com.kma.quiz_game.R

@Composable
fun ExitDialog(
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit,
) {
    BaseMascotDialog(
        mascotRes = R.drawable.mascot_sad,
        title = "Wait, don't go!",
        description = "You're about to leave the lesson. Are you sure?",
        onDismiss = onDismiss,
        actions = {
            DuoButton(text = "Keep learning", onClick = onDismiss, variant = DuoButtonVariant.Primary)
            DuoButton(text = "End session", onClick = onConfirmExit, variant = DuoButtonVariant.Outline)
        },
    )
}

@Composable
fun PracticeDialog(
    onDismiss: () -> Unit,
    onPractice: () -> Unit,
) {
    BaseMascotDialog(
        mascotRes = R.drawable.mascot,
        title = "Ready to practice?",
        description = "This lesson is already complete. Fighting it again is still good practice.",
        onDismiss = onDismiss,
        actions = {
            DuoButton(text = "Practice lesson", onClick = onPractice, variant = DuoButtonVariant.Primary)
            DuoButton(text = "Not now", onClick = onDismiss, variant = DuoButtonVariant.Outline)
        },
    )
}
