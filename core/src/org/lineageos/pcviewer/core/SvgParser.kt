package org.lineageos.pcviewer.core

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

/**
 * Reads the subset of SVG the viewer draws: paths and basic shapes, groups with transforms, fill and stroke
 * (colours, linear and radial gradients, opacity), <use>, <symbol> and plain <text>. Clipping, masks,
 * filters, patterns, embedded images and CSS style sheets are ignored.
 */
object SvgParser {
    private const val DEFAULT_WIDTH = 300f
    private const val DEFAULT_HEIGHT = 150f

    /** External DTDs and entities are never fetched; the document is read from the string alone. */
    private val SAFE_FEATURES = mapOf(
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
    )

    fun parse(xml: String): SvgDocument {
        val root = readXml(xml).documentElement
        require(SvgReader.nameOf(root) == "svg") { "not an SVG document" }
        val viewBox = parseViewBox(root.getAttribute("viewBox"))
        val width = absoluteLength(root.getAttribute("width")) ?: viewBox?.width ?: DEFAULT_WIDTH
        val height = absoluteLength(root.getAttribute("height")) ?: viewBox?.height ?: DEFAULT_HEIGHT
        val elements = SvgReader(root).read()
        return SvgDocument(width, height, viewBox ?: ViewBox(0f, 0f, width, height), elements)
    }

    private fun absoluteLength(text: String): Float? =
        text.takeUnless { it.isBlank() || it.trim().endsWith("%") }?.let { SvgLengths.parse(it) }?.takeIf { it > 0 }

    private fun parseViewBox(text: String): ViewBox? {
        val v = text.trim().split(Regex("""[\s,]+""")).mapNotNull { it.toFloatOrNull() }
        return v.takeIf { it.size == 4 && it[2] > 0 && it[3] > 0 }?.let { ViewBox(it[0], it[1], it[2], it[3]) }
    }

    private fun readXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
            isExpandEntityReferences = false
        }
        SAFE_FEATURES.forEach { (name, value) -> runCatching { factory.setFeature(name, value) } }
        val builder = factory.newDocumentBuilder()
        builder.setErrorHandler(StrictErrors)
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        return builder.parse(InputSource(StringReader(xml)))
    }

    private object StrictErrors : ErrorHandler {
        override fun warning(exception: SAXParseException) = Unit

        override fun error(exception: SAXParseException) = throw exception

        override fun fatalError(exception: SAXParseException) = throw exception
    }
}

/** Inherited presentation properties, as written; [opacity] carries the product of the ancestors' opacities. */
private data class SvgStyle(
    val fill: String = "black",
    val fillOpacity: Float = 1f,
    val fillRule: String = "nonzero",
    val stroke: String = "none",
    val strokeOpacity: Float = 1f,
    val strokeWidth: Float = 1f,
    val lineCap: String = "butt",
    val lineJoin: String = "miter",
    val opacity: Float = 1f,
    val fontSize: Float = 16f,
    val fontWeight: String = "normal",
    val anchor: String = "start",
    val color: String = "black",
    val visible: Boolean = true,
)

private class SvgReader(private val root: Element) {
    private val ids: Map<String, Element> = allElements(root).filter { it.getAttribute("id").isNotEmpty() }
        .associateBy { it.getAttribute("id") }

    private val setters: Map<String, (SvgStyle, String) -> SvgStyle> = mapOf(
        "fill" to { s, v -> s.copy(fill = v) },
        "fill-opacity" to { s, v -> s.copy(fillOpacity = fraction(v) ?: s.fillOpacity) },
        "fill-rule" to { s, v -> s.copy(fillRule = v) },
        "stroke" to { s, v -> s.copy(stroke = v) },
        "stroke-opacity" to { s, v -> s.copy(strokeOpacity = fraction(v) ?: s.strokeOpacity) },
        "stroke-width" to { s, v -> s.copy(strokeWidth = SvgLengths.parse(v) ?: s.strokeWidth) },
        "stroke-linecap" to { s, v -> s.copy(lineCap = v) },
        "stroke-linejoin" to { s, v -> s.copy(lineJoin = v) },
        "opacity" to { s, v -> s.copy(opacity = s.opacity * (fraction(v) ?: 1f)) },
        "font-size" to { s, v -> s.copy(fontSize = SvgLengths.parse(v, s.fontSize) ?: s.fontSize) },
        "font-weight" to { s, v -> s.copy(fontWeight = v) },
        "text-anchor" to { s, v -> s.copy(anchor = v) },
        "color" to { s, v -> s.copy(color = v) },
        "visibility" to { s, v -> s.copy(visible = v != "hidden" && v != "collapse") },
    )

