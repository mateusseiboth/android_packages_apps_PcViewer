package org.lineageos.pcviewer.core

/** A rectangle in fractions (0 to 1) of an image's width and height. */
data class NormRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    companion object {
        val FULL = NormRect(0f, 0f, 1f, 1f)
    }
}

data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * The edits made to an image: quarter turns and mirroring of the shown image, a crop (in fractions of the
 * turned image), brightness (-1 to 1) and contrast (0 to 2, 1 is unchanged). The geometry is "turn the
 * source, then mirror"; a turn after a mirror swaps which axis is mirrored, so the state stays in that form.
 */
data class ImageEdit(
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val crop: NormRect = NormRect.FULL,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
) {
    val isIdentity: Boolean get() = this == ImageEdit()

    fun rotateClockwise() = copy(
        quarterTurns = Math.floorMod(quarterTurns + 1, 4),
        flipH = flipV,
        flipV = flipH,
        crop = NormRect(1 - crop.bottom, crop.x, crop.height, crop.width),
    )

    fun rotateCounterClockwise() = copy(
        quarterTurns = Math.floorMod(quarterTurns - 1, 4),
        flipH = flipV,
        flipV = flipH,
        crop = NormRect(crop.y, 1 - crop.right, crop.height, crop.width),
    )

    fun flipHorizontally() = copy(flipH = !flipH, crop = crop.copy(x = 1 - crop.right))

    fun flipVertically() = copy(flipV = !flipV, crop = crop.copy(y = 1 - crop.bottom))

    fun reset() = ImageEdit()

    fun orientedSize(width: Int, height: Int): PixelSize =
        PixelSize(height, width).takeIf { quarterTurns % 2 == 1 } ?: PixelSize(width, height)

    /** Maps source pixels to the turned and mirrored image. */
    fun geometry(width: Int, height: Int): Affine {
        val w = width.toFloat()
        val h = height.toFloat()
        val turns = listOf(
            Affine.IDENTITY,
            Affine(0f, 1f, -1f, 0f, h, 0f),
            Affine(-1f, 0f, 0f, -1f, w, h),
            Affine(0f, -1f, 1f, 0f, 0f, w),
        )
        val oriented = orientedSize(width, height)
        val mirrorH = Affine(-1f, 0f, 0f, 1f, oriented.width.toFloat(), 0f).takeIf { flipH } ?: Affine.IDENTITY
        val mirrorV = Affine(1f, 0f, 0f, -1f, 0f, oriented.height.toFloat()).takeIf { flipV } ?: Affine.IDENTITY
        return mirrorV * mirrorH * turns[quarterTurns]
    }

    /** The crop in pixels of the turned image, at least one pixel each way. */
    fun cropPixels(width: Int, height: Int): PixelRect {
        val size = orientedSize(width, height)
        val x = (crop.x * size.width).toInt().coerceIn(0, size.width - 1)
        val y = (crop.y * size.height).toInt().coerceIn(0, size.height - 1)
        val w = Math.round(crop.width * size.width).coerceIn(1, size.width - x)
        val h = Math.round(crop.height * size.height).coerceIn(1, size.height - y)
        return PixelRect(x, y, w, h)
    }

    /** A 4x5 colour matrix (Android's ColorMatrix layout): contrast around mid grey, then brightness. */
    fun colorMatrix(): FloatArray {
        val offset = 128f * (1 - contrast) + brightness * 255f
        return floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
