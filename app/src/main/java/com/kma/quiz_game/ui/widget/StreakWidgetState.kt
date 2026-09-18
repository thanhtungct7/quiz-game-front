package com.kma.quiz_game.ui.widget

import com.kma.quiz_game.data.local.StreakSnapshot
import com.kma.quiz_game.data.widget.StreakClock
import java.time.temporal.ChronoUnit

/**
 * Which face the widget is wearing. The palette and the mascot follow from this rather than from
 * the copy, so a new line never silently changes the colour of the card.
 */
enum class StreakMood {
    SIGNED_OUT,
    FRESH_START,
    DONE_TODAY,
    MORNING,
    DAYTIME,
    AT_RISK,
    UNVERIFIED,
}

/** Everything the widget draws, with no clock and no storage left in it. */
data class StreakWidgetUi(
    val streak: Int,
    val message: String,
    val mood: StreakMood,
    val level: Int = 0,
    val cefr: String = "",
    val levelFraction: Float = 0f,
) {
    /** A zero streak is not worth a flame -- same call `UserProgressBar` makes in the app. */
    val showStreak: Boolean get() = streak > 0
}

/** From when the evening reminder goes out. Deliberately the backend's own
 * `streak_reminder_hour` default, so the widget turns urgent at the hour the push arrives rather
 * than an hour of its own. */
private const val REMINDER_HOUR = 20
private const val MORNING_ENDS_HOUR = 12

/**
 * What to draw, given what the app last knew and what time it is now.
 *
 * Pure on purpose -- the widget's only real logic is this decision, and it is worth being able to
 * test every branch of it without a launcher, a DataStore or a clock.
 *
 * Two things make it less obvious than a `when` on the streak number:
 *
 * - **A stored streak expires.** The number is whatever the server last said, and it stays in the
 *   store while the days roll on. A streak whose last active day is older than yesterday is
 *   already broken -- the server will restart it at 1 on the next lesson (`streak_after`) -- so
 *   it is shown as gone rather than as a number the app would have to retract.
 * - **The last active day can be unknown.** Only a settlement seen on this device records it, so a
 *   fresh install signed into an account with a long streak has the number and no idea whether
 *   today counts. That is [StreakMood.UNVERIFIED]: the streak is shown, but nobody is told their
 *   streak is at risk on a day they may well have finished already.
 */
fun streakWidgetUi(snapshot: StreakSnapshot, nowMillis: Long): StreakWidgetUi {
    val base = StreakWidgetUi(
        streak = 0,
        message = "",
        mood = StreakMood.SIGNED_OUT,
        level = snapshot.level,
        cefr = snapshot.cefr,
        levelFraction = snapshot.levelFraction,
    )
    if (!snapshot.signedIn) {
        return base.copy(message = "Đăng nhập để học")
    }

    val today = StreakClock.today(nowMillis)
    val lastActive = StreakClock.parseOrNull(snapshot.lastActiveDate)
    val daysSince = lastActive?.let { ChronoUnit.DAYS.between(it, today) }

    // A streak the store cannot vouch for any more. Includes a clock pushed backwards, where
    // `daysSince` is negative: an unknown gap is not evidence of a live streak.
    val expired = daysSince != null && (daysSince > 1 || daysSince < 0)
    if (snapshot.dayStreak <= 0 || expired) {
        return base.copy(mood = StreakMood.FRESH_START, message = "Bắt đầu chuỗi hôm nay!")
    }

    val streak = snapshot.dayStreak
    return when {
        daysSince == null ->
            base.copy(streak = streak, mood = StreakMood.UNVERIFIED, message = "Vào học tiếp nhé!")

        daysSince == 0L ->
            base.copy(streak = streak, mood = StreakMood.DONE_TODAY, message = "Hẹn gặp lại nhé!")

        StreakClock.hourOfDay(nowMillis) < MORNING_ENDS_HOUR ->
            base.copy(streak = streak, mood = StreakMood.MORNING, message = "Học sớm nhé?")

        StreakClock.hourOfDay(nowMillis) < REMINDER_HOUR ->
            base.copy(streak = streak, mood = StreakMood.DAYTIME, message = "Giữ chuỗi $streak ngày nhé!")

        else ->
            base.copy(streak = streak, mood = StreakMood.AT_RISK, message = "Sắp mất chuỗi rồi!")
    }
}
