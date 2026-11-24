package org.lineageos.pcviewer.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** A 2D affine transform: x' = a·x + c·y + e, y' = b·x + d·y + f (the order of SVG's matrix()). */
data class Affine(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val e: Float = 0f,
    val f: Float = 0f,
) {
    fun map(x: Float, y: Float): Pair<Float, Float> = (a * x + c * y + e) to (b * x + d * y + f)

    /** This transform after [other]: [other] is applied first. */
    operator fun times(other: Affine) = Affine(
        a * other.a + c * other.b,
        b * other.a + d * other.b,
        a * other.c + c * other.d,
        b * other.c + d * other.d,
        a * other.e + c * other.f + e,
        b * other.e + d * other.f + f,
    )

    companion object {
        val IDENTITY = Affine()

        fun translate(x: Float, y: Float) = Affine(e = x, f = y)

        fun scale(x: Float, y: Float) = Affine(a = x, d = y)

        fun rotate(degrees: Float): Affine {
            val r = Math.toRadians(degrees.toDouble())
            return Affine(cos(r).toFloat(), sin(r).toFloat(), -sin(r).toFloat(), cos(r).toFloat())
        }

        fun skewX(degrees: Float) = Affine(c = tan(Math.toRadians(degrees.toDouble())).toFloat())

        fun skewY(degrees: Float) = Affine(b = tan(Math.toRadians(degrees.toDouble())).toFloat())
    }
}

/** SVG's transform attribute: a list of functions, the rightmost applied first. Unknown ones are ignored. */
object SvgTransforms {
    private val FUNCTION = Regex("""([A-Za-z]+)\s*\(([^)]*)\)""")
    private val SEPARATOR = Regex("""[\s,]+""")

    private val BUILDERS: Map<String, (List<Float>) -> Affine?> = mapOf(
        "matrix" to { v -> v.takeIf { it.size == 6 }?.let { Affine(it[0], it[1], it[2], it[3], it[4], it[5]) } },
        "translate" to { v -> v.firstOrNull()?.let { Affine.translate(it, v.getOrElse(1) { 0f }) } },
        "scale" to { v -> v.firstOrNull()?.let { sx -> Affine.scale(sx, v.getOrElse(1) { sx }) } },
        "rotate" to ::rotation,
        "skewX" to { v -> v.firstOrNull()?.let(Affine::skewX) },
        "skewY" to { v -> v.firstOrNull()?.let(Affine::skewY) },
    )

    fun parse(text: String): Affine = FUNCTION.findAll(text)
        .mapNotNull { match -> BUILDERS[match.groupValues[1]]?.invoke(numbers(match.groupValues[2])) }
        .fold(Affine.IDENTITY) { acc, next -> acc * next }

    /** rotate(a) or rotate(a cx cy): around the origin, or around (cx, cy). */
    private fun rotation(values: List<Float>): Affine? {
        val angle = values.firstOrNull() ?: return null
        val cx = values.getOrElse(1) { 0f }
        val cy = values.getOrElse(2) { 0f }
        return Affine.translate(cx, cy) * Affine.rotate(angle) * Affine.translate(-cx, -cy)
    }

    private fun numbers(text: String): List<Float> =
        text.trim().split(SEPARATOR).mapNotNull { it.toFloatOrNull() }
}
