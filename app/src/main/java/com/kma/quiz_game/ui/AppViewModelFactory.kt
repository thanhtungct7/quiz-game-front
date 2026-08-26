package com.kma.quiz_game.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.ui.screens.learn.LearnViewModel

class AppViewModelFactory(private val app: DuoGameApplication) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when (modelClass) {
            LearnViewModel::class.java -> LearnViewModel(
                app.learnRepository,
                app.userProgressRepository,
                app.authRepository,
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
}

@Composable
fun rememberAppViewModelFactory(): AppViewModelFactory {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    return AppViewModelFactory(app)
}
