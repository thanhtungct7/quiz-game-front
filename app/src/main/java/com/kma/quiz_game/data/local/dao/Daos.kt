package com.kma.quiz_game.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.kma.quiz_game.data.local.entities.CachedCourseEntity
import com.kma.quiz_game.data.local.entities.CachedLessonEntity
import com.kma.quiz_game.data.local.entities.CachedUnitEntity
import com.kma.quiz_game.data.local.entities.UserProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE userId = :userId")
    fun observe(userId: String): Flow<UserProgressEntity?>

    @Upsert
    suspend fun upsert(progress: UserProgressEntity)
}

@Dao
interface CourseContentDao {
    @Query("SELECT * FROM cached_course LIMIT 1")
    fun observeCourse(): Flow<CachedCourseEntity?>

    @Query("SELECT * FROM cached_course LIMIT 1")
    suspend fun getCourse(): CachedCourseEntity?

    @Query("SELECT * FROM cached_unit ORDER BY orderIndex")
    fun observeUnits(): Flow<List<CachedUnitEntity>>

    @Query("SELECT * FROM cached_lesson ORDER BY orderIndex")
    fun observeLessons(): Flow<List<CachedLessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CachedCourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<CachedUnitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<CachedLessonEntity>)

    @Query("DELETE FROM cached_course")
    suspend fun clearCourses()

    @Query("DELETE FROM cached_unit")
    suspend fun clearUnits()

    @Query("DELETE FROM cached_lesson")
    suspend fun clearLessons()

    /**
     * Swap in a freshly fetched tree.
     *
     * Wiping first is what drops units and lessons the server no longer serves -- inserting alone
     * would leave them behind. One transaction, so a reader never sees a half-replaced path.
     */
    @Transaction
    suspend fun replaceTree(
        course: CachedCourseEntity,
        units: List<CachedUnitEntity>,
        lessons: List<CachedLessonEntity>,
    ) {
        clearLessons()
        clearUnits()
        clearCourses()
        insertCourse(course)
        insertUnits(units)
        insertLessons(lessons)
    }
}
