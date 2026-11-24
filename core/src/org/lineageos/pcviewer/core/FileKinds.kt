package org.lineageos.pcviewer.core

enum class FileKind { IMAGE, SVG, PDF, TEXT, AUDIO, VIDEO, UNSUPPORTED }

/** What a file is, from its MIME type, or from its extension when the type says nothing. */
object FileKinds {
    private val GENERIC = setOf("application/octet-stream", "")

    private val BY_MIME = mapOf(
        "application/pdf" to FileKind.PDF,
        "image/svg+xml" to FileKind.SVG,
        "application/ogg" to FileKind.AUDIO,
        "application/json" to FileKind.TEXT,
        "application/xml" to FileKind.TEXT,
        "application/javascript" to FileKind.TEXT,
        "application/x-sh" to FileKind.TEXT,
    )

    private val BY_MIME_PREFIX = mapOf(
        "image/" to FileKind.IMAGE,
        "text/" to FileKind.TEXT,
        "audio/" to FileKind.AUDIO,
        "video/" to FileKind.VIDEO,
    )

    private val BY_EXTENSION = mapOf(
        "jpg" to FileKind.IMAGE, "jpeg" to FileKind.IMAGE, "png" to FileKind.IMAGE, "gif" to FileKind.IMAGE,
        "webp" to FileKind.IMAGE, "bmp" to FileKind.IMAGE, "heic" to FileKind.IMAGE, "heif" to FileKind.IMAGE,
        "avif" to FileKind.IMAGE, "ico" to FileKind.IMAGE, "dng" to FileKind.IMAGE, "wbmp" to FileKind.IMAGE,
        "pdf" to FileKind.PDF,
        "txt" to FileKind.TEXT, "md" to FileKind.TEXT, "log" to FileKind.TEXT, "csv" to FileKind.TEXT,
        "json" to FileKind.TEXT, "xml" to FileKind.TEXT, "ini" to FileKind.TEXT,
        "conf" to FileKind.TEXT, "sh" to FileKind.TEXT,
        "svg" to FileKind.SVG,
        "mp4" to FileKind.VIDEO, "m4v" to FileKind.VIDEO, "mkv" to FileKind.VIDEO, "webm" to FileKind.VIDEO,
        "mov" to FileKind.VIDEO, "3gp" to FileKind.VIDEO, "avi" to FileKind.VIDEO, "ts" to FileKind.VIDEO,
        "mp3" to FileKind.AUDIO, "m4a" to FileKind.AUDIO, "aac" to FileKind.AUDIO, "flac" to FileKind.AUDIO,
        "ogg" to FileKind.AUDIO, "oga" to FileKind.AUDIO, "opus" to FileKind.AUDIO, "wav" to FileKind.AUDIO,
        "amr" to FileKind.AUDIO, "mid" to FileKind.AUDIO, "midi" to FileKind.AUDIO,
    )

    fun detect(mime: String?, name: String?): FileKind {
        val type = mime?.lowercase().orEmpty()
        if (type in GENERIC) return BY_EXTENSION[extensionOf(name)] ?: FileKind.UNSUPPORTED
        return BY_MIME[type] ?: BY_MIME_PREFIX.entries.firstOrNull { type.startsWith(it.key) }?.value
            ?: FileKind.UNSUPPORTED
    }

    /** "PNG", "JPEG"...: the extension, else the MIME subtype, upper case. */
    fun typeName(mime: String?, name: String?): String {
        val extension = extensionOf(name)
        val subtype = mime?.substringAfter('/', "")?.substringBefore('+').orEmpty()
        return extension.ifEmpty { subtype }.uppercase()
    }

    private fun extensionOf(name: String?): String {
        val file = name.orEmpty()
        return file.substringAfterLast('.', "").takeIf { file.lastIndexOf('.') > 0 }?.lowercase().orEmpty()
    }
}
