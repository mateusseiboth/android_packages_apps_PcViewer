package org.lineageos.pcviewer.core

import kotlin.math.roundToInt

/** SVG/CSS colours as ARGB: #rgb, #rgba, #rrggbb, #rrggbbaa, rgb(), rgba() and the common names. */
object SvgColors {
    private val NAMED = mapOf(
        "black" to 0x000000, "white" to 0xFFFFFF, "red" to 0xFF0000, "green" to 0x008000, "blue" to 0x0000FF,
        "yellow" to 0xFFFF00, "cyan" to 0x00FFFF, "aqua" to 0x00FFFF, "magenta" to 0xFF00FF, "fuchsia" to 0xFF00FF,
        "gray" to 0x808080, "grey" to 0x808080, "silver" to 0xC0C0C0, "maroon" to 0x800000, "olive" to 0x808000,
        "lime" to 0x00FF00, "teal" to 0x008080, "navy" to 0x000080, "purple" to 0x800080, "orange" to 0xFFA500,
        "pink" to 0xFFC0CB, "brown" to 0xA52A2A, "gold" to 0xFFD700, "darkgray" to 0xA9A9A9,
        "darkgrey" to 0xA9A9A9, "lightgray" to 0xD3D3D3, "lightgrey" to 0xD3D3D3, "darkred" to 0x8B0000,
        "darkgreen" to 0x006400, "darkblue" to 0x00008B, "orangered" to 0xFF4500, "indigo" to 0x4B0082,
        "violet" to 0xEE82EE, "skyblue" to 0x87CEEB, "steelblue" to 0x4682B4, "tomato" to 0xFF6347,
        "crimson" to 0xDC143C, "coral" to 0xFF7F50, "salmon" to 0xFA8072, "beige" to 0xF5F5DC,
        "ivory" to 0xFFFFF0, "khaki" to 0xF0E68C, "tan" to 0xD2B48C, "chocolate" to 0xD2691E,
        "turquoise" to 0x40E0D0, "slategray" to 0x708090, "whitesmoke" to 0xF5F5F5, "gainsboro" to 0xDCDCDC,
    ).mapValues { (_, rgb) -> OPAQUE or rgb } + ("transparent" to 0)

    private const val OPAQUE = 0xFF shl 24
    private val FUNCTION = Regex("""rgba?\(([^)]*)\)""")

    private val HEX_FORMS: Map<Int, (String) -> Int> = mapOf(
        3 to { h -> argb(255, twice(h[0]), twice(h[1]), twice(h[2])) },
        4 to { h -> argb(twice(h[3]), twice(h[0]), twice(h[1]), twice(h[2])) },
        6 to { h -> argb(255, h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16)) },
        8 to { h ->
            argb(h.substring(6, 8).toInt(16), h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
        },
    )

    /** The colour, or null for "none" and for anything that is not a colour. */
    fun parse(text: String): Int? {
        val value = text.trim().lowercase()
        NAMED[value]?.let { return it }
        value.takeIf { it.startsWith("#") }?.let { return hex(it.substring(1)) }
        return FUNCTION.matchEntire(value)?.let { functional(it.groupValues[1]) }
    }

    fun withAlpha(argb: Int, factor: Float): Int {
        val alpha = ((argb ushr 24) * factor).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (argb and 0xFFFFFF)
    }

    private fun hex(digits: String): Int? =
        runCatching { HEX_FORMS[digits.length]?.invoke(digits) }.getOrNull()

    private fun functional(arguments: String): Int? {
        val parts = arguments.split(Regex("""[\s,/]+""")).filter { it.isNotEmpty() }
        if (parts.size < 3) return null
        val channels = parts.take(3).map { channel(it) ?: return null }
        val alpha = parts.getOrNull(3)?.let { alpha(it) ?: return null } ?: 255
        return argb(alpha, channels[0], channels[1], channels[2])
    }

    private fun channel(part: String): Int? = part.removeSuffix("%").toFloatOrNull()?.let { value ->
        (value * 2.55f).takeIf { part.endsWith("%") } ?: value
    }?.roundToInt()?.coerceIn(0, 255)

    private fun alpha(part: String): Int? = part.removeSuffix("%").toFloatOrNull()?.let { value ->
        (value / 100f).takeIf { part.endsWith("%") } ?: value
    }?.let { (it * 255).roundToInt().coerceIn(0, 255) }

    private fun twice(c: Char) = "$c$c".toInt(16)

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
}
