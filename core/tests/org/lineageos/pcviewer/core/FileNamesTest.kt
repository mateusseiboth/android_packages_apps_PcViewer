package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNamesTest {
    @Test
    fun problems() {
        assertEquals(NameProblem.Empty, FileNames.problemWith(""))
        assertEquals(NameProblem.Empty, FileNames.problemWith("   "))
        assertEquals(NameProblem.StartsWithDot, FileNames.problemWith(".oculto"))
        assertEquals(NameProblem.InvalidCharacter('/'), FileNames.problemWith("a/b.txt"))
        assertEquals(NameProblem.InvalidCharacter('?'), FileNames.problemWith("o que?.txt"))
        assertNull(FileNames.problemWith("férias 2026.jpg"))
    }

    @Test
    fun theNamePartWithoutTheExtensionIsPreselected() {
        assertEquals(10, FileNames.baseLength("foto.final.jpg"))
        assertEquals(6, FileNames.baseLength("README"))
    }
}
