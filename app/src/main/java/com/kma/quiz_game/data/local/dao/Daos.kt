package com.kma.quiz_game.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.kma.quiz_game.data.local.entities.ChallengeEntity
import com.kma.quiz_game.data.local.entities.ChallengeOptionEntity
import com.kma.quiz_game.data.local.entities.ChallengeProgressEntity
import com.kma.quiz_game.data.local.entities.CourseEntity
import com.kma.quiz_game.data.local.entities.LessonEntity
import com.kma.quiz_game.data.local.entities.UnitEntity
import com.kma.quiz_game.data.local.entities.UserProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY id")
    fun getAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id")
    fun getById(id: Long): Flow<CourseEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(courses: List<CourseEntity>): List<Long>
}

@Dao
interface UnitDao {
    @Query("SELECT * FROM units WHERE courseId = :courseId ORDER BY `order`")
    fun getByCourse(courseId: Long): Flow<List<UnitEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(units: List<UnitEntity>): List<Long>
}

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE unitId = :unitId ORDER BY `order`")
    fun getByUnit(unitId: Long): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    fun getById(id: Long): Flow<LessonEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(lessons: List<LessonEntity>): List<Long>
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges WHERE lessonId = :lessonId ORDER BY `order`")
    fun getByLesson(lessonId: Long): Flow<List<ChallengeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(challenges: List<ChallengeEntity>): List<Long>
}

@Dao
interface ChallengeOptionDao {
    @Query("SELECT * FROM challenge_options WHERE challengeId = :challengeId")
    fun getByChallenge(challengeId: Long): Flow<List<ChallengeOptionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(options: List<ChallengeOptionEntity>): List<Long>
}

@Dao
interface ChallengeProgressDao {
    @Query(
        """
        SELECT challengeId FROM challenge_progress
        WHERE userId = :userId AND completed = 1
        """
    )
    fun getCompletedChallengeIds(userId: String): Flow<List<Long>>

    @Query(
        """
        SELECT * FROM challenge_progress
        WHERE userId = :userId AND challengeId = :challengeId LIMIT 1
        """
    )
    suspend fun getForChallenge(userId: String, challengeId: Long): ChallengeProgressEntity?

    @Upsert
    suspend fun upsert(progress: ChallengeProgressEntity)
}

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE userId = :userId")
    fun observe(userId: String): Flow<UserProgressEntity?>

    @Upsert
    suspend fun upsert(progress: UserProgressEntity)
}
