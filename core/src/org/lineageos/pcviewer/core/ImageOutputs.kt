package org.lineageos.pcviewer.core

/** The formats a rotated image can be written in; [extensions] start with the one used for new names. */
enum class ImageOutput(val mime: String, val extensions: List<String>) {
    PNG("image/png", listOf("png")),
    JPEG("image/jpeg", listOf("jpg", "jpeg")),
    WEBP("image/webp", listOf("webp")),
}

object ImageOutputs {
    private val BY_MIME = ImageOutput.entries.associateBy { it.mime }

    /** Formats Android can't write (HEIC, GIF, BMP...) are saved as PNG. */
    fun forMime(mime: String?): ImageOutput = BY_MIME[mime?.lowercase()] ?: ImageOutput.PNG

    /** Overwriting is only offered when the file keeps its format. */
    fun canOverwrite(mime: String?): Boolean = mime?.lowercase() in BY_MIME

    fun copyName(name: String, output: ImageOutput): String {
        val hasExtension = name.lastIndexOf('.') > 0
        val extension = name.substringAfterLast('.').lowercase().takeIf { hasExtension }
        if (extension in output.extensions) return name
        val base = name.substringBeforeLast('.').takeIf { hasExtension } ?: name
        return "$base.${output.extensions.first()}"
    }
}
