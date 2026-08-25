package com.kma.quiz_game.data.local.seed

import com.kma.quiz_game.data.GameConstants
import com.kma.quiz_game.data.local.AppDatabase
import com.kma.quiz_game.data.local.entities.ChallengeEntity
import com.kma.quiz_game.data.local.entities.ChallengeOptionEntity
import com.kma.quiz_game.data.local.entities.ChallengeType
import com.kma.quiz_game.data.local.entities.CourseEntity
import com.kma.quiz_game.data.local.entities.LessonEntity
import com.kma.quiz_game.data.local.entities.UnitEntity
import com.kma.quiz_game.data.local.entities.UserProgressEntity

/**
 * Small, hand-written sample content so the Learn/Lesson screens have something real to
 * render and play through (mirrors the shape of duolingo-clone's db seed script).
 */
object SeedData {

    suspend fun populate(db: AppDatabase) {
        val courseId = db.courseDao().insertAll(
            listOf(CourseEntity(title = "Spanish", imageSrc = "es"))
        ).first()

        val unitDefs = listOf(
            "Unit 1" to "Learn the basics of Spanish",
            "Unit 2" to "Talk about people and things",
            "Unit 3" to "Order food and drinks",
        )

        unitDefs.forEachIndexed { unitIndex, (title, description) ->
            val unitId = db.unitDao().insertAll(
                listOf(
                    UnitEntity(
                        title = title,
                        description = description,
                        courseId = courseId,
                        order = unitIndex + 1,
                    )
                )
            ).first()

            repeat(3) { lessonIndex ->
                val lessonId = db.lessonDao().insertAll(
                    listOf(
                        LessonEntity(
                            title = "Lesson ${lessonIndex + 1}",
                            unitId = unitId,
                            order = lessonIndex + 1,
                        )
                    )
                ).first()

                seedChallenges(db, lessonId, unitIndex, lessonIndex)
            }
        }

        db.userProgressDao().upsert(
            UserProgressEntity(
                userId = AppDatabase.LOCAL_USER_ID,
                userName = "You",
                activeCourseId = courseId,
                hearts = GameConstants.MAX_HEARTS,
                points = 0,
            )
        )
    }

    private suspend fun seedChallenges(
        db: AppDatabase,
        lessonId: Long,
        unitIndex: Int,
        lessonIndex: Int,
    ) {
        val vocab = VOCAB[unitIndex % VOCAB.size]

        val challengeDefs = listOf(
            Triple(ChallengeType.SELECT, "Which one of these is \"${vocab[0].second}\"?", vocab),
            Triple(ChallengeType.ASSIST, "\"${vocab[1].second}\"", vocab),
            Triple(ChallengeType.SELECT, "Which one of these is \"${vocab[2].second}\"?", vocab.shuffled()),
            Triple(ChallengeType.ASSIST, "\"${vocab[3].second}\"", vocab.shuffled()),
        )

        challengeDefs.forEachIndexed { index, (type, question, pool) ->
            val correctWord = when {
                question.contains(vocab[0].second) -> vocab[0]
                question.contains(vocab[1].second) -> vocab[1]
                question.contains(vocab[2].second) -> vocab[2]
                else -> vocab[3]
            }

            val challengeId = db.challengeDao().insertAll(
                listOf(
                    ChallengeEntity(
                        lessonId = lessonId,
                        type = type,
                        question = question,
                        order = index + 1,
                    )
                )
            ).first()

            val options = pool.take(4).map { (english, spanish) ->
                ChallengeOptionEntity(
                    challengeId = challengeId,
                    text = if (type == ChallengeType.ASSIST) english else spanish,
                    correct = spanish == correctWord.second,
                )
            }
            db.challengeOptionDao().insertAll(options)
        }
    }

    /** unit index -> list of (english, spanish) word pairs */
    private val VOCAB: List<List<Pair<String, String>>> = listOf(
        listOf("the man" to "el hombre", "the woman" to "la mujer", "the boy" to "el niño", "the girl" to "la niña"),
        listOf("water" to "el agua", "bread" to "el pan", "milk" to "la leche", "apple" to "la manzana"),
        listOf("coffee" to "el café", "the table" to "la mesa", "the check" to "la cuenta", "more" to "más"),
    )
}
