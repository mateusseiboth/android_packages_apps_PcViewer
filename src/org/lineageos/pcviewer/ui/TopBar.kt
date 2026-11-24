package org.lineageos.pcviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.lineageos.pcviewer.Picture
import org.lineageos.pcviewer.R
import org.lineageos.pcviewer.ViewerContent
import org.lineageos.pcviewer.ViewerViewModel
import org.lineageos.pcviewer.core.FileKind

/** File name on the left; the actions the shown content allows on the right. Editing has its own bar. */
@Composable
fun TopBar(vm: ViewerViewModel, actions: ScreenActions, typing: MutableState<Boolean>) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            vm.item?.name ?: stringResource(R.string.app_name),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (vm.isEditing) return@Row EditingActions(vm)
        ViewingActions(vm, actions, typing)
    }
}

@Composable
private fun EditingActions(vm: ViewerViewModel) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = vm::cancelEdit) { Text(stringResource(R.string.cancel)) }
    FilledTonalButton(onClick = vm::saveEdit, modifier = Modifier.padding(start = 4.dp)) { Text(stringResource(R.string.save)) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.save_as))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.save_as)) }, onClick = {
                open = false
                vm.saveEditAs()
            })
        }
    }
}

@Composable
private fun ViewingActions(vm: ViewerViewModel, actions: ScreenActions, typing: MutableState<Boolean>) {
    val content = vm.content
    val search = vm.pdfSearch
    if (content is ViewerContent.Pdf && search != null) SearchField(vm, typing)
    if (content is ViewerContent.Pdf && search == null) BarIcon(Icons.Outlined.Search, R.string.find_in_pdf, vm::openSearch)
    if (content is ViewerContent.Image || content is ViewerContent.Pdf) ZoomControls(vm)
    if (content is ViewerContent.Image) RotateControls(vm)
    if ((content as? ViewerContent.Image)?.picture is Picture.Still) BarIcon(Icons.Outlined.Tune, R.string.edit, vm::startEdit)
    if (content.item?.kind == FileKind.TEXT) BarIcon(Icons.Outlined.Edit, R.string.edit_file, actions.edit)
    if (content.item != null) BarIcon(Icons.Outlined.Share, R.string.share, actions.share)
    MoreMenu(vm, actions)
}

/** Find in PDF: the query, "3 / 12", previous, next and close. */
@Composable
private fun SearchField(vm: ViewerViewModel, typing: MutableState<Boolean>) {
    val search = vm.pdfSearch ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = search.query,
        onValueChange = vm::setSearchQuery,
        modifier = Modifier.width(280.dp).focusRequester(focus).onFocusChanged { typing.value = it.isFocused },
        placeholder = { Text(stringResource(R.string.find_in_pdf)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { vm.searchNext() }),
        suffix = {
            search.query.takeIf { it.isNotBlank() }?.let {
                Text(
                    stringResource(R.string.match_count, search.cursor.position, search.cursor.count),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
    )
    BarIcon(Icons.Filled.KeyboardArrowUp, R.string.find_previous, vm::searchPrevious)
    BarIcon(Icons.Filled.KeyboardArrowDown, R.string.find_next, vm::searchNext)
    BarIcon(Icons.Filled.Close, R.string.find_close) {
        typing.value = false
        vm.closeSearch()
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
    BarIcon(Icons.AutoMirrored.Outlined.RotateLeft, R.string.rotate_left, vm::rotateLeft)
    BarIcon(Icons.AutoMirrored.Outlined.RotateRight, R.string.rotate_right, vm::rotateRight)
}

@Composable
private fun MoreMenu(vm: ViewerViewModel, actions: ScreenActions) {
    var open by remember { mutableStateOf(false) }
    val hasFile = vm.item != null
    val printable = vm.content is ViewerContent.Image || vm.content is ViewerContent.Pdf || vm.content is ViewerContent.Text
    val entries = listOf(
        MenuEntry(R.string.open_other, true, actions.open),
        MenuEntry(R.string.rename, hasFile, vm::requestRename),
        MenuEntry(R.string.trash, hasFile, vm::trash),
        MenuEntry(R.string.print, printable, vm::print),
        MenuEntry(R.string.file_info, hasFile, vm::showDetails),
    )
    Box {
        BarIcon(Icons.Filled.MoreVert, R.string.more_options) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.label)) },
                    enabled = entry.enabled,
                    onClick = {
                        open = false
                        entry.onClick()
                    },
                )
            }
        }
    }
}

private data class MenuEntry(val label: Int, val enabled: Boolean, val onClick: () -> Unit)

@Composable
private fun BarIcon(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = stringResource(label)) }
}
