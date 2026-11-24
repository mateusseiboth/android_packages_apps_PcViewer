package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SvgTransformsTest {
    private fun assertMaps(transform: String, x: Float, y: Float, toX: Float, toY: Float) {
        val (mx, my) = SvgTransforms.parse(transform).map(x, y)
        assertEquals("x of $transform", toX, mx, 0.001f)
        assertEquals("y of $transform", toY, my, 0.001f)
    }

    @Test
    fun eachKind() {
        assertMaps("translate(10,20)", 1f, 1f, 11f, 21f)
        assertMaps("translate(10)", 1f, 1f, 11f, 1f)
        assertMaps("scale(2)", 3f, 4f, 6f, 8f)
        assertMaps("scale(2, 3)", 3f, 4f, 6f, 12f)
        assertMaps("rotate(90)", 1f, 0f, 0f, 1f)
        assertMaps("rotate(90 10 10)", 20f, 10f, 10f, 20f)
        assertMaps("skewX(45)", 0f, 1f, 1f, 1f)
        assertMaps("skewY(45)", 1f, 0f, 1f, 1f)
        assertMaps("matrix(1 0 0 1 5 6)", 0f, 0f, 5f, 6f)
    }

    @Test
    fun listsApplyRightToLeft() {
        assertMaps("translate(10) scale(2)", 1f, 1f, 12f, 2f)
        assertMaps("scale(2),translate(10)", 1f, 1f, 22f, 2f)
    }

    @Test
    fun invalidIsIdentity() {
        assertMaps("wobble(3)", 4f, 5f, 4f, 5f)
        assertMaps("", 4f, 5f, 4f, 5f)
    }
}