    private val skipped = setOf(
        "defs", "title", "desc", "metadata", "linearGradient", "radialGradient", "clipPath", "mask", "pattern",
        "filter", "symbol", "style", "script", "marker", "image", "foreignObject",
    )

    private val shapes: Map<String, (Element) -> List<PathOp>?> = mapOf(
        "path" to { el -> SvgPath.parse(el.getAttribute("d")).takeIf { it.isNotEmpty() } },
        "rect" to ::rectPath,
        "circle" to { el -> el.length("r").takeIf { it > 0 }?.let { r -> ellipse(el.length("cx"), el.length("cy"), r, r) } },
        "ellipse" to ::ellipsePath,
        "line" to { el ->
            listOf(PathOp.Move(el.length("x1"), el.length("y1")), PathOp.Line(el.length("x2"), el.length("y2")))
        },
        "polyline" to { el -> polyPath(el, close = false) },
        "polygon" to { el -> polyPath(el, close = true) },
    )

    private val drawers: Map<String, (Element, SvgStyle, Affine, Int) -> List<SvgElement>> =
        listOf("g", "svg", "a", "switch").associateWith { _ -> ::children } +
            shapes.mapValues { (_, path) -> { el: Element, s: SvgStyle, t: Affine, _: Int -> shape(path(el), s, t) } } +
            mapOf("text" to ::text, "use" to ::use)

    private val caps = mapOf("butt" to LineCap.BUTT, "round" to LineCap.ROUND, "square" to LineCap.SQUARE)
    private val joins = mapOf("miter" to LineJoin.MITER, "round" to LineJoin.ROUND, "bevel" to LineJoin.BEVEL)
    private val anchors = mapOf("start" to TextAnchor.START, "middle" to TextAnchor.MIDDLE, "end" to TextAnchor.END)

    fun read(): List<SvgElement> = children(root, SvgStyle(), Affine.IDENTITY, 0)

    private fun children(parent: Element, style: SvgStyle, transform: Affine, depth: Int): List<SvgElement> =
        childElements(parent).flatMap { element(it, style, transform, depth) }

    private fun element(el: Element, parentStyle: SvgStyle, parentTransform: Affine, depth: Int): List<SvgElement> {
        val name = nameOf(el)
        val declared = declarations(el)
        if (name in skipped || declared["display"] == "none") return emptyList()
        val style = declared.entries.fold(parentStyle) { s, (key, value) -> setters[key]?.invoke(s, value) ?: s }
        val transform = parentTransform * SvgTransforms.parse(el.getAttribute("transform"))
        return drawers[name]?.invoke(el, style, transform, depth) ?: emptyList()
    }

    /** Presentation attributes, then the style attribute, which wins. */
    private fun declarations(el: Element): Map<String, String> {
        val attributes = (setters.keys + "display").mapNotNull { key ->
            el.getAttribute(key).takeIf { it.isNotEmpty() }?.let { key to it.trim() }
        }
        val inline = el.getAttribute("style").split(';').mapNotNull { rule ->
            rule.split(':', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> k.trim() to v.trim() }
        }
        return (attributes + inline).toMap()
    }

    private fun shape(path: List<PathOp>?, style: SvgStyle, transform: Affine): List<SvgElement> {
        if (path == null || !style.visible) return emptyList()
        return listOf(
            SvgShape(
                path = path,
                fill = paint(style.fill, style.fillOpacity * style.opacity, style.color),
                stroke = paint(style.stroke, style.strokeOpacity * style.opacity, style.color),
                strokeWidth = style.strokeWidth,
                evenOdd = style.fillRule == "evenodd",
                transform = transform,
                lineCap = caps[style.lineCap] ?: LineCap.BUTT,
                lineJoin = joins[style.lineJoin] ?: LineJoin.MITER,
            ),
        )
    }

