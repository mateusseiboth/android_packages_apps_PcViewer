package org.lineageos.pcviewer.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class CropHandle { TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, MOVE }

/** Crop proportions; [ratio] is width over height in pixels, or null when free. */
enum class CropAspect(private val fixed: Float?) {
    FREE(null), ORIGINAL(null), SQUARE(1f), WIDE(16f / 9f), STANDARD(4f / 3f), PHOTO(3f / 2f);

    fun ratio(imageAspect: Float): Float? = imageAspect.takeIf { this == ORIGINAL } ?: fixed
}

/**
 * Dragging the crop box. All values are fractions of the turned image; a [ratio] here is the box's width
 * over its height in those fractions (see [normalisedRatio]).
 */
object CropBox {
    const val MIN_SIZE = 0.05f

    /** Which edges each handle moves: left, top, right, bottom. */
    private val EDGES = mapOf(
        CropHandle.TOP_LEFT to Edges(left = true, top = true),
        CropHandle.TOP to Edges(top = true),
        CropHandle.TOP_RIGHT to Edges(right = true, top = true),
        CropHandle.RIGHT to Edges(right = true),
        CropHandle.BOTTOM_RIGHT to Edges(right = true, bottom = true),
        CropHandle.BOTTOM to Edges(bottom = true),
        CropHandle.BOTTOM_LEFT to Edges(left = true, bottom = true),
        CropHandle.LEFT to Edges(left = true),
    )

    private data class Edges(
        val left: Boolean = false,
        val top: Boolean = false,
        val right: Boolean = false,
        val bottom: Boolean = false,
    ) {
        val isCorner: Boolean get() = (left || right) && (top || bottom)
    }

    fun normalisedRatio(aspect: Float, imageAspect: Float): Float = aspect / imageAspect

    /** The largest box of [ratio] inside [rect], centred on it. */
    fun withRatio(rect: NormRect, ratio: Float): NormRect {
        val width = min(rect.width, rect.height * ratio)
        val height = width / ratio
        return NormRect(rect.x + (rect.width - width) / 2, rect.y + (rect.height - height) / 2, width, height)
    }

    fun drag(rect: NormRect, handle: CropHandle, dx: Float, dy: Float, ratio: Float?): NormRect {
        val edges = EDGES[handle] ?: return moved(rect, dx, dy)
        val free = freeDrag(rect, edges, dx, dy)
        ratio ?: return free
        return keepRatio(rect, free, edges, ratio)
    }

    /** The handle under (x, y), corners first, with a tolerance per axis; MOVE inside, null outside. */
    fun handleAt(rect: NormRect, x: Float, y: Float, toleranceX: Float, toleranceY: Float): CropHandle? {
        val nearX = { edge: Float -> abs(x - edge) <= toleranceX }
        val nearY = { edge: Float -> abs(y - edge) <= toleranceY }
        val withinX = x >= rect.x - toleranceX && x <= rect.right + toleranceX
        val withinY = y >= rect.y - toleranceY && y <= rect.bottom + toleranceY
        val candidates = listOf(
            CropHandle.TOP_LEFT to (nearX(rect.x) && nearY(rect.y)),
            CropHandle.TOP_RIGHT to (nearX(rect.right) && nearY(rect.y)),
            CropHandle.BOTTOM_RIGHT to (nearX(rect.right) && nearY(rect.bottom)),
            CropHandle.BOTTOM_LEFT to (nearX(rect.x) && nearY(rect.bottom)),
            CropHandle.LEFT to (nearX(rect.x) && withinY),
            CropHandle.RIGHT to (nearX(rect.right) && withinY),
            CropHandle.TOP to (nearY(rect.y) && withinX),
            CropHandle.BOTTOM to (nearY(rect.bottom) && withinX),
            CropHandle.MOVE to (x > rect.x && x < rect.right && y > rect.y && y < rect.bottom),
        )
        return candidates.firstOrNull { it.second }?.first
    }

    private fun moved(rect: NormRect, dx: Float, dy: Float) = rect.copy(
        x = (rect.x + dx).coerceIn(0f, 1 - rect.width),
        y = (rect.y + dy).coerceIn(0f, 1 - rect.height),
    )

    private fun freeDrag(rect: NormRect, edges: Edges, dx: Float, dy: Float): NormRect {
        val left = (rect.x + dx).coerceIn(0f, rect.right - MIN_SIZE).takeIf { edges.left } ?: rect.x
        val top = (rect.y + dy).coerceIn(0f, rect.bottom - MIN_SIZE).takeIf { edges.top } ?: rect.y
        val right = (rect.right + dx).coerceIn(rect.x + MIN_SIZE, 1f).takeIf { edges.right } ?: rect.right
        val bottom = (rect.bottom + dy).coerceIn(rect.y + MIN_SIZE, 1f).takeIf { edges.bottom } ?: rect.bottom
        return NormRect(left, top, right - left, bottom - top)
    }

    /**
     * Corners keep the opposite corner still and take the width from the drag; edges keep the opposite edge
     * still and grow the other axis around its centre. Either way the box is shrunk to fit inside the image.
     */
    private fun keepRatio(old: NormRect, free: NormRect, edges: Edges, ratio: Float): NormRect {
        val anchorX = old.x.takeIf { !edges.left } ?: old.right
        val anchorY = old.y.takeIf { !edges.top } ?: old.bottom
        val roomX = (1 - anchorX).takeIf { !edges.left } ?: anchorX
        val horizontal = edges.left || edges.right
        val wanted = free.width.takeIf { horizontal } ?: (free.height * ratio)
        val centreX = old.x + old.width / 2
        val centreY = old.y + old.height / 2
        val roomY = (1 - anchorY).takeIf { !edges.top } ?: anchorY
        val maxWidth = edges.isCorner.let { corner ->
            min(roomX, roomY * ratio).takeIf { corner }
                ?: min(roomX, 2 * min(centreY, 1 - centreY) * ratio).takeIf { horizontal }
                ?: min(2 * min(centreX, 1 - centreX), roomY * ratio)
        }
        val width = wanted.coerceIn(max(MIN_SIZE, MIN_SIZE * ratio), max(maxWidth, MIN_SIZE))
        val height = width / ratio
        val x = (anchorX - width).takeIf { edges.left } ?: anchorX.takeIf { horizontal } ?: (centreX - width / 2)
        val y = (anchorY - height).takeIf { edges.top } ?: anchorY.takeIf { edges.top || edges.bottom } ?: (centreY - height / 2)
        return NormRect(x, y, width, height)
    }
}
