package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgParserTest {
    private fun svg(body: String, attributes: String = "width=\"100\" height=\"100\"") =
        SvgParser.parse("<svg xmlns=\"http://www.w3.org/2000/svg\" $attributes>$body</svg>")

    private fun only(document: SvgDocument) = document.elements.single() as SvgShape

    @Test
    fun rectWithSizeAndViewBox() {
        val doc = svg("<rect x=\"10\" y=\"10\" width=\"20\" height=\"30\" fill=\"#00f\"/>", "width=\"100\" height=\"50\" viewBox=\"0 0 200 100\"")
        assertEquals(100f, doc.width, 0.001f)
        assertEquals(50f, doc.height, 0.001f)
        assertEquals(ViewBox(0f, 0f, 200f, 100f), doc.viewBox)
        val rect = only(doc)
        assertEquals(SvgPaint.Solid(0xFF0000FF.toInt()), rect.fill)
        assertEquals(
            listOf(PathOp.Move(10f, 10f), PathOp.Line(30f, 10f), PathOp.Line(30f, 40f), PathOp.Line(10f, 40f), PathOp.Close),
            rect.path,
        )
    }

    @Test
    fun sizeComesFromTheViewBoxOrTheDefault() {
        val fromViewBox = svg("", "viewBox=\"0 0 24 12\"")
        assertEquals(24f, fromViewBox.width, 0.001f)
        assertEquals(12f, fromViewBox.height, 0.001f)
        val bare = svg("", "")
        assertEquals(300f, bare.width, 0.001f)
        assertEquals(150f, bare.height, 0.001f)
    }

    @Test
    fun units() {
        assertEquals(192f, svg("", "width=\"2in\" height=\"1cm\"").width, 0.01f)
        assertEquals(37.795f, svg("", "width=\"2in\" height=\"1cm\"").height, 0.01f)
    }

    @Test
    fun paintDefaults() {
        val shape = only(svg("<circle cx=\"5\" cy=\"5\" r=\"5\"/>"))
        assertEquals(SvgPaint.Solid(0xFF000000.toInt()), shape.fill)
        assertNull(shape.stroke)
        assertEquals(1f, shape.strokeWidth, 0.001f)
    }

    @Test
    fun groupsPassStyleAndTransformDown() {
        val shape = only(svg("<g fill=\"red\" transform=\"translate(5,5)\"><circle cx=\"0\" cy=\"0\" r=\"1\"/></g>"))
        assertEquals(SvgPaint.Solid(0xFFFF0000.toInt()), shape.fill)
        assertEquals(5f to 5f, shape.transform.map(0f, 0f))
    }

    @Test
    fun styleAttributeWins() {
        val shape = only(svg("<path d=\"M0 0L1 1\" fill=\"red\" style=\"stroke:#0f0; stroke-width:2;fill:none\"/>"))
        assertNull(shape.fill)
        assertEquals(SvgPaint.Solid(0xFF00FF00.toInt()), shape.stroke)
        assertEquals(2f, shape.strokeWidth, 0.001f)
    }

    @Test
    fun opacityMultipliesIntoTheColour() {
        val shape = only(svg("<g opacity=\"0.5\"><rect width=\"1\" height=\"1\" fill=\"red\" fill-opacity=\"0.5\"/></g>"))
        assertEquals(SvgPaint.Solid(0x40FF0000), shape.fill)
    }

    @Test
    fun linearGradientByReference() {
        val doc = svg(
            "<defs><linearGradient id=\"g\"><stop offset=\"0\" stop-color=\"#f00\"/>" +
                "<stop offset=\"100%\" stop-color=\"#00f\" stop-opacity=\"0.5\"/></linearGradient></defs>" +
                "<rect width=\"1\" height=\"1\" fill=\"url(#g)\"/>",
        )
        val fill = only(doc).fill as SvgPaint.Linear
        assertEquals(listOf(SvgStop(0f, 0xFFFF0000.toInt()), SvgStop(1f, 0x800000FF.toInt())), fill.stops)
        assertEquals(listOf(0f, 0f, 1f, 0f), listOf(fill.x1, fill.y1, fill.x2, fill.y2))
        assertTrue(fill.objectBox)
    }

    @Test
    fun radialGradient() {
        val doc = svg(
            "<radialGradient id=\"r\" gradientUnits=\"userSpaceOnUse\" cx=\"5\" cy=\"6\" r=\"7\">" +
                "<stop offset=\"0\" stop-color=\"white\"/></radialGradient><circle r=\"1\" fill=\"url(#r)\"/>",
        )
        val fill = only(doc).fill as SvgPaint.Radial
        assertEquals(listOf(5f, 6f, 7f), listOf(fill.cx, fill.cy, fill.r))
        assertEquals(false, fill.objectBox)
    }

    @Test
    fun useDrawsTheReferencedElementMoved() {
        val doc = svg("<defs><circle id=\"c\" cx=\"0\" cy=\"0\" r=\"1\"/></defs><use href=\"#c\" x=\"10\"/>")
        assertEquals(10f to 0f, only(doc).transform.map(0f, 0f))
    }

    @Test
    fun hiddenAndNonDrawingElementsAreSkipped() {
        val doc = svg(
            "<title>t</title><desc>d</desc><metadata/><rect width=\"1\" height=\"1\" display=\"none\"/>" +
                "<rect width=\"1\" height=\"1\" visibility=\"hidden\"/><rect width=\"0\" height=\"1\"/>",
        )
        assertEquals(emptyList<SvgElement>(), doc.elements)
    }

    @Test
    fun otherShapes() {
        val polygon = only(svg("<polygon points=\"0,0 10,0 10,10\"/>"))
        assertEquals(listOf(PathOp.Move(0f, 0f), PathOp.Line(10f, 0f), PathOp.Line(10f, 10f), PathOp.Close), polygon.path)
        val polyline = only(svg("<polyline points=\"0 0 1 1\" fill=\"none\" stroke=\"black\"/>"))
        assertEquals(listOf(PathOp.Move(0f, 0f), PathOp.Line(1f, 1f)), polyline.path)
        val line = only(svg("<line x1=\"1\" y1=\"2\" x2=\"3\" y2=\"4\" stroke=\"black\"/>"))
        assertEquals(listOf(PathOp.Move(1f, 2f), PathOp.Line(3f, 4f)), line.path)
        val ellipse = only(svg("<ellipse cx=\"5\" cy=\"5\" rx=\"4\" ry=\"2\"/>"))
        assertEquals(6, ellipse.path.size)
        val rounded = only(svg("<rect x=\"0\" y=\"0\" width=\"10\" height=\"10\" rx=\"2\"/>"))
        assertEquals(PathOp.Move(2f, 0f), rounded.path.first())
    }

    @Test
    fun evenOddFillRule() {
        assertTrue(only(svg("<path d=\"M0 0L1 1\" fill-rule=\"evenodd\"/>")).evenOdd)
    }

    @Test
    fun text() {
        val text = svg("<text x=\"1\" y=\"2\" font-size=\"12\" fill=\"#000\" text-anchor=\"middle\">Oi</text>").elements.single() as SvgText
        assertEquals("Oi", text.text)
        assertEquals(listOf(1f, 2f, 12f), listOf(text.x, text.y, text.fontSize))
        assertEquals(TextAnchor.MIDDLE, text.anchor)
    }

    @Test
    fun viewTransformMeetsAndCentres() {
        val doc = svg("", "width=\"100\" height=\"50\" viewBox=\"0 0 200 100\"")
        assertEquals(400f to 200f, doc.viewTransform(400f, 200f).map(200f, 100f))
        assertEquals(0f to 100f, doc.viewTransform(400f, 400f).map(0f, 0f))
    }

    @Test
    fun brokenXmlFails() {
        assertEquals(true, runCatching { SvgParser.parse("<svg><rect") }.isFailure)
        assertEquals(true, runCatching { SvgParser.parse("<html/>") }.isFailure)
    }
}
