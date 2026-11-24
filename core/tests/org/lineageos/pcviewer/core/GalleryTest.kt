package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryTest {
    private val folder = listOf("a.png", "b.png", "c.pdf", "d.png")

    @Test
    fun aSingleFileHasNoNeighbours() {
        val gallery = Gallery.of(data = "x.png", siblings = emptyList(), index = null)
        assertEquals("x.png", gallery.current)
        assertFalse(gallery.hasNext)
        assertFalse(gallery.hasPrevious)
    }

    @Test
    fun siblingsWithAnIndexStartThere() {
        val gallery = Gallery.of(data = "b.png", siblings = folder, index = 1)
        assertEquals("b.png", gallery.current)
        assertTrue(gallery.hasNext)
        assertTrue(gallery.hasPrevious)
    }

    @Test
    fun withoutAnIndexTheDataIsLookedUpAmongTheSiblings() {
        assertEquals(3, Gallery.of(data = "d.png", siblings = folder, index = null).index)
    }

    @Test
    fun dataMissingFromTheSiblingsStartsAtTheFirst() {
        assertEquals("a.png", Gallery.of(data = null, siblings = folder, index = null).current)
    }

    @Test
    fun outOfRangeIndexIsClamped() {
        assertEquals("d.png", Gallery.of(data = null, siblings = folder, index = 42).current)
    }

    @Test
    fun nextAndPreviousStopAtTheEnds() {
        val last = Gallery.of(data = "d.png", siblings = folder, index = 3)
        assertEquals(last, last.next())
        assertEquals("c.pdf", last.previous().current)
        val first = Gallery.of(data = "a.png", siblings = folder, index = 0)
        assertEquals(first, first.previous())
    }

    @Test
    fun retainKeepsTheCurrentItemAndItsPosition() {
        val gallery = Gallery.of(data = "d.png", siblings = folder, index = 3).retain { it.endsWith(".png") }
        assertEquals(listOf("a.png", "b.png", "d.png"), gallery.items)
        assertEquals("d.png", gallery.current)
        assertEquals("b.png", gallery.previous().current)
    }

    @Test
    fun positionIsOneBased() {
        val gallery = Gallery.of(data = "b.png", siblings = folder, index = 1)
        assertEquals(2, gallery.position)
        assertEquals(4, gallery.size)
    }
}
