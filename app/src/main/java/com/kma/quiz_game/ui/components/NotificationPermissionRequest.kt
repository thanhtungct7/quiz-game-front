package com.kma.quiz_game.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kma.quiz_game.data.push.PushRepository
import kotlinx.coroutines.flow.first

/**
 * Asks for permission to post notifications, once, the first time a signed-in learner reaches the
 * app. Not asked at all when the build has no Firebase project, since nothing would be sent.
 *
 * Recorded as asked before the dialog opens rather than after it closes: a dismissed or
 * interrupted dialog should not come back on every launch. A learner who declines turns it on
 * later from system settings.
 */
@Composable
fun NotificationPermissionRequest(pushRepository: PushRepository) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted || !pushRepository.isAvailable || pushRepository.hasAskedForPermission.first()) {
            return@LaunchedEffect
        }
        pushRepository.markPermissionAsked()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
