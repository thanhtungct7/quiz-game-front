package com.kma.quiz_game.ui.screens.lesson

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.R
import com.kma.quiz_game.ui.components.DuoButton

@Composable
fun LessonResultScreen(
    earnedPoints: Int,
    hearts: Int,
    isPro: Boolean,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.mascot), contentDescription = null, modifier = Modifier.size(140.dp))
        Text(text = "Lesson complete!", style = MaterialTheme.typography.headlineLarge)
        Row(
            modifier = Modifier.padding(top = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ResultStat(iconRes = R.drawable.ic_points, label = "Total XP", value = "+$earnedPoints")
            ResultStat(iconRes = R.drawable.ic_heart, label = "Hearts left", value = if (isPro) "∞" else hearts.toString())
        }
        DuoButton(
            text = "Continue",
            onClick = onFinish,
            modifier = Modifier.padding(top = 40.dp),
        )
    }
}

@Composable
private fun ResultStat(iconRes: Int, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(40.dp))
        Text(text = value, style = MaterialTheme.typography.titleLarge)
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
