package com.pixelplumber.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioTest {
    @Test
    fun voicesHaveEqualLength() {
        for (t in Tracks.all) {
            val l = t.stepLengths
            assertEquals(1, l.toSet().size, "Stimmen unterschiedlich lang: $l")
        }
    }

    @Test
    fun synthProducesSound() {
        val s = Synth(22050)
        s.music(Tracks.TITLE)
        s.sfx(Sfx.JUMP)
        val buf = ShortArray(4096)
        s.render(buf, buf.size)
        assertTrue(buf.any { it.toInt() != 0 })
    }

    @Test
    fun noteFrequencies() {
        assertEquals(440f, Track.noteFreq("A4"), 0.01f)
        assertEquals(261.63f, Track.noteFreq("C4"), 0.05f)
    }
}
