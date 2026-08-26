package com.kma.quiz_game.data.remote

import kotlinx.serialization.Serializable
import retrofit2.HttpException

@Serializable
private data class ErrorDetail(val detail: String? = null)

/** FastAPI's standard error shape is `{"detail": "..."}` -- surface that message when present,
 * otherwise fall back to a generic status/network message. */
fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> {
        val body = response()?.errorBody()?.string()
        val detail = body?.let { runCatching { NetworkModule.json.decodeFromString<ErrorDetail>(it) }.getOrNull()?.detail }
        detail ?: "Yêu cầu thất bại (mã lỗi ${code()})"
    }
    else -> "Không thể kết nối tới máy chủ. Kiểm tra kết nối mạng và thử lại."
}
