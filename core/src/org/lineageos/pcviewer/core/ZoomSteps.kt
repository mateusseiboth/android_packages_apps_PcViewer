package org.lineageos.pcviewer.core

/** The zoom presets the zoom in and zoom out buttons step through, for images and PDF pages alike. */
object ZoomSteps {
    private const val EPSILON = 0.001f
    private val STEPS = listOf(0.05f, 0.1f, 0.2f, 0.25f, 0.33f, 0.5f, 0.67f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 6f, 8f)

    fun zoomIn(scale: Float, max: Float): Float = (STEPS.firstOrNull { it > scale + EPSILON } ?: max).coerceAtMost(max)

    fun zoomOut(scale: Float, min: Float): Float = (STEPS.lastOrNull { it < scale - EPSILON } ?: min).coerceAtLeast(min)
}
