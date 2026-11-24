package org.lineageos.pcviewer.core

data class PageMatch<T>(val page: Int, val match: T)

/** Search results across a document's pages and the selected one; next and previous wrap around. */
data class MatchCursor<T>(val matches: List<PageMatch<T>>, val index: Int) {
    val count: Int get() = matches.size
    val current: PageMatch<T>? get() = matches.getOrNull(index)
    val position: Int get() = current?.let { index + 1 } ?: 0

    fun next(): MatchCursor<T> = moveBy(1)

    fun previous(): MatchCursor<T> = moveBy(-1)

    private fun moveBy(step: Int): MatchCursor<T> {
        if (matches.isEmpty()) return this
        return copy(index = Math.floorMod(index + step, matches.size))
    }

    companion object {
        /** Starts at the first match on or after [startPage], wrapping to the first one. */
        fun <T> of(pages: List<List<T>>, startPage: Int): MatchCursor<T> {
            val matches = pages.flatMapIndexed { page, found -> found.map { PageMatch(page, it) } }
            val start = matches.indexOfFirst { it.page >= startPage }.takeIf { it >= 0 } ?: 0
            return MatchCursor(matches, start)
        }
    }
}

/** A rectangle in page points: left, top, right, bottom. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

sealed interface LinkTarget {
    data class Web(val uri: String) : LinkTarget

    data class Page(val index: Int, val y: Float) : LinkTarget
}

data class PageLink(val boxes: List<Box>, val target: LinkTarget)

object LinkHits {
    fun find(links: List<PageLink>, x: Float, y: Float): PageLink? =
        links.firstOrNull { link -> link.boxes.any { it.contains(x, y) } }
}
