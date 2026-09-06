package com.kma.quiz_game.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.DuoGameApplication
import com.kma.quiz_game.data.remote.dto.PublicProfileDto
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.profile.LearningStatsBlock
import com.kma.quiz_game.ui.components.profile.PvpStatsBlock
import com.kma.quiz_game.ui.components.profile.RpgProfileCard
import com.kma.quiz_game.ui.theme.Neutral500

/**
 * Another player's card, opened from a leaderboard row or the PvP lobby.
 *
 * A sheet rather than a screen on purpose: looking someone up is a glance taken in the middle of
 * doing something else, and pushing a route would cost the player their place on the board.
 *
 * [seed] is whatever the caller already had -- a leaderboard row carries the name, avatar, rating
 * and tier -- so the sheet opens with those already drawn and fills the rest in when the request
 * lands. The player sees content immediately, not a spinner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileSheet(
    userId: String,
    onDismiss: () -> Unit,
    seed: PublicProfileDto? = null,
) {
    val app = LocalContext.current.applicationContext as DuoGameApplication
    val viewModel: PublicProfileViewModel = viewModel(
        // Keyed by the player: tapping a second row must not reuse the first one's card.
        key = "public-profile-$userId",
        factory = PublicProfileViewModel.factory(app.profileRepository, userId, seed),
    )
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val card = state.visible

            when {
                state.showsError -> SheetMessage(
                    message = state.errorMessage.orEmpty(),
                    onRetry = viewModel::load,
                )

                card == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                else -> {
                    RpgProfileCard(profile = card, avatarSize = 72.dp)
                    PvpStatsBlock(card.pvp)
                    LearningStatsBlock(card.learning)
                    // The seed carries a rating and a name but no study totals, so while the
                    // full card is still in flight those blocks read as zeroes. Say so.
                    if (state.isLoading) {
                        Text(
                            text = "Đang tải đầy đủ hồ sơ…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Neutral500,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetMessage(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Không tải được hồ sơ",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Neutral500,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        DuoButton(text = "Thử lại", onClick = onRetry, variant = DuoButtonVariant.Primary)
    }
}
