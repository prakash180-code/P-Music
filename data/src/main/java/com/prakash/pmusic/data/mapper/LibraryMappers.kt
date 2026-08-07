package com.prakash.pmusic.data.mapper

import com.prakash.pmusic.core.database.entity.SongEntity
import com.prakash.pmusic.core.database.model.AlbumProjection
import com.prakash.pmusic.core.database.model.ArtistProjection
import com.prakash.pmusic.core.database.model.GenreProjection
import com.prakash.pmusic.domain.model.Album
import com.prakash.pmusic.domain.model.Artist
import com.prakash.pmusic.domain.model.Genre
import com.prakash.pmusic.domain.model.Song

/**
 * Maps persistence-layer types (Room entities/projections) into pure domain
 * models. Kept as top-level extension functions so they are trivially
 * unit-testable and importable by name.
 */

fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    albumArtist = albumArtist,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    genre = genre,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    path = path,
    dateAdded = dateAdded,
    dateModified = dateModified,
    composer = composer,
    playCount = playCount,
    skipCount = skipCount,
    lastPlayedAt = lastPlayedAt,
    isFavorite = isFavorite,
    bitrate = bitrate,
    sampleRate = sampleRate,
    channels = channels,
    artPath = artPath
)

fun AlbumProjection.toDomain(): Album = Album(
    id = id,
    name = name,
    artist = artist,
    year = year,
    songCount = songCount,
    durationMs = durationMs,
    artPath = artPath
)

fun ArtistProjection.toDomain(): Artist = Artist(
    id = id,
    name = name,
    albumCount = albumCount,
    songCount = songCount
)

fun GenreProjection.toDomain(): Genre = Genre(
    // MediaStore genre ids are device-specific, so a stable hash of the
    // genre name is used as the app-level identifier.
    id = name.hashCode().toLong(),
    name = name,
    songCount = songCount
)
