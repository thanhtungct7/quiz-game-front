package com.kma.quiz_game.data.remote.dto

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Every PvE frame, in both directions, is the envelope `{"type": "<name>", "data": {...}}`.
 *
 * Decoding reads `type` first and then picks the payload serializer by hand, exactly as
 * [decodeDuoEvent] does: the discriminator lives outside `data`, and a `when` over plain strings is
 * far easier to follow when a payload turns out not to match the server.
 */
sealed interface BattleEvent {

    /** Socket-level, synthesised by [com.kma.quiz_game.data.remote.BattleSocket]. */
    data object SocketOpen : BattleEvent

    /** [willRetry] false means the socket is gone for good and the UI must say so. */
    data class SocketClosed(val willRetry: Boolean) : BattleEvent

    data class Connected(val data: BattleConnectedDto) : BattleEvent
    data class Started(val data: BattleStartedDto) : BattleEvent

    /**
     * A snapshot, plus the device clock reading of when it landed.
     *
     * The pair is the whole clock-synchronisation mechanism. `data.t` is the server's clock at the
     * moment it built the frame, so `data.t - receivedAtMs` is this device's offset from the
     * server -- recomputed ten times a second, and needing no ping, no handshake and no trust in
     * the device's wall clock.
     */
    data class StateTick(val data: BattleStateTickDto, val receivedAtMs: Long) : BattleEvent

    data class QuestionPush(val data: BattleQuestionPushDto) : BattleEvent
    data class AnswerResult(val data: BattleAnswerResultDto) : BattleEvent
    data class MonsterSwing(val data: MonsterSwingDto) : BattleEvent
    data class SkillUsed(val data: BattleSkillUsedDto) : BattleEvent
    data class Finished(val data: BattleFinishedDto) : BattleEvent
    data class Pong(val data: BattlePongDto, val receivedAtMs: Long) : BattleEvent
    data class Failed(val data: BattleErrorDto) : BattleEvent
}

/** Frame names the client may send. */
object BattleClientEvent {
    const val BATTLE_START = "battle.start"
    const val ANSWER_SUBMIT = "answer.submit"
    const val SKILL_USE = "skill.use"
    const val BATTLE_LEAVE = "battle.leave"
    const val PING = "ping"
}

private const val TAG = "BattleEvent"

/**
 * Turns one raw text frame into a [BattleEvent], or null for anything unrecognised or malformed.
 *
 * A bad frame is logged and dropped rather than thrown: the server does the same with our malformed
 * input, and tearing down a live battle over one unparseable frame would be worse than missing it.
 */
fun Json.decodeBattleEvent(raw: String, receivedAtMs: Long = 0L): BattleEvent? {
    val envelope = runCatching { parseToJsonElement(raw).jsonObject }.getOrElse {
        Log.w(TAG, "Dropping unparseable frame: ${raw.take(200)}")
        return null
    }
    val type = envelope["type"]?.jsonPrimitive?.content ?: return null
    val data = envelope["data"] as? JsonObject ?: JsonObject(emptyMap())

    return runCatching {
        when (type) {
            "connected" -> BattleEvent.Connected(decodeFromJsonElement(BattleConnectedDto.serializer(), data))
            "battle.started" -> BattleEvent.Started(decodeFromJsonElement(BattleStartedDto.serializer(), data))
            "state.tick" -> BattleEvent.StateTick(
                decodeFromJsonElement(BattleStateTickDto.serializer(), data),
                receivedAtMs,
            )
            "question.push" -> BattleEvent.QuestionPush(decodeFromJsonElement(BattleQuestionPushDto.serializer(), data))
            "answer.result" -> BattleEvent.AnswerResult(decodeFromJsonElement(BattleAnswerResultDto.serializer(), data))
            "monster.swing" -> BattleEvent.MonsterSwing(decodeFromJsonElement(MonsterSwingDto.serializer(), data))
            "skill.used" -> BattleEvent.SkillUsed(decodeFromJsonElement(BattleSkillUsedDto.serializer(), data))
            "battle.finished" -> BattleEvent.Finished(decodeFromJsonElement(BattleFinishedDto.serializer(), data))
            "pong" -> BattleEvent.Pong(
                decodeFromJsonElement(BattlePongDto.serializer(), data),
                receivedAtMs,
            )
            "error" -> BattleEvent.Failed(decodeFromJsonElement(BattleErrorDto.serializer(), data))
            else -> {
                Log.w(TAG, "Ignoring unknown event type '$type'")
                null
            }
        }
    }.getOrElse { cause ->
        Log.w(TAG, "Dropping '$type' frame that did not match its schema", cause)
        null
    }
}
