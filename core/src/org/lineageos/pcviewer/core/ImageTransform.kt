package org.lineageos.pcviewer.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Size2(val width: Float, val height: Float) {
    fun swapped() = Size2(height, width)
}

/** A point or a displacement, in pixels, measured from the centre of the viewport. */
data class Point2(val x: Float, val y: Float) {
    operator fun plus(other: Point2) = Point2(x + other.x, y + other.y)

    operator fun minus(other: Point2) = Point2(x - other.x, y - other.y)

    operator fun times(factor: Float) = Point2(x * factor, y * factor)

    companion object {
        val ZERO = Point2(0f, 0f)
    }
}

/**
 * How an image sits in the viewport: [scale] (1 = one image pixel per screen pixel), [offset] of the image's
 * centre from the viewport's centre, and [rotation] in degrees clockwise (0, 90, 180, 270). A fitted image
 * ([isFit]) is refitted when the viewport or the rotation changes.
 */
data class ImageTransform(
    val content: Size2,
    val viewport: Size2,
    val scale: Float,
    val offset: Point2,
    val rotation: Int,
    val isFit: Boolean,
) {
    val percent: Int get() = (scale * 100).roundToInt()

    private val rotatedContent: Size2 get() = content.swapped().takeIf { rotation % 180 != 0 } ?: content

    /** The whole image in view, never enlarged past its actual size. */
    private val fitScale: Float
        get() = min(1f, min(viewport.width / rotatedContent.width, viewport.height / rotatedContent.height))
            .takeIf { it.isFinite() && it > 0f } ?: 1f

    fun fit(): ImageTransform = copy(scale = fitScale, offset = Point2.ZERO, isFit = true)

    fun withViewport(size: Size2): ImageTransform {
        val resized = copy(viewport = size)
        return resized.fit().takeIf { isFit } ?: resized.clamped()
    }

    fun zoomTo(target: Float, focus: Point2 = Point2.ZERO): ImageTransform {
        val next = target.coerceIn(min(MIN_SCALE, fitScale), MAX_SCALE)
        val moved = focus - (focus - offset) * (next / scale)
        return copy(scale = next, offset = moved, isFit = false).clamped()
    }

    fun zoomBy(factor: Float, focus: Point2): ImageTransform = zoomTo(scale * factor, focus)

    fun zoomIn(): ImageTransform = zoomTo(STEPS.firstOrNull { it > scale + EPSILON } ?: MAX_SCALE)

    fun zoomOut(): ImageTransform = zoomTo(STEPS.lastOrNull { it < scale - EPSILON } ?: MIN_SCALE)

    /** Double click: a fitted image goes to its actual size (2x if it already fits at 1x), otherwise back to fit. */
    fun toggleZoom(focus: Point2): ImageTransform {
        if (!isFit) return fit()
        val target = 2f.takeIf { abs(fitScale - 1f) < EPSILON } ?: 1f
        return zoomTo(target, focus)
    }

    fun panBy(dx: Float, dy: Float): ImageTransform = copy(offset = offset + Point2(dx, dy)).clamped()

    fun rotateClockwise(): ImageTransform = rotatedBy(90)

    fun rotateCounterClockwise(): ImageTransform = rotatedBy(-90)

    private fun rotatedBy(degrees: Int): ImageTransform = copy(rotation = Math.floorMod(rotation + degrees, 360)).fit()

    /** The image may not be dragged past its edges; an image smaller than the viewport stays centred. */
    private fun clamped(): ImageTransform {
        val slackX = max(0f, (rotatedContent.width * scale - viewport.width) / 2)
        val slackY = max(0f, (rotatedContent.height * scale - viewport.height) / 2)
        return copy(offset = Point2(offset.x.coerceIn(-slackX, slackX), offset.y.coerceIn(-slackY, slackY)))
    }

    companion object {
        const val MIN_SCALE = 0.05f
        const val MAX_SCALE = 8f
        private const val EPSILON = 0.001f
        private val STEPS = listOf(0.05f, 0.1f, 0.2f, 0.25f, 0.33f, 0.5f, 0.67f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 6f, 8f)

        fun initial(content: Size2, viewport: Size2): ImageTransform =
            ImageTransform(content, viewport, 1f, Point2.ZERO, 0, isFit = true).fit()
    }
}
