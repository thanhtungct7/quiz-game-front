package com.kma.quiz_game.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.ui.screens.conversation.ConversationHistoryViewModel
import com.kma.quiz_game.ui.screens.conversation.ConversationTopicsViewModel
import com.kma.quiz_game.ui.screens.duo.DuoHistoryViewModel
import com.kma.quiz_game.ui.screens.duo.DuoHomeViewModel
import com.kma.quiz_game.ui.screens.duo.DuoMatchViewModel
import com.kma.quiz_game.ui.screens.duo.DuoResultViewModel
import com.kma.quiz_game.ui.screens.game.ClassPickerViewModel
import com.kma.quiz_game.ui.screens.game.InventoryViewModel
import com.kma.quiz_game.ui.screens.game.LoadoutViewModel
import com.kma.quiz_game.ui.screens.game.ShopViewModel
import com.kma.quiz_game.ui.screens.game.SkillTreeViewModel
import com.kma.quiz_game.ui.screens.leaderboard.LeaderboardViewModel
import com.kma.quiz_game.ui.screens.learn.LearnViewModel
import com.kma.quiz_game.ui.screens.profile.ProfileViewModel
import com.kma.quiz_game.ui.screens.quests.DailyQuestsViewModel

/**
 * Builds the ViewModels whose dependencies are all application-scoped singletons.
 *
 * ViewModels that need a route argument (a lesson id, a match id) are not listed here -- those use
 * an inline `viewModelFactory { initializer { ... } }` at their call site, keyed by the argument,
 * so each one gets its own instance.
 */
class AppViewModelFactory(private val app: DuoGameApplication) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when (modelClass) {
            LearnViewModel::class.java -> LearnViewModel(
                app.learnRepository,
                app.battleRepository,
                app.profileRepository,
                app.gameRepository,
                app.settingsStore,
                app.questRepository,
            ) as T

            LeaderboardViewModel::class.java ->
                LeaderboardViewModel(app.duoRepository, app.authRepository) as T

            DuoHomeViewModel::class.java ->
                DuoHomeViewModel(app.duoRepository, app.gameRepository) as T

            DuoMatchViewModel::class.java ->
                DuoMatchViewModel(app.duoRepository, app.gameRepository) as T

            DuoResultViewModel::class.java -> DuoResultViewModel(app.duoRepository) as T

            DuoHistoryViewModel::class.java -> DuoHistoryViewModel(app.duoRepository) as T

            ProfileViewModel::class.java ->
                ProfileViewModel(app.profileRepository, app.gameRepository, app.settingsStore) as T

            ClassPickerViewModel::class.java -> ClassPickerViewModel(app.gameRepository) as T

            SkillTreeViewModel::class.java -> SkillTreeViewModel(app.gameRepository) as T

            LoadoutViewModel::class.java -> LoadoutViewModel(app.gameRepository) as T

            InventoryViewModel::class.java -> InventoryViewModel(app.gameRepository) as T

            ShopViewModel::class.java -> ShopViewModel(app.gameRepository) as T

            DailyQuestsViewModel::class.java -> DailyQuestsViewModel(app.questRepository) as T

            ConversationTopicsViewModel::class.java ->
                ConversationTopicsViewModel(app.conversationRepository) as T

            ConversationHistoryViewModel::class.java ->
                ConversationHistoryViewModel(app.conversationRepository) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
}

@Composable
fun rememberAppViewModelFactory(): AppViewModelFactory {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    return AppViewModelFactory(app)
}
