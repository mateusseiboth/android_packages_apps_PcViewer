package org.lineageos.pcviewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process

/** Hands the shown file to other apps: share it, or edit it (text goes to the text editor). */
object FileIntents {
    fun share(context: Context, item: ViewerItem): Boolean {
        val send = Intent(Intent.ACTION_SEND)
            .setType(item.mime ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, item.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, Intent.createChooser(send, null))
    }

    /** Write access is passed on only when the viewer holds it; granting what it lacks would fail. */
    fun edit(context: Context, item: ViewerItem): Boolean {
        val canWrite = context.checkUriPermission(
            item.uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
        val writeFlag = Intent.FLAG_GRANT_WRITE_URI_PERMISSION.takeIf { canWrite } ?: 0
        val edit = Intent(Intent.ACTION_EDIT)
            .setDataAndType(item.uri, item.mime ?: "text/plain")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or writeFlag or Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, edit)
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }
            .map { true }
            .recover { error -> (error as? ActivityNotFoundException)?.let { false } ?: throw error }
            .getOrThrow()
}
