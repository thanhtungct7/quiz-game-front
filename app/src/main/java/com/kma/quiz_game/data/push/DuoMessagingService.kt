package com.kma.quiz_game.data.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.MainActivity
import com.kma.quiz_game.R
import kotlinx.coroutines.launch

class DuoMessagingService : FirebaseMessagingService() {

    /**
     * Firebase rotated this install's token. Signed in, the new one replaces the old on the
     * server; signed out there is no account to register it for, and the next sign-in's
     * [PushRepository.syncToken] picks it up.
     */
    override fun onNewToken(token: String) {
        val app = application as DuoGameApplication
        app.appScope.launch { app.pushRepository.registerIfSignedIn(token) }
    }

    /**
     * Only reached while the app is in the foreground: in the background the system draws a
     * notification message by itself and never calls this. Drawn here the same way, so a push is
     * not swallowed just because the app happened to be open.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val id = (message.messageId ?: message.sentTime.toString()).hashCode()
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data[PushRoute.EXTRA_KEY]?.let { putExtra(PushRoute.EXTRA_KEY, it) }
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            id,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val drawn = NotificationCompat.Builder(this, notification.channelId ?: NotificationChannels.STREAK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(this).notify(id, drawn)
    }
}
