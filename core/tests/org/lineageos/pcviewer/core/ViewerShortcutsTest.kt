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
    fun unboundKeys() {
        assertNull(ViewerShortcuts.commandFor(KeyChord("R")))
        assertNull(ViewerShortcuts.commandFor(KeyChord("Left", ctrl = true)))
    }
}
