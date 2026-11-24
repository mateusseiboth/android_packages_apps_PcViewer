package org.lineageos.pcviewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.max
import org.lineageos.pcviewer.core.Affine
import org.lineageos.pcviewer.core.LineCap
import org.lineageos.pcviewer.core.LineJoin
import org.lineageos.pcviewer.core.PathOp
import org.lineageos.pcviewer.core.SvgDocument
import org.lineageos.pcviewer.core.SvgElement
import org.lineageos.pcviewer.core.SvgPaint
import org.lineageos.pcviewer.core.SvgShape
import org.lineageos.pcviewer.core.SvgStop
import org.lineageos.pcviewer.core.SvgText
import org.lineageos.pcviewer.core.TextAnchor

/** Draws a parsed SVG document (core SvgParser) with android.graphics. */
object SvgRenderer {
    /** The long side of the bitmap: large enough to stay sharp when zoomed in a few times. */
    private const val TARGET_LONG_SIDE = 2048f

    private val caps = mapOf(LineCap.BUTT to Paint.Cap.BUTT, LineCap.ROUND to Paint.Cap.ROUND, LineCap.SQUARE to Paint.Cap.SQUARE)
    private val joins = mapOf(LineJoin.MITER to Paint.Join.MITER, LineJoin.ROUND to Paint.Join.ROUND, LineJoin.BEVEL to Paint.Join.BEVEL)
    private val aligns = mapOf(TextAnchor.START to Paint.Align.LEFT, TextAnchor.MIDDLE to Paint.Align.CENTER, TextAnchor.END to Paint.Align.RIGHT)

    fun render(document: SvgDocument): Bitmap {
        val scale = TARGET_LONG_SIDE / max(document.width, document.height)
        val width = ceil(document.width * scale).toInt().coerceAtLeast(1)
        val height = ceil(document.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val view = document.viewTransform(width.toFloat(), height.toFloat())
        document.elements.forEach { element -> draw(canvas, element, view * element.transform) }
        return bitmap
    }

    private fun draw(canvas: Canvas, element: SvgElement, transform: Affine) {
        canvas.save()
        canvas.concat(matrixOf(transform))
        when (element) {
            is SvgShape -> drawShape(canvas, element)
            is SvgText -> drawText(canvas, element)
        }
        canvas.restore()
    }

    private fun drawShape(canvas: Canvas, shape: SvgShape) {
        val path = pathOf(shape.path).apply { fillType = Path.FillType.EVEN_ODD.takeIf { shape.evenOdd } ?: Path.FillType.WINDING }
        val bounds = RectF().also { path.computeBounds(it, true) }
        shape.fill?.let { fill ->
            canvas.drawPath(path, paintOf(fill, bounds).apply { style = Paint.Style.FILL })
        }
        shape.stroke?.takeIf { shape.strokeWidth > 0 }?.let { stroke ->
            val paint = paintOf(stroke, bounds).apply {
                style = Paint.Style.STROKE
                strokeWidth = shape.strokeWidth
                strokeCap = caps.getValue(shape.lineCap)
                strokeJoin = joins.getValue(shape.lineJoin)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawText(canvas: Canvas, text: SvgText) {
        val fill = text.fill ?: return
        val paint = paintOf(fill, RectF()).apply {
            textSize = text.fontSize
            textAlign = aligns.getValue(text.anchor)
            typeface = Typeface.DEFAULT_BOLD.takeIf { text.bold } ?: Typeface.DEFAULT
        }
        canvas.drawText(text.text, text.x, text.y, paint)
    }

    private fun pathOf(ops: List<PathOp>): Path = Path().apply {
        ops.forEach { op ->
            when (op) {
                is PathOp.Move -> moveTo(op.x, op.y)
                is PathOp.Line -> lineTo(op.x, op.y)
                is PathOp.Quad -> quadTo(op.x1, op.y1, op.x, op.y)
                is PathOp.Cubic -> cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
                PathOp.Close -> close()
            }
        }
    }

    private fun paintOf(paint: SvgPaint, bounds: RectF): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        when (paint) {
            is SvgPaint.Solid -> color = paint.argb
            is SvgPaint.Linear -> shader = LinearGradient(
                paint.x1, paint.y1, paint.x2, paint.y2, colors(paint.stops), offsets(paint.stops), Shader.TileMode.CLAMP,
            ).also { it.setLocalMatrix(gradientMatrix(paint.objectBox, paint.transform, bounds)) }
            is SvgPaint.Radial -> shader = RadialGradient(
                paint.cx, paint.cy, paint.r.coerceAtLeast(0.0001f), colors(paint.stops), offsets(paint.stops),
                Shader.TileMode.CLAMP,
            ).also { it.setLocalMatrix(gradientMatrix(paint.objectBox, paint.transform, bounds)) }
        }
    }

    /** Gradients in bounding-box units span the shape's bounds; the gradient's own transform comes first. */
    private fun gradientMatrix(objectBox: Boolean, transform: Affine, bounds: RectF): Matrix {
        val box = Affine(bounds.width(), 0f, 0f, bounds.height(), bounds.left, bounds.top).takeIf { objectBox } ?: Affine.IDENTITY
        return matrixOf(box * transform)
    }

    /** Android needs at least two stops; a single stop is a flat colour. */
    private fun colors(stops: List<SvgStop>): IntArray {
        val all = stops.ifEmpty { listOf(SvgStop(0f, 0)) }
        return (all + all.takeIf { it.size == 1 }.orEmpty()).map { it.argb }.toIntArray()
    }

    private fun offsets(stops: List<SvgStop>): FloatArray {
        val all = stops.ifEmpty { listOf(SvgStop(0f, 0)) }
        val list = all.map { it.offset } + listOf(1f).takeIf { all.size == 1 }.orEmpty()
        // Offsets must not go backwards.
        return list.runningReduce { previous, next -> max(previous, next) }.toFloatArray()
    }

    fun matrixOf(affine: Affine): Matrix = Matrix().apply {
        setValues(floatArrayOf(affine.a, affine.c, affine.e, affine.b, affine.d, affine.f, 0f, 0f, 1f))
    }
}
