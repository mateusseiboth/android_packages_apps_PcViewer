package org.lineageos.pcviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.print.PrintHelper
import java.io.FileOutputStream
import org.lineageos.pcviewer.core.PrintLayout

/** Sends the shown file to the system print dialog: images fitted on the page, PDFs as they are, text paginated. */
object Printing {
    fun printImage(context: Context, name: String, bitmap: Bitmap) {
        PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }.printBitmap(name, bitmap)
    }

    fun printPdf(context: Context, name: String, uri: Uri) {
        printManager(context).print(name, PdfFileAdapter(context, name, uri), null)
    }

    fun printText(context: Context, name: String, lines: List<String>) {
        printManager(context).print(name, TextAdapter(context, name, lines), null)
    }

    private fun printManager(context: Context) = context.getSystemService(PrintManager::class.java)
}

private fun PrintDocumentAdapter.LayoutResultCallback.finish(name: String, pages: Int) {
    onLayoutFinished(
        PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(),
        true,
    )
}

/** The PDF file goes to the printer unchanged; the print system picks the pages. */
private class PdfFileAdapter(context: Context, private val name: String, private val uri: Uri) : PrintDocumentAdapter() {
    private val resolver = context.contentResolver

    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancel: CancellationSignal,
        callback: LayoutResultCallback, extras: Bundle?,
    ) {
        if (cancel.isCanceled) return callback.onLayoutCancelled()
        callback.finish(name, PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
    }

    override fun onWrite(
        pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal, callback: WriteResultCallback,
    ) {
        runCatching {
            val input = resolver.openInputStream(uri) ?: error("no input stream for $uri")
            input.use { source -> FileOutputStream(destination.fileDescriptor).use { source.copyTo(it) } }
        }.onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
            .onFailure { callback.onWriteFailed(it.message) }
    }
}

/** Text in a monospace font, long lines wrapped, as many lines per page as the paper holds. */
private class TextAdapter(
    private val context: Context,
    private val name: String,
    private val lines: List<String>,
) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null
    private var pages: List<List<String>> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = TEXT_POINTS
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancel: CancellationSignal,
        callback: LayoutResultCallback, extras: Bundle?,
    ) {
        if (cancel.isCanceled) return callback.onLayoutCancelled()
        attributes = newAttributes
        val probe = PrintedPdfDocument(context, newAttributes)
        val page = probe.startPage(0)
        val area = page.info.contentRect
        probe.finishPage(page)
        probe.close()
        val columns = (area.width() / paint.measureText("M")).toInt()
        val perPage = (area.height() / paint.fontSpacing).toInt().coerceAtLeast(1)
        val wrapped = PrintLayout.wrap(lines, columns)
        pages = PrintLayout.paginate(wrapped.size, perPage).map { range -> wrapped.slice(range) }
        callback.finish(name, pages.size)
    }

    override fun onWrite(
        ranges: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal, callback: WriteResultCallback,
    ) {
        val printAttributes = attributes ?: return callback.onWriteFailed(null)
        val document = PrintedPdfDocument(context, printAttributes)
        pages.forEachIndexed { index, text ->
            if (ranges.none { index in it.start..it.end }) return@forEachIndexed
            drawPage(document, index, text)
        }
        runCatching { FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) } }
            .onSuccess { callback.onWriteFinished(ranges) }
            .onFailure { callback.onWriteFailed(it.message) }
        document.close()
    }

    private fun drawPage(document: PrintedPdfDocument, index: Int, text: List<String>) {
        val page = document.startPage(index)
        val area = page.info.contentRect
        text.forEachIndexed { line, content ->
            val baseline = area.top + (line + 1) * paint.fontSpacing - paint.descent()
            page.canvas.drawText(content, area.left.toFloat(), baseline, paint)
        }
        document.finishPage(page)
    }

    private companion object {
        const val TEXT_POINTS = 10f
    }
}
