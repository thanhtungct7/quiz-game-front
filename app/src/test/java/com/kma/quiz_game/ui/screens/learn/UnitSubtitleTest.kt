package com.kma.quiz_game.ui.screens.learn

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitSubtitleTest {
    @Test
    fun `the band the title already names is not repeated`() {
        assertEquals(
            "CEFR A1-A2 · Điền từ, Ghép câu, Sửa lỗi Đ/S",
            unitSubtitle(
                title = "TOEIC 250-450 · Chặng 1",
                description = "TOEIC 250-450 · CEFR A1-A2 · Điền từ, Ghép câu, Sửa lỗi Đ/S",
            ),
        )
    }

    @Test
    fun `a description that does not repeat the title is left alone`() {
        assertEquals(
            "TOEIC 500-650 · CEFR B1",
            unitSubtitle(title = "TOEIC 250-450 · Chặng 1", description = "TOEIC 500-650 · CEFR B1"),
        )
    }

    @Test
    fun `a title with no separator is left alone`() {
        assertEquals("Chào hỏi cơ bản", unitSubtitle(title = "Unit 1", description = "Chào hỏi cơ bản"))
    }
}
