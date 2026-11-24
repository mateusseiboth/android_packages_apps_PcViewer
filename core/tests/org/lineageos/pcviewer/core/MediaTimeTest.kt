package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTimeTest {
    @Test
    fun formatsMinutesAndHours() {
        assertEquals("0:00", MediaTime.format(0))
        assertEquals("1:05", MediaTime.format(65_000))
        assertEquals("1:02:03", MediaTime.format(3_723_000))
        assertEquals("0:00", MediaTime.format(-5))
    }

    @Test
    fun skipStaysWithinTheMedia() {
        assertEquals(0L, MediaTime.skip(position = 5_000, by = -10_000, duration = 60_000))
        assertEquals(60_000L, MediaTime.skip(position = 55_000, by = 10_000, duration = 60_000))
        assertEquals(25_000L, MediaTime.skip(position = 15_000, by = 10_000, duration = 60_000))
    }

    @Test
    fun speedsCycle() {
        assertEquals(1.25f, MediaTime.nextSpeed(1f), 0.001f)
        assertEquals(0.5f, MediaTime.nextSpeed(2f), 0.001f)
        assertEquals(1f, MediaTime.nextSpeed(0.9f), 0.001f)
    }
}
