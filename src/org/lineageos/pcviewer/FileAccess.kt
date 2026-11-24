package org.lineageos.pcviewer

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import kotlin.math.roundToInt
import org.lineageos.pcviewer.core.FileKind
import org.lineageos.pcviewer.core.FileKinds
import org.lineageos.pcviewer.core.ImageEdit
import org.lineageos.pcviewer.core.ImageOutput
import org.lineageos.pcviewer.core.PixelSize
import org.lineageos.pcviewer.core.RenderSizes
import org.lineageos.pcviewer.core.SvgParser

data class ViewerItem(
    val uri: Uri,
    val name: String,
    val mime: String?,
    val kind: FileKind,
    val size: Long,
    val modified: Long?,
)

/** What an image shows: one picture, or an animation (GIF, WebP, AVIF, HEIF sequences). */
sealed interface Picture {
    class Still(val bitmap: Bitmap) : Picture

    class Moving(val drawable: AnimatedImageDrawable) : Picture
}

class DecodedPicture(val picture: Picture, val original: PixelSize)

/** Reads, writes, renames and removes files through their content URIs. Every call blocks: run it off the main thread. */
class FileAccess(private val context: Context) {
    private val resolver = context.contentResolver

    /** Columns that may hold the modification time, and how to turn each into milliseconds. */
    private val modifiedColumns: List<Pair<String, (Long) -> Long>> = listOf(
        DocumentsContract.Document.COLUMN_LAST_MODIFIED to { millis -> millis },
        MediaStore.MediaColumns.DATE_MODIFIED to { seconds -> seconds * 1000 },
    )

    private val compressFormats = mapOf(
        ImageOutput.PNG to Bitmap.CompressFormat.PNG,
        ImageOutput.JPEG to Bitmap.CompressFormat.JPEG,
        ImageOutput.WEBP to Bitmap.CompressFormat.WEBP_LOSSLESS,
    )

    /** How each provider renames: documents through DocumentsContract, media through MediaStore. */
    private val renamers: List<Pair<(Uri) -> Boolean, (Uri, String) -> Uri?>> = listOf(
        { uri: Uri -> DocumentsContract.isDocumentUri(context, uri) } to { uri: Uri, name: String ->
            DocumentsContract.renameDocument(resolver, uri, name)
        },
        { uri: Uri -> uri.authority == MediaStore.AUTHORITY } to { uri: Uri, name: String ->
            val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
            uri.takeIf { resolver.update(it, values, null, null) > 0 }
        },
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

    /** Animated formats keep their frames; huge pictures are subsampled to [RenderSizes.MAX_IMAGE_PIXELS]. */
    fun decodePicture(uri: Uri): DecodedPicture {
        var original = PixelSize(0, 0)
        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            original = PixelSize(info.size.width, info.size.height)
            decoder.setTargetSampleSize(RenderSizes.sampleSize(original.width, original.height, RenderSizes.MAX_IMAGE_PIXELS))
        }
        return DecodedPicture(pictureOf(drawable), original)
    }

    /** A full-quality, software (editable) copy for saving or printing. */
    fun decodeStill(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(RenderSizes.sampleSize(info.size.width, info.size.height, RenderSizes.MAX_IMAGE_PIXELS))
        }

    /** SVG drawn by our renderer; its "actual size" is the document's own width and height. */
    fun renderSvg(uri: Uri): DecodedPicture {
        val document = SvgParser.parse(String(readBytes(uri, SVG_MAX_BYTES), Charsets.UTF_8))
        val original = PixelSize(document.width.roundToInt().coerceAtLeast(1), document.height.roundToInt().coerceAtLeast(1))
        return DecodedPicture(Picture.Still(SvgRenderer.render(document)), original)
    }

    fun readBytes(uri: Uri, limit: Int): ByteArray = open(uri).use { it.readNBytes(limit) }

    /**
     * Applies [edit] to [source] and writes it to [target] in [output]'s format. The photo's EXIF data is
     * copied from [exifFrom] (orientation reset, since the pixels are already turned).
     */
    fun writeEdited(source: Bitmap, edit: ImageEdit, target: Uri, output: ImageOutput, exifFrom: Uri?) {
        val crop = edit.cropPixels(source.width, source.height)
        val result = Bitmap.createBitmap(crop.width, crop.height, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            translate(-crop.x.toFloat(), -crop.y.toFloat())
            concat(SvgRenderer.matrixOf(edit.geometry(source.width, source.height)))
            drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(edit.colorMatrix())
            })
        }
        val temp = File.createTempFile("edited", "." + output.extensions.first(), context.cacheDir)
        runCatching {
            temp.outputStream().use { result.compress(compressFormats.getValue(output), JPEG_QUALITY, it) }
            exifFrom?.let { ExifKeeper.copy(resolver, it, temp, result.width, result.height) }
            val stream = resolver.openOutputStream(target, "wt") ?: error("no output stream for $target")
            stream.use { out -> temp.inputStream().use { it.copyTo(out) } }
        }.also { temp.delete() }.getOrThrow()
    }

    fun openForPdf(uri: Uri) = resolver.openFileDescriptor(uri, "r") ?: error("no file descriptor for $uri")

    fun copyTo(uri: Uri, target: File) {
        open(uri).use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    /** The new URI (some providers change it), or null when the provider refused. */
    fun rename(uri: Uri, name: String): Uri? =
        renamers.firstOrNull { (handles, _) -> handles(uri) }?.second?.invoke(uri, name)

    /** The MediaStore entry of a file on shared storage, needed to move it to the system trash. */
    fun mediaUriOf(uri: Uri): Uri? {
        if (uri.authority == MediaStore.AUTHORITY) return uri
        return runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
    }

    fun trashRequest(mediaUri: Uri): IntentSender =
        MediaStore.createTrashRequest(resolver, listOf(mediaUri), true).intentSender

    fun writeRequest(mediaUri: Uri): IntentSender = MediaStore.createWriteRequest(resolver, listOf(mediaUri)).intentSender

    fun delete(uri: Uri): Boolean = DocumentsContract.isDocumentUri(context, uri) && DocumentsContract.deleteDocument(resolver, uri)

    private fun pictureOf(drawable: Drawable): Picture {
        (drawable as? AnimatedImageDrawable)?.let { return Picture.Moving(it) }
        return Picture.Still((drawable as BitmapDrawable).bitmap)
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
        const val SVG_MAX_BYTES = 16 shl 20
    }
}
