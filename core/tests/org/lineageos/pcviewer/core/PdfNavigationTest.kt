package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfNavigationTest {
    private val matches = MatchCursor.of(listOf(listOf("a", "b"), emptyList(), listOf("c")), startPage = 1)

    @Test
    fun matchesAreCountedAcrossPagesFromTheCurrentPage() {
        assertEquals(3, matches.count)
        assertEquals(PageMatch(2, "c"), matches.current)
        assertEquals(3, matches.position)
    }

    @Test
    fun nextAndPreviousWrap() {
        assertEquals(PageMatch(0, "a"), matches.next().current)
        assertEquals(PageMatch(0, "b"), matches.previous().current)
    }

    @Test
    fun noMatches() {
        val none = MatchCursor.of(listOf(emptyList<String>()), startPage = 0)
        assertNull(none.current)
        assertEquals(0, none.position)
        assertNull(none.next().current)
    }

    @Test
    fun linkUnderThePoint() {
        val web = PageLink(listOf(Box(10f, 10f, 50f, 20f)), LinkTarget.Web("https://lineageos.org"))
        val page = PageLink(listOf(Box(0f, 100f, 20f, 110f), Box(0f, 120f, 20f, 130f)), LinkTarget.Page(4, 300f))
        assertEquals(web, LinkHits.find(listOf(web, page), 30f, 15f))
        assertEquals(page, LinkHits.find(listOf(web, page), 5f, 125f))
        assertNull(LinkHits.find(listOf(web, page), 100f, 100f))
    }
}
