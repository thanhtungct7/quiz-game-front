package com.kma.quiz_game.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * One channel per kind of push, so a learner can mute the season news in system settings and
 * keep the streak reminder -- or the other way round. That is the whole of the app's push
 * settings for now.
 */
object NotificationChannels {
    /** Must match `STREAK_CHANNEL`, `SEASON_CHANNEL` and `QUEST_CHANNEL` in the backend's
     * `messages.py`. */
    const val STREAK = "streak"
    const val SEASON = "season"
    const val QUEST = "quest"

    /** Safe on every start: creating a channel that exists only refreshes its name and text. */
    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(STREAK, "Nhắc học mỗi ngày", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Nhắc bạn vào học để giữ chuỗi ngày học." },
                NotificationChannel(SEASON, "Mùa giải", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Báo khi mùa giải xếp hạng mới bắt đầu." },
                NotificationChannel(QUEST, "Nhiệm vụ ngày", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Nhắc buổi tối khi còn nhiệm vụ hoặc rương chưa nhận." },
            ),
        )
    }
}
