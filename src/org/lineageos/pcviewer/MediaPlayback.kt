package org.lineageos.pcviewer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.SurfaceHolder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.lineageos.pcviewer.core.MediaTime
import org.lineageos.pcviewer.core.PixelSize

/**
 * Audio or video through the platform's MediaPlayer (Media3/ExoPlayer in external/exoplayer is visible only
 * to MediaProvider). Created and used on the main thread; its callbacks arrive there too.
 */
class MediaPlayback(context: Context, uri: Uri, isVideo: Boolean) {
    private val player = MediaPlayer()

    var isReady by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var hasFailed by mutableStateOf(false)
        private set
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var videoSize by mutableStateOf<PixelSize?>(null)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var isMuted by mutableStateOf(false)
        private set

    init {
        val usage = AudioAttributes.CONTENT_TYPE_MOVIE.takeIf { isVideo } ?: AudioAttributes.CONTENT_TYPE_MUSIC
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(usage).build())
        player.setOnPreparedListener { onPrepared() }
        player.setOnCompletionListener { onCompleted() }
        player.setOnErrorListener { _, _, _ -> onError() }
        player.setOnVideoSizeChangedListener { _, width, height ->
            videoSize = PixelSize(width, height).takeIf { width > 0 && height > 0 }
        }
        runCatching {
            player.setDataSource(context, uri)
            player.prepareAsync()
        }.onFailure { hasFailed = true }
    }

    fun toggle() {
        if (!isReady) return
        if (isPlaying) return pause()
        play()
    }

    fun pause() {
        if (!isPlaying) return
        player.pause()
        isPlaying = false
        refreshPosition()
    }

    fun seekTo(millis: Long) {
        if (!isReady) return
        val target = millis.coerceIn(0, duration)
        player.seekTo(target, MediaPlayer.SEEK_CLOSEST)
        position = target
    }

    fun skip(by: Long) = seekTo(MediaTime.skip(position, by, duration))

    /** Setting a speed resumes a paused player, so a paused one keeps it until the next play. */
    fun nextSpeed() {
        speed = MediaTime.nextSpeed(speed)
        if (isPlaying) applySpeed()
    }

    fun toggleMute() {
        isMuted = !isMuted
        val volume = 0f.takeIf { isMuted } ?: 1f
        player.setVolume(volume, volume)
    }

    fun setDisplay(holder: SurfaceHolder?) {
        runCatching { player.setDisplay(holder) }
    }

    /** Called a few times a second while playing, to move the slider. */
    fun refreshPosition() {
        if (!isReady) return
        position = player.currentPosition.toLong()
    }

    fun release() {
        isPlaying = false
        isReady = false
        player.release()
    }

    private fun play() {
        if (position >= duration) seekTo(0)
        player.start()
        applySpeed()
        isPlaying = true
    }

    private fun applySpeed() {
        runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
    }

    private fun onPrepared() {
        isReady = true
        duration = player.duration.toLong().coerceAtLeast(0)
        play()
    }

    private fun onCompleted() {
        isPlaying = false
        position = duration
    }

    private fun onError(): Boolean {
        hasFailed = true
        isPlaying = false
        return true
    }
}
