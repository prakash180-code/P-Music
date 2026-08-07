package com.prakash.pmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single audio track.
 *
 * This is the single source of truth for the library. Every column is
 * indexed that features commonly filter/sort on (album, artist, genre,
 * title, recently added, most played) so queries stay fast at 100k+ rows.
 *
 * User state (favorite, play counts) lives on the same row so a media scan
 * (REPLACE insert) cannot lose it; the scanner carries these values over.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index("albumId"),
        Index("artistId"),
        Index("genre"),
        Index("title"),
        Index("dateAdded"),
        Index("playCount"),
        Index("lastPlayedAt"),
        Index("isFavorite")
    ]
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val albumArtist: String?,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val genre: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val path: String,
    val dateAdded: Long,
    val dateModified: Long,
    val composer: String?,
    val playCount: Int,
    val skipCount: Int,
    val lastPlayedAt: Long?,
    val isFavorite: Boolean,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    /** Absolute path of the album artwork, denormalised for fast album queries. */
    val artPath: String?
)
