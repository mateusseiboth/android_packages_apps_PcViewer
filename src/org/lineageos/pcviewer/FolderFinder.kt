package org.lineageos.pcviewer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import org.lineageos.pcviewer.core.DocumentIds
import org.lineageos.pcviewer.core.FileKind
import org.lineageos.pcviewer.core.FileKinds
import org.lineageos.pcviewer.core.Gallery
import org.lineageos.pcviewer.core.NaturalOrder

/** What the user must allow before the viewer can list a file's folder. */
sealed interface FolderAccess {
    data object Media : FolderAccess

    data class Folder(val initialUri: Uri) : FolderAccess
}

sealed interface FolderResult {
    data class Found(val gallery: Gallery<Uri>) : FolderResult

    data class Ask(val access: FolderAccess) : FolderResult

    data object Unknown : FolderResult
}

/**
 * Finds the other files in the folder of a file opened on its own. Photos, videos and audio are listed
 * through MediaStore (with the media read permission); other files on shared storage through a folder the
 * user granted once (the grant is kept). Providers whose folders can't be known are left alone.
 */
class FolderFinder(private val context: Context) {
    private val resolver = context.contentResolver
    private val files = FileAccess(context)

    private val mediaKinds = setOf(FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO)

    private val collections: Map<Int, (String) -> Uri> = mapOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE to MediaStore.Images.Media::getContentUri,
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO to MediaStore.Video.Media::getContentUri,
        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO to MediaStore.Audio.Media::getContentUri,
    )

    fun find(uri: Uri, kind: FileKind): FolderResult {
        val media = files.mediaUriOf(uri)?.takeIf { kind in mediaKinds }
        media ?: return findInFolder(uri)
        if (!hasMediaAccess()) return FolderResult.Ask(FolderAccess.Media)
        return mediaSiblings(media)?.let { FolderResult.Found(it) } ?: FolderResult.Unknown
    }

    /** Called with the folder the user picked; null when the shown file is not in it. */
    fun listGranted(tree: Uri, shown: Uri): Gallery<Uri>? {
        runCatching { resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        return listTree(tree, shown)
    }

    fun hasMediaAccess(): Boolean = MEDIA_PERMISSIONS.any {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun findInFolder(uri: Uri): FolderResult {
        val authority = uri.authority.orEmpty()
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return FolderResult.Unknown
        val parent = DocumentIds.parentOf(authority, documentId) ?: return FolderResult.Unknown
        val granted = resolver.persistedUriPermissions.map { it.uri }.firstOrNull { tree ->
            tree.authority == authority && DocumentsContract.isTreeUri(tree) &&
                isInside(parent, DocumentsContract.getTreeDocumentId(tree))
        }
        granted ?: return FolderResult.Ask(FolderAccess.Folder(DocumentsContract.buildDocumentUri(authority, parent)))
        return listTree(granted, uri)?.let { FolderResult.Found(it) } ?: FolderResult.Unknown
    }

    private fun listTree(tree: Uri, shown: Uri): Gallery<Uri>? {
        val shownId = runCatching { DocumentsContract.getDocumentId(shown) }.getOrNull() ?: return null
        val folder = DocumentIds.parentOf(tree.authority.orEmpty(), shownId) ?: return null
        if (!isInside(folder, DocumentsContract.getTreeDocumentId(tree))) return null
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder)
        val rows = resolver.query(children, columns, null, null, null)?.use { cursor ->
            generateSequence { cursor.takeIf { it.moveToNext() } }
                .map { Triple(it.getString(0), it.getString(1).orEmpty(), it.getString(2)) }
                .toList()
        } ?: return null
        val shown = rows.filter { (id, name, mime) ->
            id == shownId || (mime != DocumentsContract.Document.MIME_TYPE_DIR && FileKinds.detect(mime, name) != FileKind.UNSUPPORTED)
        }.sortedWith(compareBy(NaturalOrder) { it.second })
        val index = shown.indexOfFirst { it.first == shownId }.takeIf { it >= 0 } ?: return null
        return Gallery(shown.map { DocumentsContract.buildDocumentUriUsingTree(tree, it.first) }, index)
    }

    /** A folder is inside a granted tree when it is the tree's folder or one below it. */
    private fun isInside(folder: String, treeId: String): Boolean =
        folder == treeId || folder.startsWith("$treeId/") || (treeId.endsWith(":") && folder.startsWith(treeId))

    private fun mediaSiblings(media: Uri): Gallery<Uri>? {
        val volume = runCatching { MediaStore.getVolumeName(media) }.getOrNull() ?: return null
        val id = ContentUris.parseId(media)
        val folder = resolver.query(media, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use {
            it.takeIf { c -> c.moveToFirst() }?.getString(0)
        } ?: return null
        val columns = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?, ?)"
        val arguments = arrayOf(folder) + collections.keys.map { it.toString() }
        val rows = resolver.query(MediaStore.Files.getContentUri(volume), columns, selection, arguments, null)?.use { cursor ->
            generateSequence { cursor.takeIf { it.moveToNext() } }
                .map { Triple(it.getLong(0), it.getString(1).orEmpty(), it.getInt(2)) }
                .toList()
        } ?: return null
        val sorted = rows.sortedWith(compareBy(NaturalOrder) { it.second })
        val index = sorted.indexOfFirst { it.first == id }.takeIf { it >= 0 } ?: return null
        val uris = sorted.map { (rowId, _, type) -> ContentUris.withAppendedId(collections.getValue(type)(volume), rowId) }
        return Gallery(uris, index)
    }

    companion object {
        val MEDIA_PERMISSIONS = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    }
}
