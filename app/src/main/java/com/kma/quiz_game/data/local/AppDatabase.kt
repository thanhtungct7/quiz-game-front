package com.kma.quiz_game.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kma.quiz_game.data.local.dao.CourseContentDao
import com.kma.quiz_game.data.local.entities.CachedCourseEntity
import com.kma.quiz_game.data.local.entities.CachedLessonEntity
import com.kma.quiz_game.data.local.entities.CachedUnitEntity

@Database(
    entities = [
        CachedCourseEntity::class,
        CachedUnitEntity::class,
        CachedLessonEntity::class,
    ],
    // 1 -> 2: course/unit/lesson/challenge/challenge_progress tables dropped when content moved
    // to the backend API. 2 -> 3: cached_* tables added so the learn path renders from disk on
    // launch instead of being refetched every time. No real migration needed (pre-release,
    // dev-only data) -- dropping the cache just costs one refetch on the next launch. 3 -> 4: the
    // local-only user_progress table (hearts/points/pro) dropped along with the hearts system; the
    // destructive fallback clears it, and the course cache refetches once.
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseContentDao(): CourseContentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "duo_game.db",
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
