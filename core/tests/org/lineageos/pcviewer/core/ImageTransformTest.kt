package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTransformTest {
    private val viewport = Size2(1000f, 500f)
    private val photo = Size2(4000f, 1000f)

    private fun assertNear(expected: Float, actual: Float) = assertEquals(expected, actual, 0.001f)

    @Test
    fun bigImagesStartFitted() {
        val view = ImageTransform.initial(photo, viewport)
        assertNear(0.25f, view.scale)
        assertEquals(Point2.ZERO, view.offset)
        assertTrue(view.isFit)
    }

    @Test
    fun smallImagesStartAtActualSize() {
        val view = ImageTransform.initial(Size2(200f, 100f), viewport)
        assertNear(1f, view.scale)
    }

    @Test
    fun rotationSwapsTheSidesForFitting() {
        val view = ImageTransform.initial(photo, viewport).rotateClockwise()
        assertEquals(90, view.rotation)
        assertNear(0.125f, view.scale)
        assertTrue(view.isFit)
    }

    @Test
    fun rotationWrapsBothWays() {
        val view = ImageTransform.initial(photo, viewport)
        assertEquals(270, view.rotateCounterClockwise().rotation)
        assertEquals(0, view.rotateClockwise().rotateClockwise().rotateClockwise().rotateClockwise().rotation)
    }

    @Test
    fun zoomInAndOutFollowThePresetSteps() {
        val view = ImageTransform.initial(photo, viewport)
        assertNear(0.33f, view.zoomIn().scale)
        assertNear(0.2f, view.zoomOut().scale)
        assertFalse(view.zoomIn().isFit)
    }

    @Test
    fun zoomStopsAtTheLimits() {
        val big = ImageTransform.initial(photo, viewport).zoomTo(100f)
        assertNear(ImageTransform.MAX_SCALE, big.scale)
        assertNear(ImageTransform.MAX_SCALE, big.zoomIn().scale)
        val tiny = ImageTransform.initial(photo, viewport).zoomTo(0.0001f)
        assertNear(ImageTransform.MIN_SCALE, tiny.scale)
    }

    @Test
    fun zoomKeepsThePointUnderTheCursorStill() {
        val view = ImageTransform.initial(photo, viewport).zoomTo(1f)
        val focus = Point2(200f, 100f)
        val zoomed = view.zoomTo(2f, focus)
        // The image point under the cursor was 200 px right of the centre at 1x; at 2x it would be 400 px,
        // so the image moves 200 px left to keep it under the cursor.
        assertNear(-200f, zoomed.offset.x)
        assertNear(-100f, zoomed.offset.y)
    }

    @Test
    fun panIsLimitedToTheImageEdges() {
        val view = ImageTransform.initial(photo, viewport).zoomTo(1f).panBy(5000f, -5000f)
        // 4000x1000 at 1x in 1000x500: 1500 px of slack each way horizontally, 250 vertically.
        assertNear(1500f, view.offset.x)
        assertNear(-250f, view.offset.y)
    }

    @Test
    fun imagesSmallerThanTheViewportStayCentred() {
        val view = ImageTransform.initial(photo, viewport).panBy(300f, 300f)
        assertEquals(Point2.ZERO, view.offset)
    }

    @Test
    fun fitAfterZoomingRestoresTheFit() {
        val view = ImageTransform.initial(photo, viewport).zoomTo(3f).panBy(100f, 0f).fit()
        assertNear(0.25f, view.scale)
        assertEquals(Point2.ZERO, view.offset)
        assertTrue(view.isFit)
    }

    @Test
    fun resizingTheWindowRefitsAFittedImage() {
        val view = ImageTransform.initial(photo, viewport).withViewport(Size2(2000f, 1000f))
        assertNear(0.5f, view.scale)
    }

    @Test
    fun resizingTheWindowKeepsTheZoomOfAZoomedImage() {
        val view = ImageTransform.initial(photo, viewport).zoomTo(2f).withViewport(Size2(2000f, 1000f))
        assertNear(2f, view.scale)
    }

    @Test
    fun percentIsRounded() {
        assertEquals(25, ImageTransform.initial(photo, viewport).percent)
        assertEquals(33, ImageTransform.initial(photo, viewport).zoomIn().percent)
    }

    @Test
    fun zoomByMultipliesTheScale() {
        val view = ImageTransform.initial(photo, viewport).zoomBy(2f, Point2.ZERO)
        assertNear(0.5f, view.scale)
    }

    @Test
    fun toggleZoomSwitchesBetweenFitAndActualSize() {
        val fitted = ImageTransform.initial(photo, viewport)
        assertNear(1f, fitted.toggleZoom(Point2.ZERO).scale)
        assertTrue(fitted.toggleZoom(Point2.ZERO).toggleZoom(Point2.ZERO).isFit)
    }
}
