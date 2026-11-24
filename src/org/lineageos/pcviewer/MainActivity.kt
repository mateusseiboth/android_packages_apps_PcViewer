package org.lineageos.pcviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.lineageos.pcviewer.core.Gallery
import org.lineageos.pcviewer.ui.ViewerScreen
import org.lineageos.pcviewer.ui.ViewerTheme

class MainActivity : ComponentActivity() {
    private val vm: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        savedInstanceState ?: show(intent)
        setContent {
            ViewerTheme { ViewerScreen(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        show(intent)
    }

    /**
     * VIEW, QUICK_VIEW and EDIT all carry the file in the data; the file manager's quick view adds the
     * folder's other files as clip items and the shown one's position as EXTRA_INDEX.
     */
    private fun show(intent: Intent?) {
        intent ?: return
        val clip = intent.clipData
        val siblings = clip?.let { (0 until it.itemCount).mapNotNull { i -> it.getItemAt(i).uri } }.orEmpty()
        val index = intent.getIntExtra(Intent.EXTRA_INDEX, -1).takeIf { it >= 0 }
        val data: Uri? = intent.data
        if (data == null && siblings.isEmpty()) return
        vm.show(Gallery.of(data, siblings, index))
    }
}
