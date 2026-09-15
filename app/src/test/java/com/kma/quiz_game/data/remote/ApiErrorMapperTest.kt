package com.kma.quiz_game.data.remote

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** What a failed request says to the learner, for each shape of error body the backend sends. */
class ApiErrorMapperTest {
    private fun httpError(code: Int, body: String): HttpException =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun `a string detail is shown as the server wrote it`() {
        val error = httpError(404, """{"detail": "Không tìm thấy bài học"}""")

        assertEquals("Không tìm thấy bài học", error.toUserMessage())
    }

    @Test
    fun `a validation error list is not shown as a raw status code`() {
        val body = """{"detail": [{"type": "value_error", "loc": ["body", "email"], "msg": "not an email"}]}"""

        assertEquals("Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.", httpError(422, body).toUserMessage())
    }

    @Test
    fun `a status the screen names replaces the server's english detail`() {
        val error = httpError(401, """{"detail": "Invalid email or password"}""")

        assertEquals("Sai rồi", error.toUserMessage(mapOf(401 to "Sai rồi")))
    }

    @Test
    fun `a status the screen does not name still falls through to the detail`() {
        val error = httpError(500, """{"detail": "Internal server error"}""")

        assertEquals("Internal server error", error.toUserMessage(mapOf(401 to "Sai rồi")))
    }

    @Test
    fun `a body without a detail falls back to the status code`() {
        assertEquals("Yêu cầu thất bại (mã lỗi 502)", httpError(502, "<html>Bad gateway</html>").toUserMessage())
    }

    @Test
    fun `rate limiting names the wait even when the screen maps other codes`() {
        val raw = okhttp3.Response.Builder()
            .code(429)
            .message("Too Many Requests")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header("Retry-After", "30")
            .build()
        val error = HttpException(Response.error<Any>("".toResponseBody(null), raw))

        assertEquals(
            "Bạn thao tác quá nhanh. Vui lòng thử lại sau 30 giây.",
            error.toUserMessage(mapOf(401 to "Sai rồi")),
        )
    }

    @Test
    fun `no response at all is a connection problem`() {
        assertEquals(
            "Không thể kết nối tới máy chủ. Kiểm tra kết nối mạng và thử lại.",
            IOException("timeout").toUserMessage(),
        )
    }
}
