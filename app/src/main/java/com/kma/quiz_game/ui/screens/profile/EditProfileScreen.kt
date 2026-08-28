package com.kma.quiz_game.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.ui.AppViewModelFactory
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.theme.Neutral600
import com.kma.quiz_game.ui.theme.Rose500

@Composable
fun EditProfileScreen(
    onDone: () -> Unit,
    factory: AppViewModelFactory = rememberAppViewModelFactory(),
    viewModel: ProfileViewModel = viewModel(factory = factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    // The save lands on this screen but is meant to be read on the previous one, so leaving is
    // driven by the result rather than by the button's onClick.
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Chỉnh sửa hồ sơ", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.draftUsername,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Tên hiển thị") },
            singleLine = true,
            isError = uiState.draftUsername.trim().length > MAX_USERNAME_LENGTH,
            supportingText = {
                Text(
                    text = "${uiState.draftUsername.trim().length}/$MAX_USERNAME_LENGTH",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.draftBio,
            onValueChange = viewModel::onBioChange,
            label = { Text("Tiểu sử") },
            placeholder = { Text("Vài dòng về bạn") },
            minLines = 3,
            maxLines = 6,
            isError = uiState.draftBio.trim().length > MAX_BIO_LENGTH,
            supportingText = {
                Text(
                    text = "${uiState.draftBio.trim().length}/$MAX_BIO_LENGTH",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState.username.isNotEmpty() && uiState.draftUsername.isBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tên hiển thị không được để trống.",
                color = Neutral600,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        uiState.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = message, color = Rose500, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))
        DuoButton(
            text = if (uiState.isSaving) "Đang lưu..." else "Lưu thay đổi",
            onClick = viewModel::save,
            enabled = uiState.canSubmit,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = {
                viewModel.discardDraft()
                onDone()
            },
        ) {
            Text("Huỷ")
        }
    }
}
