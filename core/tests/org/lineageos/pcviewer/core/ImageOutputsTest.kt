package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageOutputsTest {
    @Test
    fun keepsTheFormatsThatCanBeWritten() {
        assertEquals(ImageOutput.PNG, ImageOutputs.forMime("image/png"))
        assertEquals(ImageOutput.JPEG, ImageOutputs.forMime("image/jpeg"))
        assertEquals(ImageOutput.WEBP, ImageOutputs.forMime("image/webp"))
    }

    @Test
    fun otherFormatsBecomePng() {
        assertEquals(ImageOutput.PNG, ImageOutputs.forMime("image/heic"))
        assertEquals(ImageOutput.PNG, ImageOutputs.forMime(null))
    }

    @Test
    fun canOverwriteOnlyWhenTheFormatIsKept() {
        assertEquals(true, ImageOutputs.canOverwrite("image/jpeg"))
        assertEquals(false, ImageOutputs.canOverwrite("image/heic"))
    }

    @Test
    fun copyNameGetsTheOutputExtension() {
        assertEquals("foto.jpg", ImageOutputs.copyName("foto.jpg", ImageOutput.JPEG))
        assertEquals("foto.png", ImageOutputs.copyName("foto.heic", ImageOutput.PNG))
        assertEquals("captura.png", ImageOutputs.copyName("captura", ImageOutput.PNG))
    }
}
