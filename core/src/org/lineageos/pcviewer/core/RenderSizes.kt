package org.lineageos.pcviewer.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PixelSize(val width: Int, val height: Int)

/** Bitmap sizes that keep memory bounded: images are subsampled, PDF pages are rendered to fit a width. */
object RenderSizes {
    const val MAX_IMAGE_PIXELS = 32_000_000
    const val MAX_PAGE_PIXELS = 12_000_000

    /** The smallest power of two that brings [width] x [height] under [maxPixels]. */
    fun sampleSize(width: Int, height: Int, maxPixels: Int): Int {
        if (width <= 0 || height <= 0) return 1
        val pixels = width.toLong() * height
        return generateSequence(1) { it * 2 }.first { pixels / (it.toLong() * it) <= maxPixels }
    }

    /** A page of [pageWidth] x [pageHeight] points drawn [targetWidth] pixels wide, within [maxPixels]. */
    fun pageSize(pageWidth: Int, pageHeight: Int, targetWidth: Int, maxPixels: Int): PixelSize {
        if (pageWidth <= 0 || pageHeight <= 0 || targetWidth <= 0) return PixelSize(1, 1)
        val wanted = targetWidth.toDouble() / pageWidth
        val allowed = sqrt(maxPixels.toDouble() / (pageWidth.toDouble() * pageHeight))
        val scale = min(wanted, allowed)
        return PixelSize(max(1, floor(pageWidth * scale).toInt()), max(1, floor(pageHeight * scale).toInt()))
    }
}