    private fun text(el: Element, style: SvgStyle, transform: Affine, depth: Int): List<SvgElement> {
        val content = el.textContent.trim().replace(Regex("""\s+"""), " ")
        if (content.isEmpty() || !style.visible) return emptyList()
        val bold = style.fontWeight in setOf("bold", "bolder") || (style.fontWeight.toIntOrNull() ?: 0) >= 600
        return listOf(
            SvgText(
                text = content,
                x = firstNumber(el.getAttribute("x")),
                y = firstNumber(el.getAttribute("y")),
                fontSize = style.fontSize,
                fill = paint(style.fill, style.fillOpacity * style.opacity, style.color),
                anchor = anchors[style.anchor] ?: TextAnchor.START,
                bold = bold,
                transform = transform,
            ),
        )
    }

    /** Draws the referenced element moved by x and y; references nested deeper than [MAX_USE_DEPTH] are dropped. */
    private fun use(el: Element, style: SvgStyle, transform: Affine, depth: Int): List<SvgElement> {
        if (depth >= MAX_USE_DEPTH) return emptyList()
        val target = referenceOf(el)?.let(ids::get) ?: return emptyList()
        val moved = transform * Affine.translate(el.length("x"), el.length("y"))
        if (nameOf(target) == "symbol") return children(target, style, moved, depth + 1)
        return element(target, style, moved, depth + 1)
    }

    private fun paint(raw: String, opacity: Float, color: String): SvgPaint? {
        val value = raw.takeUnless { it == "currentColor" } ?: color
        val gradient = Regex("""url\(\s*#([^)\s]+)\s*\)\s*(.*)""").matchEntire(value)
        gradient ?: return SvgColors.parse(value)?.let { SvgPaint.Solid(SvgColors.withAlpha(it, opacity)) }
        val (id, fallback) = gradient.destructured
        return gradientOf(id, opacity) ?: fallback.takeIf { it.isNotBlank() }?.let { paint(it, opacity, color) }
    }

    private fun gradientOf(id: String, opacity: Float): SvgPaint? {
        val el = ids[id] ?: return null
        val stops = stopsOf(el, opacity)
        val objectBox = el.getAttribute("gradientUnits") != "userSpaceOnUse"
        val transform = SvgTransforms.parse(el.getAttribute("gradientTransform"))
        val builders: Map<String, () -> SvgPaint> = mapOf(
            "linearGradient" to {
                SvgPaint.Linear(
                    coord(el, "x1", 0f), coord(el, "y1", 0f), coord(el, "x2", 1f), coord(el, "y2", 0f),
                    stops, objectBox, transform,
                )
            },
            "radialGradient" to {
                SvgPaint.Radial(coord(el, "cx", 0.5f), coord(el, "cy", 0.5f), coord(el, "r", 0.5f), stops, objectBox, transform)
            },
        )
        return builders[nameOf(el)]?.invoke()
    }

    /** A gradient without stops of its own takes the stops of the gradient it references. */
    private fun stopsOf(el: Element, opacity: Float, depth: Int = 0): List<SvgStop> {
        val own = childElements(el).filter { nameOf(it) == "stop" }.map { stopOf(it, opacity) }
        if (own.isNotEmpty() || depth >= MAX_USE_DEPTH) return own
        return referenceOf(el)?.let(ids::get)?.let { stopsOf(it, opacity, depth + 1) } ?: own
    }

    private fun stopOf(el: Element, opacity: Float): SvgStop {
        val declared = (listOf("stop-color", "stop-opacity").mapNotNull { key ->
            el.getAttribute(key).takeIf { it.isNotEmpty() }?.let { key to it }
        } + el.getAttribute("style").split(';').mapNotNull { rule ->
            rule.split(':', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> k.trim() to v.trim() }
        }).toMap()
        val color = declared["stop-color"]?.let { SvgColors.parse(it) } ?: 0xFF000000.toInt()
        val stopOpacity = declared["stop-opacity"]?.let { fraction(it) } ?: 1f
        return SvgStop(fraction(el.getAttribute("offset"))?.coerceIn(0f, 1f) ?: 0f, SvgColors.withAlpha(color, stopOpacity * opacity))
    }

