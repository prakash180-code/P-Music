package com.prakash.pmusic.data.mapper

import com.prakash.pmusic.core.database.model.PlaylistProjection
import com.prakash.pmusic.domain.model.Playlist

/**
 * Maps persistence-layer types (Room entities/projections) into pure domain
 * models. Kept as top-level extension functions so they are trivially
 * unit-testable and importable by name.
 */

fun PlaylistProjection.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    songCount = songCount
)
