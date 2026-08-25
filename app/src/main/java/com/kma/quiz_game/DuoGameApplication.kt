package com.kma.quiz_game

import android.app.Application
import com.kma.quiz_game.data.local.AppDatabase
import com.kma.quiz_game.data.repository.ChallengeProgressRepository
import com.kma.quiz_game.data.repository.ChallengeRepository
import com.kma.quiz_game.data.repository.LearnRepository
import com.kma.quiz_game.data.repository.UserProgressRepository

class DuoGameApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val learnRepository: LearnRepository by lazy {
        LearnRepository(database.courseDao(), database.unitDao(), database.lessonDao(), database.challengeDao())
    }

    val challengeProgressRepository: ChallengeProgressRepository by lazy {
        ChallengeProgressRepository(database.challengeProgressDao())
    }

    val challengeRepository: ChallengeRepository by lazy {
        ChallengeRepository(database.challengeDao(), database.challengeOptionDao(), database.challengeProgressDao())
    }

    val userProgressRepository: UserProgressRepository by lazy {
        UserProgressRepository(database.userProgressDao(), AppDatabase.LOCAL_USER_ID)
    }
}
