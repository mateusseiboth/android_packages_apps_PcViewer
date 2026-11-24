package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderTest {
    @Test
    fun naturalOrderSortsNumbersByValueAndIgnoresCase() {
        val names = listOf("img10.jpg", "img2.jpg", "IMG1.jpg", "a.jpg", "img2b.jpg")
        assertEquals(listOf("a.jpg", "IMG1.jpg", "img2.jpg", "img2b.jpg", "img10.jpg"), names.sortedWith(NaturalOrder))
    }

    @Test
    fun parentOfAnExternalStorageDocument() {
        val authority = DocumentIds.EXTERNAL_STORAGE
        assertEquals("primary:DCIM/Camera", DocumentIds.parentOf(authority, "primary:DCIM/Camera/a.jpg"))
        assertEquals("primary:", DocumentIds.parentOf(authority, "primary:a.jpg"))
        assertEquals("1234-ABCD:x", DocumentIds.parentOf(authority, "1234-ABCD:x/y.pdf"))
        assertNull(DocumentIds.parentOf(authority, "primary:"))
    }

    @Test
    fun otherProvidersHaveNoKnownParent() {
        assertNull(DocumentIds.parentOf("com.android.providers.downloads.documents", "msf:12"))
    }
}
