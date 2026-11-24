package org.lineageos.pcviewer

import android.app.Application
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lineageos.pctexteditor.core.EncodingDetector
import org.lineageos.pctexteditor.core.TextCodec
import org.lineageos.pcviewer.core.Box
import org.lineageos.pcviewer.core.CropAspect
import org.lineageos.pcviewer.core.CropBox
import org.lineageos.pcviewer.core.CropHandle
import org.lineageos.pcviewer.core.FileKind
import org.lineageos.pcviewer.core.Gallery
import org.lineageos.pcviewer.core.ImageEdit
import org.lineageos.pcviewer.core.ImageOutputs
import org.lineageos.pcviewer.core.ImageTransform
import org.lineageos.pcviewer.core.MatchCursor
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
    data class Image(override val item: ViewerItem, val picture: Picture, val original: PixelSize) : ViewerContent
    data class Pdf(override val item: ViewerItem, val document: PdfDocument) : ViewerContent
    data class PdfLocked(override val item: ViewerItem, val wrongPassword: Boolean) : ViewerContent
    data class Text(override val item: ViewerItem, val lines: List<String>) : ViewerContent
    data class Media(override val item: ViewerItem, val playback: MediaPlayback, val isVideo: Boolean) : ViewerContent
    data class Failed(override val item: ViewerItem?, val name: String, val reason: FailReason) : ViewerContent
}

sealed interface ViewerDialog {
    data object Details : ViewerDialog
    data class Rename(val name: String) : ViewerDialog
    data class Delete(val name: String) : ViewerDialog
    data class DiscardEdits(val name: String, val then: () -> Unit) : ViewerDialog
}

/** What the print dialog gets: a still image, the PDF file, or text lines. */
sealed interface PrintJob {
    val name: String

    data class Picture(override val name: String, val bitmap: Bitmap) : PrintJob
    data class Document(override val name: String, val uri: Uri) : PrintJob
    data class Lines(override val name: String, val lines: List<String>) : PrintJob
}

data class SaveAsRequest(val name: String, val mime: String)

/** Find in PDF: the query, each page's matches (boxes per line), and the selected match. */
data class PdfSearch(val query: String = "", val cursor: MatchCursor<List<Box>> = MatchCursor(emptyList(), 0))

/** The file on screen, the folder it came from, how it is zoomed and turned, and the edits in progress. */
class ViewerViewModel(app: Application) : AndroidViewModel(app) {
    private val files = FileAccess(app)
    private val folders = FolderFinder(app)
    private val cacheDir = app.cacheDir
    private var viewport = Size2(1f, 1f)
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var pdfCopy: File? = null
    private var pendingConsent: (() -> Unit)? = null

    var gallery by mutableStateOf<Gallery<Uri>?>(null)
        private set
    var content by mutableStateOf<ViewerContent>(ViewerContent.Welcome)
        private set
    var image by mutableStateOf<ImageTransform?>(null)
        private set
    var pdfZoom by mutableFloatStateOf(1f)
        private set
    var pdfPage by mutableIntStateOf(0)
    var pdfSearch by mutableStateOf<PdfSearch?>(null)
        private set
    var edit by mutableStateOf<ImageEdit?>(null)
        private set
    var cropAspect by mutableStateOf(CropAspect.FREE)
        private set
    var folderAccess by mutableStateOf<FolderAccess?>(null)
        private set
    var dialog by mutableStateOf<ViewerDialog?>(null)
        private set

    val messages = Channel<Int>(Channel.BUFFERED)
    val openRequests = Channel<Unit>(Channel.CONFLATED)
    val saveAsRequests = Channel<SaveAsRequest>(Channel.BUFFERED)
    val consentRequests = Channel<IntentSender>(Channel.BUFFERED)
    val mediaPermissionRequests = Channel<Unit>(Channel.CONFLATED)
    val folderRequests = Channel<Uri>(Channel.CONFLATED)
    val printJobs = Channel<PrintJob>(Channel.BUFFERED)

