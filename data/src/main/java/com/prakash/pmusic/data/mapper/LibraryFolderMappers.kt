package com.prakash.pmusic.data.mapper

import com.prakash.pmusic.core.database.entity.LibraryFolderEntity
import com.prakash.pmusic.domain.model.LibraryFolder
import com.prakash.pmusic.domain.model.LibraryFolderType

/**
 * Maps between the Room library folder entity and the domain model.
 *
 * [LibraryFolderType] is stored by name in the `type` column so the schema
 * stays string-friendly and forward-compatible.
 */

fun LibraryFolderEntity.toDomain(): LibraryFolder = LibraryFolder(
    id = id,
    folderPath = folderPath,
    displayName = displayName,
    type = type.toFolderType(),
    enabled = enabled,
    recursive = recursive,
    lastScanned = lastScanned,
    songCount = songCount,
    dateAdded = dateAdded
)

fun LibraryFolder.toEntity(): LibraryFolderEntity = LibraryFolderEntity(
    id = id,
    folderPath = folderPath,
    displayName = displayName,
    type = type.name,
    enabled = enabled,
    recursive = recursive,
    lastScanned = lastScanned,
    songCount = songCount,
    dateAdded = dateAdded
)

fun LibraryFolderType.toDb(): String = name

fun String.toFolderType(): LibraryFolderType =
    runCatching { LibraryFolderType.valueOf(this) }
        .getOrDefault(LibraryFolderType.INCLUDED)
