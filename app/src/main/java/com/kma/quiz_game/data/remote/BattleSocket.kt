package com.kma.quiz_game.data.remote

import android.os.SystemClock
import android.util.Log
import com.kma.quiz_game.BuildConfig
import com.kma.quiz_game.data.remote.dto.BattleEvent
import com.kma.quiz_game.data.remote.dto.decodeBattleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "BattleSocket"

/**
 * Reconnect backoff, capped rather than exhausted.
 *
 * A dropped socket ends a lesson battle at once -- the server holds no seat and grants no grace
 * window -- so these retries are only about getting the player back to a *new* fight. They used to
 * stop after three attempts, which is seven seconds: a server that took longer than that to come
 * back left this socket dead for the rest of the session with nothing to revive it, and the battle
 * screen sat on its spinner forever. The ladder now flattens out at [MAX_RETRY_DELAY_MS] and keeps
 * going; [close] is what ends it, and logout is what calls that.
 */
private val RETRY_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000)

/** Where the ladder above flattens out, and stays. */
private const val MAX_RETRY_DELAY_MS = 15_000L

/**
 * The lesson-battle socket, wrapping [okhttp3.WebSocket].
 *
 * The handshake cannot carry an `Authorization` header, so the access token rides in the query
 * string exactly as the backend expects: `.../api/v1/battles/ws?token=<access-token>`.
 *
 * @param tokenProvider must return a *fresh* access token; it is called again before each
 *   reconnect, since the 30-minute access token can expire while a socket is open.
 */
class BattleSocket(
    private val client: OkHttpClient,
    private val json: Json,
    private val tokenProvider: suspend () -> String?,
    baseUrl: String = BuildConfig.API_BASE_URL,
) {
    /** `API_BASE_URL` already ends in `/api/v1/`. OkHttp upgrades an http(s) URL to a WebSocket
     * itself, so no separate ws:// BuildConfig field is needed. */
    private val endpoint = baseUrl.trimEnd('/') + "/battles/ws"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<BattleEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BattleEvent> = _events.asSharedFlow()

    private var socket: WebSocket? = null
    private var connectJob: Job? = null

    /** Set while a caller-initiated [close] is in flight, so the listener does not reconnect. */
    @Volatile
    private var closedByUs = false

    /**
     * True only while [openWithRetries] is sleeping between two attempts.
     *
     * That is the one state a fresh [connect] can usefully interrupt: a caller asking for the
     * socket is a better reason to try now than to sit out the rest of a fifteen-second backoff.
     * An attempt already in flight is left alone, so entering the battle screen -- which connects
     * and then starts, back to back -- cannot cancel its own handshake.
     */
    @Volatile
    private var waitingToRetry = false

    val isConnected: Boolean get() = socket != null

    /** Opens the socket if it is not already open. Safe to call repeatedly. */
    fun connect() {
        if (socket != null) return
        closedByUs = false
        if (connectJob?.isActive == true && !waitingToRetry) return
        connectJob?.cancel()
        connectJob = scope.launch { openWithRetries() }
    }

    /** Closes for good: no reconnect, and listeners see [BattleEvent.SocketClosed] with
     * willRetry=false. */
    fun close() {
        closedByUs = true
        waitingToRetry = false
        connectJob?.cancel()
        connectJob = null
        socket?.close(1000, "client closed")
        socket = null
    }

    /** Sends one envelope. No-op with a warning when the socket is down -- callers are UI taps, and
     * there is nothing useful to do with a failure beyond not crashing. */
    fun send(type: String, data: JsonObject = JsonObject(emptyMap())) {
        val target = socket
        if (target == null) {
            Log.w(TAG, "Dropping '$type': socket is not open")
            return
        }
        val frame = buildString {
            append("""{"type":"""")
            append(type)
            append("""","data":""")
            append(json.encodeToString(JsonObject.serializer(), data))
            append("}")
        }
        target.send(frame)
    }

    private suspend fun openWithRetries() {
        var attempt = 0
        try {
            while (true) {
                val token = tokenProvider()
                // No token means logged out, which no amount of retrying fixes.
                if (token == null) {
                    Log.w(TAG, "No access token available; not connecting")
                    _events.emit(BattleEvent.SocketClosed(willRetry = false))
                    return
                }
                if (open(token)) return

                if (closedByUs) {
                    _events.emit(BattleEvent.SocketClosed(willRetry = false))
                    return
                }
                _events.emit(BattleEvent.SocketClosed(willRetry = true))
                waitingToRetry = true
                delay(RETRY_DELAYS_MS.getOrElse(attempt) { MAX_RETRY_DELAY_MS })
                waitingToRetry = false
                attempt++
            }
        } finally {
            waitingToRetry = false
        }
    }

    /**
     * Opens one socket and suspends until it dies.
     *
     * @return true when the socket closed because we asked it to, false when it dropped on its own
     *   and the caller should retry.
     */
    private suspend fun open(token: String): Boolean {
        val request = Request.Builder().url("$endpoint?token=$token").build()
        val closedCleanly = suspendCancellableCoroutine { continuation ->
            var resumed = false
            fun finish(clean: Boolean) {
                if (resumed) return
                resumed = true
                socket = null
                if (continuation.isActive) continuation.resumeWith(Result.success(clean))
            }

            val ws = client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        socket = webSocket
                        _events.tryEmit(BattleEvent.SocketOpen)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        // Stamped here, at the earliest point the frame exists on this device, so
                        // the clock offset a snapshot yields is not polluted by however long the
                        // reducer or the UI took to get to it. `elapsedRealtime` rather than the
                        // wall clock: a fight must not skip when the device syncs its time.
                        val now = SystemClock.elapsedRealtime()
                        json.decodeBattleEvent(text, now)?.let { _events.tryEmit(it) }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "Socket closed ($code) $reason")
                        // 1008 is the server rejecting the token -- retrying with the same one
                        // would just loop, so treat it as final.
                        finish(closedByUs || code == 1008)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "Socket failed (${response?.code})", t)
                        finish(closedByUs)
                    }
                },
            )
            continuation.invokeOnCancellation {
                runCatching { ws.close(1000, "cancelled") }
                socket = null
            }
        }
        return closedCleanly
    }
}
