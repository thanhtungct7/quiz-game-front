package com.kma.quiz_game.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kma.quiz_game.R
import com.kma.quiz_game.data.push.PushRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Offers push notifications, once, the first time [ask] is true -- after a won battle, when a
 * reminder to keep the streak going has started to mean something, rather than at sign-in. The
 * reason comes first; the system dialog only opens for a learner who says yes to it.
 *
 * Recorded as asked whichever way the learner answers: the offer should not come back after every
 * lesson. One who declines turns it on later from system settings. Not offered at all when the
 * build has no Firebase project, since nothing would be sent.
 */
@Composable
fun NotificationPermissionRequest(pushRepository: PushRepository, ask: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var offering by remember { mutableStateOf(false) }

    LaunchedEffect(ask) {
        if (!ask) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted || !pushRepository.isAvailable || pushRepository.hasAskedForPermission.first()) {
            return@LaunchedEffect
        }
        offering = true
    }

    if (!offering) return

    val answer: (Boolean) -> Unit = { enable ->
        offering = false
        scope.launch { pushRepository.markPermissionAsked() }
        if (enable) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    BaseMascotDialog(
        mascotRes = R.drawable.mascot,
        title = "Nhắc bạn giữ chuỗi?",
        description = "Bật thông báo để được nhắc lúc 20h vào hôm bạn chưa học, " +
            "và biết ngay khi mùa giải mới bắt đầu.",
        onDismiss = { answer(false) },
        actions = {
            DuoButton(text = "Bật thông báo", onClick = { answer(true) })
            DuoButton(text = "Để sau", onClick = { answer(false) }, variant = DuoButtonVariant.Outline)
        },
    )
}
