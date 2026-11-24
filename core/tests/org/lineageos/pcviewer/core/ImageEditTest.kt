package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEditTest {
    private fun assertMaps(edit: ImageEdit, x: Float, y: Float, toX: Float, toY: Float) {
        val (mx, my) = edit.geometry(400, 300).map(x, y)
        assertEquals(toX, mx, 0.001f)
        assertEquals(toY, my, 0.001f)
    }

    @Test
    fun startsUntouched() {
        assertTrue(ImageEdit().isIdentity)
        assertEquals(PixelSize(400, 300), ImageEdit().orientedSize(400, 300))
        assertEquals(PixelRect(0, 0, 400, 300), ImageEdit().cropPixels(400, 300))
    }

    @Test
    fun rotatingSwapsTheSides() {
        val edit = ImageEdit().rotateClockwise()
        assertFalse(edit.isIdentity)
        assertEquals(PixelSize(300, 400), edit.orientedSize(400, 300))
        assertMaps(edit, 0f, 0f, 300f, 0f)
        assertMaps(edit, 400f, 300f, 0f, 400f)
        assertMaps(ImageEdit().rotateCounterClockwise(), 0f, 0f, 0f, 400f)
    }

    @Test
    fun flipsMirrorTheShownImage() {
        assertMaps(ImageEdit().flipHorizontally(), 0f, 0f, 400f, 0f)
        assertMaps(ImageEdit().flipVertically(), 0f, 0f, 0f, 300f)
        assertMaps(ImageEdit().rotateClockwise().flipHorizontally(), 0f, 0f, 0f, 0f)
    }

    @Test
    fun cropInPixels() {
        val edit = ImageEdit(crop = NormRect(0.25f, 0f, 0.5f, 1f))
        assertEquals(PixelRect(100, 0, 200, 300), edit.cropPixels(400, 300))
    }

    @Test
    fun cropTurnsAndMirrorsWithTheImage() {
        val leftHalf = ImageEdit(crop = NormRect(0f, 0f, 0.5f, 1f))
        assertEquals(NormRect(0f, 0f, 1f, 0.5f), leftHalf.rotateClockwise().crop)
        assertEquals(NormRect(0f, 0.5f, 1f, 0.5f), leftHalf.rotateCounterClockwise().crop)
        assertEquals(NormRect(0.5f, 0f, 0.5f, 1f), leftHalf.flipHorizontally().crop)
        assertEquals(NormRect(0f, 0f, 0.5f, 1f), leftHalf.flipVertically().crop)
    }

    @Test
    fun colourMatrix() {
        val identity = ImageEdit().colorMatrix()
        assertEquals(1f, identity[0], 0.001f)
        assertEquals(0f, identity[4], 0.001f)
        val adjusted = ImageEdit(brightness = 0.1f, contrast = 1.5f).colorMatrix()
        assertEquals(1.5f, adjusted[0], 0.001f)
        assertEquals(1.5f, adjusted[6], 0.001f)
        assertEquals(1.5f, adjusted[12], 0.001f)
        assertEquals(128f * (1 - 1.5f) + 25.5f, adjusted[4], 0.01f)
        assertEquals(1f, adjusted[18], 0.001f)
    }

    @Test
    fun resetUndoesEverything() {
        val edit = ImageEdit().rotateClockwise().flipVertically().copy(brightness = 0.3f, crop = NormRect(0f, 0f, 0.5f, 0.5f))
        assertTrue(edit.reset().isIdentity)
    }
}
