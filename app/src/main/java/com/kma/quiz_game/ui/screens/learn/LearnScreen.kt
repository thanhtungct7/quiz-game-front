package com.kma.quiz_game.ui.screens.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.ui.AppViewModelFactory
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.LessonNodeStatus
import com.kma.quiz_game.ui.components.LessonPath
import com.kma.quiz_game.ui.components.PracticeDialog
import com.kma.quiz_game.ui.components.UserProgressBar
import com.kma.quiz_game.ui.rememberAppViewModelFactory

@Composable
fun LearnScreen(
    onLessonClick: (String) -> Unit,
    factory: AppViewModelFactory = rememberAppViewModelFactory(),
    viewModel: LearnViewModel = viewModel(factory = factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    var practiceLessonId by remember { mutableStateOf<String?>(null) }

    // Re-runs when the screen re-enters composition on the way back from a lesson, refreshing the
    // completed/active markers. The path itself is served from cache and revalidated with an ETag.
    LaunchedEffect(Unit) { viewModel.sync() }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Nothing cached and the refresh failed -- the only case where there is no path to draw.
    if (uiState.units.isEmpty()) {
        EmptyPath(message = uiState.errorMessage, onRetry = viewModel::sync)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                UserProgressBar(
                    points = uiState.points,
                    hearts = uiState.hearts,
                    isPro = uiState.isPro,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(uiState.units) { unit ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    UnitBanner(title = unit.title, description = unit.description)
                    LessonPath(
                        lessons = unit.lessons,
                        onLessonClick = { lessonId ->
                            val lesson = unit.lessons.first { it.id == lessonId }
                            when (lesson.status) {
                                LessonNodeStatus.ACTIVE -> onLessonClick(lessonId)
                                LessonNodeStatus.COMPLETE -> practiceLessonId = lessonId
                                LessonNodeStatus.LOCKED -> Unit
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 24.dp))
                }
            }
        }
    }

    practiceLessonId?.let { lessonId ->
        PracticeDialog(
            onDismiss = { practiceLessonId = null },
            onPractice = {
                practiceLessonId = null
                onLessonClick(lessonId)
            },
        )
    }
}

@Composable
private fun EmptyPath(message: String?, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = message ?: "Chưa có bài học nào.",
                style = MaterialTheme.typography.bodyLarge,
            )
            DuoButton(text = "Thử lại", onClick = onRetry)
        }
    }
}

@Composable
private fun UnitBanner(title: String, description: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
            Text(text = description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
