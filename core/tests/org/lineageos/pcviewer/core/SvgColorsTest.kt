package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SvgColorsTest {
    @Test
    fun hexForms() {
        assertEquals(0xFFFF0000.toInt(), SvgColors.parse("#f00"))
        assertEquals(0xFF123456.toInt(), SvgColors.parse("#123456"))
        assertEquals(0x8000FF00.toInt(), SvgColors.parse("#00ff0080"))
    }

    @Test
    fun functionalForms() {
        assertEquals(0xFFFF0000.toInt(), SvgColors.parse("rgb(255, 0, 0)"))
        assertEquals(0xFFFF0000.toInt(), SvgColors.parse("rgb(100%,0%,0%)"))
        assertEquals(0x800000FF.toInt(), SvgColors.parse("rgba(0,0,255,0.5)"))
    }

    @Test
    fun namedColours() {
        assertEquals(0xFFFF0000.toInt(), SvgColors.parse("red"))
        assertEquals(0xFF008000.toInt(), SvgColors.parse("Green"))
        assertEquals(0x00000000, SvgColors.parse("transparent"))
    }

    @Test
    fun noneAndUnknown() {
        assertNull(SvgColors.parse("none"))
        assertNull(SvgColors.parse("#zzz"))
    }
}
