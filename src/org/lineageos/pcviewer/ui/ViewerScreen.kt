package org.lineageos.pcviewer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lineageos.pcviewer.FailReason
import org.lineageos.pcviewer.FileIntents
import org.lineageos.pcviewer.FolderFinder
import org.lineageos.pcviewer.Picture
import org.lineageos.pcviewer.PrintJob
import org.lineageos.pcviewer.Printing
import org.lineageos.pcviewer.R
import org.lineageos.pcviewer.SaveAsRequest
import org.lineageos.pcviewer.ViewerContent
import org.lineageos.pcviewer.ViewerViewModel
import org.lineageos.pcviewer.core.Gallery
import org.lineageos.pcviewer.core.KeyChord
import org.lineageos.pcviewer.core.ViewerShortcuts

private val OPEN_TYPES = arrayOf("image/*", "application/pdf", "text/plain", "audio/*", "video/*")

/** "Save as" for an edited image, through the system file picker. */
private class CreateImageDocument : ActivityResultContract<SaveAsRequest, Uri?>() {
    override fun createIntent(context: Context, input: SaveAsRequest): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mime)
            .putExtra(Intent.EXTRA_TITLE, input.name)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

data class ScreenActions(val open: () -> Unit, val edit: () -> Unit, val share: () -> Unit)

/** Each print job goes to its own kind of print document. */
private fun print(context: Context, job: PrintJob) = when (job) {
    is PrintJob.Picture -> Printing.printImage(context, job.name, job.bitmap)
    is PrintJob.Document -> Printing.printPdf(context, job.name, job.uri)
    is PrintJob.Lines -> Printing.printText(context, job.name, job.lines)
}

@Composable
fun ViewerScreen(vm: ViewerViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val typing = remember { mutableStateOf(false) }
    val openFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.takeIf { it.isNotEmpty() }?.let { vm.show(Gallery(it, 0)) }
    }
    val actions = ScreenActions(
        open = { openFiles.launch(OPEN_TYPES) },
        edit = { vm.handOver { FileIntents.edit(context, it) } },
        share = { vm.handOver { FileIntents.share(context, it) } },
    )
    RequestChannels(vm, snackbar, actions)

    Scaffold(
        modifier = Modifier.onPreviewKeyEvent { event ->
            val chord = event.toChord() ?: return@onPreviewKeyEvent false
            if (typing.value && isPlainKey(chord)) return@onPreviewKeyEvent false
            val command = ViewerShortcuts.commandFor(chord) ?: return@onPreviewKeyEvent false
            vm.run(command)
            true
        },
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopBar(vm, actions, typing) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainer)) {
            MainArea(vm, actions)
        }
    }
    ViewerDialogs(vm)
}

/** Keys without Ctrl or Alt belong to a text field while one has focus. */
private fun isPlainKey(chord: KeyChord) = !chord.ctrl && !chord.alt

@Composable
private fun MainArea(vm: ViewerViewModel, actions: ScreenActions) {
    val shown = vm.content as? ViewerContent.Image
    val editable = (shown?.picture as? Picture.Still)?.bitmap
    if (vm.isEditing && shown != null && editable != null) return EditPane(editable, shown.original, vm)
    ContentArea(vm, actions)
    NavigationButtons(vm.gallery, vm::showPrevious, vm::showNext)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        vm.folderAccess?.let { access -> FolderBanner(access, vm::requestFolderAccess, vm::dismissFolderAccess, Modifier) }
    }
}

/** The view model's requests that need an activity: pickers, consent dialogs, permissions, printing. */
@Composable
private fun RequestChannels(vm: ViewerViewModel, snackbar: SnackbarHostState, actions: ScreenActions) {
    val context = LocalContext.current
    val saveAs = rememberLauncherForActivityResult(CreateImageDocument(), vm::onSaveAsPicked)
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onConsent(result.resultCode == Activity.RESULT_OK)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        vm.onMediaPermission(granted.values.any { it })
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), vm::onFolderPicked)

    LaunchedEffect(Unit) { for (text in vm.messages) snackbar.showSnackbar(context.getString(text)) }
    LaunchedEffect(Unit) { for (request in vm.openRequests) actions.open() }
    LaunchedEffect(Unit) { for (request in vm.saveAsRequests) saveAs.launch(request) }
    LaunchedEffect(Unit) { for (sender in vm.consentRequests) consent.launch(IntentSenderRequest.Builder(sender).build()) }
    LaunchedEffect(Unit) { for (request in vm.mediaPermissionRequests) permissions.launch(FolderFinder.MEDIA_PERMISSIONS) }
    LaunchedEffect(Unit) { for (initial in vm.folderRequests) folder.launch(initial) }
    LaunchedEffect(Unit) {
        for (job in vm.printJobs) {
            runCatching { print(context, job) }
                .onFailure { snackbar.showSnackbar(context.getString(R.string.print_failed)) }
        }
    }
}

@Composable
private fun ContentArea(vm: ViewerViewModel, actions: ScreenActions) {
    val context = LocalContext.current
    when (val content = vm.content) {
        ViewerContent.Welcome -> WelcomeState(actions.open)
        ViewerContent.Loading -> LoadingState()
        is ViewerContent.Image -> PicturePane(
            content.picture,
            content.original,
            vm.image,
            ImageGestures(onViewport = vm::setViewport, onTransform = vm::updateImage),
        )
        is ViewerContent.Pdf -> PdfPane(
            content.document,
            vm.pdfZoom,
            vm.pdfSearch,
            PdfActions(
                onZoomBy = vm::zoomPdfBy,
                onPage = { vm.pdfPage = it },
                onWebLink = { uri -> vm.handOver { FileIntents.openLink(context, uri) } },
            ),
        )
        is ViewerContent.PdfLocked -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PdfPasswordState(content.item.name, content.wrongPassword, vm::unlockPdf)
        }
        is ViewerContent.Text -> TextPane(content.lines)
        is ViewerContent.Media -> MediaPane(content.playback, content.item.name, content.isVideo)
        is ViewerContent.Failed -> FailedState(
            content.name,
            content.reason,
            onOpen = actions.open,
            onEdit = actions.edit.takeIf { content.reason == FailReason.TOO_LARGE && content.item != null },
        )
    }
}

/** Round buttons at the left and right edges to step through the folder. */
@Composable
private fun NavigationButtons(gallery: Gallery<Uri>?, onPrevious: () -> Unit, onNext: () -> Unit) {
    gallery ?: return
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (gallery.hasPrevious) {
            RoundButton(Icons.AutoMirrored.Filled.ArrowBack, R.string.previous, onPrevious, Modifier.align(Alignment.CenterStart))
        }
        if (gallery.hasNext) {
            RoundButton(Icons.AutoMirrored.Filled.ArrowForward, R.string.next, onNext, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: Int, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = stringResource(label))
    }
}
