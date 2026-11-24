package org.lineageos.pcviewer.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import org.lineageos.pcviewer.R
import org.lineageos.pcviewer.ViewerContent
import org.lineageos.pcviewer.ViewerItem
import org.lineageos.pcviewer.core.FileKind
import org.lineageos.pcviewer.core.FileKinds

/** Name, type, size, date and, for images and PDFs, the dimensions or page count. */
@Composable
fun DetailsDialog(content: ViewerContent, onDismiss: () -> Unit) {
    val item = content.item ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                detailRows(content, item).forEach { (label, value) -> DetailRow(stringResource(label), value) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun detailRows(content: ViewerContent, item: ViewerItem): List<Pair<Int, String>> {
    val context = LocalContext.current
    val size = item.size.takeIf { it >= 0 }?.let { Formatter.formatFileSize(context, it) }
    val modified = item.modified?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
    val extra = when (content) {
        is ViewerContent.Image -> R.string.details_dimensions to
            stringResource(R.string.dimensions_value, content.original.width, content.original.height)
        is ViewerContent.Pdf -> R.string.details_pages to content.document.pageCount.toString()
        else -> null
    }
    return listOfNotNull(
        R.string.details_name to item.name,
        R.string.details_type to typeLabel(item),
        size?.let { R.string.details_size to it },
        modified?.let { R.string.details_modified to it },
        extra,
    )
}

@Composable
private fun typeLabel(item: ViewerItem): String {
    val type = FileKinds.typeName(item.mime, item.name)
    val labels = mapOf(
        FileKind.IMAGE to R.string.type_image,
        FileKind.PDF to R.string.type_pdf,
        FileKind.TEXT to R.string.type_text,
        FileKind.UNSUPPORTED to R.string.type_other,
    )
    return stringResource(labels.getValue(item.kind), type)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
