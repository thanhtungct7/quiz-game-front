package com.kma.quiz_game.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kma.quiz_game.data.local.entities.UserProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE userId = :userId")
    fun observe(userId: String): Flow<UserProgressEntity?>

    @Upsert
    suspend fun upsert(progress: UserProgressEntity)
}
