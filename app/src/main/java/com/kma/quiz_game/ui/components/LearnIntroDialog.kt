package com.kma.quiz_game.ui.components

import androidx.compose.runtime.Composable
import com.kma.quiz_game.R

/**
 * The one-off explanation of the strip above the learn path.
 *
 * It exists because nothing else on the screen says what ⚡, 🔥 or A1 mean, and all three are
 * spent or earned by the learner's own choices -- a player who does not know that a lesson refills
 * energy will queue duo matches until they run out and read that as the app breaking.
 *
 * Deliberately one modal rather than a tour: the strip is four chips, and a multi-step overlay
 * would cost more attention than the thing it explains. It is shown once per install (see
 * `SettingsStore.hasSeenLearnIntro`) and dismissing it is the only way out, so it can never
 * stand between a returning learner and their first lesson.
 */
@Composable
fun LearnIntroDialog(onDismiss: () -> Unit) {
    BaseMascotDialog(
        mascotRes = R.drawable.mascot,
        title = "Ba thứ trên đầu màn hình",
        description = "⚡ Lượt chơi — mỗi trận Đấu tốn 1 lượt. Lượt tự hồi 1 điểm mỗi 30 phút, " +
            "nhưng học xong một bài được thẳng 2 lượt.\n\n" +
            "🔥 Chuỗi ngày — học ít nhất một bài mỗi ngày thì chuỗi tăng. Chuỗi càng dài, bạn " +
            "vào trận với càng nhiều máu.\n\n" +
            "A1 và Lv — bậc năng lực theo chuẩn CEFR và cấp độ của bạn. Cấp lên bằng EXP, còn " +
            "bậc thì phải thi đỗ bài kiểm tra cuối chặng mới được công nhận.",
        onDismiss = onDismiss,
        actions = {
            DuoButton(text = "Bắt đầu học", onClick = onDismiss)
        },
    )
}
