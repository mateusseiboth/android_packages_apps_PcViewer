package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SvgPathTest {
    private fun assertEnd(op: PathOp, x: Float, y: Float) {
        val (ex, ey) = op.end()
        assertEquals(x, ex, 0.01f)
        assertEquals(y, ey, 0.01f)
    }

    @Test
    fun absoluteCommands() {
        assertEquals(
            listOf(PathOp.Move(10f, 20f), PathOp.Line(30f, 40f), PathOp.Close),
            SvgPath.parse("M10 20 L30 40 Z"),
        )
    }

    @Test
    fun relativeAndAxisCommands() {
        assertEquals(
            listOf(PathOp.Move(10f, 20f), PathOp.Line(15f, 25f), PathOp.Line(25f, 25f), PathOp.Line(25f, 20f), PathOp.Close),
            SvgPath.parse("m10 20 l5 5 h10 v-5 z"),
        )
    }

    @Test
    fun coordinatesAfterAMoveAreLines() {
        assertEquals(
            listOf(PathOp.Move(0f, 0f), PathOp.Line(10f, 10f), PathOp.Line(20f, 0f)),
            SvgPath.parse("M0 0 10 10 20 0"),
        )
    }

    @Test
    fun compactNumbers() {
        assertEquals(listOf(PathOp.Move(1.5f, -2.5f), PathOp.Line(0.5f, 0.5f)), SvgPath.parse("M1.5-2.5.5.5"))
        assertEquals(listOf(PathOp.Move(10f, 0.2f)), SvgPath.parse("M1e1,2E-1"))
    }

    @Test
    fun smoothCurvesReflectTheLastControlPoint() {
        assertEquals(
            listOf(
                PathOp.Move(0f, 0f),
                PathOp.Cubic(0f, 10f, 10f, 10f, 10f, 0f),
                PathOp.Cubic(10f, -10f, 20f, -10f, 20f, 0f),
            ),
            SvgPath.parse("M0 0 C 0 10 10 10 10 0 S 20 -10 20 0"),
        )
        assertEquals(
            listOf(PathOp.Move(0f, 0f), PathOp.Quad(5f, 10f, 10f, 0f), PathOp.Quad(15f, -10f, 20f, 0f)),
            SvgPath.parse("M0 0 Q 5 10 10 0 T 20 0"),
        )
    }

    @Test
    fun arcsBecomeCubics() {
        val ops = SvgPath.parse("M0 0 A 10 10 0 0 1 20 0")
        assertEquals(3, ops.size)
        assertEnd(ops[1], 10f, -10f)
        assertEnd(ops[2], 20f, 0f)
    }

    @Test
    fun relativeArcWithFlagsWrittenTogether() {
        val ops = SvgPath.parse("M0 0a10 10 0 0120 0")
        assertEnd(ops.last(), 20f, 0f)
    }

    @Test
    fun arcWithoutRadiusIsALine() {
        assertEquals(listOf(PathOp.Move(0f, 0f), PathOp.Line(5f, 5f)), SvgPath.parse("M0 0 A 0 0 0 0 1 5 5"))
    }

    @Test
    fun badDataKeepsWhatCameBefore() {
        assertEquals(listOf(PathOp.Move(0f, 0f)), SvgPath.parse("M0 0 L 10 x 20"))
    }
}
