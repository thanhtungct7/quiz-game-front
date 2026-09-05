package com.kma.quiz_game.data.remote

import android.os.SystemClock
import android.util.Log
import com.kma.quiz_game.BuildConfig
import com.kma.quiz_game.data.remote.dto.DuoEvent
import com.kma.quiz_game.data.remote.dto.decodeDuoEvent
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

private const val TAG = "DuoSocket"

/** Reconnect backoff. The server forfeits a disconnected player after 30s, so every attempt --
 * 1 + 2 + 4 + 8 = 15s of waiting plus the handshakes -- has to fit inside that window. */
private val RETRY_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000)

/**
 * The 1v1 match socket, wrapping [okhttp3.WebSocket].
 *
 * The handshake cannot carry an `Authorization` header, so the access token rides in the query
 * string exactly as the backend expects: `.../api/v1/duo/ws?token=<access-token>`.
 *
 * @param tokenProvider must return a *fresh* access token; it is called again before each
 *   reconnect, since the 30-minute access token can expire while a socket is open.
 */
class DuoSocket(
    private val client: OkHttpClient,
    private val json: Json,
    private val tokenProvider: suspend () -> String?,
    baseUrl: String = BuildConfig.API_BASE_URL,
) {
    /** `API_BASE_URL` already ends in `/api/v1/`. OkHttp upgrades an http(s) URL to a WebSocket
     * itself, so no separate ws:// BuildConfig field is needed. */
    private val endpoint = baseUrl.trimEnd('/') + "/duo/ws"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<DuoEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<DuoEvent> = _events.asSharedFlow()

    private var socket: WebSocket? = null
    private var connectJob: Job? = null

    /** Set while a caller-initiated [close] is in flight, so the listener does not reconnect. */
    @Volatile
    private var closedByUs = false

    val isConnected: Boolean get() = socket != null

    /** Opens the socket if it is not already open. Safe to call repeatedly. */
    fun connect() {
        if (socket != null || connectJob?.isActive == true) return
        closedByUs = false
        connectJob = scope.launch { openWithRetries() }
    }

    /** Closes for good: no reconnect, and listeners see [DuoEvent.SocketClosed] with willRetry=false. */
    fun close() {
        closedByUs = true
        connectJob?.cancel()
        connectJob = null
        socket?.close(1000, "client closed")
        socket = null
    }

    /** Sends one envelope. No-op with a warning when the socket is down -- callers are UI taps,
     * and there is nothing useful to do with a failure beyond not crashing. */
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
        while (true) {
            val token = tokenProvider()
            if (token == null) {
                Log.w(TAG, "No access token available; not connecting")
                _events.emit(DuoEvent.SocketClosed(willRetry = false))
                return
            }
            if (open(token)) return

            if (closedByUs || attempt >= RETRY_DELAYS_MS.size) {
                _events.emit(DuoEvent.SocketClosed(willRetry = false))
                return
            }
            _events.emit(DuoEvent.SocketClosed(willRetry = true))
            delay(RETRY_DELAYS_MS[attempt])
            attempt++
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
        val closedCleanly = suspendCancellableCoroutine<Boolean> { continuation ->
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
                        _events.tryEmit(DuoEvent.SocketOpen)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        // Stamped here, at the earliest point the frame exists on this device, so
                        // the clock offset a snapshot yields is not polluted by however long the
                        // reducer or the UI took to get to it. `elapsedRealtime` rather than the
                        // wall clock: a match must not skip when the device syncs its time.
                        val now = SystemClock.elapsedRealtime()
                        json.decodeDuoEvent(text, now)?.let { _events.tryEmit(it) }
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
