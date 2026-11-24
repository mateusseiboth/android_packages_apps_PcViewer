package org.lineageos.pcviewer.core

import java.util.Locale

/** Time labels, skips and playback speeds for audio and video. */
object MediaTime {
    const val SKIP_MILLIS = 10_000L
    val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private const val EPSILON = 0.001f

    /** "1:05", or "1:02:03" past an hour. */
    fun format(millis: Long): String {
        val seconds = millis.coerceAtLeast(0) / 1000
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val rest = seconds % 60
        return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest).takeIf { hours > 0 }
            ?: String.format(Locale.ROOT, "%d:%02d", minutes, rest)
    }

    fun skip(position: Long, by: Long, duration: Long): Long = (position + by).coerceIn(0, duration.coerceAtLeast(0))

    /** The next faster speed, back to the slowest after the fastest. */
    fun nextSpeed(current: Float): Float = SPEEDS.firstOrNull { it > current + EPSILON } ?: SPEEDS.first()
}
