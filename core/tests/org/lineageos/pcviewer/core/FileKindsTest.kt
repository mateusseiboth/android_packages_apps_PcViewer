package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FileKindsTest {
    @Test
    fun imagesByMime() {
        assertEquals(FileKind.IMAGE, FileKinds.detect("image/png", "a.png"))
        assertEquals(FileKind.IMAGE, FileKinds.detect("image/heic", null))
        assertEquals(FileKind.IMAGE, FileKinds.detect("IMAGE/JPEG", null))
    }

    @Test
    fun pdfByMime() {
        assertEquals(FileKind.PDF, FileKinds.detect("application/pdf", "x"))
    }

    @Test
    fun textByMime() {
        assertEquals(FileKind.TEXT, FileKinds.detect("text/plain", null))
        assertEquals(FileKind.TEXT, FileKinds.detect("text/csv", null))
        assertEquals(FileKind.TEXT, FileKinds.detect("application/json", null))
    }

    @Test
    fun svgIsShownAsText() {
        assertEquals(FileKind.TEXT, FileKinds.detect("image/svg+xml", "logo.svg"))
    }

    @Test
    fun genericOrMissingMimeFallsBackToTheExtension() {
        assertEquals(FileKind.IMAGE, FileKinds.detect("application/octet-stream", "foto.JPG"))
        assertEquals(FileKind.PDF, FileKinds.detect(null, "contrato.pdf"))
        assertEquals(FileKind.TEXT, FileKinds.detect(null, "notas.md"))
    }

    @Test
    fun unknownIsUnsupported() {
        assertEquals(FileKind.UNSUPPORTED, FileKinds.detect("video/mp4", "clip.mp4"))
        assertEquals(FileKind.UNSUPPORTED, FileKinds.detect(null, "arquivo"))
        assertEquals(FileKind.UNSUPPORTED, FileKinds.detect("application/zip", "a.png"))
    }

    @Test
    fun typeLabelUsesTheExtension() {
        assertEquals("PNG", FileKinds.typeName("image/png", "foto.png"))
        assertEquals("JPEG", FileKinds.typeName("image/jpeg", "sem-extensao"))
        assertEquals("", FileKinds.typeName(null, "sem-extensao"))
    }
}
