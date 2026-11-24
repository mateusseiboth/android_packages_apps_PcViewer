package org.lineageos.pcviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.lineageos.pcviewer.FailReason
import org.lineageos.pcviewer.R

private val REASON_TEXTS = mapOf(
    FailReason.UNSUPPORTED to R.string.cant_open_unsupported,
    FailReason.FAILED to R.string.cant_open_failed,
    FailReason.EMPTY to R.string.cant_open_empty,
    FailReason.PROTECTED to R.string.cant_open_protected,
    FailReason.TOO_LARGE to R.string.cant_open_too_large,
)

/** The window opened from the launcher: a welcome and the button to pick files. */
@Composable
fun WelcomeState(onOpen: () -> Unit) {
    CentredState(Icons.Outlined.PhotoLibrary, stringResource(R.string.welcome_title), stringResource(R.string.welcome_subtitle)) {
        Button(onClick = onOpen) { Text(stringResource(R.string.open_file)) }
    }
}

/** A file that can't be shown; a text file too large to show can still be edited. */
@Composable
fun FailedState(name: String, reason: FailReason, onOpen: () -> Unit, onEdit: (() -> Unit)?) {
    CentredState(
        Icons.Outlined.BrokenImage,
        stringResource(R.string.cant_open_title),
        stringResource(REASON_TEXTS.getValue(reason), name),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            onEdit?.let { OutlinedButton(onClick = it) { Text(stringResource(R.string.edit_file)) } }
            Button(onClick = onOpen) { Text(stringResource(R.string.open_other)) }
        }
    }
}

@Composable
fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun CentredState(icon: ImageVector, title: String, subtitle: String, actions: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            actions()
        }
    }
}
