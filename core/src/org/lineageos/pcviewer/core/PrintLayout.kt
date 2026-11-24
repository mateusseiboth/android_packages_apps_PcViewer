package org.lineageos.pcviewer.core

import kotlin.math.min

data class Area(val left: Float, val top: Float, val width: Float, val height: Float)

/** Pages for printing: text split into lines and pages, images fitted on the page. */
object PrintLayout {
    /** Line ranges per page; an empty text still prints one blank page. */
    fun paginate(lineCount: Int, linesPerPage: Int): List<IntRange> {
        if (lineCount == 0) return listOf(0 until 0)
        return (0 until lineCount step linesPerPage).map { it..min(it + linesPerPage - 1, lineCount - 1) }
    }

    /** Lines longer than [columns] are broken; empty lines stay. */
    fun wrap(lines: List<String>, columns: Int): List<String> =
        lines.flatMap { line -> line.chunked(columns.coerceAtLeast(1)).ifEmpty { listOf("") } }

    /** [width] x [height] made as large as fits in [area] and centred, never enlarged. */
    fun fit(width: Float, height: Float, area: Area): Area {
        val scale = min(1f, min(area.width / width, area.height / height))
        val w = width * scale
        val h = height * scale
        return Area(area.left + (area.width - w) / 2, area.top + (area.height - h) / 2, w, h)
    }
}
