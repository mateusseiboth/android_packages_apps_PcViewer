package org.lineageos.pcviewer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerShortcutsTest {
    @Test
    fun navigation() {
        assertEquals(ViewerCommand.PREVIOUS, ViewerShortcuts.commandFor(KeyChord("Left")))
        assertEquals(ViewerCommand.NEXT, ViewerShortcuts.commandFor(KeyChord("Right")))
    }

    @Test
    fun zoom() {
        assertEquals(ViewerCommand.ZOOM_IN, ViewerShortcuts.commandFor(KeyChord("Plus", ctrl = true)))
        assertEquals(ViewerCommand.ZOOM_IN, ViewerShortcuts.commandFor(KeyChord("Equals", ctrl = true)))
        assertEquals(ViewerCommand.ZOOM_OUT, ViewerShortcuts.commandFor(KeyChord("Minus", ctrl = true)))
        assertEquals(ViewerCommand.FIT, ViewerShortcuts.commandFor(KeyChord("0", ctrl = true)))
    }

    @Test
    fun rotateAndFiles() {
        assertEquals(ViewerCommand.ROTATE_RIGHT, ViewerShortcuts.commandFor(KeyChord("R", ctrl = true)))
        assertEquals(ViewerCommand.ROTATE_LEFT, ViewerShortcuts.commandFor(KeyChord("R", ctrl = true, shift = true)))
        assertEquals(ViewerCommand.OPEN, ViewerShortcuts.commandFor(KeyChord("O", ctrl = true)))
        assertEquals(ViewerCommand.SAVE, ViewerShortcuts.commandFor(KeyChord("S", ctrl = true)))
    }

    @Test
    fun mediaFindEditAndFileCommands() {
        assertEquals(ViewerCommand.PLAY_PAUSE, ViewerShortcuts.commandFor(KeyChord("Space")))
        assertEquals(ViewerCommand.FIND, ViewerShortcuts.commandFor(KeyChord("F", ctrl = true)))
        assertEquals(ViewerCommand.EDIT, ViewerShortcuts.commandFor(KeyChord("E", ctrl = true)))
        assertEquals(ViewerCommand.PRINT, ViewerShortcuts.commandFor(KeyChord("P", ctrl = true)))
        assertEquals(ViewerCommand.RENAME, ViewerShortcuts.commandFor(KeyChord("F2")))
        assertEquals(ViewerCommand.TRASH, ViewerShortcuts.commandFor(KeyChord("Delete")))
        assertEquals(ViewerCommand.CLOSE, ViewerShortcuts.commandFor(KeyChord("Escape")))
    }

    @Test
    fun unboundKeys() {
        assertNull(ViewerShortcuts.commandFor(KeyChord("R")))
        assertNull(ViewerShortcuts.commandFor(KeyChord("Left", ctrl = true)))
    }
}
