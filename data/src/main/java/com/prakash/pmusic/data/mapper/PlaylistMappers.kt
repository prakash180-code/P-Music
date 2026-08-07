package com.prakash.pmusic.data.mapper

import com.prakash.pmusic.core.database.model.PlaylistProjection
import com.prakash.pmusic.domain.model.Playlist

/**
 * Maps persistence-layer types (Room entities/projections) into pure domain
 * models. Kept as top-level extension functions so they are trivially
 * unit-testable and importable by name.
 */

/**
 * Projects a playlist row into the domain model.
 *
 * @param smartCount for a smart playlist, its live derived song count, which
 *   overrides the join-table count (always zero for smart playlists, since
 *   their contents are never stored).
 */
fun PlaylistProjection.toDomain(smartCount: Int? = null): Playlist = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    songCount = smartCount ?: songCount,
    rule = rule
)
