package com.kma.quiz_game.data.remote.dto

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Every duo frame, in both directions, is the envelope `{"type": "<name>", "data": {...}}`.
 *
 * Decoding reads `type` first and then picks the payload serializer by hand rather than using
 * kotlinx polymorphic serialization: the discriminator lives outside `data`, and a `when` over
 * plain strings is far easier to follow when a payload turns out not to match the server.
 */
sealed interface DuoEvent {

    /** Socket-level, synthesised by [com.kma.quiz_game.data.remote.DuoSocket] -- not server frames. */
    data object SocketOpen : DuoEvent

    /** [willRetry] false means the socket is gone for good and the UI must say so. */
    data class SocketClosed(val willRetry: Boolean) : DuoEvent

    data class Connected(val data: ConnectedDto) : DuoEvent
    data class QueueWaiting(val data: QueueWaitingDto) : DuoEvent
    data object QueueLeft : DuoEvent
    data object QueueTimeout : DuoEvent
    data class RoomCreated(val data: RoomCreatedDto) : DuoEvent
    data class MatchFound(val data: MatchFoundDto) : DuoEvent
    data class MatchStarted(val data: MatchStartedDto) : DuoEvent
    data class RoundStart(val data: RoundStartDto) : DuoEvent
    data class OpponentAnswered(val data: OpponentAnsweredDto) : DuoEvent
    data class RoundResult(val data: RoundResultDto) : DuoEvent
    data class MatchResume(val data: MatchResumeDto) : DuoEvent
    data class OpponentDisconnected(val data: OpponentDisconnectedDto) : DuoEvent
    data object OpponentReconnected : DuoEvent
    data class MatchFinished(val data: MatchFinishedDto) : DuoEvent
    data class Chat(val data: ChatMessageDto) : DuoEvent
    data object Pong : DuoEvent
    data class Failed(val data: ErrorDto) : DuoEvent
}

/** Frame names the client may send. */
object DuoClientEvent {
    const val QUEUE_JOIN = "queue.join"
    const val QUEUE_LEAVE = "queue.leave"
    const val ROOM_CREATE = "room.create"
    const val ROOM_JOIN = "room.join"
    const val MATCH_START = "match.start"
    const val ANSWER_SUBMIT = "answer.submit"
    const val MATCH_LEAVE = "match.leave"
    const val CHAT_SEND = "chat.send"
    const val PING = "ping"
}

private const val TAG = "DuoEvent"

/**
 * Turns one raw text frame into a [DuoEvent], or null for anything unrecognised or malformed.
 *
 * A bad frame is logged and dropped rather than thrown: the server does the same with our
 * malformed input, and tearing down a live match over one unparseable frame would be worse than
 * missing it.
 */
fun Json.decodeDuoEvent(raw: String): DuoEvent? {
    val envelope = runCatching { parseToJsonElement(raw).jsonObject }.getOrElse {
        Log.w(TAG, "Dropping unparseable frame: ${raw.take(200)}")
        return null
    }
    val type = envelope["type"]?.jsonPrimitive?.content ?: return null
    val data = envelope["data"] as? JsonObject ?: JsonObject(emptyMap())

    return runCatching {
        when (type) {
            "connected" -> DuoEvent.Connected(decodeFromJsonElement(ConnectedDto.serializer(), data))
            "queue.waiting" -> DuoEvent.QueueWaiting(decodeFromJsonElement(QueueWaitingDto.serializer(), data))
            "queue.left" -> DuoEvent.QueueLeft
            "queue.timeout" -> DuoEvent.QueueTimeout
            "room.created" -> DuoEvent.RoomCreated(decodeFromJsonElement(RoomCreatedDto.serializer(), data))
            "match.found" -> DuoEvent.MatchFound(decodeFromJsonElement(MatchFoundDto.serializer(), data))
            "match.started" -> DuoEvent.MatchStarted(decodeFromJsonElement(MatchStartedDto.serializer(), data))
            "round.start" -> DuoEvent.RoundStart(decodeFromJsonElement(RoundStartDto.serializer(), data))
            "round.opponent_answered" -> DuoEvent.OpponentAnswered(decodeFromJsonElement(OpponentAnsweredDto.serializer(), data))
            "round.result" -> DuoEvent.RoundResult(decodeFromJsonElement(RoundResultDto.serializer(), data))
            "match.resume" -> DuoEvent.MatchResume(decodeFromJsonElement(MatchResumeDto.serializer(), data))
            "opponent.disconnected" -> DuoEvent.OpponentDisconnected(decodeFromJsonElement(OpponentDisconnectedDto.serializer(), data))
            "opponent.reconnected" -> DuoEvent.OpponentReconnected
            "match.finished" -> DuoEvent.MatchFinished(decodeFromJsonElement(MatchFinishedDto.serializer(), data))
            "chat.message" -> DuoEvent.Chat(decodeFromJsonElement(ChatMessageDto.serializer(), data))
            "pong" -> DuoEvent.Pong
            "error" -> DuoEvent.Failed(decodeFromJsonElement(ErrorDto.serializer(), data))
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
