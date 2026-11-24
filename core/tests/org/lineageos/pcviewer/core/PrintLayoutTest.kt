package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintLayoutTest {
    @Test
    fun linesArePaginated() {
        assertEquals(listOf(0..3, 4..7, 8..9), PrintLayout.paginate(lineCount = 10, linesPerPage = 4))
        assertEquals(listOf(0 until 0), PrintLayout.paginate(lineCount = 0, linesPerPage = 4))
    }

    @Test
    fun longLinesAreWrapped() {
        assertEquals(listOf("abcd", "efgh", "ij", "", "xy"), PrintLayout.wrap(listOf("abcdefghij", "", "xy"), columns = 4))
    }

    @Test
    fun contentIsFittedAndCentred() {
        assertEquals(Area(0f, 25f, 100f, 50f), PrintLayout.fit(200f, 100f, Area(0f, 0f, 100f, 100f)))
        assertEquals(Area(35f, 10f, 30f, 80f), PrintLayout.fit(30f, 80f, Area(10f, 10f, 80f, 80f)))
    }
}
