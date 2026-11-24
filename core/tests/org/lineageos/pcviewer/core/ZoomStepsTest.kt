package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomStepsTest {
    @Test
    fun stepsUpAndDownFromAnyScale() {
        assertEquals(1.25f, ZoomSteps.zoomIn(1f, max = 8f), 0.001f)
        assertEquals(0.75f, ZoomSteps.zoomOut(1f, min = 0.05f), 0.001f)
        assertEquals(1f, ZoomSteps.zoomIn(0.9f, max = 8f), 0.001f)
        assertEquals(0.75f, ZoomSteps.zoomOut(0.9f, min = 0.05f), 0.001f)
    }

    @Test
    fun stopsAtTheBounds() {
        assertEquals(4f, ZoomSteps.zoomIn(4f, max = 4f), 0.001f)
        assertEquals(0.5f, ZoomSteps.zoomOut(0.5f, min = 0.5f), 0.001f)
        assertEquals(4f, ZoomSteps.zoomIn(3.5f, max = 4f), 0.001f)
    }
}
