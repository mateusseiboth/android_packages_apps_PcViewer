package org.lineageos.pcviewer.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.lineageos.pcviewer.core.KeyChord

/** Names of the keys the shortcut table (core ViewerShortcuts) uses. */
private val KEY_NAMES = mapOf(
    Key.DirectionLeft to "Left", Key.DirectionRight to "Right", Key.Plus to "Plus", Key.NumPadAdd to "Plus",
    Key.Equals to "Equals", Key.Minus to "Minus", Key.NumPadSubtract to "Minus", Key.Zero to "0",
    Key.R to "R", Key.O to "O", Key.S to "S", Key.Spacebar to "Space", Key.F to "F", Key.E to "E", Key.P to "P",
    Key.F2 to "F2", Key.Delete to "Delete", Key.Escape to "Escape",
)

fun KeyEvent.toChord(): KeyChord? {
    if (type != KeyEventType.KeyDown) return null
    val name = KEY_NAMES[key] ?: return null
    return KeyChord(name, ctrl = isCtrlPressed, shift = isShiftPressed, alt = isAltPressed)
}
