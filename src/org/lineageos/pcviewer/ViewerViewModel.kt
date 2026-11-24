package org.lineageos.pcviewer

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lineageos.pctexteditor.core.EncodingDetector
import org.lineageos.pctexteditor.core.TextCodec
import org.lineageos.pcviewer.core.FileKind
import org.lineageos.pcviewer.core.Gallery
import org.lineageos.pcviewer.core.ImageOutputs
import org.lineageos.pcviewer.core.ImageTransform
import org.lineageos.pcviewer.core.PixelSize
import org.lineageos.pcviewer.core.Size2
import org.lineageos.pcviewer.core.ViewerCommand
import org.lineageos.pcviewer.core.ZoomSteps

enum class FailReason { UNSUPPORTED, FAILED, EMPTY, PROTECTED, TOO_LARGE }

/** What the window shows; [item] is the file behind it, when there is one. */
sealed interface ViewerContent {
    val item: ViewerItem? get() = null

    data object Welcome : ViewerContent
    data object Loading : ViewerContent
    data class Image(override val item: ViewerItem, val bitmap: ImageBitmap, val original: PixelSize) : ViewerContent
    data class Pdf(override val item: ViewerItem, val document: PdfDocument) : ViewerContent
    data class Text(override val item: ViewerItem, val lines: List<String>) : ViewerContent
    data class Failed(override val item: ViewerItem?, val name: String, val reason: FailReason) : ViewerContent
}

data class SaveAsRequest(val name: String, val mime: String)

/** The file on screen, the folder it came from, and how it is zoomed and turned. */
class ViewerViewModel(app: Application) : AndroidViewModel(app) {
    private val files = FileAccess(app.contentResolver)
    private val cacheDir = app.cacheDir
    private var viewport = Size2(1f, 1f)
    private var loadJob: Job? = null
    private var pdfCopy: File? = null

    var gallery by mutableStateOf<Gallery<Uri>?>(null)
        private set
    var content by mutableStateOf<ViewerContent>(ViewerContent.Welcome)
        private set
    var image by mutableStateOf<ImageTransform?>(null)
        private set
    var pdfZoom by mutableFloatStateOf(1f)
        private set
    var showDetails by mutableStateOf(false)

    val messages = Channel<Int>(Channel.BUFFERED)
    val openRequests = Channel<Unit>(Channel.CONFLATED)
    val saveAsRequests = Channel<SaveAsRequest>(Channel.BUFFERED)

    val item: ViewerItem? get() = content.item
    val canSave: Boolean get() = (image?.rotation ?: 0) != 0

    private val loaders: Map<FileKind, (ViewerItem) -> ViewerContent> = mapOf(
        FileKind.IMAGE to ::loadImage,
        FileKind.PDF to ::loadPdf,
        FileKind.TEXT to ::loadText,
        FileKind.UNSUPPORTED to { item -> ViewerContent.Failed(item, item.name, FailReason.UNSUPPORTED) },
    )

    private val commands: Map<ViewerCommand, () -> Unit> = mapOf(
        ViewerCommand.PREVIOUS to ::showPrevious,
        ViewerCommand.NEXT to ::showNext,
        ViewerCommand.ZOOM_IN to ::zoomIn,
        ViewerCommand.ZOOM_OUT to ::zoomOut,
        ViewerCommand.FIT to ::fit,
        ViewerCommand.ROTATE_LEFT to { updateImage { it.rotateCounterClockwise() } },
        ViewerCommand.ROTATE_RIGHT to { updateImage { it.rotateClockwise() } },
        ViewerCommand.OPEN to { openRequests.trySend(Unit) },
        ViewerCommand.SAVE to ::save,
    )

    fun run(command: ViewerCommand) = commands.getValue(command)()

    fun show(next: Gallery<Uri>) {
        gallery = next
        load(next.current)
        dropUnsupportedNeighbours(next)
    }

    fun showNext() {
        gallery?.takeIf { it.hasNext }?.let { move(it.next()) }
    }

    fun showPrevious() {
        gallery?.takeIf { it.hasPrevious }?.let { move(it.previous()) }
    }

    /** Gives the shown file to another app; [start] says whether one took it. */
    fun handOver(start: (ViewerItem) -> Boolean) {
        val shown = item ?: return
        if (!start(shown)) message(R.string.no_app)
    }

    fun setViewport(size: Size2) {
        viewport = size
        image = image?.withViewport(size)
    }

    fun updateImage(change: (ImageTransform) -> ImageTransform) {
        image = image?.let(change)
    }

    // PDF pages zoom relative to the width that fits the window; images use their own transform.
    fun zoomIn() {
        updateImage { it.zoomIn() }
        pdfZoom = ZoomSteps.zoomIn(pdfZoom, PDF_MAX_ZOOM)
    }

    fun zoomOut() {
        updateImage { it.zoomOut() }
        pdfZoom = ZoomSteps.zoomOut(pdfZoom, PDF_MIN_ZOOM)
    }

    fun fit() {
        updateImage { it.fit() }
        pdfZoom = 1f
    }

    fun zoomPdfBy(factor: Float) {
        pdfZoom = (pdfZoom * factor).coerceIn(PDF_MIN_ZOOM, PDF_MAX_ZOOM)
    }

