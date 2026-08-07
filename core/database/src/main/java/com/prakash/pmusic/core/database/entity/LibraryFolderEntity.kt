package com.prakash.pmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a configured library folder rule.
 *
 * [type] stores the [com.prakash.pmusic.domain.model.LibraryFolderType] name
 * ("INCLUDED"/"EXCLUDED"); [enabled] decides whether the rule is currently
 * applied. [folderPath] is a normalized absolute path (lower-cased comparison
 * happens in the matcher, the stored value keeps the user's casing).
 *
 * Rules live in Room so Android Auto Backup carries them between devices and
 * restores the user's library-scope decisions on a fresh install.
 */
@Entity(
    tableName = "library_folders",
    indices = [Index(value = ["folderPath"], unique = true)]
)
data class LibraryFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderPath: String,
    val displayName: String,
    val type: String,
    val enabled: Boolean,
    val recursive: Boolean,
    val lastScanned: Long?,
    val songCount: Int,
    val dateAdded: Long
)
