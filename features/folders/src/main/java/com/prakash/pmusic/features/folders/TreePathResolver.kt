package com.prakash.pmusic.features.folders

import java.net.URLDecoder

/**
 * Converts a Storage Access Framework tree URI (returned by the folder picker)
 * into the real absolute path the scanner compares against MediaStore `DATA`
 * paths.
 *
 * Tree document ids look like `primary:Music` for internal storage or
 * `XXXX-XXXX:Music` for removable volumes; the resolver maps those prefixes to
 * the filesystem roots MediaStore reports. Folders the platform does not
 * expose as plain paths (e.g. some USB providers) resolve to null and the
 * caller shows a friendly error instead.
 *
 * Kept as pure string handling so it is unit-testable without Android.
 */
object TreePathResolver {

    private val VOLUME_ID = Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$")

    /**
     * @param treeUri the `OpenDocumentTree` result, e.g.
     * `content://com.android.externalstorage.documents/tree/primary%3AMusic`.
     * @return the absolute path (no trailing slash), or null when the URI is
     * not a resolvable storage tree.
     */
    fun resolve(treeUri: String): String? {
        if (treeUri.isBlank()) return null
        val encoded = treeUri.substringAfter("/tree/", missingDelimiterValue = "")
        if (encoded.isEmpty()) return null

        val documentId = URLDecoder.decode(encoded, Charsets.UTF_8.name())
        val parts = documentId.split(':', limit = 2)
        val root = parts[0]
        val relative = parts.getOrElse(1) { "" }

        val base = when {
            root.equals("primary", ignoreCase = true) -> "/storage/emulated/0"
            VOLUME_ID.matches(root) -> "/storage/$root"
            else -> return null
        }
        return (base + "/" + relative.trim('/')).trimEnd('/')
    }
}
