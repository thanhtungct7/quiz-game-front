package com.kma.quiz_game.ui.screens.lesson

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.kma.quiz_game.R

/** Plays the correct/incorrect SFX whenever [answerStatus] settles on an answered state. */
@Composable
fun AnswerSoundEffect(answerStatus: AnswerStatus) {
    val context = LocalContext.current
    LaunchedEffect(answerStatus) {
        val soundRes = when (answerStatus) {
            AnswerStatus.CORRECT -> R.raw.correct
            AnswerStatus.WRONG -> R.raw.incorrect
            AnswerStatus.NONE -> null
        } ?: return@LaunchedEffect
        playOneShot(context, soundRes)
    }
}

@Composable
fun FinishSoundEffect(isComplete: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(isComplete) {
        if (isComplete) playOneShot(context, R.raw.finish)
    }
}

private fun playOneShot(context: Context, soundRes: Int) {
    val player = MediaPlayer.create(context, soundRes) ?: return
    player.setOnCompletionListener { it.release() }
    player.start()
}
