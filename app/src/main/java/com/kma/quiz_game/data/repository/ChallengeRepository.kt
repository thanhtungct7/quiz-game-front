package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.local.dao.ChallengeDao
import com.kma.quiz_game.data.local.dao.ChallengeOptionDao
import com.kma.quiz_game.data.local.dao.ChallengeProgressDao
import com.kma.quiz_game.data.local.entities.ChallengeEntity
import com.kma.quiz_game.data.local.entities.ChallengeOptionEntity
import com.kma.quiz_game.data.local.entities.ChallengeProgressEntity
import kotlinx.coroutines.flow.first

data class ChallengeWithOptions(val challenge: ChallengeEntity, val options: List<ChallengeOptionEntity>)

class ChallengeRepository(
    private val challengeDao: ChallengeDao,
    private val optionDao: ChallengeOptionDao,
    private val progressDao: ChallengeProgressDao,
) {
    suspend fun loadChallenges(lessonId: Long): List<ChallengeWithOptions> {
        val challenges = challengeDao.getByLesson(lessonId).first()
        return challenges.map { challenge ->
            ChallengeWithOptions(challenge, optionDao.getByChallenge(challenge.id).first())
        }
    }

    suspend fun wasAlreadyCompleted(userId: String, challengeId: Long): Boolean {
        return progressDao.getForChallenge(userId, challengeId)?.completed == true
    }

    suspend fun markCompleted(userId: String, challengeId: Long) {
        progressDao.upsert(ChallengeProgressEntity(userId, challengeId, completed = true))
    }
}
