package com.kma.quiz_game.data.push

/**
 * Where a tapped push notification should land.
 *
 * The backend puts one of these in the message's `route` data field -- see
 * `duo-game-back/app/services/notification/messages.py`. When the system draws the notification
 * (app in the background) FCM hands data fields to the launched activity as intent extras, and
 * [DuoMessagingService] puts the same extra on the notifications it draws itself, so a tap
 * arrives at `MainActivity` the same way either way.
 */
enum class PushRoute(val wireName: String) {
    LEARN("learn"),
    LEADERBOARD("leaderboard"),
    ;

    companion object {
        const val EXTRA_KEY = "route"

        /** Null for a missing or unknown route: a newer server must not crash an older app. */
        fun fromWire(value: String?): PushRoute? = entries.firstOrNull { it.wireName == value }
    }
}
