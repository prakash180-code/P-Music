package com.prakash.pmusic.data.file

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.provider.MediaStore
import com.prakash.pmusic.domain.model.AudioFileDetails
import com.prakash.pmusic.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily reads audio container headers with [MediaExtractor] to fill in the
 * fields MediaStore does not index (sample rate, channel count, bitrate).
 *
 * The reader opens the song's content Uri and inspects the first audio track.
 * It never touches more than the headers the extractor itself parses, and it
 * returns null on any failure so callers can fall back to indexed values.
 */
@Singleton
class AudioFileMetadataReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Extracts file details for [song], or null if the track cannot be read.
     */
    fun read(song: Song): AudioFileDetails? {
        val extractor = MediaExtractor()
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                song.id
            )
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue

                val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else {
                    0
                }
                val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else {
                    0
                }
                val bitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    format.getInteger(MediaFormat.KEY_BIT_RATE)
                } else {
                    0
                }
                return AudioFileDetails(
                    sampleRateHz = sampleRate,
                    channelCount = channels,
                    bitrate = bitrate,
                    mimeType = mime
                )
            }
        } catch (_: Exception) {
            // Unreadable/unsupported file; caller falls back to indexed data.
        } finally {
            runCatching { extractor.release() }
        }
        return null
    }
}
