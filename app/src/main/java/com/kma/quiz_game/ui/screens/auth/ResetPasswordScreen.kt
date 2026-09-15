package com.kma.quiz_game.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.PasswordField
import com.kma.quiz_game.ui.theme.Rose500

/**
 * Sets a new password from the one-time token in the reset email.
 *
 * [token] arrives from the `quizgame://reset-password?token=...` deep link, or is empty when the
 * user opened the screen by hand -- then the field for it is shown instead.
 */
@Composable
fun ResetPasswordScreen(
    token: String,
    onResetComplete: () -> Unit,
    onRequestNewLink: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val viewModel: ResetPasswordViewModel = viewModel(
        key = token,
        factory = viewModelFactory {
            initializer { ResetPasswordViewModel(app.authRepository, token) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    // The backend revoked every session as part of the reset, so there is nothing to do here
    // but send the user back to sign in with the password they just chose.
    LaunchedEffect(uiState.isReset) {
        if (uiState.isReset) onResetComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        Text(text = "Đặt lại mật khẩu", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Chọn mật khẩu mới. Mọi thiết bị đang đăng nhập sẽ bị đăng xuất.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.needsToken) {
            OutlinedTextField(
                value = uiState.token,
                onValueChange = viewModel::onTokenChange,
                label = { Text("Mã đặt lại") },
                supportingText = { Text("Dán mã trong email đặt lại mật khẩu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        PasswordField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Mật khẩu mới",
            supportingText = { Text("Ít nhất 8 ký tự") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField(
            value = uiState.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = "Nhập lại mật khẩu mới",
            isError = uiState.passwordsMismatch,
            supportingText = {
                if (uiState.passwordsMismatch) Text("Hai mật khẩu chưa khớp")
            },
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = message, color = Rose500, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))
        DuoButton(
            text = if (uiState.isSubmitting) "Đang lưu..." else "Đặt lại mật khẩu",
            onClick = viewModel::submit,
            enabled = uiState.canSubmit,
        )

        Spacer(modifier = Modifier.height(16.dp))
        // A used or expired token is the common failure here, and the only way out is a new one.
        TextButton(onClick = onRequestNewLink) {
            Text("Mã hết hạn? Yêu cầu liên kết mới")
        }
    }
}
