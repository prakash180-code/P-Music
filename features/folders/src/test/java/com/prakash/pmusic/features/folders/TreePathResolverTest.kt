package com.prakash.pmusic.features.folders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the Storage Access Framework tree-URI resolver: internal-storage
 * and removable-volume roots, URL decoding, nested folders, and rejection of
 * unresolvable URIs.
 */
class TreePathResolverTest {

    @Test
    fun `primary root resolves to internal storage`() {
        assertEquals(
            "/storage/emulated/0/Music",
            TreePathResolver.resolve("content://com.android.externalstorage.documents/tree/primary%3AMusic")
        )
    }

    @Test
    fun `nested primary folder resolves with sub path`() {
        assertEquals(
            "/storage/emulated/0/Music/Tamil/Deep",
            TreePathResolver.resolve("content://com.android.externalstorage.documents/tree/primary%3AMusic%2FTamil%2FDeep")
        )
    }

    @Test
    fun `removable volume resolves under its id`() {
        assertEquals(
            "/storage/1234-ABCD/Music",
            TreePathResolver.resolve("content://com.android.externalstorage.documents/tree/1234-ABCD%3AMusic")
        )
    }

    @Test
    fun `blank uri resolves to null`() {
        assertNull(TreePathResolver.resolve(""))
        assertNull(TreePathResolver.resolve("   "))
    }

    @Test
    fun `non tree uri resolves to null`() {
        assertNull(TreePathResolver.resolve("content://com.android.documentsui/files"))
    }

    @Test
    fun `unknown root resolves to null`() {
        assertNull(TreePathResolver.resolve("content://com.android.externalstorage.documents/tree/foo%3AMusic"))
    }

    @Test
    fun `root of primary without folder resolves to base`() {
        assertEquals(
            "/storage/emulated/0",
            TreePathResolver.resolve("content://com.android.externalstorage.documents/tree/primary%3A")
        )
    }
}
