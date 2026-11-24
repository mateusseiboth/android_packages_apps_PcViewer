package org.lineageos.pcviewer

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import org.lineageos.pcviewer.core.FileKind
import org.lineageos.pcviewer.core.FileKinds
import org.lineageos.pcviewer.core.ImageOutput
import org.lineageos.pcviewer.core.PixelSize
import org.lineageos.pcviewer.core.RenderSizes

data class ViewerItem(
    val uri: Uri,
    val name: String,
    val mime: String?,
    val kind: FileKind,
    val size: Long,
    val modified: Long?,
)

class DecodedImage(val bitmap: Bitmap, val original: PixelSize)

/** Reads files through their content URIs. Every call blocks: run it off the main thread. */
class FileAccess(private val resolver: ContentResolver) {
    /** Columns that may hold the modification time, and how to turn each into milliseconds. */
    private val modifiedColumns: List<Pair<String, (Long) -> Long>> = listOf(
        "last_modified" to { millis -> millis },
        "date_modified" to { seconds -> seconds * 1000 },
    )

    private val compressFormats = mapOf(
        ImageOutput.PNG to Bitmap.CompressFormat.PNG,
        ImageOutput.JPEG to Bitmap.CompressFormat.JPEG,
        ImageOutput.WEBP to Bitmap.CompressFormat.WEBP_LOSSLESS,
    )

    fun itemOf(uri: Uri): ViewerItem {
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
        val row = runCatching { resolver.query(uri, null, null, null, null) }.getOrNull()
        val item = ViewerItem(uri, fallbackName, mime, FileKinds.detect(mime, fallbackName), -1, null)
        return row?.use { readRow(it, item) } ?: item
    }

    fun kindOf(uri: Uri): FileKind {
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        return FileKinds.detect(mime, uri.lastPathSegment)
    }

    /** Huge images are subsampled so the bitmap stays within [RenderSizes.MAX_IMAGE_PIXELS]. */
    fun decodeImage(uri: Uri, software: Boolean = false): DecodedImage {
        var original = PixelSize(0, 0)
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            original = PixelSize(info.size.width, info.size.height)
            decoder.setTargetSampleSize(
                RenderSizes.sampleSize(original.width, original.height, RenderSizes.MAX_IMAGE_PIXELS),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE.takeIf { software } ?: ImageDecoder.ALLOCATOR_DEFAULT
        }
        return DecodedImage(bitmap, original)
    }

    fun readBytes(uri: Uri, limit: Int): ByteArray = open(uri).use { it.readNBytes(limit) }

    /** Writes the image turned by [degrees] clockwise in [output]'s format. */
    fun writeRotated(source: Uri, target: Uri, degrees: Int, output: ImageOutput) {
        val image = decodeImage(source, software = true).bitmap
        val rotated = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply {
            postRotate(degrees.toFloat())
        }, true)
        val stream = resolver.openOutputStream(target, "wt") ?: error("no output stream for $target")
        stream.use { rotated.compress(compressFormats.getValue(output), JPEG_QUALITY, it) }
    }

    fun openForPdf(uri: Uri) = resolver.openFileDescriptor(uri, "r") ?: error("no file descriptor for $uri")

    fun copyTo(uri: Uri, target: java.io.File) {
        open(uri).use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    private fun readRow(cursor: Cursor, item: ViewerItem): ViewerItem {
        if (!cursor.moveToFirst()) return item
        val name = cursor.stringAt(OpenableColumns.DISPLAY_NAME) ?: item.name
        val size = cursor.longAt(OpenableColumns.SIZE) ?: item.size
        val modified = modifiedColumns.firstNotNullOfOrNull { (column, toMillis) -> cursor.longAt(column)?.let(toMillis) }
        return item.copy(name = name, size = size, modified = modified, kind = FileKinds.detect(item.mime, name))
    }

    private fun Cursor.stringAt(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun Cursor.longAt(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    private fun open(uri: Uri) = resolver.openInputStream(uri) ?: error("no input stream for $uri")

    private companion object {
        const val JPEG_QUALITY = 95
    }
}
