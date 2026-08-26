package com.kma.quiz_game.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kma.quiz_game.data.local.dao.UserProgressDao
import com.kma.quiz_game.data.local.entities.UserProgressEntity

@Database(
    entities = [UserProgressEntity::class],
    // Bumped from 1: course/unit/lesson/challenge/challenge_progress tables were dropped when
    // content moved to the backend API. No real migration needed (pre-release, dev-only data).
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProgressDao(): UserProgressDao

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
