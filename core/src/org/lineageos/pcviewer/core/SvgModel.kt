package org.lineageos.pcviewer.core

data class ViewBox(val x: Float, val y: Float, val width: Float, val height: Float)

data class SvgStop(val offset: Float, val argb: Int)

/** How a shape is filled or stroked. Gradients in [objectBox] units are relative to the shape's bounds. */
sealed interface SvgPaint {
    data class Solid(val argb: Int) : SvgPaint

    data class Linear(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val stops: List<SvgStop>, val objectBox: Boolean, val transform: Affine = Affine.IDENTITY,
    ) : SvgPaint

    data class Radial(
        val cx: Float, val cy: Float, val r: Float,
        val stops: List<SvgStop>, val objectBox: Boolean, val transform: Affine = Affine.IDENTITY,
    ) : SvgPaint
}

enum class LineCap { BUTT, ROUND, SQUARE }

enum class LineJoin { MITER, ROUND, BEVEL }

enum class TextAnchor { START, MIDDLE, END }

/** Something to draw, in the document's user units; [transform] maps it into the viewBox. */
sealed interface SvgElement {
    val transform: Affine
}

data class SvgShape(
    val path: List<PathOp>,
    val fill: SvgPaint?,
    val stroke: SvgPaint?,
    val strokeWidth: Float,
    val evenOdd: Boolean,
    override val transform: Affine,
    val lineCap: LineCap = LineCap.BUTT,
    val lineJoin: LineJoin = LineJoin.MITER,
) : SvgElement

data class SvgText(
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float,
    val fill: SvgPaint?,
    val anchor: TextAnchor,
    val bold: Boolean,
    override val transform: Affine,
) : SvgElement

data class SvgDocument(val width: Float, val height: Float, val viewBox: ViewBox, val elements: List<SvgElement>) {
    /** Maps the viewBox into a [targetWidth] x [targetHeight] canvas, whole and centred (xMidYMid meet). */
    fun viewTransform(targetWidth: Float, targetHeight: Float): Affine {
        val scale = minOf(targetWidth / viewBox.width, targetHeight / viewBox.height)
        val tx = (targetWidth - viewBox.width * scale) / 2 - viewBox.x * scale
        val ty = (targetHeight - viewBox.height * scale) / 2 - viewBox.y * scale
        return Affine(scale, 0f, 0f, scale, tx, ty)
    }
}

/** SVG lengths in CSS pixels (96 per inch); percentages are of [reference]. */
internal object SvgLengths {
    private val UNITS = mapOf(
        "" to 1f, "px" to 1f, "pt" to 4f / 3f, "pc" to 16f, "mm" to 96f / 25.4f, "cm" to 96f / 2.54f,
        "in" to 96f, "em" to 16f, "ex" to 8f,
    )
    private val LENGTH = Regex("""\s*([+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)\s*([a-zA-Z%]*)\s*""")

    fun parse(text: String?, reference: Float = 0f): Float? {
        val match = text?.let { LENGTH.matchEntire(it) } ?: return null
        val value = match.groupValues[1].toFloat()
        val unit = match.groupValues[2].lowercase()
        if (unit == "%") return value / 100f * reference
        return UNITS[unit]?.let { value * it }
    }
}
