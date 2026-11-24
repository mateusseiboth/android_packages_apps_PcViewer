package org.lineageos.pcviewer.core

enum class ViewerCommand {
    PREVIOUS, NEXT, ZOOM_IN, ZOOM_OUT, FIT, ROTATE_LEFT, ROTATE_RIGHT, OPEN, SAVE, PLAY_PAUSE, FIND, EDIT, PRINT,
    RENAME, TRASH, CLOSE,
}

/** A key with its modifiers; [key] is the key's name ("Left", "Plus", "0"). */
data class KeyChord(val key: String, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false)

object ViewerShortcuts {
    private val COMMANDS = mapOf(
        KeyChord("Left") to ViewerCommand.PREVIOUS,
        KeyChord("Right") to ViewerCommand.NEXT,
        KeyChord("Plus", ctrl = true) to ViewerCommand.ZOOM_IN,
        KeyChord("Equals", ctrl = true) to ViewerCommand.ZOOM_IN,
        KeyChord("Minus", ctrl = true) to ViewerCommand.ZOOM_OUT,
        KeyChord("0", ctrl = true) to ViewerCommand.FIT,
        KeyChord("R", ctrl = true) to ViewerCommand.ROTATE_RIGHT,
        KeyChord("R", ctrl = true, shift = true) to ViewerCommand.ROTATE_LEFT,
        KeyChord("O", ctrl = true) to ViewerCommand.OPEN,
        KeyChord("S", ctrl = true) to ViewerCommand.SAVE,
        KeyChord("Space") to ViewerCommand.PLAY_PAUSE,
        KeyChord("F", ctrl = true) to ViewerCommand.FIND,
        KeyChord("E", ctrl = true) to ViewerCommand.EDIT,
        KeyChord("P", ctrl = true) to ViewerCommand.PRINT,
        KeyChord("F2") to ViewerCommand.RENAME,
        KeyChord("Delete") to ViewerCommand.TRASH,
        KeyChord("Escape") to ViewerCommand.CLOSE,
    )

    fun commandFor(chord: KeyChord): ViewerCommand? = COMMANDS[chord]
}
