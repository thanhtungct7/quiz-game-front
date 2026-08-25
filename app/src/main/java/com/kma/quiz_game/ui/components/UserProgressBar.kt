package com.kma.quiz_game.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.R
import com.kma.quiz_game.ui.theme.Orange500
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Rose500

@Composable
fun UserProgressBar(
    points: Int,
    hearts: Int,
    isPro: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Image(painterResource(R.drawable.flag_es), contentDescription = null, modifier = Modifier.size(28.dp))
        }
        StatChip(iconRes = R.drawable.ic_points, tint = Orange500, value = points.toString())
        HeartsChip(hearts = hearts, isPro = isPro)
    }
}

@Composable
private fun StatChip(iconRes: Int, tint: androidx.compose.ui.graphics.Color, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HeartsChip(hearts: Int, isPro: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Image(painterResource(R.drawable.ic_heart), contentDescription = null, modifier = Modifier.size(24.dp))
        if (isPro) {
            Icon(Icons.Filled.AllInclusive, contentDescription = "unlimited", tint = Rose500, modifier = Modifier.size(20.dp))
        } else {
            Text(text = hearts.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserProgressBarPreview() {
    Quiz_gameTheme {
        UserProgressBar(points = 120, hearts = 3, isPro = false)
    }
}
