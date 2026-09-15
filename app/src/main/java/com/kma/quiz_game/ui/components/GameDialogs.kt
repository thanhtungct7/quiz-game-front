package com.kma.quiz_game.ui.components

import androidx.compose.runtime.Composable
import com.kma.quiz_game.R

@Composable
fun PracticeDialog(
    onDismiss: () -> Unit,
    onPractice: () -> Unit,
) {
    BaseMascotDialog(
        mascotRes = R.drawable.mascot,
        title = "Ôn lại bài này?",
        description = "Bạn đã hoàn thành bài này. Đánh lại vẫn giúp bạn nhớ lâu hơn.",
        onDismiss = onDismiss,
        actions = {
            DuoButton(text = "Ôn lại", onClick = onPractice, variant = DuoButtonVariant.Primary)
            DuoButton(text = "Để sau", onClick = onDismiss, variant = DuoButtonVariant.Outline)
        },
    )
}
