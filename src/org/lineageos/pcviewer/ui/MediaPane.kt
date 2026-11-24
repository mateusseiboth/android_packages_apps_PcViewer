package org.lineageos.pcviewer.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.lineageos.pcviewer.MediaPlayback
import org.lineageos.pcviewer.R
import org.lineageos.pcviewer.core.MediaTime

private const val POSITION_TICK_MILLIS = 250L

/** A video fills the window above the controls; audio shows a note and the file name. */
@Composable
fun MediaPane(playback: MediaPlayback, name: String, isVideo: Boolean) {
    LaunchedEffect(playback, playback.isPlaying) {
        while (playback.isPlaying) {
            playback.refreshPosition()
            delay(POSITION_TICK_MILLIS)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (playback.hasFailed) return@Box MediaFailed(name)
            if (isVideo) return@Box VideoSurface(playback)
            AudioArt(name)
        }
        MediaControls(playback)
    }
}

@Composable
private fun VideoSurface(playback: MediaPlayback) {
    val ratio = playback.videoSize?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = playback.setDisplay(holder)

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) = playback.setDisplay(null)
                    })
                }
            },
            modifier = Modifier.aspectRatio(ratio),
        )
    }
}

@Composable
private fun AudioArt(name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(160.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MediaFailed(name: String) {
    Text(
        stringResource(R.string.media_failed, name),
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MediaControls(playback: MediaPlayback) {
    val duration = playback.duration.coerceAtLeast(1)
    val positionLabel = stringResource(R.string.media_position)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(MediaTime.format(playback.position), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = playback.position.toFloat() / duration,
                onValueChange = { playback.seekTo((it * duration).toLong()) },
                enabled = playback.isReady,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = positionLabel },
            )
            Text(MediaTime.format(playback.duration), style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = playback::nextSpeed) {
                Text(stringResource(R.string.speed_value, formatSpeed(playback.speed)))
            }
            IconButton(onClick = { playback.skip(-MediaTime.SKIP_MILLIS) }) {
                Icon(Icons.Outlined.Replay10, contentDescription = stringResource(R.string.back_10))
            }
            val playLabel = R.string.pause.takeIf { playback.isPlaying } ?: R.string.play
            FilledIconButton(onClick = playback::toggle, modifier = Modifier.size(56.dp), enabled = playback.isReady) {
                Icon(Icons.Filled.Pause.takeIf { playback.isPlaying } ?: Icons.Filled.PlayArrow, contentDescription = stringResource(playLabel))
            }
            IconButton(onClick = { playback.skip(MediaTime.SKIP_MILLIS) }) {
                Icon(Icons.Outlined.Forward10, contentDescription = stringResource(R.string.forward_10))
            }
            val muteLabel = R.string.unmute.takeIf { playback.isMuted } ?: R.string.mute
            IconButton(onClick = playback::toggleMute) {
                Icon(
                    Icons.AutoMirrored.Outlined.VolumeOff.takeIf { playback.isMuted } ?: Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = stringResource(muteLabel),
                )
            }
        }
    }
}

/** "1", "1.25", "0.5": no trailing zeros. */
private fun formatSpeed(speed: Float): String = speed.toString().removeSuffix(".0")
