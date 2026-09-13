package com.kma.quiz_game.data.remote

import kotlinx.serialization.Serializable
import retrofit2.HttpException
import retrofit2.Response

@Serializable
private data class ErrorDetail(val detail: String? = null)

/** FastAPI's standard error shape is `{"detail": "..."}` -- surface that message when present,
 * otherwise fall back to a generic status/network message. */
fun Throwable.toUserMessage(): String = when (this) {
    // Rate limited. Said here rather than passing the server's English detail through, and with
    // the wait the server asked for when it sent one.
    is HttpException if code() == 429 -> {
        val seconds = response()?.headers()?.get("Retry-After")?.toLongOrNull()
        if (seconds != null && seconds > 0) {
            "Bạn thao tác quá nhanh. Vui lòng thử lại sau $seconds giây."
        } else {
            "Bạn thao tác quá nhanh. Vui lòng thử lại sau ít phút."
        }
    }
    is HttpException -> {
        val body = response()?.errorBody()?.string()
        val detail = body?.let { runCatching { NetworkModule.json.decodeFromString<ErrorDetail>(it) }.getOrNull()?.detail }
        detail ?: "Yêu cầu thất bại (mã lỗi ${code()})"
    }
    else -> "Không thể kết nối tới máy chủ. Kiểm tra kết nối mạng và thử lại."
}

/**
 * Retrofit only raises [HttpException] for methods that return a body; a `Response<Unit>` hands
 * back 4xx as an unsuccessful response instead. Rethrow it so callers see the same failure
 * regardless of which shape the endpoint uses, and [toUserMessage] still finds the `detail`.
 */
fun Response<Unit>.throwIfUnsuccessful() {
    if (!isSuccessful) throw HttpException(this)
}
