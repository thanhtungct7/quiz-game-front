package com.kma.quiz_game.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.theme.Rose500

@Composable
fun ForgotPasswordScreen(
    onNavigateToLogin: () -> Unit,
    onEnterCodeManually: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val viewModel: ForgotPasswordViewModel = viewModel(
        factory = viewModelFactory { initializer { ForgotPasswordViewModel(app.authRepository) } },
    )
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        Text(text = "Quên mật khẩu", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isSent) {
            SentConfirmation(
                email = uiState.email.trim(),
                onResend = viewModel::submit,
                onEditEmail = viewModel::editEmail,
                onEnterCodeManually = onEnterCodeManually,
                isSubmitting = uiState.isSubmitting,
            )
        } else {
            Text(
                text = "Nhập email của tài khoản. Chúng tôi sẽ gửi cho bạn một liên kết " +
                    "để đặt lại mật khẩu.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = message, color = Rose500, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))
            DuoButton(
                text = if (uiState.isSubmitting) "Đang gửi..." else "Gửi liên kết đặt lại",
                onClick = viewModel::submit,
                enabled = uiState.canSubmit,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onNavigateToLogin) {
            Text("Quay lại đăng nhập")
        }
    }
}

@Composable
private fun SentConfirmation(
    email: String,
    isSubmitting: Boolean,
    onResend: () -> Unit,
    onEditEmail: () -> Unit,
    onEnterCodeManually: () -> Unit,
) {
    // Deliberately does not confirm that [email] has an account -- saying so would turn this
    // screen into the account-enumeration oracle the backend refuses to be.
    Text(
        text = "Nếu $email có tài khoản, một liên kết đặt lại mật khẩu đã được gửi tới hộp thư " +
            "đó. Liên kết hết hạn sau 15 phút.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(24.dp))
    DuoButton(
        text = if (isSubmitting) "Đang gửi lại..." else "Gửi lại",
        variant = DuoButtonVariant.Outline,
        onClick = onResend,
        enabled = !isSubmitting,
    )
    Spacer(modifier = Modifier.height(12.dp))
    TextButton(onClick = onEditEmail) {
        Text("Nhập email khác")
    }
    // The link only opens the app if the email is read on this phone; on a desktop the token
    // has to make the trip by hand.
    TextButton(onClick = onEnterCodeManually) {
        Text("Đã có mã đặt lại? Nhập tại đây")
    }
}
