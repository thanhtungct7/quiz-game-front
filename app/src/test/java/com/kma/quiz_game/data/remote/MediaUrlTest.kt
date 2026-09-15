package com.kma.quiz_game.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUrlTest {
    private val base = "https://firebasestorage.googleapis.com/v0/b/duo-d298a.firebasestorage.app/o/"

    @Test
    fun `the storage path becomes one escaped segment`() {
        assertEquals(
            "${base}vocab%2Fimages%2F01_0001.jpg?alt=media",
            storageMediaUrl("vocab/images/01_0001.jpg", base),
        )
    }

    @Test
    fun `an audio path is escaped the same way`() {
        assertEquals(
            "${base}vocab%2Faudio%2F01_0001_meaning.mp3?alt=media",
            storageMediaUrl("vocab/audio/01_0001_meaning.mp3", base),
        )
    }

    @Test
    fun `a stored picture ships as the webp of the same name`() {
        assertEquals("vocab/images/01_0001.webp", bundledImageAsset("vocab/images/01_0001.jpg"))
    }

    @Test
    fun `recordings and other paths are not bundled`() {
        assertNull(bundledImageAsset("vocab/audio/01_0001.mp3"))
        assertNull(bundledImageAsset("quiz-bank-cover.svg"))
    }
}
