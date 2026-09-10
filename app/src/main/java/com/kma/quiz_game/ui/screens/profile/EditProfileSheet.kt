package com.kma.quiz_game.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.UserAvatar
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Rose500

/**
 * Name, bio and avatar, edited without leaving the profile.
 *
 * A sheet rather than the screen this replaced. Renaming yourself is a ten-second job, and a
 * pushed route made it cost a screen transition each way -- and, worse, took the card the edit is
 * *about* off screen while you typed.
 *
 * The avatar is handled differently from the two text fields on purpose: picking a picture uploads
 * it immediately, while the name and bio are a draft that has to be submitted. The upload has
 * nothing to validate and no other field to be consistent with, and making a player choose a photo
 * and then remember to press save is how avatars get lost.
 *
 * Gender is not here. The account has no such field -- not in `users`, not in the profile schema,
 * not in the wire types -- so the sheet would have nowhere to send it. Adding a picker that quietly
 * dropped the answer would be worse than not asking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
    state: ProfileUiState,
    onUsernameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The save lands here but is meant to be read on the card behind, so closing is driven by the
    // result rather than by the button's onClick -- a failed save keeps the sheet and the text.
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Chỉnh sửa hồ sơ",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center) {
                UserAvatar(
                    userId = state.userId,
                    username = state.username,
                    avatarUrl = state.avatarUrl,
                    size = 96.dp,
                )
                if (state.isUploadingAvatar) {
                    CircularProgressIndicator(modifier = Modifier.size(96.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPickAvatar, enabled = !state.isUploadingAvatar) {
                    Text(if (state.hasAvatar) "Đổi ảnh" else "Thêm ảnh")
                }
                // Only an uploaded avatar can be removed: a Google account's picture comes with
                // the account and is not this app's to delete.
                if (state.hasUploadedAvatar) {
                    TextButton(onClick = onRemoveAvatar, enabled = state.canRemoveAvatar) {
                        Text("Xoá ảnh", color = Rose500)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.draftUsername,
                onValueChange = onUsernameChange,
                label = { Text("Tên hiển thị") },
                singleLine = true,
                isError = state.draftUsername.trim().length > MAX_USERNAME_LENGTH,
                supportingText = {
                    Text("${state.draftUsername.trim().length}/$MAX_USERNAME_LENGTH")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.draftBio,
                onValueChange = onBioChange,
                label = { Text("Tiểu sử") },
                minLines = 3,
                isError = state.draftBio.trim().length > MAX_BIO_LENGTH,
                supportingText = { Text("${state.draftBio.trim().length}/$MAX_BIO_LENGTH") },
                modifier = Modifier.fillMaxWidth(),
            )

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(text = message, color = Rose500, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            DuoButton(
                text = if (state.isSaving) "Đang lưu..." else "Lưu thay đổi",
                onClick = onSubmit,
                enabled = state.canSubmit,
            )

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) { Text("Huỷ", color = Neutral500) }
        }
    }
}
