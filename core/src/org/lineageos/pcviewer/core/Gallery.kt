package org.lineageos.pcviewer.core

/**
 * The files of a folder the viewer can step through, and the one on screen. The file manager hands them over
 * as the intent's clip items plus EXTRA_INDEX; a single file is a gallery of one.
 */
data class Gallery<T>(val items: List<T>, val index: Int) {
    val current: T get() = items[index]
    val hasNext: Boolean get() = index < items.lastIndex
    val hasPrevious: Boolean get() = index > 0
    val position: Int get() = index + 1
    val size: Int get() = items.size

    fun next(): Gallery<T> = copy(index = (index + 1).coerceAtMost(items.lastIndex))

    fun previous(): Gallery<T> = copy(index = (index - 1).coerceAtLeast(0))

    /** Keeps the items that pass [keep], and always the current one. */
    fun retain(keep: (T) -> Boolean): Gallery<T> {
        val kept = items.filterIndexed { i, item -> i == index || keep(item) }
        return Gallery(kept, kept.indexOf(current))
    }

    companion object {
        fun <T> of(data: T?, siblings: List<T>, index: Int?): Gallery<T> {
            if (siblings.isEmpty()) return Gallery(listOfNotNull(data), 0)
            val start = index ?: siblings.indexOf(data).coerceAtLeast(0)
            return Gallery(siblings, start.coerceIn(0, siblings.lastIndex))
        }
    }
}
