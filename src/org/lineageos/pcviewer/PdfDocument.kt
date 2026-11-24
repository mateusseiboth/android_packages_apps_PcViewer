package org.lineageos.pcviewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lineageos.pcviewer.core.PixelSize
import org.lineageos.pcviewer.core.RenderSizes

/**
 * A PDF opened with the platform's PdfRenderer. The renderer allows one open page at a time and is not
 * thread safe, so every page goes through [lock]. Page sizes are in points (1/72 inch).
 */
class PdfDocument private constructor(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
    val pageSizes: List<PixelSize>,
) {
    private val lock = Mutex()
    private var closed = false

    val pageCount: Int get() = pageSizes.size

    /** The page drawn [targetWidth] pixels wide on white, or null once the document is closed. */
    suspend fun render(index: Int, targetWidth: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) { renderUnlocked(index, targetWidth) }
    }

    fun close() {
        CLOSER.launch(NonCancellable) {
            lock.withLock {
                closed = true
                renderer.close()
                descriptor.close()
            }
        }
    }

    private fun renderUnlocked(index: Int, targetWidth: Int): Bitmap? {
        if (closed) return null
        return renderer.openPage(index).use { page ->
            val size = RenderSizes.pageSize(page.width, page.height, targetWidth, RenderSizes.MAX_PAGE_PIXELS)
            Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    companion object {
        private val CLOSER = CoroutineScope(Dispatchers.IO)

        /** Reads every page's size up front so the page list can be laid out before any page is drawn. */
        fun open(descriptor: ParcelFileDescriptor): PdfDocument {
            val renderer = PdfRenderer(descriptor)
            val sizes = (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { PixelSize(it.width, it.height) }
            }
            return PdfDocument(renderer, descriptor, sizes)
        }
    }
}
