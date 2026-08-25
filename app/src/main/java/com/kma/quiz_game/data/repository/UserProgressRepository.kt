package com.kma.quiz_game.data.repository

import com.kma.quiz_game.data.GameConstants
import com.kma.quiz_game.data.local.dao.UserProgressDao
import com.kma.quiz_game.data.local.entities.UserProgressEntity
import kotlinx.coroutines.flow.Flow

sealed interface ReduceHeartResult {
    data object Ok : ReduceHeartResult
    data object AlreadyPracticing : ReduceHeartResult
    data object Subscribed : ReduceHeartResult
    data object OutOfHearts : ReduceHeartResult
}

class UserProgressRepository(
    private val dao: UserProgressDao,
    private val userId: String,
) {
    fun observe(): Flow<UserProgressEntity?> = dao.observe(userId)

    /** @param isPractice true when the challenge being retried was already completed before. */
    suspend fun reduceHeart(current: UserProgressEntity, isPractice: Boolean): ReduceHeartResult {
        if (isPractice) return ReduceHeartResult.AlreadyPracticing
        if (current.isPro) return ReduceHeartResult.Subscribed
        if (current.hearts <= 0) return ReduceHeartResult.OutOfHearts
        dao.upsert(current.copy(hearts = current.hearts - 1))
        return ReduceHeartResult.Ok
    }

    suspend fun addPoints(current: UserProgressEntity, amount: Int = GameConstants.POINTS_PER_CORRECT) {
        dao.upsert(current.copy(points = current.points + amount))
    }

    suspend fun regenHeartFromPractice(current: UserProgressEntity) {
        if (current.hearts < GameConstants.MAX_HEARTS) {
            dao.upsert(current.copy(hearts = current.hearts + 1))
        }
    }

    suspend fun refillHeartsWithPoints(current: UserProgressEntity): Boolean {
        if (current.hearts >= GameConstants.MAX_HEARTS) return false
        if (current.points < GameConstants.POINTS_TO_REFILL) return false
        dao.upsert(
            current.copy(
                hearts = GameConstants.MAX_HEARTS,
                points = current.points - GameConstants.POINTS_TO_REFILL,
            )
        )
        return true
    }
}