    /** Overwrites the file when its format can be written back, otherwise asks where to save a copy. */
    fun save() {
        val shown = content as? ViewerContent.Image ?: return
        val rotation = image?.rotation?.takeIf { it != 0 } ?: return
        if (!ImageOutputs.canOverwrite(shown.item.mime)) return saveAs()
        writeRotated(shown.item, shown.item.uri, rotation)
    }

    fun saveAs() {
        val shown = content as? ViewerContent.Image ?: return
        val output = ImageOutputs.forMime(shown.item.mime)
        saveAsRequests.trySend(SaveAsRequest(ImageOutputs.copyName(shown.item.name, output), output.mime))
    }

    fun onSaveAsPicked(target: Uri?) {
        val shown = content as? ViewerContent.Image ?: return
        val rotation = image?.rotation ?: return
        target?.let { writeRotated(shown.item, it, rotation) }
    }

    override fun onCleared() {
        release(content)
    }

    private fun move(next: Gallery<Uri>) {
        gallery = next
        load(next.current)
    }

    private fun load(uri: Uri) {
        loadJob?.cancel()
        release(content)
        content = ViewerContent.Loading
        image = null
        pdfZoom = 1f
        loadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { read(uri) }.getOrElse { failure(uri, it) } }
            content = loaded
            image = (loaded as? ViewerContent.Image)?.let {
                ImageTransform.initial(Size2(it.original.width.toFloat(), it.original.height.toFloat()), viewport)
            }
        }
    }

    private fun read(uri: Uri): ViewerContent {
        val item = files.itemOf(uri)
        if (item.size == 0L) return ViewerContent.Failed(item, item.name, FailReason.EMPTY)
        return loaders.getValue(item.kind)(item)
    }

    private fun loadImage(item: ViewerItem): ViewerContent {
        val decoded = files.decodeImage(item.uri)
        return ViewerContent.Image(item, decoded.bitmap.asImageBitmap(), decoded.original)
    }

    /** PdfRenderer needs a seekable file; streams that are not are copied to the cache first. */
    private fun loadPdf(item: ViewerItem): ViewerContent {
        val document = runCatching { PdfDocument.open(files.openForPdf(item.uri)) }
            .recoverCatching { error -> (error as? SecurityException)?.let { throw it } ?: openCopy(item.uri) }
            .getOrElse { error ->
                val reason = FailReason.PROTECTED.takeIf { error is SecurityException } ?: FailReason.FAILED
                return ViewerContent.Failed(item, item.name, reason)
            }
        return ViewerContent.Pdf(item, document)
    }

    private fun openCopy(uri: Uri): PdfDocument {
        val copy = File.createTempFile("document", ".pdf", cacheDir)
        pdfCopy = copy
        files.copyTo(uri, copy)
        return PdfDocument.open(ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY))
    }

    private fun loadText(item: ViewerItem): ViewerContent {
        val tooLarge = ViewerContent.Failed(item, item.name, FailReason.TOO_LARGE)
        if (item.size > TEXT_MAX_BYTES) return tooLarge
        val bytes = files.readBytes(item.uri, TEXT_MAX_BYTES + 1)
        if (bytes.size > TEXT_MAX_BYTES) return tooLarge
        if (EncodingDetector.isBinary(bytes)) return ViewerContent.Failed(item, item.name, FailReason.UNSUPPORTED)
        return ViewerContent.Text(item, TextCodec.decode(bytes).text.split('\n'))
    }

    private fun failure(uri: Uri, error: Throwable): ViewerContent {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
        val reason = FailReason.PROTECTED.takeIf { error is SecurityException } ?: FailReason.FAILED
        return ViewerContent.Failed(null, name, reason)
    }

    private fun writeRotated(item: ViewerItem, target: Uri, rotation: Int) {
        val output = ImageOutputs.forMime(item.mime)
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching { files.writeRotated(item.uri, target, rotation, output) }
            }
            written.onSuccess { onSaved(item, target) }.onFailure { message(R.string.save_failed) }
        }
    }

    private fun onSaved(item: ViewerItem, target: Uri) {
        message(R.string.saved)
        if (target == item.uri) load(item.uri)
    }

    /** The file manager hands over every file of the folder; only the ones the viewer can show are kept. */
    private fun dropUnsupportedNeighbours(start: Gallery<Uri>) {
        if (start.size < 2) return
        viewModelScope.launch {
            val supported = withContext(Dispatchers.IO) {
                start.items.filter { files.kindOf(it) != FileKind.UNSUPPORTED }.toSet()
            }
            val current = gallery ?: return@launch
            gallery = current.retain { it in supported }
        }
    }

    private fun release(old: ViewerContent) {
        (old as? ViewerContent.Pdf)?.document?.close()
        pdfCopy?.delete()
        pdfCopy = null
    }

    private fun message(@StringRes text: Int) {
        messages.trySend(text)
    }

    private companion object {
        const val TEXT_MAX_BYTES = 4 shl 20
        const val PDF_MIN_ZOOM = 0.5f
        const val PDF_MAX_ZOOM = 4f
    }
}
