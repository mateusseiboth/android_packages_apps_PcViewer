package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderSizesTest {
    @Test
    fun smallImagesAreDecodedWhole() {
        assertEquals(1, RenderSizes.sampleSize(4000, 3000, maxPixels = 16_000_000))
    }

    @Test
    fun hugeImagesAreDecodedAtAPowerOfTwoFraction() {
        assertEquals(2, RenderSizes.sampleSize(8000, 6000, maxPixels = 16_000_000))
        assertEquals(4, RenderSizes.sampleSize(16000, 12000, maxPixels = 16_000_000))
    }

    @Test
    fun unknownSizesAreDecodedWhole() {
        assertEquals(1, RenderSizes.sampleSize(0, -1, maxPixels = 16_000_000))
    }

    @Test
    fun pdfPagesFollowTheTargetWidth() {
        // A4 is 595 x 842 points.
        assertEquals(PixelSize(1190, 1684), RenderSizes.pageSize(595, 842, targetWidth = 1190, maxPixels = 16_000_000))
    }

    @Test
    fun pdfPagesAreCappedByPixelCount() {
        val size = RenderSizes.pageSize(595, 842, targetWidth = 10_000, maxPixels = 4_000_000)
        assertEquals(true, size.width * size.height <= 4_000_000)
        assertEquals(595.0 / 842.0, size.width.toDouble() / size.height, 0.01)
    }

    @Test
    fun pdfPagesAreNeverEmpty() {
        assertEquals(PixelSize(1, 1), RenderSizes.pageSize(0, 0, targetWidth = 0, maxPixels = 100))
    }
}
