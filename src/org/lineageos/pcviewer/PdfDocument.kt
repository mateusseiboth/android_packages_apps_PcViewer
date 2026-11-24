package org.lineageos.pcviewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lineageos.pcviewer.core.Box
import org.lineageos.pcviewer.core.LinkTarget
import org.lineageos.pcviewer.core.PageLink
import org.lineageos.pcviewer.core.PixelSize
import org.lineageos.pcviewer.core.RenderSizes

/**
 * A PDF opened with the platform's PdfRenderer (framework-pdf). The renderer allows one open page at a time
 * and is not thread safe, so every page goes through [lock]. Page sizes, search boxes and link boxes are in
 * points (1/72 inch).
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
    suspend fun render(index: Int, targetWidth: Int): Bitmap? = onPage(index) { page ->
        val size = RenderSizes.pageSize(page.width, page.height, targetWidth, RenderSizes.MAX_PAGE_PIXELS)
        Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    /** Each page's matches, each match as the boxes of its lines. */
    suspend fun search(query: String): List<List<List<Box>>> = (0 until pageCount).map { index ->
        onPage(index) { page -> page.searchText(query).map { match -> match.bounds.map(::boxOf) } }.orEmpty()
    }

    /** Web links and links to other pages of the document. */
    suspend fun links(index: Int): List<PageLink> = onPage(index) { page ->
        val web = page.linkContents.map { link -> PageLink(link.bounds.map(::boxOf), LinkTarget.Web(link.uri.toString())) }
        val internal = page.gotoLinks.map { link ->
            PageLink(link.bounds.map(::boxOf), LinkTarget.Page(link.destination.pageNumber, link.destination.yCoordinate))
        }
        web + internal
    }.orEmpty()

    fun close() {
        CLOSER.launch(NonCancellable) {
            lock.withLock {
                closed = true
                renderer.close()
                descriptor.close()
            }
        }
    }

    private suspend fun <T> onPage(index: Int, work: (PdfRenderer.Page) -> T): T? = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.takeUnless { closed }?.openPage(index)?.use(work)
        }
    }

    private fun boxOf(rect: RectF) = Box(rect.left, rect.top, rect.right, rect.bottom)

    companion object {
        private val CLOSER = CoroutineScope(Dispatchers.IO)

        /**
         * Reads every page's size up front so the page list can be laid out before any page is drawn. A
         * protected PDF opened without its [password], or with a wrong one, throws SecurityException.
         */
        fun open(descriptor: ParcelFileDescriptor, password: String? = null): PdfDocument {
            val params = password?.let { LoadParams.Builder().setPassword(it).build() }
            val renderer = params?.let { PdfRenderer(descriptor, it) } ?: PdfRenderer(descriptor)
            val sizes = (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { PixelSize(it.width, it.height) }
            }
            return PdfDocument(renderer, descriptor, sizes)
        }
    }
}