    private fun rectPath(el: Element): List<PathOp>? {
        val x = el.length("x")
        val y = el.length("y")
        val w = el.length("width")
        val h = el.length("height")
        if (w <= 0 || h <= 0) return null
        val rxGiven = SvgLengths.parse(el.getAttribute("rx").ifEmpty { null })
        val ryGiven = SvgLengths.parse(el.getAttribute("ry").ifEmpty { null })
        val rx = (rxGiven ?: ryGiven ?: 0f).coerceIn(0f, w / 2)
        val ry = (ryGiven ?: rxGiven ?: 0f).coerceIn(0f, h / 2)
        if (rx == 0f || ry == 0f) {
            return listOf(PathOp.Move(x, y), PathOp.Line(x + w, y), PathOp.Line(x + w, y + h), PathOp.Line(x, y + h), PathOp.Close)
        }
        val kx = KAPPA * rx
        val ky = KAPPA * ry
        return listOf(
            PathOp.Move(x + rx, y),
            PathOp.Line(x + w - rx, y),
            PathOp.Cubic(x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry),
            PathOp.Line(x + w, y + h - ry),
            PathOp.Cubic(x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h),
            PathOp.Line(x + rx, y + h),
            PathOp.Cubic(x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry),
            PathOp.Line(x, y + ry),
            PathOp.Cubic(x, y + ry - ky, x + rx - kx, y, x + rx, y),
            PathOp.Close,
        )
    }

    private fun ellipsePath(el: Element): List<PathOp>? {
        val rx = el.length("rx")
        val ry = el.length("ry")
        if (rx <= 0 || ry <= 0) return null
        return ellipse(el.length("cx"), el.length("cy"), rx, ry)
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): List<PathOp> {
        val kx = KAPPA * rx
        val ky = KAPPA * ry
        return listOf(
            PathOp.Move(cx + rx, cy),
            PathOp.Cubic(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry),
            PathOp.Cubic(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy),
            PathOp.Cubic(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry),
            PathOp.Cubic(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy),
            PathOp.Close,
        )
    }

    private fun polyPath(el: Element, close: Boolean): List<PathOp>? {
        val v = el.getAttribute("points").trim().split(Regex("""[\s,]+""")).mapNotNull { it.toFloatOrNull() }
        val points = v.chunked(2).filter { it.size == 2 }
        if (points.size < 2) return null
        val lines = listOf(PathOp.Move(points[0][0], points[0][1])) + points.drop(1).map { PathOp.Line(it[0], it[1]) }
        return lines + listOfNotNull(PathOp.Close.takeIf { close })
    }

    private fun referenceOf(el: Element): String? {
        val href = el.getAttribute("href").ifEmpty { el.getAttributeNS(XLINK, "href") }.ifEmpty { el.getAttribute("xlink:href") }
        return href.takeIf { it.startsWith("#") }?.substring(1)
    }

    /** A gradient coordinate: a number, or a percentage as a fraction. */
    private fun coord(el: Element, name: String, default: Float): Float = fraction(el.getAttribute(name)) ?: default

    private fun Element.length(name: String): Float = SvgLengths.parse(getAttribute(name).ifEmpty { null }) ?: 0f

    private fun firstNumber(text: String): Float =
        text.trim().split(Regex("""[\s,]+""")).firstOrNull()?.let { SvgLengths.parse(it) } ?: 0f

    companion object {
        private const val XLINK = "http://www.w3.org/1999/xlink"
        private const val MAX_USE_DEPTH = 8
        private const val KAPPA = 0.5522848f

        fun nameOf(el: Element): String = el.localName ?: el.nodeName.substringAfter(':')

        fun fraction(text: String): Float? {
            val value = text.trim()
            return value.removeSuffix("%").toFloatOrNull()?.let { it / 100f }?.takeIf { value.endsWith("%") }
                ?: value.toFloatOrNull()
        }

        private fun childElements(parent: Element): List<Element> =
            (0 until parent.childNodes.length).mapNotNull { parent.childNodes.item(it) as? Element }

        private fun allElements(root: Element): List<Element> = listOf(root) + childElements(root).flatMap { allElements(it) }
    }
}
