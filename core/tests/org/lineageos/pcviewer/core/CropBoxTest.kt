package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropBoxTest {
    private val full = NormRect.FULL

    private fun assertRect(expected: NormRect, actual: NormRect) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
        assertEquals(expected.width, actual.width, 0.001f)
        assertEquals(expected.height, actual.height, 0.001f)
    }

    @Test
    fun edgesAndCornersMoveFreely() {
        assertRect(NormRect(0f, 0f, 0.9f, 1f), CropBox.drag(full, CropHandle.RIGHT, -0.1f, 0f, ratio = null))
        assertRect(NormRect(0.2f, 0.2f, 0.8f, 0.8f), CropBox.drag(full, CropHandle.TOP_LEFT, 0.2f, 0.2f, ratio = null))
    }

    @Test
    fun neverSmallerThanTheMinimumNorOutside() {
        assertEquals(CropBox.MIN_SIZE, CropBox.drag(full, CropHandle.RIGHT, -2f, 0f, ratio = null).width, 0.001f)
        val inner = NormRect(0.2f, 0.2f, 0.5f, 0.5f)
        assertRect(NormRect(0f, 0.2f, 0.7f, 0.5f), CropBox.drag(inner, CropHandle.LEFT, -0.5f, 0f, ratio = null))
    }

    @Test
    fun movingKeepsTheSizeInside() {
        assertRect(NormRect(0.2f, 0.2f, 0.8f, 0.8f), CropBox.drag(NormRect(0f, 0f, 0.8f, 0.8f), CropHandle.MOVE, 0.5f, 0.5f, ratio = null))
    }

    @Test
    fun aspectGivesTheLargestCentredBox() {
        // A square in an image twice as wide as tall is half as wide as the image in normalised units.
        val ratio = CropBox.normalisedRatio(1f, imageAspect = 2f)
        assertRect(NormRect(0.25f, 0f, 0.5f, 1f), CropBox.withRatio(full, ratio))
    }

    @Test
    fun cornersKeepTheAspect() {
        val square = NormRect(0.25f, 0f, 0.5f, 1f)
        assertRect(NormRect(0.25f, 0f, 0.4f, 0.8f), CropBox.drag(square, CropHandle.BOTTOM_RIGHT, -0.1f, 0f, ratio = 0.5f))
    }

    @Test
    fun handleUnderThePointer() {
        val box = NormRect(0.2f, 0.2f, 0.6f, 0.6f)
        assertEquals(CropHandle.TOP_LEFT, CropBox.handleAt(box, 0.21f, 0.19f, 0.03f, 0.03f))
        assertEquals(CropHandle.BOTTOM_RIGHT, CropBox.handleAt(box, 0.8f, 0.8f, 0.03f, 0.03f))
        assertEquals(CropHandle.RIGHT, CropBox.handleAt(box, 0.8f, 0.5f, 0.03f, 0.03f))
        assertEquals(CropHandle.MOVE, CropBox.handleAt(box, 0.5f, 0.5f, 0.03f, 0.03f))
        assertNull(CropBox.handleAt(box, 0.05f, 0.05f, 0.03f, 0.03f))
    }

    @Test
    fun aspectPresets() {
        assertNull(CropAspect.FREE.ratio(4f / 3f))
        assertEquals(4f / 3f, CropAspect.ORIGINAL.ratio(4f / 3f)!!, 0.001f)
        assertEquals(16f / 9f, CropAspect.WIDE.ratio(1f)!!, 0.001f)
    }
}
