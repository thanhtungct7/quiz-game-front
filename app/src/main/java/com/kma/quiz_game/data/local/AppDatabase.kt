package com.kma.quiz_game.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.kma.quiz_game.data.local.dao.ChallengeDao
import com.kma.quiz_game.data.local.dao.ChallengeOptionDao
import com.kma.quiz_game.data.local.dao.ChallengeProgressDao
import com.kma.quiz_game.data.local.dao.CourseDao
import com.kma.quiz_game.data.local.dao.LessonDao
import com.kma.quiz_game.data.local.dao.UnitDao
import com.kma.quiz_game.data.local.dao.UserProgressDao
import com.kma.quiz_game.data.local.entities.ChallengeEntity
import com.kma.quiz_game.data.local.entities.ChallengeOptionEntity
import com.kma.quiz_game.data.local.entities.ChallengeProgressEntity
import com.kma.quiz_game.data.local.entities.ChallengeType
import com.kma.quiz_game.data.local.entities.CourseEntity
import com.kma.quiz_game.data.local.entities.LessonEntity
import com.kma.quiz_game.data.local.entities.UnitEntity
import com.kma.quiz_game.data.local.entities.UserProgressEntity
import com.kma.quiz_game.data.local.seed.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromChallengeType(value: ChallengeType): String = value.name

    @TypeConverter
    fun toChallengeType(value: String): ChallengeType = ChallengeType.valueOf(value)
}

@Database(
    entities = [
        CourseEntity::class,
        UnitEntity::class,
        LessonEntity::class,
        ChallengeEntity::class,
        ChallengeOptionEntity::class,
        ChallengeProgressEntity::class,
        UserProgressEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun unitDao(): UnitDao
    abstract fun lessonDao(): LessonDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun challengeOptionDao(): ChallengeOptionDao
    abstract fun challengeProgressDao(): ChallengeProgressDao
    abstract fun userProgressDao(): UserProgressDao

    companion object {
        const val LOCAL_USER_ID = "local_user"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            lateinit var instance: AppDatabase
            instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "duo_game.db",
            ).addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        SeedData.populate(instance)
                    }
                }
            }).build()
            return instance
        }
    }
}