    val item: ViewerItem? get() = content.item
    val isEditing: Boolean get() = edit != null

    private val loaders: Map<FileKind, (ViewerItem) -> ViewerContent> = mapOf(
        FileKind.IMAGE to ::loadPicture,
        FileKind.SVG to ::loadSvg,
        FileKind.PDF to { loadPdf(it, password = null) },
        FileKind.TEXT to ::loadText,
        FileKind.AUDIO to { ViewerContent.Media(it, MediaPlayback(getApplication(), it.uri, isVideo = false), isVideo = false) },
        FileKind.VIDEO to { ViewerContent.Media(it, MediaPlayback(getApplication(), it.uri, isVideo = true), isVideo = true) },
        FileKind.UNSUPPORTED to { item -> ViewerContent.Failed(item, item.name, FailReason.UNSUPPORTED) },
    )

    private val commands: Map<ViewerCommand, () -> Unit> = mapOf(
        ViewerCommand.PREVIOUS to ::showPrevious,
        ViewerCommand.NEXT to ::showNext,
        ViewerCommand.ZOOM_IN to ::zoomIn,
        ViewerCommand.ZOOM_OUT to ::zoomOut,
        ViewerCommand.FIT to ::fit,
        ViewerCommand.ROTATE_LEFT to ::rotateLeft,
        ViewerCommand.ROTATE_RIGHT to ::rotateRight,
        ViewerCommand.OPEN to { openRequests.trySend(Unit) },
        ViewerCommand.SAVE to ::saveEdit,
        ViewerCommand.PLAY_PAUSE to { (content as? ViewerContent.Media)?.playback?.toggle() },
        ViewerCommand.FIND to ::openSearch,
        ViewerCommand.EDIT to ::startEdit,
        ViewerCommand.PRINT to ::print,
        ViewerCommand.RENAME to ::requestRename,
        ViewerCommand.TRASH to ::trash,
        ViewerCommand.CLOSE to ::closeOverlay,
    )

    fun run(command: ViewerCommand) = commands.getValue(command)()

    // Files and the folder

    fun show(next: Gallery<Uri>) = guardEdits {
        gallery = next
        load(next.current)
        dropUnsupportedNeighbours(next)
    }

    fun showNext() {
        gallery?.takeIf { it.hasNext }?.let { next -> guardEdits { move(next.next()) } }
    }

    fun showPrevious() {
        gallery?.takeIf { it.hasPrevious }?.let { previous -> guardEdits { move(previous.previous()) } }
    }

    fun requestFolderAccess() {
        val access = folderAccess ?: return
        folderAccess = null
        when (access) {
            FolderAccess.Media -> mediaPermissionRequests.trySend(Unit)
            is FolderAccess.Folder -> folderRequests.trySend(access.initialUri)
        }
    }

    fun dismissFolderAccess() {
        folderAccess = null
    }

    fun onMediaPermission(granted: Boolean) {
        val shown = item ?: return
        if (granted) findNeighbours(shown)
    }

