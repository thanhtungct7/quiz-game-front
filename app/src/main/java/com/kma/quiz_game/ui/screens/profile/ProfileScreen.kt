package com.kma.quiz_game.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.ui.AppViewModelFactory
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.data.AvatarImage
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.UserAvatar
import com.kma.quiz_game.ui.components.profile.LearningStatsBlock
import com.kma.quiz_game.ui.components.profile.PvpStatsBlock
import com.kma.quiz_game.ui.components.profile.RpgProfileCard
import com.kma.quiz_game.ui.components.profile.StatCellData
import com.kma.quiz_game.ui.components.profile.StatRow
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Sky500
import com.kma.quiz_game.ui.theme.Neutral600
import com.kma.quiz_game.ui.theme.Rose500
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onLogout: () -> Unit,
    factory: AppViewModelFactory = rememberAppViewModelFactory(),
    viewModel: ProfileViewModel = viewModel(factory = factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // The system photo picker: it hands back a single image without the app ever holding
    // READ_MEDIA_IMAGES, so there is no runtime permission to ask for.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching { AvatarImage.readScaledJpeg(context, uri) }
                .onSuccess { viewModel.uploadAvatar(it, AvatarImage.MIME_TYPE) }
                .onFailure { viewModel.onAvatarReadFailed() }
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            UserAvatar(
                userId = uiState.userId,
                username = uiState.username,
                avatarUrl = uiState.avatarUrl,
                size = 112.dp,
            )
            if (uiState.isUploadingAvatar) {
                CircularProgressIndicator(modifier = Modifier.size(112.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            enabled = !uiState.isUploadingAvatar,
        ) {
            Text(if (uiState.hasAvatar) "Đổi ảnh đại diện" else "Thêm ảnh đại diện")
        }
        if (uiState.hasUploadedAvatar) {
            TextButton(onClick = viewModel::removeAvatar, enabled = uiState.canRemoveAvatar) {
                Text("Xoá ảnh", color = Rose500)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.username.ifBlank { "Chưa đặt tên hiển thị" },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = uiState.email,
            style = MaterialTheme.typography.bodyMedium,
            color = Neutral600,
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = uiState.bio.ifBlank { "Chưa có tiểu sử." },
            style = MaterialTheme.typography.bodyLarge,
            color = if (uiState.bio.isBlank()) Neutral600 else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // The RPG half of the profile. Absent until the aggregated card arrives, and absent for
        // good if it fails -- the identity above is what makes this screen usable, and the card
        // is an enrichment rather than a prerequisite.
        uiState.card?.let { card ->
            Spacer(modifier = Modifier.height(24.dp))
            RpgProfileCard(
                profile = card.asPublic(),
                levelFraction = card.levelFraction,
                // The avatar is already at the top of this screen, at 112dp with the picker
                // attached to it. Drawing it again inside the card would be the same face twice.
                showAvatar = false,
            )

            Spacer(modifier = Modifier.height(20.dp))
            StatRow(
                StatCellData("Vàng", "${card.gold}", Orange400),
                StatCellData("Kinh nghiệm", "${card.totalExp}", Sky500),
                StatCellData("Còn tới Lv.${card.level + 1}", "${card.expToNextLevel}", Green500),
            )

            card.nextCefr?.let { band ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Đạt $band ở cấp ${card.nextCefrAtLevel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neutral600,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            PvpStatsBlock(card.pvp)

            Spacer(modifier = Modifier.height(24.dp))
            LearningStatsBlock(card.learning)
        }

        uiState.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = message, color = Rose500, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))
        DuoButton(text = "Chỉnh sửa hồ sơ", onClick = onEditProfile)

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        DuoButton(
            text = "Đăng xuất",
            onClick = onLogout,
            variant = DuoButtonVariant.DangerOutline,
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
            },
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}
