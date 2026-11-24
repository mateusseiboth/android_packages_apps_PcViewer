package org.lineageos.pcviewer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.lineageos.pcviewer.FolderAccess
import org.lineageos.pcviewer.R
import org.lineageos.pcviewer.ViewerDialog
import org.lineageos.pcviewer.ViewerViewModel
import org.lineageos.pcviewer.core.FileNames
import org.lineageos.pcviewer.core.NameProblem

/** Shows the dialog the view model asks for; each kind has its own composable. */
@Composable
fun ViewerDialogs(vm: ViewerViewModel) {
    when (val dialog = vm.dialog) {
        ViewerDialog.Details -> DetailsDialog(vm.content, vm::dismissDialog)
        is ViewerDialog.Rename -> RenameDialog(dialog.name, vm::rename, vm::dismissDialog)
        is ViewerDialog.Delete -> DeleteDialog(dialog.name, vm::confirmDelete, vm::dismissDialog)
        is ViewerDialog.DiscardEdits -> DiscardDialog(dialog.name, vm::answerDiscard)
        null -> Unit
    }
}

@Composable
private fun RenameDialog(name: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(name, TextRange(0, FileNames.baseLength(name)))) }
    val problem = FileNames.problemWith(field.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val confirm = { field.text.trim().takeIf { problem == null && it != name }?.let(onRename) ?: onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                isError = problem != null,
                supportingText = { problem?.let { Text(problemText(it)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        },
        confirmButton = { TextButton(onClick = { confirm() }, enabled = problem == null) { Text(stringResource(R.string.rename)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun problemText(problem: NameProblem): String = when (problem) {
    NameProblem.Empty -> stringResource(R.string.name_empty)
    NameProblem.StartsWithDot -> stringResource(R.string.name_dot)
    is NameProblem.InvalidCharacter -> stringResource(R.string.name_char, problem.character.toString())
}

@Composable
private fun DeleteDialog(name: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_message, name)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DiscardDialog(name: String, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_message, name)) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.keep_editing)) } },
    )
}

/** Asks for a protected PDF's password, in place of the pages. */
@Composable
fun PdfPasswordState(name: String, wrongPassword: Boolean, onOpen: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val wrongHint: @Composable () -> Unit = { Text(stringResource(R.string.pdf_password_wrong)) }
    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 3.dp, modifier = Modifier.widthIn(max = 420.dp).padding(24.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.pdf_password_title), style = MaterialTheme.typography.headlineSmall)
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text(stringResource(R.string.pdf_password_hint)) },
                singleLine = true,
                isError = wrongPassword,
                supportingText = wrongHint.takeIf { wrongPassword },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onOpen(password) }),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { onOpen(password) }, enabled = password.isNotEmpty()) { Text(stringResource(R.string.open)) }
            }
        }
    }
}

/** Offers access to the folder of a file opened on its own, so previous and next can step through it. */
@Composable
fun FolderBanner(access: FolderAccess, onAllow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    val text = R.string.folder_media.takeIf { access == FolderAccess.Media } ?: R.string.folder_tree
    Surface(modifier = modifier.padding(16.dp).widthIn(max = 640.dp), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, shadowElevation = 3.dp) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(text), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) }
            TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) }
        }
    }
}
