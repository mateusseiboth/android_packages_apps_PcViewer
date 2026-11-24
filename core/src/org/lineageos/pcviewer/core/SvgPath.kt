package org.lineageos.pcviewer.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Path drawing operations in absolute coordinates; arcs and smooth curves are already resolved. */
sealed interface PathOp {
    fun end(): Pair<Float, Float>

    data class Move(val x: Float, val y: Float) : PathOp {
        override fun end() = x to y
    }

    data class Line(val x: Float, val y: Float) : PathOp {
        override fun end() = x to y
    }

    data class Quad(val x1: Float, val y1: Float, val x: Float, val y: Float) : PathOp {
        override fun end() = x to y
    }

    data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : PathOp {
        override fun end() = x to y
    }

    data object Close : PathOp {
        override fun end() = Float.NaN to Float.NaN
    }
}

/** SVG path data ("M10 10 h5 a2 2 0 0 1 4 0 z"). Parsing stops at the first error, keeping what came before. */
object SvgPath {
    fun parse(data: String): List<PathOp> = PathBuilder(PathScanner(data)).build()
}

private class PathScanner(private val text: String) {
    private var at = 0
    private val number = Regex("""[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?""")

    fun command(): Char? {
        skipSeparators()
        return text.getOrNull(at)?.takeIf { it.isLetter() }?.also { at++ }
    }

    fun hasNumber(): Boolean {
        skipSeparators()
        return number.matchAt(text, at) != null
    }

    fun numbers(count: Int): FloatArray? {
        val values = FloatArray(count)
        repeat(count) { i -> values[i] = next() ?: return null }
        return values
    }

    /** Arc flags are a single 0 or 1 and may be written without separators ("0120"). */
    fun flag(): Boolean? {
        skipSeparators()
        val c = text.getOrNull(at)?.takeIf { it == '0' || it == '1' } ?: return null
        at++
        return c == '1'
    }

    private fun next(): Float? {
        skipSeparators()
        val match = number.matchAt(text, at) ?: return null
        at = match.range.last + 1
        return match.value.toFloatOrNull()
    }

    private fun skipSeparators() {
        while (at < text.length && (text[at].isWhitespace() || text[at] == ',')) at++
    }
}

private class PathBuilder(private val scan: PathScanner) {
    private val ops = mutableListOf<PathOp>()
    private var x = 0f
    private var y = 0f
    private var startX = 0f
    private var startY = 0f
    private var cubicControl: Pair<Float, Float>? = null
    private var quadControl: Pair<Float, Float>? = null

    /** Each command reads its numbers; false means the data was broken. */
    private val commands: Map<Char, (Boolean) -> Boolean> = mapOf(
        'M' to ::move, 'L' to ::line, 'H' to ::horizontal, 'V' to ::vertical, 'C' to ::cubic,
        'S' to ::smoothCubic, 'Q' to ::quad, 'T' to ::smoothQuad, 'A' to ::arc, 'Z' to { _ -> close() },
    )

    /** Coordinates repeated after a command repeat it; after a move they are lines. */
    private val repeats = mapOf('M' to 'L')

    fun build(): List<PathOp> {
        while (true) {
            val letter = scan.command() ?: return ops
            val command = commands[letter.uppercaseChar()] ?: return ops
            val relative = letter.isLowerCase()
            if (!command(relative)) return ops
            val again = commands.getValue(repeats[letter.uppercaseChar()] ?: letter.uppercaseChar())
            while (letter.uppercaseChar() != 'Z' && scan.hasNumber()) {
                if (!again(relative)) return ops
            }
        }
    }

    private fun base(relative: Boolean) = (x to y).takeIf { relative } ?: (0f to 0f)

    private fun move(relative: Boolean): Boolean {
        val (bx, by) = base(relative)
        val v = scan.numbers(2) ?: return false
        moveTo(bx + v[0], by + v[1])
        startX = x
        startY = y
        ops += PathOp.Move(x, y)
        return true
    }

    private fun line(relative: Boolean): Boolean {
        val (bx, by) = base(relative)
        val v = scan.numbers(2) ?: return false
        return lineTo(bx + v[0], by + v[1])
    }

    private fun horizontal(relative: Boolean): Boolean {
        val v = scan.numbers(1) ?: return false
        return lineTo(v[0] + x.takeIf { relative }.orZero(), y)
    }

    private fun vertical(relative: Boolean): Boolean {
        val v = scan.numbers(1) ?: return false
        return lineTo(x, v[0] + y.takeIf { relative }.orZero())
    }

    private fun cubic(relative: Boolean): Boolean {
        val (bx, by) = base(relative)
        val v = scan.numbers(6) ?: return false
        return cubicTo(bx + v[0], by + v[1], bx + v[2], by + v[3], bx + v[4], by + v[5])
    }

    /** The first control point mirrors the previous curve's second one, or is the current point. */
    private fun smoothCubic(relative: Boolean): Boolean {
        val (bx, by) = base(relative)
        val v = scan.numbers(4) ?: return false
        val (cx, cy) = cubicControl?.let { (px, py) -> 2 * x - px to 2 * y - py } ?: (x to y)
        return cubicTo(cx, cy, bx + v[0], by + v[1], bx + v[2], by + v[3])
    }

