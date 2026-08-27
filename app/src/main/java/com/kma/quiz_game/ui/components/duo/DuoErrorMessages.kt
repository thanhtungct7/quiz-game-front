package com.kma.quiz_game.ui.components.duo

import com.kma.quiz_game.data.remote.dto.DuoErrorCode

/**
 * Player-facing text for the socket's error codes.
 *
 * The server sends stable codes precisely so the client can branch on them; this is the one place
 * that turns them into words. `ROUND_CLOSED` and `ALREADY_ANSWERED` never reach here -- the
 * reducer swallows them, since losing a race to answer is normal play, not an error worth showing.
 */
fun DuoErrorCode.toUserMessage(): String = when (this) {
    DuoErrorCode.INVALID_PAYLOAD -> "Dữ liệu gửi lên không hợp lệ."
    DuoErrorCode.UNKNOWN_EVENT -> "Máy chủ không hiểu yêu cầu này."
    DuoErrorCode.ALREADY_IN_MATCH -> "Bạn đang ở trong một trận khác."
    DuoErrorCode.ALREADY_IN_QUEUE -> "Bạn đã ở trong hàng chờ rồi."
    DuoErrorCode.NOT_IN_QUEUE -> "Bạn không ở trong hàng chờ."
    DuoErrorCode.ROOM_NOT_FOUND -> "Không tìm thấy phòng. Kiểm tra lại mã phòng."
    DuoErrorCode.ROOM_FULL -> "Phòng đã đủ hai người."
    DuoErrorCode.NOT_HOST -> "Chỉ chủ phòng mới được bắt đầu trận."
    DuoErrorCode.NOT_ENOUGH_PLAYERS -> "Cần đủ hai người mới bắt đầu được."
    DuoErrorCode.MATCH_ALREADY_STARTED -> "Trận đã bắt đầu rồi."
    DuoErrorCode.NOT_IN_MATCH -> "Bạn không ở trong trận nào."
    DuoErrorCode.ROUND_CLOSED -> "Hiệp này đã kết thúc."
    DuoErrorCode.ALREADY_ANSWERED -> "Bạn đã trả lời câu này rồi."
    DuoErrorCode.INVALID_OPTION -> "Đáp án không thuộc câu hỏi này."
    DuoErrorCode.NO_QUESTIONS_AVAILABLE -> "Ngân hàng câu hỏi không đủ để mở trận."
    DuoErrorCode.UNKNOWN -> "Đã xảy ra lỗi không xác định."
}
