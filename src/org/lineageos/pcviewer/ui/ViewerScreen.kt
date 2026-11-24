package org.lineageos.pcviewer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.lineageos.pcviewer.FailReason
import org.lineageos.pcviewer.FileIntents
import org.lineageos.pcviewer.R
import org.lineageos.pcviewer.SaveAsRequest
import org.lineageos.pcviewer.ViewerContent
import org.lineageos.pcviewer.ViewerViewModel
import org.lineageos.pcviewer.core.FileKind
import org.lineageos.pcviewer.core.Gallery
import org.lineageos.pcviewer.core.ViewerShortcuts

private val OPEN_TYPES = arrayOf("image/*", "application/pdf", "text/plain")

/** "Save as" for a rotated image, through the system file picker. */
private class CreateImageDocument : ActivityResultContract<SaveAsRequest, Uri?>() {
    override fun createIntent(context: Context, input: SaveAsRequest): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mime)
            .putExtra(Intent.EXTRA_TITLE, input.name)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

@Composable
fun ViewerScreen(vm: ViewerViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val openFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.takeIf { it.isNotEmpty() }?.let { vm.show(Gallery(it, 0)) }
    }
    val saveAs = rememberLauncherForActivityResult(CreateImageDocument(), vm::onSaveAsPicked)
    val actions = ScreenActions(
        open = { openFiles.launch(OPEN_TYPES) },
        edit = { vm.handOver { FileIntents.edit(context, it) } },
        share = { vm.handOver { FileIntents.share(context, it) } },
    )

    LaunchedEffect(Unit) { for (text in vm.messages) snackbar.showSnackbar(context.getString(text)) }
    LaunchedEffect(Unit) { for (request in vm.openRequests) actions.open() }
    LaunchedEffect(Unit) { for (request in vm.saveAsRequests) saveAs.launch(request) }

    Scaffold(
        modifier = Modifier.onPreviewKeyEvent { event ->
            val command = event.toChord()?.let(ViewerShortcuts::commandFor) ?: return@onPreviewKeyEvent false
            vm.run(command)
            true
        },
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopBar(vm, actions) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainer)) {
            ContentArea(vm, actions)
            NavigationButtons(vm.gallery, vm::showPrevious, vm::showNext)
        }
    }
    if (vm.showDetails) DetailsDialog(vm.content) { vm.showDetails = false }
}

private data class ScreenActions(val open: () -> Unit, val edit: () -> Unit, val share: () -> Unit)

@Composable
private fun ContentArea(vm: ViewerViewModel, actions: ScreenActions) {
    when (val content = vm.content) {
        ViewerContent.Welcome -> WelcomeState(actions.open)
        ViewerContent.Loading -> LoadingState()
        is ViewerContent.Image -> ImagePane(
            content.bitmap,
            content.original,
            vm.image,
            ImageGestures(onViewport = vm::setViewport, onTransform = vm::updateImage),
        )
        is ViewerContent.Pdf -> PdfPane(content.document, vm.pdfZoom, vm::zoomPdfBy)
        is ViewerContent.Text -> TextPane(content.lines)
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

/** File name on the left; zoom, rotate, save, edit, share and the menu on the right, as the content allows. */
@Composable
private fun TopBar(vm: ViewerViewModel, actions: ScreenActions) {
    val content = vm.content
    val kind = content.item?.kind
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            content.item?.name ?: stringResource(R.string.app_name),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (content is ViewerContent.Image || content is ViewerContent.Pdf) ZoomControls(vm)
        if (content is ViewerContent.Image) RotateControls(vm)
        if (vm.canSave) SaveButton(vm)
        if (kind == FileKind.TEXT) BarIcon(Icons.Outlined.Edit, R.string.edit_file, actions.edit)
        if (content.item != null) BarIcon(Icons.Outlined.Share, R.string.share, actions.share)
        MoreMenu(vm, actions)
    }
}

@Composable
private fun ZoomControls(vm: ViewerViewModel) {
    val percent = vm.image?.percent ?: (vm.pdfZoom * 100).toInt()
    BarIcon(Icons.Filled.Remove, R.string.zoom_out, vm::zoomOut)
    TextButton(onClick = vm::fit) { Text(stringResource(R.string.zoom_percent, percent)) }
    BarIcon(Icons.Filled.Add, R.string.zoom_in, vm::zoomIn)
}

@Composable
private fun RotateControls(vm: ViewerViewModel) {
    BarIcon(Icons.AutoMirrored.Outlined.RotateLeft, R.string.rotate_left) { vm.updateImage { it.rotateCounterClockwise() } }
    BarIcon(Icons.AutoMirrored.Outlined.RotateRight, R.string.rotate_right) { vm.updateImage { it.rotateClockwise() } }
}

@Composable
private fun SaveButton(vm: ViewerViewModel) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = vm::save, modifier = Modifier.padding(start = 4.dp)) {
            Text(stringResource(R.string.save))
        }
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.save_as))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.save_as)) }, onClick = {
                open = false
                vm.saveAs()
            })
        }
    }
}

@Composable
private fun MoreMenu(vm: ViewerViewModel, actions: ScreenActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarIcon(Icons.Filled.MoreVert, R.string.more_options) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.open_other)) }, onClick = {
                open = false
                actions.open()
            })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.file_info)) },
                enabled = vm.item != null,
                onClick = {
                    open = false
                    vm.showDetails = true
                },
            )
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = stringResource(label)) }
}