    private fun quad(relative: Boolean): Boolean {
        val (bx, by) = base(relative)
        val v = scan.numbers(4) ?: return false
        return quadTo(bx + v[0], by + v[1], bx + v[2], by + v[3])
    }

    private fun smoothQuad(relative: Boolean): Boolean {
        val (bx, by) = base(relative)
        val v = scan.numbers(2) ?: return false
        val (cx, cy) = quadControl?.let { (px, py) -> 2 * x - px to 2 * y - py } ?: (x to y)
        return quadTo(cx, cy, bx + v[0], by + v[1])
    }

    private fun arc(relative: Boolean): Boolean {
        val (bx, by) = base(relative)
        val radii = scan.numbers(3) ?: return false
        val large = scan.flag() ?: return false
        val sweep = scan.flag() ?: return false
        val end = scan.numbers(2) ?: return false
        val segments = Arcs.toCubics(x, y, radii[0], radii[1], radii[2], large, sweep, bx + end[0], by + end[1])
        ops += segments
        moveTo(bx + end[0], by + end[1])
        return true
    }

    private fun close(): Boolean {
        ops += PathOp.Close
        moveTo(startX, startY)
        return true
    }

    private fun lineTo(nx: Float, ny: Float): Boolean {
        ops += PathOp.Line(nx, ny)
        moveTo(nx, ny)
        return true
    }

    private fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, nx: Float, ny: Float): Boolean {
        ops += PathOp.Cubic(x1, y1, x2, y2, nx, ny)
        moveTo(nx, ny)
        cubicControl = x2 to y2
        return true
    }

    private fun quadTo(x1: Float, y1: Float, nx: Float, ny: Float): Boolean {
        ops += PathOp.Quad(x1, y1, nx, ny)
        moveTo(nx, ny)
        quadControl = x1 to y1
        return true
    }

    /** Moves the current point; the smooth-curve memory only survives the curve that set it. */
    private fun moveTo(nx: Float, ny: Float) {
        x = nx
        y = ny
        cubicControl = null
        quadControl = null
    }

    private fun Float?.orZero() = this ?: 0f
}

/** Elliptical arcs to cubic Béziers (SVG 1.1, appendix F.6), in pieces of at most a quarter turn. */
internal object Arcs {
    fun toCubics(
        x0: Float, y0: Float, rxIn: Float, ryIn: Float, angle: Float, large: Boolean, sweep: Boolean, x: Float, y: Float,
    ): List<PathOp> {
        if (x0 == x && y0 == y) return emptyList()
        if (rxIn == 0f || ryIn == 0f) return listOf(PathOp.Line(x, y))
        val phi = Math.toRadians(angle.toDouble())
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx2 = (x0 - x) / 2.0
        val dy2 = (y0 - y) / 2.0
        val x1p = cosPhi * dx2 + sinPhi * dy2
        val y1p = -sinPhi * dx2 + cosPhi * dy2
        // Radii too small to reach the end point are scaled up, as the specification says.
        val lambda = x1p * x1p / (rxIn * rxIn) + y1p * y1p / (ryIn * ryIn)
        val grow = sqrt(lambda).takeIf { lambda > 1 } ?: 1.0
        val rx = abs(rxIn) * grow
        val ry = abs(ryIn) * grow
        val sign = (-1.0).takeIf { large == sweep } ?: 1.0
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val coef = sign * sqrt(max(0.0, num / den))
        val cxp = coef * rx * y1p / ry
        val cyp = -coef * ry * x1p / rx
        val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y) / 2.0
        val ux = (x1p - cxp) / rx
        val uy = (y1p - cyp) / ry
        val start = angleBetween(1.0, 0.0, ux, uy)
        val raw = angleBetween(ux, uy, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        // The sweep flag picks the direction; the raw angle is turned the other way round when it disagrees.
        val delta = when {
            !sweep && raw > 0 -> raw - 2 * PI
            sweep && raw < 0 -> raw + 2 * PI
            else -> raw
        }
        val pieces = ceil(abs(delta) / (PI / 2)).toInt().coerceAtLeast(1)
        val step = delta / pieces
        val k = 4.0 / 3.0 * tan(step / 4)
        val point = { t: Double ->
            (cx + cosPhi * rx * cos(t) - sinPhi * ry * sin(t)) to (cy + sinPhi * rx * cos(t) + cosPhi * ry * sin(t))
        }
        val slope = { t: Double ->
            (-cosPhi * rx * sin(t) - sinPhi * ry * cos(t)) to (-sinPhi * rx * sin(t) + cosPhi * ry * cos(t))
        }
        return (0 until pieces).map { i ->
            val a1 = start + i * step
            val a2 = a1 + step
            val (p1x, p1y) = point(a1)
            val (p2x, p2y) = (x.toDouble() to y.toDouble()).takeIf { i == pieces - 1 } ?: point(a2)
            val (d1x, d1y) = slope(a1)
            val (d2x, d2y) = slope(a2)
            PathOp.Cubic(
                (p1x + k * d1x).toFloat(), (p1y + k * d1y).toFloat(),
                (p2x - k * d2x).toFloat(), (p2y - k * d2y).toFloat(),
                p2x.toFloat(), p2y.toFloat(),
            )
        }
    }

    private fun angleBetween(ux: Double, uy: Double, vx: Double, vy: Double) = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
}