    fun onFolderPicked(tree: Uri?) {
        val shown = item ?: return
        tree ?: return
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { folders.listGranted(tree, shown.uri) }.getOrNull() }
            found?.let { gallery = it }
        }
    }

    /** Gives the shown file to another app; [start] says whether one took it. */
    fun handOver(start: (ViewerItem) -> Boolean) {
        val shown = item ?: return
        if (!start(shown)) message(R.string.no_app)
    }

    fun showDetails() {
        dialog = ViewerDialog.Details.takeIf { item != null }
    }

    fun dismissDialog() {
        dialog = null
    }

    // Zoom and view rotation

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

    /** While editing, turning changes the picture; otherwise only the view turns. */
    fun rotateLeft() {
        edit?.let { return updateEdit { e -> e.rotateCounterClockwise() } }
        updateImage { it.rotateCounterClockwise() }
    }

    fun rotateRight() {
        edit?.let { return updateEdit { e -> e.rotateClockwise() } }
        updateImage { it.rotateClockwise() }
    }

    // Editing

    fun startEdit() {
        val shown = content as? ViewerContent.Image ?: return
        if (shown.picture !is Picture.Still) return
        edit = ImageEdit()
        cropAspect = CropAspect.FREE
    }

    fun updateEdit(change: (ImageEdit) -> ImageEdit) {
        edit = edit?.let(change)
    }

    fun chooseCropAspect(aspect: CropAspect) {
        cropAspect = aspect
        val ratio = cropRatio() ?: return
        updateEdit { it.copy(crop = CropBox.withRatio(it.crop, ratio)) }
    }

    fun dragCrop(handle: CropHandle, dx: Float, dy: Float) {
        updateEdit { it.copy(crop = CropBox.drag(it.crop, handle, dx, dy, cropRatio())) }
    }

    fun cancelEdit() = guardEdits {}

    /** Overwrites the file when its format can be written back, otherwise asks where to save a copy. */
    fun saveEdit() {
        val shown = content as? ViewerContent.Image ?: return
        edit ?: return
        val canOverwrite = shown.item.kind == FileKind.IMAGE && ImageOutputs.canOverwrite(shown.item.mime)
        if (!canOverwrite) return saveEditAs()
        writeEdited(shown, shown.item.uri)
    }

    fun saveEditAs() {
        val shown = content as? ViewerContent.Image ?: return
        edit ?: return
        val output = ImageOutputs.forMime(shown.item.mime.takeIf { shown.item.kind == FileKind.IMAGE })
        saveAsRequests.trySend(SaveAsRequest(ImageOutputs.copyName(shown.item.name, output), output.mime))
    }

    fun onSaveAsPicked(target: Uri?) {
        val shown = content as? ViewerContent.Image ?: return
        target?.let { writeEdited(shown, it) }
    }

    fun answerDiscard(discard: Boolean) {
        val pending = dialog as? ViewerDialog.DiscardEdits ?: return
        dialog = null
        if (!discard) return
        edit = null
        pending.then()
    }

    // PDF

    fun unlockPdf(password: String) {
        val locked = content as? ViewerContent.PdfLocked ?: return
        content = ViewerContent.Loading
        viewModelScope.launch {
            content = withContext(Dispatchers.IO) { loadPdf(locked.item, password) }
        }
    }

    fun openSearch() {
        if (content !is ViewerContent.Pdf) return
        pdfSearch = pdfSearch ?: PdfSearch()
    }

    fun closeSearch() {
        searchJob?.cancel()
        pdfSearch = null
    }

    /** Searches every page once typing pauses; the first match on or after the page in view is selected. */
    fun setSearchQuery(query: String) {
        val document = (content as? ViewerContent.Pdf)?.document ?: return
        pdfSearch = PdfSearch(query)
        searchJob?.cancel()
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DELAY_MILLIS)
            val pages = document.search(query)
            pdfSearch = PdfSearch(query, MatchCursor.of(pages, pdfPage))
        }
    }

    fun searchNext() {
        pdfSearch = pdfSearch?.let { it.copy(cursor = it.cursor.next()) }
    }

    fun searchPrevious() {
        pdfSearch = pdfSearch?.let { it.copy(cursor = it.cursor.previous()) }
    }

    // Rename, trash, print

    fun requestRename() {
        dialog = item?.let { ViewerDialog.Rename(it.name) }
    }

    fun rename(name: String, consented: Boolean = false) {
        val shown = item ?: return
        dialog = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { files.rename(shown.uri, name) }.fold(
                    onSuccess = { renamed -> renamed to null },
                    onFailure = { error -> null to files.mediaUriOf(shown.uri).takeIf { error is SecurityException && !consented } },
                )
            }
            val (renamed, needsConsent) = result
            renamed?.let { return@launch onRenamed(it) }
            needsConsent ?: return@launch message(R.string.rename_failed)
            askConsent(files.writeRequest(needsConsent)) { rename(name, consented = true) }
        }
    }

    /** Media go to the system trash (kept 30 days); other documents can only be deleted, after a question. */
    fun trash() {
        val shown = item ?: return
        viewModelScope.launch {
            val media = withContext(Dispatchers.IO) { files.mediaUriOf(shown.uri) }
            media?.let { return@launch askConsent(files.trashRequest(it)) { onRemoved(R.string.trashed) } }
            dialog = ViewerDialog.Delete(shown.name)
        }
    }

    fun confirmDelete() {
        val shown = item ?: return
        dialog = null
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { runCatching { files.delete(shown.uri) }.getOrDefault(false) }
            deleted.takeIf { it }?.let { return@launch onRemoved(R.string.deleted) }
            message(R.string.trash_failed)
        }
    }

    fun onConsent(approved: Boolean) {
        val action = pendingConsent
        pendingConsent = null
        if (approved) action?.invoke()
    }

    /** Images are printed from a software copy (the shown bitmap may live on the GPU). */
    fun print() {
        val shown = content
        val name = shown.item?.name ?: return
        viewModelScope.launch {
            val job = withContext(Dispatchers.IO) { runCatching { printJobOf(shown, name) }.getOrNull() }
            job?.let { printJobs.trySend(it) } ?: message(R.string.print_failed)
        }
    }

    fun pausePlayback() {
        (content as? ViewerContent.Media)?.playback?.pause()
    }

    override fun onCleared() {
        release(content)
    }

    // Internals

    private fun closeOverlay() {
        edit?.let { return cancelEdit() }
        closeSearch()
    }

    /** Leaving the editor with changes asks first; [then] runs once the edits are dropped. */
    private fun guardEdits(then: () -> Unit) {
        val pending = edit
        val name = item?.name.orEmpty()
        if (pending != null && !pending.isIdentity) {
            dialog = ViewerDialog.DiscardEdits(name, then)
            return
        }
        edit = null
        then()
    }

    private fun cropRatio(): Float? {
        val shown = content as? ViewerContent.Image ?: return null
        val oriented = (edit ?: ImageEdit()).orientedSize(shown.original.width, shown.original.height)
        val imageAspect = oriented.width.toFloat() / oriented.height.coerceAtLeast(1)
        return cropAspect.ratio(imageAspect)?.let { CropBox.normalisedRatio(it, imageAspect) }
    }

    private fun move(next: Gallery<Uri>) {
        gallery = next
        load(next.current)
    }

    private fun load(uri: Uri) {
        loadJob?.cancel()
        searchJob?.cancel()
        release(content)
        content = ViewerContent.Loading
        image = null
        edit = null
        pdfSearch = null
        pdfZoom = 1f
        pdfPage = 0
        folderAccess = null
        loadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { read(uri) }.getOrElse { failure(uri, it) } }
            content = loaded
            image = (loaded as? ViewerContent.Image)?.let {
                ImageTransform.initial(Size2(it.original.width.toFloat(), it.original.height.toFloat()), viewport)
            }
            (loaded.item ?: return@launch).takeIf { gallery?.size == 1 }?.let(::findNeighbours)
        }
    }

    private fun read(uri: Uri): ViewerContent {
        val item = files.itemOf(uri)
        if (item.size == 0L) return ViewerContent.Failed(item, item.name, FailReason.EMPTY)
        return loaders.getValue(item.kind)(item)
    }

    private fun loadPicture(item: ViewerItem): ViewerContent {
        val decoded = files.decodePicture(item.uri)
        return ViewerContent.Image(item, decoded.picture, decoded.original)
    }

    private fun loadSvg(item: ViewerItem): ViewerContent {
        val decoded = files.renderSvg(item.uri)
        return ViewerContent.Image(item, decoded.picture, decoded.original)
    }

    /** PdfRenderer needs a seekable file; streams that are not are copied to the cache first. */
    private fun loadPdf(item: ViewerItem, password: String?): ViewerContent {
        val document = runCatching { PdfDocument.open(files.openForPdf(item.uri), password) }
            .recoverCatching { error -> (error as? SecurityException)?.let { throw it } ?: openCopy(item.uri, password) }
            .getOrElse { error ->
                (error as? SecurityException)?.let { return ViewerContent.PdfLocked(item, wrongPassword = password != null) }
                return ViewerContent.Failed(item, item.name, FailReason.FAILED)
            }
        return ViewerContent.Pdf(item, document)
    }

    private fun openCopy(uri: Uri, password: String?): PdfDocument {
        val copy = File.createTempFile("document", ".pdf", cacheDir)
        pdfCopy = copy
        files.copyTo(uri, copy)
        return PdfDocument.open(ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY), password)
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

    private fun findNeighbours(shown: ViewerItem) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { folders.find(shown.uri, shown.kind) }.getOrNull() }
            when (result) {
                is FolderResult.Found -> gallery = result.gallery
                is FolderResult.Ask -> folderAccess = result.access
                FolderResult.Unknown, null -> Unit
            }
        }
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

    /** A copy of an SVG is drawn from the rendered bitmap; photos are decoded again at full quality. */
    private fun writeEdited(shown: ViewerContent.Image, target: Uri) {
        val changes = edit ?: return
        val stillBitmap = (shown.picture as? Picture.Still)?.bitmap ?: return
        val output = ImageOutputs.forMime(shown.item.mime.takeIf { shown.item.kind == FileKind.IMAGE })
        val isPhoto = shown.item.kind == FileKind.IMAGE
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val source = files.decodeStill(shown.item.uri).takeIf { isPhoto } ?: stillBitmap
                    files.writeEdited(source, changes, target, output, exifFrom = shown.item.uri.takeIf { isPhoto })
                }
            }
            written.onSuccess { onEditSaved(shown.item, target) }.onFailure { message(R.string.save_failed) }
        }
    }

    private fun onEditSaved(saved: ViewerItem, target: Uri) {
        edit = null
        message(R.string.saved)
        if (target == saved.uri) load(saved.uri)
    }

    private fun onRenamed(renamed: Uri) {
        message(R.string.renamed)
        val current = gallery ?: Gallery(listOf(renamed), 0)
        gallery = current.copy(items = current.items.mapIndexed { i, uri -> renamed.takeIf { i == current.index } ?: uri })
        load(renamed)
    }

    /** The removed file leaves the folder; the next one (or the previous one at the end) is shown. */
    private fun onRemoved(@StringRes text: Int) {
        message(text)
        val current = gallery ?: return showWelcome()
        val remaining = current.items.filterIndexed { i, _ -> i != current.index }
        if (remaining.isEmpty()) return showWelcome()
        move(Gallery(remaining, current.index.coerceAtMost(remaining.lastIndex)))
    }

    private fun showWelcome() {
        release(content)
        gallery = null
        content = ViewerContent.Welcome
        image = null
    }

    private fun askConsent(sender: IntentSender, onApproved: () -> Unit) {
        pendingConsent = onApproved
        consentRequests.trySend(sender)
    }

    private fun printJobOf(shown: ViewerContent, name: String): PrintJob? = when (shown) {
        is ViewerContent.Image -> PrintJob.Picture(
            name,
            files.decodeStill(shown.item.uri).takeIf { shown.item.kind == FileKind.IMAGE }
                ?: (shown.picture as Picture.Still).bitmap,
        )
        is ViewerContent.Pdf -> PrintJob.Document(name, shown.item.uri)
        is ViewerContent.Text -> PrintJob.Lines(name, shown.lines)
        else -> null
    }

    private fun release(old: ViewerContent) {
        (old as? ViewerContent.Pdf)?.document?.close()
        (old as? ViewerContent.Media)?.playback?.release()
        ((old as? ViewerContent.Image)?.picture as? Picture.Moving)?.drawable?.stop()
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
        const val SEARCH_DELAY_MILLIS = 250L
    }
}
