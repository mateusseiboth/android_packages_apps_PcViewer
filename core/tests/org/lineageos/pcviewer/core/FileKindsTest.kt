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
    fun svgIsDrawn() {
        assertEquals(FileKind.SVG, FileKinds.detect("image/svg+xml", "logo.svg"))
        assertEquals(FileKind.SVG, FileKinds.detect(null, "logo.SVG"))
    }

    @Test
    fun audioAndVideo() {
        assertEquals(FileKind.VIDEO, FileKinds.detect("video/mp4", "clip.mp4"))
        assertEquals(FileKind.AUDIO, FileKinds.detect("audio/mpeg", null))
        assertEquals(FileKind.AUDIO, FileKinds.detect("application/ogg", "a.ogg"))
        assertEquals(FileKind.VIDEO, FileKinds.detect(null, "filme.mkv"))
        assertEquals(FileKind.VIDEO, FileKinds.detect(null, "a.webm"))
        assertEquals(FileKind.AUDIO, FileKinds.detect(null, "musica.flac"))
        assertEquals(FileKind.AUDIO, FileKinds.detect("application/octet-stream", "voz.opus"))
    }

    @Test
    fun genericOrMissingMimeFallsBackToTheExtension() {
        assertEquals(FileKind.IMAGE, FileKinds.detect("application/octet-stream", "foto.JPG"))
        assertEquals(FileKind.PDF, FileKinds.detect(null, "contrato.pdf"))
        assertEquals(FileKind.TEXT, FileKinds.detect(null, "notas.md"))
    }

    @Test
    fun unknownIsUnsupported() {
        assertEquals(FileKind.UNSUPPORTED, FileKinds.detect("application/zip", "a.zip"))
        assertEquals(FileKind.UNSUPPORTED, FileKinds.detect(null, "arquivo"))
        assertEquals(FileKind.UNSUPPORTED, FileKinds.detect("application/zip", "a.png"))
    }

    @Test
    fun typeLabelUsesTheExtension() {
        assertEquals("PNG", FileKinds.typeName("image/png", "foto.png"))
        assertEquals("JPEG", FileKinds.typeName("image/jpeg", "sem-extensao"))
        assertEquals("", FileKinds.typeName(null, "sem-extensao"))
        assertEquals("SVG", FileKinds.typeName("image/svg+xml", "sem-extensao"))
    }
}
